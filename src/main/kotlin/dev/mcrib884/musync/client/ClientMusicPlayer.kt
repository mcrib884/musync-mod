package dev.mcrib884.musync.client

import dev.mcrib884.musync.entityLevel
import dev.mcrib884.musync.mixin.MusicManagerAccessor
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
import org.lwjgl.openal.AL10
import java.util.concurrent.Executors

object ClientMusicPlayer {
    private val logger = org.apache.logging.log4j.LogManager.getLogger("MuSync")
    private val loadExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "MuSync-TrackLoader").apply { isDaemon = true }
    }
    private var lastResolvedSound: String? = null
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
    private var isLoading: Boolean = false
    private var loadingTrack: String? = null
    private var loadingPositionMs: Long = 0
    private var loadToken: Long = 0
    private var pendingSyncPacket: MusicSyncPacket? = null

    private data class PreparedLoad(
        val trackId: String,
        val startPositionMs: Long,
        val specificSound: String,
        val startPaused: Boolean,
        val resolvedName: String,
        val preparedAudio: CustomTrackPlayer.PreparedAudio?
    )

    private fun suppressVanillaMusic(mc: Minecraft = Minecraft.getInstance()) {
        mc.soundManager.stop(null, SoundSource.MUSIC)

        val managedMusicInstances = mc.soundManager.soundEngine.instanceToChannel.keys
            .filter { it.source == SoundSource.MUSIC }
            .toList()
        managedMusicInstances.forEach { instance ->
            mc.soundManager.stop(instance)
        }

        val accessor = mc.musicManager as? MusicManagerAccessor
        if (accessor != null) {
            val currentMusic = accessor.currentMusic
            if (currentMusic != null) {
                mc.soundManager.stop(currentMusic)
                accessor.currentMusic = null
            }
            accessor.setNextSongDelay(Int.MAX_VALUE)
        }
    }

    fun handleSyncPacket(packet: MusicSyncPacket) {
        musyncActive = true
        val mc = Minecraft.getInstance()
        mc.execute {
            suppressVanillaMusic(mc)
            when (packet.action) {
                MusicSyncPacket.Action.PLAY -> {
                    if (isLoading) {
                        pendingSyncPacket = packet
                    } else {
                        playMusic(packet.trackId, packet.startPositionMs, packet.specificSound)
                    }
                }
                MusicSyncPacket.Action.STOP -> {
                    clearLoadingState(cancelCurrentLoad = true, clearPending = true)
                    stopMusic()
                }
                MusicSyncPacket.Action.PAUSE -> {
                    if (isLoading) {
                        pendingSyncPacket = packet
                    } else {
                        applyPausedSync(packet.trackId, packet.startPositionMs, packet.specificSound)
                    }
                }
                MusicSyncPacket.Action.RESUME -> {
                    if (isLoading) {
                        pendingSyncPacket = packet
                    } else {
                        applyResumeSync(packet.trackId, packet.startPositionMs, packet.specificSound)
                    }
                }
                MusicSyncPacket.Action.SKIP -> {
                    if (isLoading) {
                        pendingSyncPacket = packet
                    } else {
                        stopMusic()
                    }
                }
                MusicSyncPacket.Action.OPEN_GUI -> {

                }
            }
        }
    }

    private fun clearLoadingState(cancelCurrentLoad: Boolean, clearPending: Boolean) {
        if (cancelCurrentLoad) loadToken++
        isLoading = false
        loadingTrack = null
        loadingPositionMs = 0
        if (clearPending) pendingSyncPacket = null
    }

    private fun processDeferredPacketIfReady() {
        if (isLoading) return
        val packet = pendingSyncPacket ?: return
        pendingSyncPacket = null
        handleSyncPacket(packet)
    }

    private fun startAsyncLoad(trackId: String, startPositionMs: Long, specificSound: String, startPaused: Boolean) {
        val mc = Minecraft.getInstance()
        stopMusicInternal()

        currentTrack = trackId
        trackStartTime = System.currentTimeMillis() - startPositionMs
        isPlaying = false
        isPaused = false
        pausedPosition = if (startPaused) startPositionMs else 0
        playStartTime = 0

        isLoading = true
        loadingTrack = trackId
        loadingPositionMs = startPositionMs
        val token = ++loadToken

        val specific = specificSound
        loadExecutor.submit {
            val prepared = if (trackId.startsWith("custom:")) {
                val fileName = trackId.removePrefix("custom:")
                val trackData = CustomTrackCache.get(fileName)
                if (trackData == null) {
                    logger.warn("Custom track data not cached: $fileName")
                    PreparedLoad(trackId, startPositionMs, specific, startPaused, fileName, null)
                } else {
                    PreparedLoad(trackId, startPositionMs, specific, startPaused, fileName, CustomTrackPlayer.decode(trackData))
                }
            } else {
                val resolved = if (specific.isNotEmpty()) {
                    if (specific.contains(":")) {
                        specific.substringBefore(":") to specific.substringAfter(":")
                    } else {
                        "minecraft" to specific
                    }
                } else {
                    resolveSoundPath(trackId, mc)
                }

                if (resolved == null) {
                    logger.warn("Failed to resolve sound path for $trackId")
                    PreparedLoad(trackId, startPositionMs, specific, startPaused, "", null)
                } else {
                    val resolvedName = "${resolved.first}:${resolved.second}"
                    PreparedLoad(
                        trackId,
                        startPositionMs,
                        specific,
                        startPaused,
                        resolvedName,
                        CustomTrackPlayer.loadResourceAudio(resolved.second, resolved.first)
                    )
                }
            }

            mc.execute {
                if (token != loadToken) return@execute

                isLoading = false
                loadingTrack = null
                loadingPositionMs = 0

                val preparedAudio = prepared.preparedAudio
                if (preparedAudio == null) {
                    logger.error("Failed to load track asynchronously: ${prepared.trackId}")
                    val failedTrack = currentTrack
                    isPlaying = false
                    isPaused = false
                    currentTrack = null
                    pausedPosition = 0
                    if (failedTrack != null) {
                        PacketHandler.sendToServer(MusicClientInfoPacket(
                            action = MusicClientInfoPacket.Action.TRACK_FINISHED,
                            trackId = failedTrack,
                            durationMs = 0
                        ))
                    }
                    processDeferredPacketIfReady()
                    return@execute
                }

                val source = CustomTrackPlayer.playPrepared(preparedAudio, prepared.startPositionMs)
                if (source == -1) {
                    logger.error("Failed to start prepared track: ${prepared.resolvedName}")
                    val failedTrack = currentTrack
                    isPlaying = false
                    isPaused = false
                    currentTrack = null
                    pausedPosition = 0
                    if (failedTrack != null) {
                        PacketHandler.sendToServer(MusicClientInfoPacket(
                            action = MusicClientInfoPacket.Action.TRACK_FINISHED,
                            trackId = failedTrack,
                            durationMs = 0
                        ))
                    }
                    processDeferredPacketIfReady()
                    return@execute
                }

                customTrackSource = source
                trackStartTime = System.currentTimeMillis() - prepared.startPositionMs
                playStartTime = System.currentTimeMillis()
                isPlaying = true
                isPaused = prepared.startPaused
                if (prepared.startPaused) {
                    pausedPosition = prepared.startPositionMs
                    AL10.alSourcePause(source)
                }

                logger.info(
                    "Playing: ${prepared.resolvedName}" +
                        if (prepared.startPositionMs > 0) " (from ${prepared.startPositionMs}ms)" else ""
                )

                if (preparedAudio.durationMs > 0) {
                    PacketHandler.sendToServer(MusicClientInfoPacket(
                        action = MusicClientInfoPacket.Action.REPORT_DURATION,
                        trackId = prepared.trackId,
                        durationMs = preparedAudio.durationMs,
                        resolvedName = prepared.resolvedName
                    ))
                }

                processDeferredPacketIfReady()
            }
        }
    }

    private fun playMusic(trackId: String, startPositionMs: Long, specificSound: String = "") {
        startAsyncLoad(trackId, startPositionMs, specificSound, startPaused = false)
    }

    private var customTrackSource: Int = -1

    private fun resolveSoundPath(trackId: String, mc: Minecraft): Pair<String, String>? {
        val soundLocation = if (trackId.contains(":")) {
            ResourceLocation.tryParse(trackId)
        } else {
            ResourceLocation.tryParse("minecraft:$trackId")
        }
        if (soundLocation == null) {
            logger.warn("Invalid track ID: $trackId")
            return null
        }

        //? if >=1.20 {
        val soundEvent = BuiltInRegistries.SOUND_EVENT.get(soundLocation)
        //?} else {
        /*val soundEvent = Registry.SOUND_EVENT.get(soundLocation)*/
        //?}
        if (soundEvent == null) {
            logger.warn("Unknown sound event: $trackId")
            return null
        }

        val weighedEvents = mc.soundManager.getSoundEvent(soundLocation)
        val poolSize = weighedEvents?.list?.size ?: 1
        val attempts = if (poolSize > 1) minOf(poolSize * 2, 10) else 1

        var bestResolved: Pair<String, String>? = null
        repeat(attempts) {
            val tempInstance = SimpleSoundInstance.forMusic(soundEvent)
            tempInstance.resolve(mc.soundManager)
            val resolvedSound = tempInstance.sound
            if (resolvedSound != null && resolvedSound !== net.minecraft.client.sounds.SoundManager.EMPTY_SOUND) {
                val resolved = resolvedSound.location.namespace to resolvedSound.location.path
                if (bestResolved == null) bestResolved = resolved
                val key = "${resolved.first}:${resolved.second}"
                if (key != lastResolvedSound) {
                    if (poolSize > 1) {
                        logger.debug("Pool '$trackId' ($poolSize sounds): resolved to $key (avoided last: $lastResolvedSound)")
                    }
                    lastResolvedSound = key
                    return resolved
                }
            }
        }

        bestResolved?.let {
            lastResolvedSound = "${it.first}:${it.second}"
        }
        if (bestResolved == null) {
            logger.warn("Could not resolve sound for: $trackId")
        }
        return bestResolved
    }

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
        suppressVanillaMusic()
        currentTrack = null
        isPlaying = false
        isPaused = false
        pausedPosition = 0
        clearLoadingState(cancelCurrentLoad = false, clearPending = true)
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

    private fun applyPausedSync(trackId: String, positionMs: Long, specificSound: String) {
        val needsRestart = currentTrack != trackId || !isPlaying
        if (needsRestart) {
            startAsyncLoad(trackId, positionMs, specificSound, startPaused = true)
        } else {
            pausedPosition = positionMs
            trackStartTime = System.currentTimeMillis() - positionMs
            isPlaying = true
            isPaused = true

            if (customTrackSource != -1) {
                AL10.alSourcePause(customTrackSource)
            }
        }

        logger.info("Music synced paused at ${pausedPosition}ms")
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

    private fun applyResumeSync(trackId: String, positionMs: Long, specificSound: String) {
        val canResumeExisting = currentTrack == trackId && isPaused
        if (canResumeExisting) {
            pausedPosition = positionMs
            resumeMusic()
            return
        }

        startAsyncLoad(trackId, positionMs, specificSound, startPaused = false)
        logger.info("Music synced resumed from ${positionMs}ms")
    }

    fun updateStatus(packet: MusicStatusPacket) {
        musyncActive = true
        currentStatus = packet
        statusReceivedAt = System.currentTimeMillis()
        suppressVanillaMusic()
    }

    fun getCurrentStatus(): MusicStatusPacket? = currentStatus
    fun getStatusAge(): Long = if (statusReceivedAt == 0L) 0L else System.currentTimeMillis() - statusReceivedAt


    fun getCurrentTrack(): String? = currentTrack
    fun isLoading(): Boolean = isLoading
    fun getLoadingTrack(): String? = loadingTrack
    fun isCurrentlyPlaying(): Boolean = isPlaying && !isPaused

    fun hasActiveTrack(): Boolean = currentTrack != null && isPlaying

    fun getCurrentPositionMs(): Long {
        return when {
            isLoading -> loadingPositionMs
            isPaused -> pausedPosition
            isPlaying -> System.currentTimeMillis() - trackStartTime
            else -> 0
        }
    }

    fun onClientTick() {

        val mc = Minecraft.getInstance()

        if (musyncActive) {
            suppressVanillaMusic(mc)
        }

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

        if (isLoading || !isPlaying || isPaused || currentTrack == null) return

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
                PacketHandler.sendToServer(endPacket)
            }
        }
    }

    fun fullReset() {
        clearLoadingState(cancelCurrentLoad = true, clearPending = true)
        suppressVanillaMusic()
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
        lastResolvedSound = null
        JukeboxTracker.clear()
    }
}
