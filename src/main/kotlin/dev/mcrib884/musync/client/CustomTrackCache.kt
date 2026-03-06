package dev.mcrib884.musync.client

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

object CustomTrackCache {
    private val logger = org.apache.logging.log4j.LogManager.getLogger("MuSync")

    private const val MAX_CACHE_BYTES = 200L * 1024 * 1024
    private const val MAX_ENTRIES = 50
    private const val MAX_PENDING = 20

    private val cache = ConcurrentHashMap<String, ByteArray>()
    private val insertionOrder = ConcurrentLinkedDeque<String>()
    private val totalCachedBytes = AtomicLong(0)

    private val pending = ConcurrentHashMap<String, Pair<Int, MutableMap<Int, ByteArray>>>()

    fun handleChunk(trackName: String, chunkIndex: Int, totalChunks: Int, data: ByteArray) {
        if (totalChunks <= 0 || totalChunks > 5000 || chunkIndex < 0 || chunkIndex >= totalChunks) {
            logger.warn("Invalid chunk metadata for $trackName: chunk=$chunkIndex total=$totalChunks")
            return
        }

        if (pending.size >= MAX_PENDING && !pending.containsKey(trackName)) {
            logger.warn("Too many pending track downloads, rejecting $trackName")
            return
        }

        val entry = pending.getOrPut(trackName) { Pair(totalChunks, mutableMapOf()) }

        if (entry.first != totalChunks) {
            logger.warn("Chunk count mismatch for $trackName: expected=${entry.first} got=$totalChunks")
            pending.remove(trackName)
            return
        }

        entry.second[chunkIndex] = data

        ClientTrackManager.onChunkReceived(trackName, chunkIndex, totalChunks, data)

        if (entry.second.size == totalChunks) {
            val totalSize = entry.second.values.sumOf { it.size }
            val assembled = ByteArray(totalSize)
            var offset = 0
            for (i in 0 until totalChunks) {
                val chunk = entry.second[i] ?: continue
                System.arraycopy(chunk, 0, assembled, offset, chunk.size)
                offset += chunk.size
            }
            putWithEviction(trackName, assembled)
            pending.remove(trackName)
            logger.info("Cached custom track: $trackName (${assembled.size} bytes)")
        } else {
            logger.debug("Received chunk ${chunkIndex + 1}/$totalChunks for $trackName")
        }
    }

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
                logger.debug("Evicted cached track: $evict (${removed.size} bytes)")
            }
        }
    }

    fun get(trackName: String): ByteArray? = cache[trackName]

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
