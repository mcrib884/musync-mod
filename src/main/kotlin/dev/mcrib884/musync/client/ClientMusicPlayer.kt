package dev.mcrib884.musync.client

import dev.mcrib884.musync.entityLevel
import dev.mcrib884.musync.mixin.MusicManagerAccessor
import dev.mcrib884.musync.mixin.SoundEngineAccessor
import dev.mcrib884.musync.mixin.SoundManagerAccessor
import dev.mcrib884.musync.mixin.WeighedSoundEventsAccessor
import dev.mcrib884.musync.network.*
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundSource
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier as ResourceLocation*/
//?} else {
import net.minecraft.resources.ResourceLocation
//?}
import org.lwjgl.openal.AL10
import org.lwjgl.openal.AL11
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

object ClientMusicPlayer {
    private fun createLoadExecutor() = Executors.newSingleThreadExecutor { task ->
        Thread(task, "MuSync-TrackLoader").apply { isDaemon = true }
    }

    @Volatile
    private var loadExecutor = createLoadExecutor()
    private var lastResolvedSound: String? = null
    private var currentTrack: String? = null
    private var trackStartTime: Long = 0
    private var isPlaying: Boolean = false
    private var isPaused: Boolean = false
    private var pausedPosition: Long = 0
    private var playStartTime: Long = 0
    private var finishCheckBlockedUntilMs: Long = 0

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
    private var loadingStartedAtMs: Long = 0
    @Volatile private var loadToken: Long = 0
    private var queuedLoadTask: Future<*>? = null
    private var pendingSyncPacket: MusicSyncPacket? = null
    private const val LOAD_TIMEOUT_MS = 30_000L
    private const val SEEK_FINISH_GRACE_MS = 750L

    private fun syncSourceGain(mc: Minecraft) {
        if (customTrackSource == -1 || inJukeboxRange) return
        val musicVol = mc.options.getSoundSourceVolume(SoundSource.MUSIC)
        val masterVol = mc.options.getSoundSourceVolume(SoundSource.MASTER)
        lastMusicVol = musicVol
        lastMasterVol = masterVol
        AL10.alSourcef(customTrackSource, AL10.AL_GAIN, musicVol * masterVol)
    }

    private data class PreparedLoad(
        val trackId: String,
        val startPositionMs: Long,
        val specificSound: String,
        val startPaused: Boolean,
        val resolvedName: String,
        val preparedAudio: CustomTrackPlayer.AudioStream?
    )

    private fun closePreparedLoad(prepared: PreparedLoad?) {
        val preparedAudio = prepared?.preparedAudio ?: return
        try {
            preparedAudio.close()
        } catch (_: Exception) {}
    }

    private fun cancelQueuedLoadTask() {
        queuedLoadTask?.cancel(false)
        queuedLoadTask = null
    }

    private fun suppressVanillaMusic(mc: Minecraft = Minecraft.getInstance()) {
        mc.soundManager.stop(null, SoundSource.MUSIC)

        val soundManagerAccessor = mc.soundManager as? SoundManagerAccessor
        val soundEngineAccessor = soundManagerAccessor?.soundEngineField as? SoundEngineAccessor
        val managedMusicInstances = soundEngineAccessor?.instanceToChannelField?.keys?.toList().orEmpty()
            .filter { it.source == SoundSource.MUSIC }
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
        val adjustedPositionMs = if (packet.serverTimeMs > 0 && packet.action in setOf(
                MusicSyncPacket.Action.PLAY, MusicSyncPacket.Action.RESUME, MusicSyncPacket.Action.SEEK
            )) {
            val transit = (System.currentTimeMillis() - packet.serverTimeMs).coerceIn(0, 500)
            packet.startPositionMs + transit
        } else {
            packet.startPositionMs
        }
        mc.execute {
            suppressVanillaMusic(mc)
            when (packet.action) {
                MusicSyncPacket.Action.PLAY -> {
                    if (isLoading) {
                        pendingSyncPacket = packet
                    } else {
                        playMusic(packet.trackId, adjustedPositionMs, packet.specificSound)
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
                        applyResumeSync(packet.trackId, adjustedPositionMs, packet.specificSound)
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
                MusicSyncPacket.Action.SEEK -> {
                    if (isLoading) {
                        pendingSyncPacket = packet
                    } else {
                        seekInPlace(packet.trackId, adjustedPositionMs)
                    }
                }
            }
        }
    }

    private fun clearLoadingState(cancelCurrentLoad: Boolean, clearPending: Boolean) {
        if (cancelCurrentLoad) {
            loadToken++
            cancelQueuedLoadTask()
        }
        isLoading = false
        loadingTrack = null
        loadingPositionMs = 0
        loadingStartedAtMs = 0
        if (clearPending) pendingSyncPacket = null
    }

    private fun failCurrentLoad(trackIdHint: String? = null) {
        val failedTrack = currentTrack ?: trackIdHint
        isLoading = false
        loadingTrack = null
        loadingPositionMs = 0
        loadingStartedAtMs = 0
        isPlaying = false
        isPaused = false
        currentTrack = null
        pausedPosition = 0
        if (failedTrack != null) {
            PacketHandler.sendToServer(MusicClientInfoPacket(
                action = MusicClientInfoPacket.Action.LOAD_FAILED,
                trackId = failedTrack,
                durationMs = 0
            ))
        }
        processDeferredPacketIfReady()
    }

    private fun processDeferredPacketIfReady() {
        if (isLoading) return
        val packet = pendingSyncPacket ?: return
        pendingSyncPacket = null
        handleSyncPacket(packet)
    }

    private fun getLocalCustomTrackFile(fileName: String): File? {
        val tracksDir = File(Minecraft.getInstance().gameDirectory, "customtracks")
        if (!tracksDir.isDirectory) return null
        val exact = File(tracksDir, fileName)
        if (exact.isFile) return exact
        // Use the same normalization as the server (TrackNameUtil) for fuzzy matching
        val normalizedTarget = dev.mcrib884.musync.TrackNameUtil.normalizeInternalName(fileName)
            ?: fileName.lowercase(java.util.Locale.ROOT).replace(" ", "_")
        return tracksDir.listFiles()?.firstOrNull { file ->
            if (!file.isFile) return@firstOrNull false
            val normalizedFile = dev.mcrib884.musync.TrackNameUtil.normalizeInternalName(file.name)
                ?: file.name.lowercase(java.util.Locale.ROOT).replace(" ", "_")
            normalizedFile == normalizedTarget
        }
    }

    private fun getCachedCustomTrackFile(fileName: String): File? {
        if (!ClientTrackManager.cacheEnabled) return null
        val cacheDir = File(Minecraft.getInstance().gameDirectory, "musynccache")
        if (!cacheDir.isDirectory) return null
        val exact = File(cacheDir, fileName)
        if (exact.isFile) return exact
        val normalizedTarget = dev.mcrib884.musync.TrackNameUtil.normalizeInternalName(fileName)
            ?: fileName.lowercase(java.util.Locale.ROOT).replace(" ", "_")
        return cacheDir.listFiles()?.firstOrNull { file ->
            if (!file.isFile) return@firstOrNull false
            val normalizedFile = dev.mcrib884.musync.TrackNameUtil.normalizeInternalName(file.name)
                ?: file.name.lowercase(java.util.Locale.ROOT).replace(" ", "_")
            normalizedFile == normalizedTarget
        }
    }

    private fun startAsyncLoad(trackId: String, startPositionMs: Long, specificSound: String, startPaused: Boolean) {
        if (loadExecutor.isShutdown || loadExecutor.isTerminated) {
            loadExecutor = createLoadExecutor()
        }
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
        loadingStartedAtMs = System.currentTimeMillis()
        cancelQueuedLoadTask()
        val token = ++loadToken

        val specific = specificSound
        queuedLoadTask = loadExecutor.submit {
            try {
                val prepared = if (trackId.startsWith("custom:")) {
                    val fileName = trackId.removePrefix("custom:")
                    val cancelled = token != loadToken
                    if (cancelled) {
                        dev.mcrib884.musync.MuSyncLog.info("Skipping load of $fileName (cancelled before start)")
                    }
                    val localFile = if (!cancelled) getLocalCustomTrackFile(fileName) else null
                    val cachedFile = if (localFile == null && !cancelled) getCachedCustomTrackFile(fileName) else null
                    val preparedAudio = when {
                        cancelled -> null
                        localFile != null -> CustomTrackPlayer.prepareStream(localFile) { token != loadToken }
                        cachedFile != null -> CustomTrackPlayer.prepareStream(cachedFile) { token != loadToken }
                        else -> {
                            val cacheData = CustomTrackCache.get(fileName)
                            if (cacheData != null) {
                                CustomTrackPlayer.prepareStream(cacheData, fileName) { token != loadToken }
                            } else {
                                dev.mcrib884.musync.MuSyncLog.warn("No local/cached file found for: $fileName (localFile=null, cachedFile=null, cacheData=null)")
                                null
                            }
                        }
                    }
                    if (preparedAudio == null && !cancelled) {
                        dev.mcrib884.musync.MuSyncLog.warn("Custom track unavailable or undecodable: $fileName")
                        PreparedLoad(trackId, startPositionMs, specific, startPaused, fileName, null)
                    } else {
                        PreparedLoad(
                            trackId,
                            startPositionMs,
                            specific,
                            startPaused,
                            fileName,
                            preparedAudio
                        )
                    }
                } else {
                    val resolved = resolveExactSoundPath(trackId, specific, mc)
                    val preparedAudio = resolved?.let { CustomTrackPlayer.loadResourceAudio(it.second, it.first) }

                    if (resolved == null || preparedAudio == null) {
                        dev.mcrib884.musync.MuSyncLog.warn("Failed to resolve sound path for $trackId")
                        PreparedLoad(trackId, startPositionMs, specific, startPaused, "", null)
                    } else {
                        val resolvedName = "${resolved.first}:${resolved.second}"
                        PreparedLoad(
                            trackId,
                            startPositionMs,
                            specific,
                            startPaused,
                            resolvedName,
                            preparedAudio
                        )
                    }
                }

                if (token != loadToken) {
                    closePreparedLoad(prepared)
                    return@submit
                }

                mc.execute {
                    if (token != loadToken) {
                        closePreparedLoad(prepared)
                        return@execute
                    }

                    isLoading = false
                    loadingTrack = null
                    loadingPositionMs = 0
                    loadingStartedAtMs = 0

                    val preparedAudio = prepared.preparedAudio
                    if (preparedAudio == null) {
                        dev.mcrib884.musync.MuSyncLog.error("Failed to load track asynchronously: ${prepared.trackId}")
                        failCurrentLoad(prepared.trackId)
                        return@execute
                    }

                    val initialGain = if (inJukeboxRange) {
                        0.0f
                    } else {
                        mc.options.getSoundSourceVolume(SoundSource.MUSIC) *
                            mc.options.getSoundSourceVolume(SoundSource.MASTER)
                    }
                    val source = CustomTrackPlayer.playStream(
                        preparedAudio,
                        prepared.startPositionMs,
                        gain = initialGain,
                        startPaused = prepared.startPaused
                    )
                    if (source == -1) {
                        dev.mcrib884.musync.MuSyncLog.error("Failed to start prepared track: ${prepared.resolvedName}")
                        closePreparedLoad(prepared)
                        failCurrentLoad(prepared.trackId)
                        return@execute
                    }

                    customTrackSource = source
                    lastMusicVol = -1f
                    lastMasterVol = -1f
                    syncSourceGain(mc)
                    trackStartTime = System.currentTimeMillis() - prepared.startPositionMs
                    playStartTime = System.currentTimeMillis()
                    isPlaying = true
                    isPaused = prepared.startPaused
                    if (prepared.startPaused) {
                        pausedPosition = prepared.startPositionMs
                    }

                    dev.mcrib884.musync.MuSyncLog.info(
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
            } catch (t: Throwable) {
                dev.mcrib884.musync.MuSyncLog.error("Async load crashed for $trackId: ${t.message}")
                mc.execute {
                    if (token != loadToken) return@execute
                    failCurrentLoad(trackId)
                }
            }
        }
    }

    private fun playMusic(trackId: String, startPositionMs: Long, specificSound: String = "") {
        startAsyncLoad(trackId, startPositionMs, specificSound, startPaused = false)
    }

    private var customTrackSource: Int = -1

    internal fun resolveExactSoundPath(trackId: String, specificSound: String, mc: Minecraft): Pair<String, String>? {
        val explicitResolved = if (specificSound.startsWith("alias:")) {
            resolveSoundPathForAlias(trackId, specificSound.removePrefix("alias:"), mc)
        } else {
            if (specificSound.isNotEmpty()) {
                if (specificSound.contains(":")) {
                    specificSound.substringBefore(":") to specificSound.substringAfter(":")
                } else {
                    "minecraft" to specificSound
                }
            } else {
                null
            }
        }
        return explicitResolved ?: resolveSoundPath(trackId, mc)
    }

    internal fun resolveSoundPath(trackId: String, mc: Minecraft): Pair<String, String>? {
        val soundLocation = if (trackId.contains(":")) {
            ResourceLocation.tryParse(trackId)
        } else {
            ResourceLocation.tryParse("minecraft:$trackId")
        }
        if (soundLocation == null) {
            dev.mcrib884.musync.MuSyncLog.warn("Invalid track ID: $trackId")
            return null
        }

        val weighedEvents = mc.soundManager.getSoundEvent(soundLocation)
        if (weighedEvents == null) {
            dev.mcrib884.musync.MuSyncLog.warn("Unknown sound event: $trackId")
            return null
        }

        val soundEvent = dev.mcrib884.musync.createSoundEvent(soundLocation)

        val weightedAccessor = weighedEvents as? WeighedSoundEventsAccessor ?: return null
        val weightedList = weightedAccessor.listField
        val poolSize = weightedList.size
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
                        dev.mcrib884.musync.MuSyncLog.debug("Pool '$trackId' ($poolSize sounds): resolved to $key (avoided last: $lastResolvedSound)")
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
            dev.mcrib884.musync.MuSyncLog.warn("Could not resolve sound for: $trackId")
        }
        return bestResolved
    }

    internal fun resolveSoundPathForAlias(trackId: String, alias: String, mc: Minecraft): Pair<String, String>? {
        val soundLocation = if (trackId.contains(":")) {
            ResourceLocation.tryParse(trackId)
        } else {
            ResourceLocation.tryParse("minecraft:$trackId")
        } ?: return null

        val weighedEvents = mc.soundManager.getSoundEvent(soundLocation) ?: return null
        val normalizedAlias = alias.trim().lowercase().replace(" ", "_")

        fun normalize(value: String): String {
            return value.lowercase()
                .replace("minecraft:", "")
                .replace("-", "_")
                .replace(".", "_")
                .replace("/", "_")
                .replace(" ", "_")
        }

        var fallback: Pair<String, String>? = null
        val weightedAccessor = weighedEvents as? WeighedSoundEventsAccessor ?: return fallback
        for (weighted in weightedAccessor.listField) {
            val sound = weighted.getSound(net.minecraft.util.RandomSource.create())
            if (sound.type != net.minecraft.client.resources.sounds.Sound.Type.FILE) continue
            val loc = sound.location
            val candidate = loc.namespace to loc.path
            if (fallback == null) fallback = candidate

            val qualifiedPath = "${loc.namespace}:${loc.path}"
            val byFile = normalize(loc.path.substringAfterLast('/'))
            val byPretty = normalize(dev.mcrib884.musync.TrackNames.formatOggName(qualifiedPath))
            if (byFile == normalizedAlias || byPretty == normalizedAlias) {
                return candidate
            }
        }
        return fallback
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
        finishCheckBlockedUntilMs = 0
        clearLoadingState(cancelCurrentLoad = false, clearPending = true)
        dev.mcrib884.musync.MuSyncLog.info("Music stopped")
    }

    private fun pauseMusic() {
        if (isPlaying && !isPaused) {
            pausedPosition = System.currentTimeMillis() - trackStartTime
            isPaused = true
            if (customTrackSource != -1) {
                AL10.alSourcePause(customTrackSource)
            }
            dev.mcrib884.musync.MuSyncLog.info("Music paused at ${pausedPosition}ms")
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

        dev.mcrib884.musync.MuSyncLog.info("Music synced paused at ${pausedPosition}ms")
    }

    private fun resumeMusic() {
        if (isPaused && currentTrack != null) {
            isPaused = false
            trackStartTime = System.currentTimeMillis() - pausedPosition
            if (customTrackSource != -1) {
                AL10.alSourcePlay(customTrackSource)
            }
            dev.mcrib884.musync.MuSyncLog.info("Music resumed from ${pausedPosition}ms")
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
        dev.mcrib884.musync.MuSyncLog.info("Music synced resumed from ${positionMs}ms")
    }

    private fun seekInPlace(trackId: String, positionMs: Long) {
        if (currentTrack != trackId || !isPlaying || customTrackSource == -1) {
            playMusic(trackId, positionMs)
            return
        }
        val now = System.currentTimeMillis()
        finishCheckBlockedUntilMs = now + SEEK_FINISH_GRACE_MS
        trackStartTime = now - positionMs
        if (isPaused) {
            pausedPosition = positionMs
        }
        CustomTrackPlayer.seek(customTrackSource, positionMs, resume = !isPaused)
        dev.mcrib884.musync.MuSyncLog.info("Seeked in place to ${positionMs}ms")
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
            isPaused || gamePaused -> pausedPosition
            isPlaying -> System.currentTimeMillis() - trackStartTime
            else -> 0
        }
    }

    fun onClientTick() {

        val mc = Minecraft.getInstance()
        ClientTrackManager.onClientTick()

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
                    syncSourceGain(mc)
                }
            }
        }
        if (customTrackSource != -1 && !inJukeboxRange) {
            val musicVol = mc.options.getSoundSourceVolume(SoundSource.MUSIC)
            val masterVol = mc.options.getSoundSourceVolume(SoundSource.MASTER)
            if (musicVol != lastMusicVol || masterVol != lastMasterVol) {
                syncSourceGain(mc)
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

        if (isLoading) {
            if (loadingStartedAtMs > 0 && System.currentTimeMillis() - loadingStartedAtMs > LOAD_TIMEOUT_MS) {
                dev.mcrib884.musync.MuSyncLog.error("Track load timed out after ${LOAD_TIMEOUT_MS}ms: ${loadingTrack ?: currentTrack}")
                clearLoadingState(cancelCurrentLoad = true, clearPending = false)
                failCurrentLoad()
            }
            return
        }

        if (!isPlaying || isPaused || currentTrack == null) return

        if (gamePaused) return

        if (System.currentTimeMillis() - playStartTime < 3000L) return

        if (System.currentTimeMillis() < finishCheckBlockedUntilMs) return

        if (customTrackSource != -1) {
            if (!CustomTrackPlayer.isPlaying(customTrackSource)) {
                val trackId = currentTrack ?: return
                dev.mcrib884.musync.MuSyncLog.info("Track finished: $trackId")
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
        loadExecutor.shutdownNow()
        suppressVanillaMusic()
        stopMusicInternal()
        CustomTrackPlayer.stopAll()
        currentTrack = null
        isPlaying = false
        isPaused = false
        pausedPosition = 0
        trackStartTime = 0
        playStartTime = 0
        finishCheckBlockedUntilMs = 0
        customTrackSource = -1
        gamePaused = false
        currentStatus = null
        statusReceivedAt = 0L
        musyncActive = false
        inJukeboxRange = false
        jukeboxCheckTicks = 0
        lastMusicVol = -1f
        lastMasterVol = -1f
        lastResolvedSound = null
        JukeboxTracker.clear()
        PausedSourceTracker.clear()
        ClientOnlyController.reset()
    }
}
