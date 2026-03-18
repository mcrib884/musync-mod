package dev.mcrib884.musync.client

import dev.mcrib884.musync.network.PacketIO
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

object CustomTrackCache {
    
    private const val MAX_CACHE_BYTES = 100L * 1024 * 1024
    private const val MAX_ENTRIES = 30
    private const val MAX_PENDING = 5
    private const val MAX_TOTAL_CHUNKS = 5000
    private const val PENDING_TIMEOUT_MS = 60_000L

    private val cache = ConcurrentHashMap<String, ByteArray>()
    private val insertionOrder = ConcurrentLinkedDeque<String>()
    private val totalCachedBytes = AtomicLong(0)

    private data class PendingTrack(
        val totalChunks: Int,
        val chunks: MutableMap<Int, ByteArray>,
        var lastUpdateMs: Long
    )

    private val pending = ConcurrentHashMap<String, PendingTrack>()

    private fun baseName(name: String): String = name.substringBeforeLast('.', name)

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
        val entry = pending.getOrPut(trackName) { PendingTrack(totalChunks, mutableMapOf(), now) }

        if (entry.totalChunks != totalChunks) {
            dev.mcrib884.musync.MuSyncLog.warn("Chunk count mismatch for $trackName: expected=${entry.totalChunks} got=$totalChunks")
            pending.remove(trackName)
            return
        }

        val previous = entry.chunks.put(chunkIndex, data)
        entry.lastUpdateMs = now
        val acceptedBytes = if (previous == null) data.size else (data.size - previous.size).coerceAtLeast(0)

        ClientTrackManager.onChunkProgress(trackName, chunkIndex, totalChunks, acceptedBytes)

        if (entry.chunks.size == totalChunks) {
            val totalSizeLong = entry.chunks.values.sumOf { it.size.toLong() }
            if (totalSizeLong <= 0L || totalSizeLong > PacketIO.MAX_TRACK_SIZE_BYTES || totalSizeLong > Int.MAX_VALUE.toLong()) {
                dev.mcrib884.musync.MuSyncLog.warn("Rejecting assembled track for $trackName due to invalid size: $totalSizeLong")
                pending.remove(trackName)
                ClientTrackManager.onTrackFailed(trackName, "invalid assembled size")
                return
            }
            val totalSize = totalSizeLong.toInt()
            val assembled = ByteArray(totalSize)
            var offset = 0
            for (i in 0 until totalChunks) {
                val chunk = entry.chunks[i]
                if (chunk == null) {
                    dev.mcrib884.musync.MuSyncLog.warn("Missing chunk $i/$totalChunks for $trackName during assembly")
                    pending.remove(trackName)
                    ClientTrackManager.onTrackFailed(trackName, "missing chunk $i")
                    return
                }
                System.arraycopy(chunk, 0, assembled, offset, chunk.size)
                offset += chunk.size
            }
            putWithEviction(trackName, assembled)
            pending.remove(trackName)
            ClientTrackManager.onTrackDownloaded(trackName, totalChunks, assembled)
            dev.mcrib884.musync.MuSyncLog.info("Cached custom track: $trackName (${assembled.size} bytes)")
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

    fun clear() {
        cache.clear()
        pending.clear()
        insertionOrder.clear()
        totalCachedBytes.set(0)
    }
}
