package dev.mcrib884.musync.client

import dev.mcrib884.musync.entityLevel
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
    private val logger = org.apache.logging.log4j.LogManager.getLogger("MuSync")
    private var currentTrack: String? = null
    private var trackStartTime: Long = 0
    private var isPlaying: Boolean = false
    private var isPaused: Boolean = false
    private var pausedPosition: Long = 0
    private var playStartTime: Long = 0

    private var currentStatus: MusicStatusPacket? = null
    private var statusReceivedAt: Long = 0L

    var musyncActive: Boolean = false

    private var inJukeboxRange: Boolean = false
    private var jukeboxCheckTicks: Int = 0
    private const val JUKEBOX_CHECK_INTERVAL = 40

    private var lastMusicVol: Float = -1f
    private var lastMasterVol: Float = -1f

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
                logger.warn("Invalid track ID: $trackId")
                return
            }

            //? if >=1.20 {
            val soundEvent = BuiltInRegistries.SOUND_EVENT.get(soundLocation)
            //?} else {
            /*val soundEvent = Registry.SOUND_EVENT.get(soundLocation)*/
            //?}
            if (soundEvent == null) {
                logger.warn("Unknown sound event: $trackId")
                return
            }

            val tempInstance = SimpleSoundInstance.forMusic(soundEvent)
            tempInstance.resolve(mc.soundManager)
            val resolvedSound = tempInstance.sound
            if (resolvedSound == null || resolvedSound === net.minecraft.client.sounds.SoundManager.EMPTY_SOUND) {
                logger.warn("Could not resolve sound for: $trackId")
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
                logger.debug("Pool '$trackId' has ${allSounds.size} sound(s):")
                allSounds.forEach { logger.debug("  - $it") }
            }
        }

        currentTrack = trackId
        trackStartTime = System.currentTimeMillis() - startPositionMs
        isPlaying = true
        isPaused = false
        playStartTime = System.currentTimeMillis()

        val source = CustomTrackPlayer.playFromResource(oggPath, startPositionMs, oggNamespace)
        if (source == -1) {
            logger.error("Failed to play: $oggNamespace:$oggPath")
            val failedTrack = currentTrack
            isPlaying = false
            currentTrack = null
            if (failedTrack != null) {
                PacketHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(), MusicClientInfoPacket(
                    action = MusicClientInfoPacket.Action.TRACK_FINISHED,
                    trackId = failedTrack,
                    durationMs = 0
                ))
            }
            return
        }

        customTrackSource = source
        logger.info("Playing: $oggNamespace:$oggPath" + if (startPositionMs > 0) " (from ${startPositionMs}ms)" else "")

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
            logger.warn("Custom track data not cached: $fileName")
            PacketHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(), MusicClientInfoPacket(
                action = MusicClientInfoPacket.Action.TRACK_FINISHED,
                trackId = trackId,
                durationMs = 0
            ))
            return
        }

        currentTrack = trackId
        trackStartTime = System.currentTimeMillis() - startPositionMs
        isPlaying = true
        isPaused = false
        playStartTime = System.currentTimeMillis()

        val source = CustomTrackPlayer.play(trackData)
        if (source == -1) {
            logger.error("Failed to play custom track: $fileName")
            val failedTrack = currentTrack
            isPlaying = false
            currentTrack = null
            if (failedTrack != null) {
                PacketHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(), MusicClientInfoPacket(
                    action = MusicClientInfoPacket.Action.TRACK_FINISHED,
                    trackId = failedTrack,
                    durationMs = 0
                ))
            }
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
        logger.info("Playing custom track: $fileName" + if (startPositionMs > 0) " (from ${startPositionMs}ms)" else "")

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
        logger.info("Music stopped")
    }

    private fun pauseMusic() {
        if (isPlaying && !isPaused) {
            pausedPosition = System.currentTimeMillis() - trackStartTime
            isPaused = true
            if (customTrackSource != -1) {
                AL10.alSourcePause(customTrackSource)
            }
            logger.info("Music paused at ${pausedPosition}ms")
        }
    }

    private fun resumeMusic() {
        if (isPaused && currentTrack != null) {
            isPaused = false
            trackStartTime = System.currentTimeMillis() - pausedPosition
            if (customTrackSource != -1) {
                AL10.alSourcePlay(customTrackSource)
            }
            logger.info("Music resumed from ${pausedPosition}ms")
        }
    }

    fun updateStatus(packet: MusicStatusPacket) {
        musyncActive = true
        currentStatus = packet
        statusReceivedAt = System.currentTimeMillis()
    }

    fun getCurrentStatus(): MusicStatusPacket? = currentStatus
    fun getStatusAge(): Long = if (statusReceivedAt == 0L) 0L else System.currentTimeMillis() - statusReceivedAt


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

        val player = mc.player
        if (player != null && musyncActive && ++jukeboxCheckTicks >= JUKEBOX_CHECK_INTERVAL) {
            jukeboxCheckTicks = 0
            val nearJukebox = JukeboxTracker.isNearActiveJukebox(
                player.entityLevel().dimension(), player.blockPosition()
            )
            if (nearJukebox != inJukeboxRange) {
                inJukeboxRange = nearJukebox
                if (nearJukebox) {
                    if (customTrackSource != -1) {
                        AL10.alSourcef(customTrackSource, AL10.AL_GAIN, 0.0f)
                    }
                } else {
                    if (customTrackSource != -1) {
                        val musicVol = mc.options.getSoundSourceVolume(SoundSource.MUSIC)
                        val masterVol = mc.options.getSoundSourceVolume(SoundSource.MASTER)
                        AL10.alSourcef(customTrackSource, AL10.AL_GAIN, musicVol * masterVol)
                    }
                }
            }
        }
        if (customTrackSource != -1 && !inJukeboxRange) {
            val musicVol = mc.options.getSoundSourceVolume(SoundSource.MUSIC)
            val masterVol = mc.options.getSoundSourceVolume(SoundSource.MASTER)
            if (musicVol != lastMusicVol || masterVol != lastMasterVol) {
                lastMusicVol = musicVol
                lastMasterVol = masterVol
                AL10.alSourcef(customTrackSource, AL10.AL_GAIN, musicVol * masterVol)
            }
        }
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
                logger.info("Track finished: $trackId")
                CustomTrackPlayer.stop(customTrackSource)
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
        jukeboxCheckTicks = 0
        lastMusicVol = -1f
        lastMasterVol = -1f
        JukeboxTracker.clear()
    }
}
