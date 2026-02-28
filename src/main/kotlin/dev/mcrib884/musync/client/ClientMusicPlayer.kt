package dev.mcrib884.musync.client

import dev.mcrib884.musync.network.*
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundSource
import net.minecraft.resources.ResourceLocation
//? if >=1.20 {
import net.minecraft.core.registries.BuiltInRegistries
//?} else {
/*import net.minecraft.core.Registry*/
//?}
import net.minecraftforge.network.PacketDistributor
import org.lwjgl.openal.AL10

object ClientMusicPlayer {
    private var currentTrack: String? = null
    private var trackStartTime: Long = 0
    private var isPlaying: Boolean = false
    private var isPaused: Boolean = false
    private var pausedPosition: Long = 0
    private var playStartTime: Long = 0

    private var currentStatus: MusicStatusPacket? = null

    var musyncActive: Boolean = false

    private var inJukeboxRange: Boolean = false
    private var muteVolume: Float = 1.0f

    private var gamePaused: Boolean = false

    fun handleSyncPacket(packet: MusicSyncPacket) {
        musyncActive = true
        val mc = Minecraft.getInstance()
        mc.execute {
            when (packet.action) {
                MusicSyncPacket.Action.PLAY -> {
                    playMusic(packet.trackId, packet.startPositionMs, packet.specificSound)
                }
                MusicSyncPacket.Action.STOP -> {
                    stopMusic()
                }
                MusicSyncPacket.Action.PAUSE -> {
                    pauseMusic()
                }
                MusicSyncPacket.Action.RESUME -> {
                    resumeMusic()
                }
                MusicSyncPacket.Action.SKIP -> {
                    stopMusic()
                }
                MusicSyncPacket.Action.OPEN_GUI -> {

                }
            }
        }
    }

    private fun playMusic(trackId: String, startPositionMs: Long, specificSound: String = "") {
        val mc = Minecraft.getInstance()

        stopMusicInternal()

        if (trackId.startsWith("custom:")) {
            playCustomTrack(trackId, startPositionMs)
            return
        }

        val oggPath: String
        var oggNamespace = "minecraft"
        if (specificSound.isNotEmpty()) {

            if (specificSound.contains(":")) {
                oggNamespace = specificSound.substringBefore(":")
                oggPath = specificSound.substringAfter(":")
            } else {
                oggPath = specificSound
            }
        } else {

            val soundLocation = if (trackId.contains(":")) {
                ResourceLocation.tryParse(trackId)
            } else {
                ResourceLocation.tryParse("minecraft:$trackId")
            }
            if (soundLocation == null) {
                println("[MuSync] Invalid track ID: $trackId")
                return
            }

            //? if >=1.20 {
            val soundEvent = BuiltInRegistries.SOUND_EVENT.get(soundLocation)
            //?} else {
            /*val soundEvent = Registry.SOUND_EVENT.get(soundLocation)*/
            //?}
            if (soundEvent == null) {
                println("[MuSync] Unknown sound event: $trackId")
                return
            }

            val tempInstance = SimpleSoundInstance.forMusic(soundEvent)
            tempInstance.resolve(mc.soundManager)
            val resolvedSound = tempInstance.sound
            if (resolvedSound == null || resolvedSound === net.minecraft.client.sounds.SoundManager.EMPTY_SOUND) {
                println("[MuSync] Could not resolve sound for: $trackId")
                return
            }
            oggPath = resolvedSound.location.path
            oggNamespace = resolvedSound.location.namespace

            val weighedEvents = mc.soundManager.getSoundEvent(soundLocation)
            if (weighedEvents != null) {
                val allSounds = weighedEvents.list.mapNotNull { entry ->
                    try {
                        val s = entry.getSound(net.minecraft.util.RandomSource.create())
                        if (s.type == net.minecraft.client.resources.sounds.Sound.Type.FILE)
                            "${s.location.namespace}:${s.location.path}"
                        else null
                    } catch (_: Exception) { null }
                }
                println("[MuSync] Pool '$trackId' has ${allSounds.size} sound(s):")
                allSounds.forEach { println("  - $it") }
            }
        }

        currentTrack = trackId
        trackStartTime = System.currentTimeMillis() - startPositionMs
        isPlaying = true
        isPaused = false
        playStartTime = System.currentTimeMillis()

        val source = CustomTrackPlayer.playFromResource(oggPath, startPositionMs, oggNamespace)
        if (source == -1) {
            println("[MuSync] Failed to play: $oggNamespace:$oggPath")
            isPlaying = false
            currentTrack = null
            return
        }

        customTrackSource = source
        println("[MuSync] Playing: $oggNamespace:$oggPath" + if (startPositionMs > 0) " (from ${startPositionMs}ms)" else "")

        val durationMs = OggDurationReader.getDurationMs(
            ResourceLocation(oggNamespace, oggPath)
        )
        if (durationMs > 0) {
            val infoPacket = MusicClientInfoPacket(
                action = MusicClientInfoPacket.Action.REPORT_DURATION,
                trackId = trackId,
                durationMs = durationMs,
                resolvedName = "$oggNamespace:$oggPath"
            )
            PacketHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(), infoPacket)
        }
    }

    private fun playCustomTrack(trackId: String, startPositionMs: Long) {
        val fileName = trackId.removePrefix("custom:")
        val trackData = CustomTrackCache.get(fileName)
        if (trackData == null) {
            println("[MuSync] Custom track data not cached: $fileName")
            return
        }

        currentTrack = trackId
        trackStartTime = System.currentTimeMillis() - startPositionMs
        isPlaying = true
        isPaused = false
        playStartTime = System.currentTimeMillis()

        val source = CustomTrackPlayer.play(trackData)
        if (source == -1) {
            println("[MuSync] Failed to play custom track: $fileName")
            isPlaying = false
            currentTrack = null
            return
        }

        val mc = Minecraft.getInstance()
        val musicVol = mc.options.getSoundSourceVolume(SoundSource.MUSIC)
        val masterVol = mc.options.getSoundSourceVolume(SoundSource.MASTER)
        AL10.alSourcef(source, AL10.AL_GAIN, musicVol * masterVol)

        if (startPositionMs > 0) {
            AL10.alSourcef(source, org.lwjgl.openal.AL11.AL_SEC_OFFSET, startPositionMs / 1000f)
        }

        customTrackSource = source
        println("[MuSync] Playing custom track: $fileName" + if (startPositionMs > 0) " (from ${startPositionMs}ms)" else "")

        val durationMs = CustomTrackPlayer.getDurationMs(trackData)
        if (durationMs > 0) {
            val infoPacket = MusicClientInfoPacket(
                action = MusicClientInfoPacket.Action.REPORT_DURATION,
                trackId = trackId,
                durationMs = durationMs,
                resolvedName = fileName
            )
            PacketHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(), infoPacket)
        }
    }

    private var customTrackSource: Int = -1

    private fun stopMusicInternal() {
        val mc = Minecraft.getInstance()
        PausedSourceTracker.clear()

        mc.soundManager.stop(null, SoundSource.MUSIC)

        if (customTrackSource != -1) {
            CustomTrackPlayer.stop(customTrackSource)
            customTrackSource = -1
        }
    }

    private fun stopMusic() {
        stopMusicInternal()
        currentTrack = null
        isPlaying = false
        isPaused = false
        pausedPosition = 0
        println("[MuSync] Music stopped")
    }

    private fun pauseMusic() {
        if (isPlaying && !isPaused) {
            pausedPosition = System.currentTimeMillis() - trackStartTime
            isPaused = true
            if (customTrackSource != -1) {
                AL10.alSourcePause(customTrackSource)
            }
            println("[MuSync] Music paused at ${pausedPosition}ms")
        }
    }

    private fun resumeMusic() {
        if (isPaused && currentTrack != null) {
            isPaused = false
            trackStartTime = System.currentTimeMillis() - pausedPosition
            if (customTrackSource != -1) {
                AL10.alSourcePlay(customTrackSource)
            }
            println("[MuSync] Music resumed from ${pausedPosition}ms")
        }
    }

    fun updateStatus(packet: MusicStatusPacket) {
        musyncActive = true
        currentStatus = packet
    }

    fun getCurrentStatus(): MusicStatusPacket? = currentStatus

    fun isInJukeboxRange(): Boolean = inJukeboxRange

    fun setInJukeboxRange(inRange: Boolean) {
        if (inJukeboxRange != inRange) {
            inJukeboxRange = inRange
            if (inRange) {
                muteVolume = 0.0f

                if (customTrackSource != -1) {
                    AL10.alSourcef(customTrackSource, AL10.AL_GAIN, 0.0f)
                }
            } else {
                muteVolume = 1.0f

                if (customTrackSource != -1) {
                    val mc = Minecraft.getInstance()
                    val musicVol = mc.options.getSoundSourceVolume(SoundSource.MUSIC)
                    val masterVol = mc.options.getSoundSourceVolume(SoundSource.MASTER)
                    AL10.alSourcef(customTrackSource, AL10.AL_GAIN, musicVol * masterVol)
                }
            }
        }
    }

    fun getCurrentTrack(): String? = currentTrack
    fun isCurrentlyPlaying(): Boolean = isPlaying && !isPaused

    fun hasActiveTrack(): Boolean = currentTrack != null && isPlaying

    fun getCurrentPositionMs(): Long {
        return when {
            isPaused -> pausedPosition
            isPlaying -> System.currentTimeMillis() - trackStartTime
            else -> 0
        }
    }

    fun onClientTick() {

        val mc = Minecraft.getInstance()
        if (mc.isLocalServer) {
            val gameCurrentlyPaused = mc.isPaused
            if (gameCurrentlyPaused != gamePaused) {
                gamePaused = gameCurrentlyPaused
                if (isPlaying && !isPaused && customTrackSource != -1) {
                    if (gamePaused) {
                        AL10.alSourcePause(customTrackSource)

                        pausedPosition = System.currentTimeMillis() - trackStartTime
                    } else {
                        AL10.alSourcePlay(customTrackSource)

                        trackStartTime = System.currentTimeMillis() - pausedPosition
                    }
                }
            }
        } else {

            gamePaused = false
        }

        if (musyncActive) {
            try {
                val soundEngine = mc.soundManager.soundEngine
                val strayMusic = soundEngine.instanceToChannel.keys.filter {
                    it.source == SoundSource.MUSIC
                }
                for (stray in strayMusic) {
                    mc.soundManager.stop(stray)
                }
            } catch (_: Exception) {}
        }

        if (!isPlaying || isPaused || currentTrack == null) return

        if (gamePaused) return

        if (System.currentTimeMillis() - playStartTime < 3000L) return

        if (customTrackSource != -1) {
            if (!CustomTrackPlayer.isPlaying(customTrackSource)) {
                val trackId = currentTrack ?: return
                println("[MuSync] Track finished: $trackId")
                currentTrack = null
                isPlaying = false
                customTrackSource = -1
                val endPacket = MusicClientInfoPacket(
                    action = MusicClientInfoPacket.Action.TRACK_FINISHED,
                    trackId = trackId,
                    durationMs = 0
                )
                PacketHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(), endPacket)
            }
        }
    }

    fun fullReset() {
        stopMusicInternal()
        currentTrack = null
        isPlaying = false
        isPaused = false
        pausedPosition = 0
        trackStartTime = 0
        playStartTime = 0
        customTrackSource = -1
        gamePaused = false
        currentStatus = null
        musyncActive = false
        inJukeboxRange = false
        muteVolume = 1.0f
    }
}
