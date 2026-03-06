package dev.mcrib884.musync.client

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks active jukeboxes (disc playing) across dimensions.
 * Fed by JukeboxBlockEntityMixin on both start and stop events.
 * Queried by ClientMusicPlayer on client tick for distance-based volume ducking.
 */
object JukeboxTracker {

    private val logger = org.apache.logging.log4j.LogManager.getLogger("MuSync")

    /** Maximum distance (blocks) at which a jukebox mutes MuSync music. Matches vanilla audible range. */
    const val JUKEBOX_RANGE = 64.0
    private const val JUKEBOX_RANGE_SQ = JUKEBOX_RANGE * JUKEBOX_RANGE

    /** dimension -> set of active jukebox positions */
    private val activeJukeboxes = ConcurrentHashMap<ResourceKey<Level>, MutableSet<BlockPos>>()

    fun onJukeboxStartPlaying(dimension: ResourceKey<Level>, pos: BlockPos) {
        val set = activeJukeboxes.getOrPut(dimension) { ConcurrentHashMap.newKeySet() }
        if (set.add(pos)) {
            logger.debug("Jukebox started at {} in {}", pos, dimension.location())
        }
    }

    fun onJukeboxStopPlaying(dimension: ResourceKey<Level>, pos: BlockPos) {
        val set = activeJukeboxes[dimension] ?: return
        if (set.remove(pos)) {
            logger.debug("Jukebox stopped at {} in {}", pos, dimension.location())
        }
        if (set.isEmpty()) activeJukeboxes.remove(dimension)
    }

    /**
     * Returns true if the player at [playerPos] in [dimension] is within
     * [JUKEBOX_RANGE] blocks of any currently-playing jukebox.
     */
    fun isNearActiveJukebox(dimension: ResourceKey<Level>, playerPos: BlockPos): Boolean {
        val set = activeJukeboxes[dimension] ?: return false
        for (jukeboxPos in set) {
            val dx = (playerPos.x - jukeboxPos.x).toDouble()
            val dy = (playerPos.y - jukeboxPos.y).toDouble()
            val dz = (playerPos.z - jukeboxPos.z).toDouble()
            if (dx * dx + dy * dy + dz * dz <= JUKEBOX_RANGE_SQ) {
                return true
            }
        }
        return false
    }

    /** Clear all tracked jukeboxes (e.g., on disconnect). */
    fun clear() {
        activeJukeboxes.clear()
    }
}
