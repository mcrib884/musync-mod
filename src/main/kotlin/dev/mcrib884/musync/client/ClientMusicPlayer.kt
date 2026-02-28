package dev.mcrib884.musync.client

import dev.mcrib884.musync.network.*
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundSource
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.registries.BuiltInRegistries
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
                MusicSyncPacket.Action.SYNC_CHECK -> {

                    handleSyncCheck(packet.trackId, packet.startPositionMs)
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
        if (specificSound.isNotEmpty()) {

            oggPath = specificSound
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

            val soundEvent = BuiltInRegistries.SOUND_EVENT.get(soundLocation)
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
        }

        currentTrack = trackId
        trackStartTime = System.currentTimeMillis() - startPositionMs
        isPlaying = true
        isPaused = false
        playStartTime = System.currentTimeMillis()

        val source = CustomTrackPlayer.playFromResource(oggPath, startPositionMs)
        if (source == -1) {
            println("[MuSync] Failed to play: $oggPath")
            isPlaying = false
            currentTrack = null
            return
        }

        customTrackSource = source
        println("[MuSync] Playing: $oggPath" + if (startPositionMs > 0) " (from ${startPositionMs}ms)" else "")

        val durationMs = OggDurationReader.getDurationMs(
            ResourceLocation("minecraft", oggPath)
        )
        if (durationMs > 0) {
            val infoPacket = MusicClientInfoPacket(
                action = MusicClientInfoPacket.Action.REPORT_DURATION,
                trackId = trackId,
                durationMs = durationMs,
                resolvedName = oggPath
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

    private const val SYNC_DRIFT_THRESHOLD_MS = 50L

    private fun handleSyncCheck(trackId: String, serverPositionMs: Long) {

        if (currentTrack != trackId || !isPlaying || isPaused || gamePaused) return
        if (customTrackSource == -1) return

        val alPositionSec = AL10.alGetSourcef(customTrackSource, org.lwjgl.openal.AL11.AL_SEC_OFFSET)
        val actualPositionMs = (alPositionSec * 1000).toLong()

        val drift = actualPositionMs - serverPositionMs

        if (kotlin.math.abs(drift) > SYNC_DRIFT_THRESHOLD_MS) {

            val targetSec = serverPositionMs / 1000f
            if (targetSec >= 0f) {
                AL10.alSourcef(customTrackSource, org.lwjgl.openal.AL11.AL_SEC_OFFSET, targetSec)

                trackStartTime = System.currentTimeMillis() - serverPositionMs
                println("[MuSync] Sync correction: drift=${drift}ms, corrected to ${serverPositionMs}ms")
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
