package dev.mcrib884.musync.client

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import org.lwjgl.openal.AL10

object MusicSeeker {

    private val logger = org.apache.logging.log4j.LogManager.getLogger("MuSync")

    fun pauseSound(instance: SoundInstance): Boolean {
        return try {
            val soundManager = Minecraft.getInstance().soundManager
            val soundEngine = soundManager.soundEngine
            val handle = soundEngine.instanceToChannel[instance]
            if (handle != null) {
                handle.execute { channel ->
                    val sourceId = channel.source
                    PausedSourceTracker.markPaused(sourceId)
                    AL10.alSourcePause(sourceId)
                    logger.debug("Channel paused (source=$sourceId)")
                }
                true
            } else {
                logger.debug("No channel found for sound instance")
                false
            }
        } catch (e: Exception) {
            logger.error("Error pausing channel: ${e.message}")
            false
        }
    }

    fun resumeSound(instance: SoundInstance): Boolean {
        return try {
            val soundManager = Minecraft.getInstance().soundManager
            val soundEngine = soundManager.soundEngine
            val handle = soundEngine.instanceToChannel[instance]
            if (handle != null) {
                handle.execute { channel ->
                    val sourceId = channel.source
                    PausedSourceTracker.markResumed(sourceId)
                    AL10.alSourcePlay(sourceId)
                    logger.debug("Channel resumed (source=$sourceId)")
                }
                true
            } else {
                logger.debug("No channel found for sound instance (may have been cleaned up)")
                false
            }
        } catch (e: Exception) {
            logger.error("Error resuming channel: ${e.message}")
            false
        }
    }

    fun hasChannel(instance: SoundInstance): Boolean {
        return try {
            val soundManager = Minecraft.getInstance().soundManager
            val soundEngine = soundManager.soundEngine
            soundEngine.instanceToChannel.containsKey(instance)
        } catch (_: Exception) {
            false
        }
    }

}
