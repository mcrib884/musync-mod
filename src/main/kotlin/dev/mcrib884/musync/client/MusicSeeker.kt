package dev.mcrib884.musync.client

import dev.mcrib884.musync.mixin.ChannelAccessor
import dev.mcrib884.musync.mixin.SoundEngineAccessor
import dev.mcrib884.musync.mixin.SoundManagerAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.ChannelAccess
import org.lwjgl.openal.AL10

object MusicSeeker {

    private fun findHandle(instance: SoundInstance): ChannelAccess.ChannelHandle? {
        val soundManager = Minecraft.getInstance().soundManager as? SoundManagerAccessor ?: return null
        val soundEngine = soundManager.soundEngineField as? SoundEngineAccessor ?: return null
        return soundEngine.instanceToChannelField[instance]
    }

    fun pauseSound(instance: SoundInstance): Boolean {
        return try {
            val handle = findHandle(instance)
            if (handle != null) {
                handle.execute { channel ->
                    val sourceId = (channel as ChannelAccessor).sourceField
                    PausedSourceTracker.markPaused(sourceId)
                    AL10.alSourcePause(sourceId)
                    dev.mcrib884.musync.MuSyncLog.debug("Channel paused (source=$sourceId)")
                }
                true
            } else {
                dev.mcrib884.musync.MuSyncLog.debug("No channel found for sound instance")
                false
            }
        } catch (e: Exception) {
            dev.mcrib884.musync.MuSyncLog.error("Error pausing channel: ${e.message}")
            false
        }
    }

    fun resumeSound(instance: SoundInstance): Boolean {
        return try {
            val handle = findHandle(instance)
            if (handle != null) {
                handle.execute { channel ->
                    val sourceId = (channel as ChannelAccessor).sourceField
                    PausedSourceTracker.markResumed(sourceId)
                    AL10.alSourcePlay(sourceId)
                    dev.mcrib884.musync.MuSyncLog.debug("Channel resumed (source=$sourceId)")
                }
                true
            } else {
                dev.mcrib884.musync.MuSyncLog.debug("No channel found for sound instance (may have been cleaned up)")
                false
            }
        } catch (e: Exception) {
            dev.mcrib884.musync.MuSyncLog.error("Error resuming channel: ${e.message}")
            false
        }
    }

    fun hasChannel(instance: SoundInstance): Boolean {
        return try {
            findHandle(instance) != null
        } catch (_: Exception) {
            false
        }
    }

}
