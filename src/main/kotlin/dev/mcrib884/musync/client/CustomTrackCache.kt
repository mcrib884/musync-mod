package dev.mcrib884.musync.client

import dev.mcrib884.musync.network.PacketIO
import net.minecraft.client.Minecraft
import java.io.File
import java.io.RandomAccessFile
import java.util.BitSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

object CustomTrackCache {
    
    private const val MAX_CACHE_BYTES = 100L * 1024 * 1024
    private const val MAX_ENTRIES = 30
    private const val MAX_PENDING = 5
    private const val MAX_TOTAL_CHUNKS = Int.MAX_VALUE
    private const val PENDING_TIMEOUT_MS = 60_000L

    private val cache = ConcurrentHashMap<String, ByteArray>()
    private val insertionOrder = ConcurrentLinkedDeque<String>()
    private val totalCachedBytes = AtomicLong(0)

    private data class PendingTrack(
        val totalChunks: Int,
        val tempFile: File,
        val receivedChunks: BitSet,
        var totalBytes: Long,
        var lastUpdateMs: Long
    )

    private val pending = ConcurrentHashMap<String, PendingTrack>()

    private fun baseName(name: String): String = name.substringBeforeLast('.', name)

    private fun getPendingFolder(): File {
        val folder = File(Minecraft.getInstance().gameDirectory, "musynctemp")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    private fun sanitizeTempName(name: String): String {
        return buildString(name.length) {
            for (ch in name) {
                append(if (ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == '.') ch else '_')
            }
        }
    }

    private fun deleteTempFile(file: File?) {
        if (file == null) return
        try {
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
    }

    fun handleChunk(trackName: String, chunkIndex: Int, totalChunks: Int, data: ByteArray) {
        purgeExpiredPending()

        if (totalChunks <= 0 || totalChunks > MAX_TOTAL_CHUNKS || chunkIndex < 0 || chunkIndex >= totalChunks) {
            dev.mcrib884.musync.MuSyncLog.warn("Invalid chunk metadata for $trackName: chunk=$chunkIndex total=$totalChunks")
            return
        }

        if (pending.size >= MAX_PENDING && !pending.containsKey(trackName)) {
            dev.mcrib884.musync.MuSyncLog.warn("Too many pending track downloads, rejecting $trackName")
            return
        }

        val now = System.currentTimeMillis()
        val entry = pending.getOrPut(trackName) {
            val pendingFile = File(getPendingFolder(), sanitizeTempName(trackName) + ".part")
            deleteTempFile(pendingFile)
            PendingTrack(totalChunks, pendingFile, BitSet(totalChunks), 0L, now)
        }

        if (entry.totalChunks != totalChunks) {
            dev.mcrib884.musync.MuSyncLog.warn("Chunk count mismatch for $trackName: expected=${entry.totalChunks} got=$totalChunks")
            pending.remove(trackName)
            deleteTempFile(entry.tempFile)
            return
        }

        val chunkOffset = chunkIndex.toLong() * dev.mcrib884.musync.network.CustomTrackDataPacket.CHUNK_SIZE.toLong()
        try {
            RandomAccessFile(entry.tempFile, "rw").use { raf ->
                raf.seek(chunkOffset)
                raf.write(data)
            }
        } catch (e: Exception) {
            pending.remove(trackName)
            deleteTempFile(entry.tempFile)
            ClientTrackManager.onTrackFailed(trackName, "failed to write chunk: ${e.message}")
            return
        }

        val previousSize = if (entry.receivedChunks.get(chunkIndex)) data.size.toLong() else 0L
        entry.receivedChunks.set(chunkIndex)
        entry.lastUpdateMs = now
        entry.totalBytes += (data.size.toLong() - previousSize).coerceAtLeast(0L)
        val acceptedBytes = (data.size.toLong() - previousSize).coerceAtLeast(0L)

        ClientTrackManager.onChunkProgress(trackName, chunkIndex, totalChunks, acceptedBytes)

        if (entry.receivedChunks.cardinality() == totalChunks) {
            val totalSizeLong = entry.totalBytes
            if (totalSizeLong <= 0L || totalSizeLong > PacketIO.MAX_TRACK_SIZE_BYTES) {
                dev.mcrib884.musync.MuSyncLog.warn("Rejecting assembled track for $trackName due to invalid size: $totalSizeLong")
                pending.remove(trackName)
                deleteTempFile(entry.tempFile)
                ClientTrackManager.onTrackFailed(trackName, "invalid assembled size")
                return
            }
            pending.remove(trackName)
            ClientTrackManager.onTrackDownloaded(trackName, totalChunks, entry.tempFile, totalSizeLong)
            dev.mcrib884.musync.MuSyncLog.info("Assembled custom track to temp file: $trackName ($totalSizeLong bytes)")
        } else {
            dev.mcrib884.musync.MuSyncLog.debug("Received chunk ${chunkIndex + 1}/$totalChunks for $trackName")
        }
    }

    private fun purgeExpiredPending() {
        val expireBefore = System.currentTimeMillis() - PENDING_TIMEOUT_MS
        val iterator = pending.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.lastUpdateMs < expireBefore) {
                dev.mcrib884.musync.MuSyncLog.warn("Expiring incomplete track download: ${entry.key}")
                deleteTempFile(entry.value.tempFile)
                iterator.remove()
            }
        }
    }

    @Synchronized
    private fun putWithEviction(name: String, data: ByteArray) {
        val old = cache.put(name, data)
        if (old != null) {
            totalCachedBytes.addAndGet(-old.size.toLong())
            insertionOrder.remove(name)
        }
        totalCachedBytes.addAndGet(data.size.toLong())
        insertionOrder.addLast(name)

        while ((totalCachedBytes.get() > MAX_CACHE_BYTES || cache.size > MAX_ENTRIES) && insertionOrder.isNotEmpty()) {
            val evict = insertionOrder.pollFirst() ?: break
            val removed = cache.remove(evict)
            if (removed != null) {
                totalCachedBytes.addAndGet(-removed.size.toLong())
                dev.mcrib884.musync.MuSyncLog.debug("Evicted cached track: $evict (${removed.size} bytes)")
            }
        }
    }

    fun get(trackName: String): ByteArray? {
        cache[trackName]?.let { return it }
        val base = baseName(trackName)
        if (base == trackName) {
            val matches = cache.entries.filter { it.key.startsWith("$trackName.") }
            if (matches.size == 1) {
                return matches.first().value
            }
        }
        return null
    }

    fun has(trackName: String): Boolean = cache.containsKey(trackName)

    fun put(trackName: String, data: ByteArray) {
        putWithEviction(trackName, data)
    }

    @Synchronized
    fun remove(trackName: String) {
        val removed = cache.remove(trackName)
        if (removed != null) {
            totalCachedBytes.addAndGet(-removed.size.toLong())
            insertionOrder.remove(trackName)
        }
    }

    fun clear() {
        cache.clear()
        pending.values.forEach { deleteTempFile(it.tempFile) }
        pending.clear()
        insertionOrder.clear()
        totalCachedBytes.set(0)
    }
}
