package dev.mcrib884.musync.client

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import org.lwjgl.openal.AL10

object MusicSeeker {

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
                    println("[MuSync] Channel paused (source=$sourceId)")
                }
                true
            } else {
                println("[MuSync] No channel found for sound instance")
                false
            }
        } catch (e: Exception) {
            println("[MuSync] Error pausing channel: ${e.message}")
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
                    println("[MuSync] Channel resumed (source=$sourceId)")
                }
                true
            } else {
                println("[MuSync] No channel found for sound instance (may have been cleaned up)")
                false
            }
        } catch (e: Exception) {
            println("[MuSync] Error resuming channel: ${e.message}")
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
