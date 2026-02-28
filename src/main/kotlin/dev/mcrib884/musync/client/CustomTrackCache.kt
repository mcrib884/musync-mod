package dev.mcrib884.musync.client

import java.util.concurrent.ConcurrentHashMap

object CustomTrackCache {
    private val cache = ConcurrentHashMap<String, ByteArray>()

    private val pending = ConcurrentHashMap<String, Pair<Int, MutableMap<Int, ByteArray>>>()

    fun handleChunk(trackName: String, chunkIndex: Int, totalChunks: Int, data: ByteArray) {
        val entry = pending.getOrPut(trackName) { Pair(totalChunks, mutableMapOf()) }
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
            cache[trackName] = assembled
            pending.remove(trackName)
            println("[MuSync] Cached custom track: $trackName (${assembled.size} bytes)")
        } else {
            println("[MuSync] Received chunk ${chunkIndex + 1}/$totalChunks for $trackName")
        }
    }

    fun get(trackName: String): ByteArray? = cache[trackName]

    fun has(trackName: String): Boolean = cache.containsKey(trackName)

    fun put(trackName: String, data: ByteArray) {
        cache[trackName] = data
    }

    fun clear() {
        cache.clear()
        pending.clear()
    }
}
