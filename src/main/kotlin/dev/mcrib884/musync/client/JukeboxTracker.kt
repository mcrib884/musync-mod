package dev.mcrib884.musync.client

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
//? if >=1.21.11 {
/*import dev.mcrib884.musync.location*/
//?}
import net.minecraft.world.level.Level
import java.util.concurrent.ConcurrentHashMap

object JukeboxTracker {

    
    const val JUKEBOX_RANGE = 64.0
    private const val JUKEBOX_RANGE_SQ = JUKEBOX_RANGE * JUKEBOX_RANGE

    private val activeJukeboxes = ConcurrentHashMap<ResourceKey<Level>, MutableSet<BlockPos>>()

    fun onJukeboxStartPlaying(dimension: ResourceKey<Level>, pos: BlockPos) {
        val set = activeJukeboxes.getOrPut(dimension) { ConcurrentHashMap.newKeySet() }
        if (set.add(pos)) {
            dev.mcrib884.musync.MuSyncLog.debug("Jukebox started at {} in {}", pos, dimension.location())
        }
    }

    fun onJukeboxStopPlaying(dimension: ResourceKey<Level>, pos: BlockPos) {
        val set = activeJukeboxes[dimension] ?: return
        if (set.remove(pos)) {
            dev.mcrib884.musync.MuSyncLog.debug("Jukebox stopped at {} in {}", pos, dimension.location())
        }
        if (set.isEmpty()) activeJukeboxes.remove(dimension)
    }

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

    fun clear() {
        activeJukeboxes.clear()
    }
}
