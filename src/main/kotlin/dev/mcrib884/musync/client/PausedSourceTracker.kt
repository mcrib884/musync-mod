package dev.mcrib884.musync.client

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object PausedSourceTracker {
    private val pausedSources: MutableSet<Int> =
        Collections.newSetFromMap(ConcurrentHashMap())

    fun markPaused(sourceId: Int) {
        pausedSources.add(sourceId)
    }

    fun markResumed(sourceId: Int) {
        pausedSources.remove(sourceId)
    }

    @JvmStatic
    fun isMuSyncPaused(sourceId: Int): Boolean {
        return pausedSources.contains(sourceId)
    }

    fun clear() {
        pausedSources.clear()
    }
}
