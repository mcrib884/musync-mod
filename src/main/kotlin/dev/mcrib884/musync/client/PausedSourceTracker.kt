package dev.mcrib884.musync.client

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object PausedSourceTracker {
    private val pausedSources: MutableSet<Int> =
        Collections.newSetFromMap(ConcurrentHashMap())
    private var lastEvictTime: Long = 0L
    private const val EVICT_INTERVAL_MS = 30_000L

    fun markPaused(sourceId: Int) {
        pausedSources.add(sourceId)
    }

    fun markResumed(sourceId: Int) {
        pausedSources.remove(sourceId)
    }

    @JvmStatic
    fun isMuSyncPaused(sourceId: Int): Boolean {
        evictStale()
        return pausedSources.contains(sourceId)
    }

    fun evictStale() {
        val now = System.currentTimeMillis()
        if (now - lastEvictTime < EVICT_INTERVAL_MS) return
        lastEvictTime = now
        pausedSources.removeIf { sourceId ->
            try {
                val state = org.lwjgl.openal.AL10.alGetSourcei(sourceId, org.lwjgl.openal.AL10.AL_SOURCE_STATE)
                state != org.lwjgl.openal.AL10.AL_PLAYING && state != org.lwjgl.openal.AL10.AL_PAUSED
            } catch (_: Exception) {
                true
            }
        }
    }

    fun clear() {
        pausedSources.clear()
    }
}
