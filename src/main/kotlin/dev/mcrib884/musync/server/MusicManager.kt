package dev.mcrib884.musync.server

import dev.mcrib884.musync.currentServer
import dev.mcrib884.musync.musicEventLocation
import dev.mcrib884.musync.network.*
import dev.mcrib884.musync.serverDir
import net.minecraft.server.level.ServerPlayer
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier as ResourceLocation*/
//?} else {
import net.minecraft.resources.ResourceLocation
//?}
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import java.io.File
import dev.mcrib884.musync.entityLevel
//? if >=1.21.11 {
/*import dev.mcrib884.musync.location*/
//?}

object MusicManager {
    private var currentTrack: String? = null
    private var trackStartTime: Long = 0
    private var trackDuration: Long = 0
    private var isPlaying: Boolean = false
    private var isPaused: Boolean = false
    private var pausedPosition: Long = 0
    private var currentMode: MusicStatusPacket.PlayMode = MusicStatusPacket.PlayMode.AUTONOMOUS
    private var repeatMode: MusicStatusPacket.RepeatMode = MusicStatusPacket.RepeatMode.OFF
    private var currentReplayTrack: String? = null
    private var resolvedTrackName: String? = null
    private var specificSound: String = ""

    private val userPlaylist: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    private var ticksSinceLastMusic: Int = 0
    private var nextMusicDelayTicks: Int = 0
    private var waitingForNextTrack: Boolean = false

    private var recentTracks: MutableList<String> = mutableListOf()
    private val maxRecentTracks = 6
    private val trackHistory: MutableList<String> = mutableListOf()
    private val maxTrackHistory = 20
    private var suppressHistoryPush = false

    private var currentTrackSession: Long = 0L
    private var pendingClientEndSession: Long = -1L
    private var finishReportBlockedUntilMs: Long = 0L
    private const val TRACK_TIMEOUT_BUFFER_MS = 15_000L
    private const val MIN_VALID_TRACK_FINISH_MS = 3000L
    private const val SEEK_FINISH_GRACE_MS = 750L

    private data class PlayerMusicState(val musicEvent: String, val lastChanged: Long)
    private val playerMusicStates = mutableMapOf<java.util.UUID, PlayerMusicState>()
    private var lastDominantMusicEvent: String? = null
    private var lastDragonFightActive: Boolean = false

    private val dimensionDelays = mutableMapOf<String, Pair<Int, Int>>()
    private var opStateCheckCounter: Int = 0
    private val OP_STATE_CHECK_INTERVAL = 20
    private var dimBiomeCheckCounter: Int = 0
    private val DIM_BIOME_CHECK_INTERVAL = 20

    private val lastSeekTime = mutableMapOf<java.util.UUID, Long>()
    private val SEEK_THROTTLE_MS = 200L
    private val lastSkipTime = mutableMapOf<java.util.UUID, Long>()
    private val SKIP_THROTTLE_MS = 350L
    private var lastQueueAdvanceAt = 0L
    private val QUEUE_ADVANCE_GUARD_MS = 350L

    private val playersDownloading: MutableSet<java.util.UUID> = ConcurrentHashMap.newKeySet()
    private val playerJoinOrder = mutableListOf<java.util.UUID>()

    private val lastTrackRequestTime = ConcurrentHashMap<java.util.UUID, Long>()
    private const val TRACK_REQUEST_COOLDOWN_MS = 10_000L
    private const val MAX_TRACKS_PER_REQUEST = 3
    private const val MAX_MANIFEST_SEND = 256
    private data class ManifestAllowlist(val version: Long, val tracks: Set<String>)
    private val playerManifestAllowlist = ConcurrentHashMap<java.util.UUID, ManifestAllowlist>()
    private val manifestVersionCounter = AtomicLong(1L)
    private val trackSendExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "MuSync-TrackSync").apply { isDaemon = true }
    }

    private val creditsPlayers = mutableMapOf<java.util.UUID, Long>()
    private val CREDITS_DURATION_MS = 480_000L

    private class DimensionStream(val dimensionId: String) {
        var track: String? = null
        var replayTrack: String? = null
        var startTime: Long = 0
        var duration: Long = 0
        var playing: Boolean = false
        var paused: Boolean = false
        var pausedPos: Long = 0
        var specific: String = ""
        var resolved: String? = null
        var finishReportBlockedUntilMs: Long = 0L
        var waiting: Boolean = false
        var ticksSince: Int = 0
        var delayTicks: Int = 0
        var recent: MutableList<String> = mutableListOf()
        val history: MutableList<String> = mutableListOf()
        var clientEnd: Boolean = false
        var active: Boolean = false
        var lastMusicEvent: String? = null

        fun reset() {
            track = null; replayTrack = null; startTime = 0; duration = 0; playing = false
            paused = false; pausedPos = 0; specific = ""; resolved = null
            finishReportBlockedUntilMs = 0L
            waiting = false; ticksSince = 0; delayTicks = 0
            recent.clear(); history.clear(); clientEnd = false; active = false; lastMusicEvent = null
        }
    }

    private val dimensionStreams = mutableMapOf<String, DimensionStream>()

    private val syncOverworld = mutableSetOf<java.util.UUID>()
    private val selectedListenDimensions = mutableMapOf<java.util.UUID, String>()
    private var wasInPriority: Boolean = false

    fun isPlayerDownloading(uuid: java.util.UUID): Boolean = uuid in playersDownloading

    //? if >=1.21.11 {
    /*private fun isPlayerOp(player: ServerPlayer): Boolean {
        return player.level().getServer().playerList.isOp(player.nameAndId())
    }*/
    //?} else {
    private fun isPlayerOp(player: ServerPlayer): Boolean {
        return player.server.playerList.isOp(player.gameProfile)
    }
    //?}

    private data class MusicTiming(val minDelay: Int, val maxDelay: Int, val replacesCurrent: Boolean = false)

    private val MUSIC_TIMING: Map<String, MusicTiming> = mapOf(
        "minecraft:music.game" to MusicTiming(12000, 24000),
        "minecraft:music.creative" to MusicTiming(12000, 24000),
        "minecraft:music.menu" to MusicTiming(20, 600, replacesCurrent = true),
        "minecraft:music.end" to MusicTiming(6000, 24000),
        "minecraft:music.dragon" to MusicTiming(0, 0, replacesCurrent = true),
        "minecraft:music.credits" to MusicTiming(0, 0, replacesCurrent = true),
        "minecraft:music.under_water" to MusicTiming(12000, 24000),
    )
    private val DEFAULT_TIMING = MusicTiming(12000, 24000)
    private val END_BETWEEN_BIOME_TIMING = MusicTiming(12000, 18000)

    //? if >=1.21.11 {
    /*private fun getBiomeMusicEvent(player: ServerPlayer): String? {
        return try {
            val bgMusic = player.entityLevel().environmentAttributes()
                .getValue(net.minecraft.world.attribute.EnvironmentAttributes.BACKGROUND_MUSIC, player.blockPosition())
            val music = bgMusic.defaultMusic().orElse(null) ?: return null
            musicEventLocation(music).toString()
        } catch (_: Exception) { null }
    }

    private fun getBiomeMusicTiming(player: ServerPlayer): MusicTiming? {
        return try {
            val bgMusic = player.entityLevel().environmentAttributes()
                .getValue(net.minecraft.world.attribute.EnvironmentAttributes.BACKGROUND_MUSIC, player.blockPosition())
            val music = bgMusic.defaultMusic().orElse(null) ?: return null
            MusicTiming(music.minDelay, music.maxDelay, music.replaceCurrentMusic())
        } catch (_: Exception) { null }
    }*/
    //?} else {
    private fun getBiomeMusicEvent(player: ServerPlayer): String? {
        return try {
            val music = player.entityLevel().getBiome(player.blockPosition()).value()
                .getBackgroundMusic().orElse(null) ?: return null
            musicEventLocation(music).toString()
        } catch (_: Exception) { null }
    }

    private fun getBiomeMusicTiming(player: ServerPlayer): MusicTiming? {
        return try {
            val music = player.entityLevel().getBiome(player.blockPosition()).value()
                .getBackgroundMusic().orElse(null) ?: return null
            MusicTiming(music.minDelay, music.maxDelay, music.replaceCurrentMusic())
        } catch (_: Exception) { null }
    }
    //?}

    private fun choosePreferredMusicEvent(primary: String, candidates: Iterable<String>, recent: List<String>): String {
        if (primary == "minecraft:music.dragon" || primary == "minecraft:music.credits") {
            return primary
        }
        val ordered = linkedSetOf<String>()
        ordered.add(primary)
        candidates.forEach { ordered.add(it) }
        return ordered.firstOrNull { it !in recent } ?: primary
    }

    private fun consumeQueuedTrack(ignoreGuard: Boolean = false): String? {
        val now = System.currentTimeMillis()
        if (!ignoreGuard && now - lastQueueAdvanceAt < QUEUE_ADVANCE_GUARD_MS) {
            dev.mcrib884.musync.MuSyncLog.debug("Queue advance suppressed by guard (${now - lastQueueAdvanceAt}ms < ${QUEUE_ADVANCE_GUARD_MS}ms)")
            return null
        }
        val nextTrack = userPlaylist.poll() ?: return null
        lastQueueAdvanceAt = now
        dev.mcrib884.musync.MuSyncLog.debug("Queue advanced to: $nextTrack")
        return nextTrack
    }

    private fun pollRandomQueuedTrack(avoidTrack: String? = null): String? {
        val list = userPlaylist.toMutableList()
        if (list.isEmpty()) return null

        val selectableIndexes = if (!avoidTrack.isNullOrEmpty()) {
            val avoidCount = list.count { it == avoidTrack }
            if (avoidCount <= 1) {
                list.indices.filter { list[it] != avoidTrack }
            } else {
                list.indices.toList()
            }
        } else {
            list.indices.toList()
        }

        val candidateIndexes = if (selectableIndexes.isNotEmpty()) selectableIndexes else list.indices.toList()
        val index = candidateIndexes[Random.nextInt(candidateIndexes.size)]
        val nextTrack = list.removeAt(index)
        userPlaylist.clear()
        userPlaylist.addAll(list)
        lastQueueAdvanceAt = System.currentTimeMillis()
        return nextTrack
    }

    private fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            MusicStatusPacket.RepeatMode.OFF -> MusicStatusPacket.RepeatMode.REPEAT_TRACK
            MusicStatusPacket.RepeatMode.REPEAT_TRACK -> MusicStatusPacket.RepeatMode.REPEAT_PLAYLIST
            MusicStatusPacket.RepeatMode.REPEAT_PLAYLIST -> MusicStatusPacket.RepeatMode.SHUFFLE
            MusicStatusPacket.RepeatMode.SHUFFLE -> MusicStatusPacket.RepeatMode.SHUFFLE_REPEAT
            MusicStatusPacket.RepeatMode.SHUFFLE_REPEAT -> MusicStatusPacket.RepeatMode.OFF
        }
        broadcastStatus()
    }

    private fun replayFinishedPrimaryTrack(
        finishedTrack: String,
        finishedMode: MusicStatusPacket.PlayMode,
        finishedSpecific: String,
        finishedReplayTrack: String?
    ) {
        currentMode = finishedMode
        playTrack(finishedTrack, finishedSpecific, finishedReplayTrack)
    }

    private fun canonicalCustomTrackId(trackId: String): String? {
        if (!trackId.startsWith("custom:", ignoreCase = true)) return trackId
        val rawName = trackId.substringAfter(":", "")
        val internal = CustomTrackManager.toInternalName(rawName)
        if (internal == null) {
            dev.mcrib884.musync.MuSyncLog.warn("Rejected unresolved custom track id: $trackId")
            return null
        }
        return "custom:$internal"
    }

    fun handleControlPacket(packet: MusicControlPacket, player: ServerPlayer) {
        if (player.uuid in playersDownloading) return

        if (packet.action == MusicControlPacket.Action.SELECT_DIMENSION && packet.targetDim != null) {
            selectedListenDimensions[player.uuid] = packet.targetDim
            sendSyncToPlayer(player)
            return
        }

        if (packet.action == MusicControlPacket.Action.TOGGLE_NETHER_SYNC) {
            handleDimSyncToggle(player)
            return
        }

        if (packet.action == MusicControlPacket.Action.CREDITS_SKIP) {
            if (creditsPlayers.remove(player.uuid) != null) {
                checkPlayerMusicStates()
                broadcastStatus()
            }
            return
        }

        if (!isPlayerOp(player)) return

        val playerDim = player.entityLevel().dimension().location().toString()
        val forcePrimaryControl = isInPriorityMode() &&
            packet.action in setOf(
                MusicControlPacket.Action.STOP,
                MusicControlPacket.Action.SKIP,
                MusicControlPacket.Action.PAUSE,
                MusicControlPacket.Action.RESUME,
                MusicControlPacket.Action.SEEK
            )
        val effectiveDim = if (forcePrimaryControl) {
            "minecraft:overworld"
        } else if (packet.targetDim != null) packet.targetDim else {
            if (playerDim != "minecraft:overworld" && player.uuid !in syncOverworld) playerDim else "minecraft:overworld"
        }
        val dimStream = if (effectiveDim != "minecraft:overworld") getStreamForDim(effectiveDim) else null

        when (packet.action) {
            MusicControlPacket.Action.PLAY_TRACK -> {
                packet.trackId?.let { friendlyName ->
                    val trackValue = dev.mcrib884.musync.command.MuSyncCommand.resolveTrackValue(friendlyName)
                    val actual = trackValue
                        ?: if (friendlyName.startsWith("custom:", ignoreCase = true)) canonicalCustomTrackId(friendlyName) else friendlyName
                    if (actual == null) return@let
                    if (dimStream != null) {
                        dimPlayTrack(dimStream, actual)
                    } else {
                        currentMode = MusicStatusPacket.PlayMode.PLAYLIST
                        playTrack(actual)
                    }
                }
            }
            MusicControlPacket.Action.STOP -> if (dimStream != null) dimStopAndSchedule(dimStream) else stopMusic()
            MusicControlPacket.Action.SKIP -> {
                val now = System.currentTimeMillis()
                val lastSkip = lastSkipTime[player.uuid] ?: 0L
                if (now - lastSkip < SKIP_THROTTLE_MS) {
                    dev.mcrib884.musync.MuSyncLog.debug("Skip throttled for ${player.name.string} (${now - lastSkip}ms)")
                    return
                }
                lastSkipTime[player.uuid] = now
                dev.mcrib884.musync.MuSyncLog.info("Skip requested by ${player.name.string} on ${dimStream?.dimensionId ?: "minecraft:overworld"}")
                if (dimStream != null) dimSkipTrack(dimStream) else skipTrack()
            }
            MusicControlPacket.Action.PAUSE -> if (dimStream != null) dimPauseMusic(dimStream) else pauseMusic()
            MusicControlPacket.Action.RESUME -> if (dimStream != null) dimResumeMusic(dimStream) else resumeMusic()
            MusicControlPacket.Action.REQUEST_SYNC -> sendSyncToPlayer(player)
            MusicControlPacket.Action.SELECT_DIMENSION -> {}
            MusicControlPacket.Action.FORCE_SYNC_ALL -> forceSyncAll()
            MusicControlPacket.Action.ADD_TO_QUEUE -> {
                packet.trackId?.let { friendlyName ->
                    val trackValue = dev.mcrib884.musync.command.MuSyncCommand.resolveTrackValue(friendlyName)
                    val actual = trackValue
                        ?: if (friendlyName.startsWith("custom:", ignoreCase = true)) canonicalCustomTrackId(friendlyName) else friendlyName
                    if (actual != null) addToQueue(actual)
                }
            }
            MusicControlPacket.Action.REMOVE_FROM_QUEUE -> {
                packet.queuePosition?.let { removeFromQueue(it) }
            }
            MusicControlPacket.Action.CLEAR_QUEUE -> clearQueue()
            MusicControlPacket.Action.SET_DELAY -> {
                val raw = packet.trackId ?: "reset"
                val dim = packet.targetDim ?: player.entityLevel().dimension().location().toString()
                if (raw == "reset") {
                    resetCustomDelay(dim)
                } else {
                    val parts = raw.split(":")
                    if (parts.size == 2) {
                        val min = parts[0].toIntOrNull()
                        val max = parts[1].toIntOrNull()
                        if (min != null && max != null && max >= min) setCustomDelay(dim, min, max)
                    }
                }
                broadcastStatus()
            }
            MusicControlPacket.Action.SEEK -> {
                val seekMs = packet.seekMs
                if (seekMs < 0) return
                val now = System.currentTimeMillis()
                val lastSeek = lastSeekTime[player.uuid] ?: 0L
                if (now - lastSeek < SEEK_THROTTLE_MS) return
                lastSeekTime[player.uuid] = now
                if (dimStream != null) dimSeekTrack(dimStream, seekMs) else seekTrack(seekMs)
            }
            MusicControlPacket.Action.CYCLE_REPEAT_MODE -> cycleRepeatMode()
            MusicControlPacket.Action.HOTLOAD_TRACKS -> hotloadTracks()
            MusicControlPacket.Action.TOGGLE_NETHER_SYNC -> {}
            MusicControlPacket.Action.CREDITS_SKIP -> {}
            MusicControlPacket.Action.PREVIOUS -> {
                if (dimStream != null) dimPreviousTrack(dimStream) else previousTrack()
            }
        }
    }

    internal fun playTrack(trackId: String, specific: String = "", replayTrackId: String? = null) {
        var actualTrackId: String
        val actualSpecific: String
        if (specific.isEmpty() && trackId.contains("|")) {
            val parts = trackId.split("|", limit = 2)
            actualTrackId = parts[0]
            actualSpecific = parts[1]
        } else {
            actualTrackId = trackId
            actualSpecific = specific
        }

        if (actualTrackId.startsWith("custom:", ignoreCase = true)) {
            actualTrackId = canonicalCustomTrackId(actualTrackId) ?: return
        }

        val effectiveReplayTrack = replayTrackId ?: when {
            actualTrackId.startsWith("custom:", ignoreCase = true) -> actualTrackId
            actualSpecific.isNotEmpty() -> "$actualTrackId|$actualSpecific"
            else -> actualTrackId
        }

        if (!suppressHistoryPush) {
            val prevReplay = currentReplayTrack ?: currentTrack
            if (prevReplay != null) {
                trackHistory.add(prevReplay)
                if (trackHistory.size > maxTrackHistory) trackHistory.removeAt(0)
            }
        }

        currentTrackSession++
        pendingClientEndSession = -1L
        currentTrack = actualTrackId
        currentReplayTrack = effectiveReplayTrack
        specificSound = actualSpecific
        trackStartTime = System.currentTimeMillis()
        trackDuration = 0
        isPlaying = true
        isPaused = false
        finishReportBlockedUntilMs = 0L
        waitingForNextTrack = false
        resolvedTrackName = if (actualSpecific.isNotEmpty() && !actualSpecific.startsWith("alias:")) actualSpecific else null

        if (actualTrackId.startsWith("custom:")) {
            val server = currentServer()
            if (server != null) {
                sendCustomTrackToPlayers(
                    actualTrackId.removePrefix("custom:"),
                    server.playerList.players.filter { shouldPlayerHearPrimary(it) }
                )
            }
        }

        broadcastSync(MusicSyncPacket.Action.PLAY)
        broadcastStatus()
    }

    internal fun stopMusic() {
        if (currentTrack != null) broadcastSync(MusicSyncPacket.Action.STOP)
        currentTrack = null
        currentReplayTrack = null
        isPlaying = false
        isPaused = false
        finishReportBlockedUntilMs = 0L
        resolvedTrackName = null
        specificSound = ""
        currentMode = MusicStatusPacket.PlayMode.AUTONOMOUS
        scheduleNextAutonomousTrack()
        broadcastStatus()
    }

    internal fun skipTrack() {
        val skippedPlaylistTrack = if (currentMode == MusicStatusPacket.PlayMode.PLAYLIST) {
            currentReplayTrack ?: currentTrack
        } else {
            null
        }

        if (skippedPlaylistTrack != null) {
            when (repeatMode) {
                MusicStatusPacket.RepeatMode.REPEAT_PLAYLIST,
                MusicStatusPacket.RepeatMode.SHUFFLE_REPEAT -> userPlaylist.add(skippedPlaylistTrack)
                else -> {}
            }
        }

        val nextTrack = when (repeatMode) {
            MusicStatusPacket.RepeatMode.SHUFFLE,
            MusicStatusPacket.RepeatMode.SHUFFLE_REPEAT -> pollRandomQueuedTrack(skippedPlaylistTrack)
            else -> consumeQueuedTrack(ignoreGuard = true)
        }
        if (nextTrack != null) {
            currentMode = MusicStatusPacket.PlayMode.PLAYLIST
            dev.mcrib884.musync.MuSyncLog.info("Playing queued track after skip: $nextTrack")
            playTrack(nextTrack)
        } else {
            dev.mcrib884.musync.MuSyncLog.info("Skip fell back to autonomous playback")
            playNextAutoTrack()
        }
    }

    internal fun previousTrack() {
        val prev = trackHistory.removeLastOrNull()
        if (prev != null) {
            suppressHistoryPush = true
            try {
                currentMode = MusicStatusPacket.PlayMode.PLAYLIST
                dev.mcrib884.musync.MuSyncLog.info("Playing previous track: $prev")
                playTrack(prev)
            } finally {
                suppressHistoryPush = false
            }
        }
    }

    internal fun pauseMusic() {
        if (isPlaying && !isPaused) {
            pausedPosition = System.currentTimeMillis() - trackStartTime
            isPaused = true
            broadcastSync(MusicSyncPacket.Action.PAUSE)
            broadcastStatus()
        }
    }

    internal fun resumeMusic() {
        if (isPaused && currentTrack != null) {
            isPaused = false
            trackStartTime = System.currentTimeMillis() - pausedPosition
            val packet = MusicSyncPacket(
                trackId = currentTrack!!,
                startPositionMs = pausedPosition,
                serverTimeMs = System.currentTimeMillis(),
                action = MusicSyncPacket.Action.RESUME
            )
            val server = currentServer() ?: return
            for (p in server.playerList.players) {
                if (shouldPlayerHearPrimary(p)) {
                    PacketHandler.sendToPlayer(p, packet)
                }
            }
            broadcastStatus()
        }
    }

    internal fun addToQueue(trackId: String) {
        val actual = if (trackId.startsWith("custom:", ignoreCase = true)) {
            canonicalCustomTrackId(trackId)
        } else {
            trackId
        }
        if (actual == null) return

        userPlaylist.add(actual)
        if (!isPlaying || currentTrack == null) {
            val nextTrack = consumeQueuedTrack(ignoreGuard = true)
            if (nextTrack != null) startPlaylistTrack(nextTrack)
        }
        broadcastStatus()
    }

    private fun resetDimensionStreamsForPlaylist() {
        val server = currentServer() ?: return
        for (stream in dimensionStreams.values) {
            for (player in server.playerList.players) {
                if (shouldPlayerHearDim(player, stream)) {
                    sendStopToPlayer(player)
                }
            }

            updateDimPopulation(stream)
            stream.track = null
            stream.replayTrack = null
            stream.startTime = 0
            stream.duration = 0
            stream.playing = false
            stream.paused = false
            stream.pausedPos = 0
            stream.specific = ""
            stream.resolved = null
            stream.finishReportBlockedUntilMs = 0L
            stream.clientEnd = false
            stream.recent.clear()

            if (stream.active) {
                stream.waiting = true
                stream.ticksSince = 0
                stream.delayTicks = 100
            } else {
                stream.waiting = false
                stream.ticksSince = 0
                stream.delayTicks = 0
            }
        }
    }

    private fun startPlaylistTrack(trackId: String) {
        resetDimensionStreamsForPlaylist()
        currentMode = MusicStatusPacket.PlayMode.PLAYLIST
        playTrack(trackId)
    }

    private fun removeFromQueue(position: Int) {
        val list = userPlaylist.toList()
        if (position in list.indices) {
            val iter = userPlaylist.iterator()
            var idx = 0
            while (iter.hasNext()) {
                iter.next()
                if (idx == position) {
                    iter.remove()
                    break
                }
                idx++
            }
            broadcastStatus()
        }
    }

    private fun clearQueue() {
        userPlaylist.clear()
        broadcastStatus()
    }

    fun onServerTick() {

        val server = currentServer() ?: return
        if (server.playerList.players.isEmpty()) {
            shutdownForNoPlayers()
            return
        }

        if (creditsPlayers.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val expired = creditsPlayers.entries.filter { now - it.value >= CREDITS_DURATION_MS }
            if (expired.isNotEmpty()) {
                expired.forEach { creditsPlayers.remove(it.key) }
                checkPlayerMusicStates()
            }
        }

        opStateCheckCounter++
        if (opStateCheckCounter >= OP_STATE_CHECK_INTERVAL) {
            opStateCheckCounter = 0
            checkPlayerMusicStates()
        }

        dimBiomeCheckCounter++
        if (dimBiomeCheckCounter >= DIM_BIOME_CHECK_INTERVAL) {
            dimBiomeCheckCounter = 0
            checkDimBiomeChanges()
        }

        if (pendingClientEndSession == currentTrackSession && isPlaying && currentTrack != null) {
            pendingClientEndSession = -1L
            onTrackFinished(ignoreQueueGuard = true)
        } else if (isPlaying && !isPaused && currentTrack != null) {
            val elapsed = System.currentTimeMillis() - trackStartTime
            if (trackDuration > 0 && elapsed > trackDuration + TRACK_TIMEOUT_BUFFER_MS) {
                dev.mcrib884.musync.MuSyncLog.warn("Track timed out after ${elapsed / 1000}s (duration=${trackDuration}ms): $currentTrack")
                onTrackFinished(ignoreQueueGuard = false)
            }
        } else if (waitingForNextTrack && currentMode == MusicStatusPacket.PlayMode.AUTONOMOUS && !isPaused) {
            ticksSinceLastMusic++
            if (ticksSinceLastMusic >= nextMusicDelayTicks) {
                waitingForNextTrack = false
                ticksSinceLastMusic = 0
                playNextAutoTrack()
            } else if (ticksSinceLastMusic % 20 == 0) {
                broadcastStatus()
            }
        }

        val inPriority = isInPriorityMode()
        if (wasInPriority && !inPriority) resyncDimPlayers()
        wasInPriority = inPriority

        for (stream in dimensionStreams.values) {
            if (stream.active && !inPriority) {
                if (stream.clientEnd) {
                    stream.clientEnd = false
                    dimOnTrackFinished(stream)
                } else if (stream.playing && !stream.paused && stream.track != null) {
                    val elapsed = System.currentTimeMillis() - stream.startTime
                    if (stream.duration > 0 && elapsed > stream.duration + TRACK_TIMEOUT_BUFFER_MS) {
                        dev.mcrib884.musync.MuSyncLog.warn("${stream.dimensionId} track timed out after ${elapsed / 1000}s (duration=${stream.duration}ms): ${stream.track}")
                        dimOnTrackFinished(stream)
                    }
                } else if (stream.waiting && !stream.paused) {
                    stream.ticksSince++
                    if (stream.ticksSince >= stream.delayTicks) {
                        stream.waiting = false
                        stream.ticksSince = 0
                        dimPlayNext(stream)
                    } else if (stream.ticksSince % 20 == 0) {
                        broadcastStatus()
                    }
                }
            }
        }
    }

    private fun onTrackFinished(ignoreQueueGuard: Boolean) {
        if (!hasOverworldPlayers()) {
            finishPrimaryStream(sendStop = true)
            return
        }

        val finishedTrack = currentTrack
        val finishedMode = currentMode
        val finishedSpecific = specificSound
        val finishedReplayTrack = currentReplayTrack

        when (repeatMode) {
            MusicStatusPacket.RepeatMode.REPEAT_TRACK -> {
                if (finishedTrack != null) {
                    replayFinishedPrimaryTrack(finishedTrack, finishedMode, finishedSpecific, finishedReplayTrack)
                } else {
                    broadcastSync(MusicSyncPacket.Action.STOP)
                    currentTrack = null
                    currentReplayTrack = null
                    isPlaying = false
                    resolvedTrackName = null
                    specificSound = ""
                    currentMode = MusicStatusPacket.PlayMode.AUTONOMOUS
                    scheduleNextAutonomousTrack()
                    broadcastStatus()
                }
                return
            }
            MusicStatusPacket.RepeatMode.REPEAT_PLAYLIST -> {
                if (finishedMode == MusicStatusPacket.PlayMode.PLAYLIST && finishedTrack != null) {
                    userPlaylist.add(finishedReplayTrack ?: finishedTrack)
                }
                val nextTrack = consumeQueuedTrack(ignoreGuard = true)
                if (nextTrack != null) {
                    currentMode = MusicStatusPacket.PlayMode.PLAYLIST
                    startPlaylistTrack(nextTrack)
                } else {
                    dev.mcrib884.musync.MuSyncLog.info("Track finished, queue empty, scheduling autonomous playback")
                    broadcastSync(MusicSyncPacket.Action.STOP)
                    currentTrack = null
                    currentReplayTrack = null
                    isPlaying = false
                    resolvedTrackName = null
                    specificSound = ""
                    currentMode = MusicStatusPacket.PlayMode.AUTONOMOUS
                    scheduleNextAutonomousTrack()
                    broadcastStatus()
                }
                return
            }
            MusicStatusPacket.RepeatMode.SHUFFLE -> {
                if (finishedMode == MusicStatusPacket.PlayMode.PLAYLIST) {
                    val nextTrack = pollRandomQueuedTrack(finishedReplayTrack ?: finishedTrack)
                    if (nextTrack != null) {
                        currentMode = MusicStatusPacket.PlayMode.PLAYLIST
                        startPlaylistTrack(nextTrack)
                    } else {
                        dev.mcrib884.musync.MuSyncLog.info("Track finished, queue empty, scheduling autonomous playback")
                        broadcastSync(MusicSyncPacket.Action.STOP)
                        currentTrack = null
                        currentReplayTrack = null
                        isPlaying = false
                        resolvedTrackName = null
                        specificSound = ""
                        currentMode = MusicStatusPacket.PlayMode.AUTONOMOUS
                        scheduleNextAutonomousTrack()
                        broadcastStatus()
                    }
                    return
                }
            }
            MusicStatusPacket.RepeatMode.SHUFFLE_REPEAT -> {
                if (finishedMode == MusicStatusPacket.PlayMode.PLAYLIST) {
                    if (finishedTrack != null) {
                        userPlaylist.add(finishedReplayTrack ?: finishedTrack)
                    }
                    val nextTrack = pollRandomQueuedTrack(finishedReplayTrack ?: finishedTrack)
                    if (nextTrack != null) {
                        currentMode = MusicStatusPacket.PlayMode.PLAYLIST
                        startPlaylistTrack(nextTrack)
                    } else {
                        dev.mcrib884.musync.MuSyncLog.info("Track finished, queue empty, scheduling autonomous playback")
                        broadcastSync(MusicSyncPacket.Action.STOP)
                        currentTrack = null
                        currentReplayTrack = null
                        isPlaying = false
                        resolvedTrackName = null
                        specificSound = ""
                        currentMode = MusicStatusPacket.PlayMode.AUTONOMOUS
                        scheduleNextAutonomousTrack()
                        broadcastStatus()
                    }
                    return
                }
                if (finishedTrack != null) {
                    replayFinishedPrimaryTrack(finishedTrack, finishedMode, finishedSpecific, finishedReplayTrack)
                    return
                }
            }
            MusicStatusPacket.RepeatMode.OFF -> {}
        }

        val nextTrack = consumeQueuedTrack(ignoreGuard = ignoreQueueGuard)
        if (nextTrack != null) {
            dev.mcrib884.musync.MuSyncLog.info("Track finished, advancing queue to: $nextTrack")
            startPlaylistTrack(nextTrack)
        } else {
            dev.mcrib884.musync.MuSyncLog.info("Track finished, queue empty, scheduling autonomous playback")
            broadcastSync(MusicSyncPacket.Action.STOP)
            currentTrack = null
            currentReplayTrack = null
            isPlaying = false
            resolvedTrackName = null
            specificSound = ""
            currentMode = MusicStatusPacket.PlayMode.AUTONOMOUS
            scheduleNextAutonomousTrack()
            broadcastStatus()
        }
    }

    private fun shutdownForNoPlayers() {
        if (currentTrack == null && !isPlaying && !waitingForNextTrack && dimensionStreams.values.none { it.playing || it.waiting || it.active }) {
            return
        }

        currentTrack = null
        currentReplayTrack = null
        trackStartTime = 0
        trackDuration = 0
        isPlaying = false
        isPaused = false
        pausedPosition = 0
        currentMode = MusicStatusPacket.PlayMode.AUTONOMOUS
        resolvedTrackName = null
        specificSound = ""

        waitingForNextTrack = false
        ticksSinceLastMusic = 0
        nextMusicDelayTicks = 0
        currentTrackSession = 0L
        pendingClientEndSession = -1L
        recentTracks.clear()
        trackHistory.clear()
        userPlaylist.clear()

        playerMusicStates.clear()
        lastDominantMusicEvent = null
        lastDragonFightActive = false
        creditsPlayers.clear()
        syncOverworld.clear()
        playersDownloading.clear()
        playerJoinOrder.clear()
        lastSeekTime.clear()
        lastSkipTime.clear()
        lastQueueAdvanceAt = 0L
        playerManifestAllowlist.clear()

        for (stream in dimensionStreams.values) {
            stream.reset()
        }
        dimensionStreams.clear()
        selectedListenDimensions.clear()
        wasInPriority = false

        dev.mcrib884.musync.MuSyncLog.info("No players online; MuSync state fully reset and idle")
    }

    private fun scheduleNextAutonomousTrack() {
        if (!hasOverworldPlayers()) {
            waitingForNextTrack = false
            ticksSinceLastMusic = 0
            nextMusicDelayTicks = 0
            return
        }

        val musicEvent = determineMusicEventForPlayers()
        val dimDelay = dimensionDelays["minecraft:overworld"]
        val timing = if (dimDelay != null) {
            MusicTiming(dimDelay.first, dimDelay.second)
        } else {
            getOverworldBiomeTiming() ?: MUSIC_TIMING[musicEvent] ?: DEFAULT_TIMING
        }

        nextMusicDelayTicks = if (timing.maxDelay <= timing.minDelay) {
            timing.minDelay
        } else {
            timing.minDelay + Random.nextInt(timing.maxDelay - timing.minDelay)
        }
        ticksSinceLastMusic = 0
        waitingForNextTrack = true
    }

    private fun getOverworldBiomeTiming(): MusicTiming? {
        val server = currentServer() ?: return null
        val player = server.playerList.players.firstOrNull {
            it.entityLevel().dimension().location().toString() == "minecraft:overworld"
        } ?: return null
        return getBiomeMusicTiming(player)
    }

    private fun playNextAutoTrack() {
        val musicEvent = choosePreferredMusicEvent(
            determineMusicEventForPlayers(),
            playerMusicStates.values.map { it.musicEvent },
            recentTracks
        )
        if (musicEvent in recentTracks) {
            dev.mcrib884.musync.MuSyncLog.debug("Skipping recently played event, but no alternatives: $musicEvent")
        }
        recentTracks.add(musicEvent)
        if (recentTracks.size > maxRecentTracks) recentTracks.removeAt(0)
        currentMode = MusicStatusPacket.PlayMode.AUTONOMOUS
        playTrack(musicEvent)
    }

    private fun isGlobalDragonFight(): Boolean {
        val server = currentServer() ?: return false
        val anyInEnd = server.playerList.players.any {
            it.entityLevel().dimension().location().toString() == "minecraft:the_end"
        }
        if (!anyInEnd) return false
        return try {
            val endLevel = server.getLevel(net.minecraft.world.level.Level.END) ?: return false
            endLevel.getEntities(net.minecraft.world.entity.EntityType.ENDER_DRAGON) { true }.isNotEmpty()
        } catch (_: Exception) { false }
    }

    private fun computeMusicEventForPlayer(player: ServerPlayer): String {
        if (player.uuid in creditsPlayers) return "minecraft:music.credits"

        val dimension = player.entityLevel().dimension().location().toString()

        if (dimension == "minecraft:the_end") {
            try {
                //? if >=1.21.11 {
                /*val endLevel = player.level().getServer().getLevel(net.minecraft.world.level.Level.END)*/
                //?} else {
                val endLevel = player.server.getLevel(net.minecraft.world.level.Level.END)
                //?}
                if (endLevel != null) {
                    val dragons = endLevel.getEntities(net.minecraft.world.entity.EntityType.ENDER_DRAGON) { true }
                    if (dragons.isNotEmpty()) return "minecraft:music.dragon"
                }
            } catch (_: Exception) {}
            return getBiomeMusicEvent(player) ?: "minecraft:music.end"
        }

        if (player.isUnderWater) return "minecraft:music.under_water"

        if (dimension != "minecraft:the_nether" && player.isCreative) {
            return "minecraft:music.creative"
        }

        return getBiomeMusicEvent(player) ?: "minecraft:music.game"
    }

    private fun checkPlayerMusicStates() {
        val server = currentServer() ?: return
        val players = server.playerList.players
        if (players.isEmpty()) return

        var anyChanged = false

        val overworldPlayers = players.filter {
            it.entityLevel().dimension().location().toString() == "minecraft:overworld"
        }
        val nonCreative = overworldPlayers.filter { !it.isCreative }
        val trackedPlayers = nonCreative.ifEmpty { overworldPlayers }

        if (trackedPlayers.isEmpty()) {
            if (playerMusicStates.isNotEmpty()) {
                playerMusicStates.clear()
                anyChanged = true
            }
        } else {
            for (player in trackedPlayers) {
                val musicEvent = computeMusicEventForPlayer(player)
                val prevState = playerMusicStates[player.uuid]
                if (prevState == null || prevState.musicEvent != musicEvent) {
                    playerMusicStates[player.uuid] = PlayerMusicState(musicEvent, System.currentTimeMillis())
                    anyChanged = true
                }
            }
            val activeUuids = trackedPlayers.map { it.uuid }.toSet()
            val removed = playerMusicStates.keys.removeAll { it !in activeUuids }
            if (removed) anyChanged = true
        }

        val dragonActive = isGlobalDragonFight()
        if (dragonActive != lastDragonFightActive) {
            lastDragonFightActive = dragonActive
            anyChanged = true
        }

        if (anyChanged) onPlayerMusicStateChanged()
    }

    private fun getMusicCategory(musicEvent: String): String {
        return when {
            musicEvent.contains("dragon") || musicEvent.contains("end") || musicEvent.contains("credits") -> "end"
            musicEvent.contains("nether") -> "nether"
            else -> "overworld"
        }
    }

    private fun onPlayerMusicStateChanged() {
        val newMusicEvent = determineDominantMusicEvent()
        if (newMusicEvent != lastDominantMusicEvent) {
            val oldCategory = getMusicCategory(lastDominantMusicEvent ?: "")
            val newCategory = getMusicCategory(newMusicEvent)
            val isPriority = newMusicEvent == "minecraft:music.dragon" || newMusicEvent == "minecraft:music.credits"
            val isDimensionChange = oldCategory != newCategory

            lastDominantMusicEvent = newMusicEvent

            val hasManualPlaylist = userPlaylist.isNotEmpty()
            val isPlayingPlaylist = currentMode == MusicStatusPacket.PlayMode.PLAYLIST

            if (hasManualPlaylist || isPlayingPlaylist) {
                if (isPriority) {
                    waitingForNextTrack = false
                    ticksSinceLastMusic = 0
                    currentMode = MusicStatusPacket.PlayMode.AUTONOMOUS
                    playTrack(newMusicEvent)
                } else if (!isPlayingPlaylist && currentMode == MusicStatusPacket.PlayMode.AUTONOMOUS && isDimensionChange) {
                    waitingForNextTrack = false
                    ticksSinceLastMusic = 0
                    val nextTrack = consumeQueuedTrack()
                    if (nextTrack != null) {
                        startPlaylistTrack(nextTrack)
                    }
                }
            } else {
                val newTiming = getTimingForEvent(newMusicEvent)
                if (isPriority || isDimensionChange || newTiming.replacesCurrent) {
                    if (currentMode == MusicStatusPacket.PlayMode.AUTONOMOUS || currentMode == MusicStatusPacket.PlayMode.PLAYLIST) {
                        waitingForNextTrack = false
                        ticksSinceLastMusic = 0
                        currentMode = MusicStatusPacket.PlayMode.AUTONOMOUS
                        playTrack(newMusicEvent)
                    }
                }
            }
            broadcastStatus()
        }
    }

    private fun getTimingForEvent(musicEvent: String): MusicTiming {
        val server = currentServer() ?: return MUSIC_TIMING[musicEvent] ?: DEFAULT_TIMING
        for (player in server.playerList.players) {
            if (player.entityLevel().dimension().location().toString() != "minecraft:overworld") continue
            val biomeEvent = getBiomeMusicEvent(player)
            if (biomeEvent == musicEvent) {
                return getBiomeMusicTiming(player) ?: continue
            }
        }
        return MUSIC_TIMING[musicEvent] ?: DEFAULT_TIMING
    }

    private fun getTimingForDimEvent(stream: DimensionStream, musicEvent: String): MusicTiming {
        val server = currentServer() ?: return MUSIC_TIMING[musicEvent] ?: DEFAULT_TIMING
        val dimPlayers = server.playerList.players.filter {
            it.entityLevel().dimension().location().toString() == stream.dimensionId
        }
        for (player in dimPlayers) {
            val biomeEvent = getBiomeMusicEvent(player)
            if (biomeEvent == musicEvent) {
                return getBiomeMusicTiming(player) ?: continue
            }
        }
        return MUSIC_TIMING[musicEvent] ?: DEFAULT_TIMING
    }

    private fun checkDimBiomeChanges() {
        for (stream in dimensionStreams.values) {
            if (!stream.active || stream.paused) continue
            val newEvent = determineDimMusicEvent(stream)
            if (newEvent != stream.lastMusicEvent) {
                stream.lastMusicEvent = newEvent
                val timing = getTimingForDimEvent(stream, newEvent)
                if (!timing.replacesCurrent) continue
                val isPriority = timing.minDelay == 0
                val isHardPriority = newEvent == "minecraft:music.dragon" || newEvent == "minecraft:music.credits"

                if (stream.playing && stream.track != null && newEvent != stream.track) {
                    if (!isPriority) continue
                    dev.mcrib884.musync.MuSyncLog.info("Biome music change in ${stream.dimensionId}: ${stream.track} -> $newEvent (replacesCurrent, priority)")
                    broadcastDimSync(stream, MusicSyncPacket.Action.STOP)
                    stream.recent.add(stream.track!!)
                    if (stream.recent.size > maxRecentTracks) stream.recent.removeAt(0)
                    stream.track = null
                    stream.playing = false
                    stream.waiting = false
                    stream.ticksSince = 0
                    stream.delayTicks = 0
                    dimPlayTrack(stream, newEvent)
                } else if (!stream.playing && stream.track == null && stream.waiting) {
                    if (!isPriority) continue
                    if (!isHardPriority) {
                        dev.mcrib884.musync.MuSyncLog.info("Biome music update in ${stream.dimensionId} during cooldown: $newEvent (keeping current cooldown)")
                        continue
                    }
                    dev.mcrib884.musync.MuSyncLog.info("Biome music override in ${stream.dimensionId} during cooldown: $newEvent (replacesCurrent, priority)")
                    stream.waiting = false
                    stream.ticksSince = 0
                    stream.delayTicks = 0
                    dimPlayTrack(stream, newEvent)
                }
            }
        }
    }

    private fun determineDominantMusicEvent(): String {
        if (isGlobalDragonFight()) return "minecraft:music.dragon"
        if (creditsPlayers.isNotEmpty()) return "minecraft:music.credits"
        if (playerMusicStates.isEmpty()) return "minecraft:music.game"

        val states = playerMusicStates.values.toList()

        if (states.size <= 2) {
            return states.maxByOrNull { it.lastChanged }?.musicEvent ?: "minecraft:music.game"
        }

        val groups = states.groupBy { it.musicEvent }
        val maxCount = groups.maxOf { it.value.size }
        val candidateGroups = groups.filter { it.value.size == maxCount }

        if (candidateGroups.size == 1) return candidateGroups.keys.first()

        return candidateGroups.maxByOrNull { group ->
            group.value.maxOf { it.lastChanged }
        }?.key ?: "minecraft:music.game"
    }

    private fun determineMusicEventForPlayers(): String {
        if (isGlobalDragonFight()) return "minecraft:music.dragon"
        if (creditsPlayers.isNotEmpty()) return "minecraft:music.credits"
        if (playerMusicStates.isNotEmpty()) return determineDominantMusicEvent()
        return "minecraft:music.game"
    }

    private fun isInPriorityMode(): Boolean {
        return isGlobalDragonFight() || creditsPlayers.isNotEmpty() ||
                userPlaylist.isNotEmpty() || currentMode == MusicStatusPacket.PlayMode.PLAYLIST
    }

    private fun hasOverworldPlayers(excludedPlayer: ServerPlayer? = null): Boolean {
        val server = currentServer() ?: return false
        return server.playerList.players.any {
            it.uuid != excludedPlayer?.uuid &&
                it.entityLevel().dimension().location().toString() == "minecraft:overworld"
        }
    }

    private fun countPrimaryListeners(excludedPlayer: ServerPlayer? = null): Int {
        val server = currentServer() ?: return 0
        return server.playerList.players.count { player ->
            player.uuid != excludedPlayer?.uuid && shouldPlayerHearPrimary(player)
        }
    }

    private fun finishPrimaryStream(sendStop: Boolean) {
        if (sendStop && currentTrack != null) {
            broadcastSync(MusicSyncPacket.Action.STOP)
        }

        currentTrack = null
        currentReplayTrack = null
        trackStartTime = 0
        trackDuration = 0
        isPlaying = false
        isPaused = false
        pausedPosition = 0
        finishReportBlockedUntilMs = 0L
        waitingForNextTrack = false
        ticksSinceLastMusic = 0
        nextMusicDelayTicks = 0
        currentTrackSession = 0L
        pendingClientEndSession = -1L
        resolvedTrackName = null
        specificSound = ""
        currentMode = MusicStatusPacket.PlayMode.AUTONOMOUS
        broadcastStatus()
    }

    private fun handleOverworldEmpty(excludedPlayer: ServerPlayer? = null) {
        val primaryListeners = countPrimaryListeners(excludedPlayer)
        finishPrimaryStream(sendStop = primaryListeners > 0)
    }

    private fun shouldPlayerHearPrimary(player: ServerPlayer): Boolean {
        if (isInPriorityMode()) return true
        val dim = selectedListenDimensions[player.uuid] ?: player.entityLevel().dimension().location().toString()
        return dim == "minecraft:overworld" || player.uuid in syncOverworld
    }

    private fun shouldPlayerHearPrimaryForDim(player: ServerPlayer, dimensionId: String): Boolean {
        if (isInPriorityMode()) return true
        val effectiveDim = selectedListenDimensions[player.uuid] ?: dimensionId
        return effectiveDim == "minecraft:overworld" || player.uuid in syncOverworld
    }

    private fun shouldPlayerHearDim(player: ServerPlayer, stream: DimensionStream): Boolean {
        if (isInPriorityMode()) return false
        val dim = selectedListenDimensions[player.uuid] ?: player.entityLevel().dimension().location().toString()
        return dim == stream.dimensionId && player.uuid !in syncOverworld
    }

    private fun shouldPlayerHearDimForDim(player: ServerPlayer, dimensionId: String, stream: DimensionStream): Boolean {
        if (isInPriorityMode()) return false
        val effectiveDim = selectedListenDimensions[player.uuid] ?: dimensionId
        return effectiveDim == stream.dimensionId && player.uuid !in syncOverworld
    }

    private fun getStreamForDim(dimensionId: String): DimensionStream? {
        if (dimensionId == "minecraft:overworld") return null
        return dimensionStreams[dimensionId]
    }

    private fun getOrCreateStream(dimensionId: String): DimensionStream? {
        if (dimensionId == "minecraft:overworld") return null
        return dimensionStreams.getOrPut(dimensionId) { DimensionStream(dimensionId) }
    }

    private fun countPlayersInDimensionExcluding(dimensionId: String, excludedPlayer: ServerPlayer?): Int {
        val server = currentServer() ?: return 0
        return server.playerList.players.count { player ->
            player.uuid != excludedPlayer?.uuid &&
                player.entityLevel().dimension().location().toString() == dimensionId
        }
    }

    private fun determineDimMusicEvent(stream: DimensionStream): String {
        val server = currentServer() ?: return "minecraft:music.game"
        val dimPlayers = server.playerList.players.filter {
            it.entityLevel().dimension().location().toString() == stream.dimensionId
        }
        if (dimPlayers.isEmpty()) return "minecraft:music.game"
        if (dimPlayers.size == 1) return computeMusicEventForPlayer(dimPlayers.first())

        val votes = dimPlayers.map { computeMusicEventForPlayer(it) }
        val groups = votes.groupBy { it }
        val maxCount = groups.maxOf { it.value.size }
        val topCandidates = groups.filter { it.value.size == maxCount }.keys.sorted()
        if (topCandidates.size == 1) return topCandidates.first()
        val previous = stream.lastMusicEvent
        return if (previous != null && previous in topCandidates) previous else topCandidates.first()
    }

    private fun updateDimPopulation(stream: DimensionStream) {
        val server = currentServer() ?: return
        stream.active = server.playerList.players.any {
            it.entityLevel().dimension().location().toString() == stream.dimensionId
        }
    }

    private fun dimPlayTrack(stream: DimensionStream, trackId: String, replayTrackId: String? = null) {
        var actualTrackId: String
        val actualSpecific: String
        if (trackId.contains("|")) {
            val parts = trackId.split("|", limit = 2)
            actualTrackId = parts[0]
            actualSpecific = parts[1]
        } else {
            actualTrackId = trackId
            actualSpecific = ""
        }

        if (actualTrackId.startsWith("custom:", ignoreCase = true)) {
            actualTrackId = canonicalCustomTrackId(actualTrackId) ?: return
        }

        val effectiveReplayTrack = replayTrackId ?: when {
            actualTrackId.startsWith("custom:", ignoreCase = true) -> actualTrackId
            actualSpecific.isNotEmpty() -> "$actualTrackId|$actualSpecific"
            else -> actualTrackId
        }

        if (!suppressHistoryPush) {
            val prevReplay = stream.replayTrack ?: stream.track
            if (prevReplay != null) {
                stream.history.add(prevReplay)
                if (stream.history.size > maxTrackHistory) stream.history.removeAt(0)
            }
        }

        stream.track = actualTrackId
        stream.replayTrack = effectiveReplayTrack
        stream.specific = actualSpecific
        stream.startTime = System.currentTimeMillis()
        stream.duration = 0
        stream.playing = true
        stream.paused = false
        stream.finishReportBlockedUntilMs = 0L
        stream.waiting = false
        stream.resolved = if (actualSpecific.isNotEmpty() && !actualSpecific.startsWith("alias:")) actualSpecific else null
        stream.clientEnd = false
        if (actualTrackId.startsWith("custom:")) {
            val server = currentServer()
            if (server != null) {
                sendCustomTrackToPlayers(
                    actualTrackId.removePrefix("custom:"),
                    server.playerList.players.filter { shouldPlayerHearDim(it, stream) }
                )
            }
        }
        broadcastDimSync(stream, MusicSyncPacket.Action.PLAY)
        broadcastStatus()
    }

    private fun dimPreviousTrack(stream: DimensionStream) {
        val prev = stream.history.removeLastOrNull()
        if (prev != null) {
            suppressHistoryPush = true
            try {
                dev.mcrib884.musync.MuSyncLog.info("Playing previous track in ${stream.dimensionId}: $prev")
                dimPlayTrack(stream, prev)
            } finally {
                suppressHistoryPush = false
            }
        }
    }

    private fun dimStopMusic(stream: DimensionStream) {
        if (stream.track != null) broadcastDimSync(stream, MusicSyncPacket.Action.STOP)
        stream.reset()
    }

    private fun dimStopAndSchedule(stream: DimensionStream) {
        dimStopMusic(stream)
        stream.active = true
        dimScheduleNext(stream)
        broadcastStatus()
    }

    private fun dimPauseMusic(stream: DimensionStream) {
        if (stream.playing && !stream.paused) {
            stream.pausedPos = System.currentTimeMillis() - stream.startTime
            stream.paused = true
            broadcastDimSync(stream, MusicSyncPacket.Action.PAUSE)
            broadcastStatus()
        }
    }

    private fun dimResumeMusic(stream: DimensionStream) {
        if (stream.paused && stream.track != null) {
            stream.paused = false
            stream.startTime = System.currentTimeMillis() - stream.pausedPos
            val packet = MusicSyncPacket(
                trackId = stream.track!!,
                startPositionMs = stream.pausedPos,
                serverTimeMs = System.currentTimeMillis(),
                action = MusicSyncPacket.Action.RESUME
            )
            val server = currentServer() ?: return
            for (p in server.playerList.players) {
                if (shouldPlayerHearDim(p, stream)) {
                    PacketHandler.sendToPlayer(p, packet)
                }
            }
            broadcastStatus()
        }
    }

    private fun dimSkipTrack(stream: DimensionStream) {
        if (stream.track != null) broadcastDimSync(stream, MusicSyncPacket.Action.STOP)
        stream.track = null
        stream.replayTrack = null
        stream.playing = false
        stream.paused = false
        stream.finishReportBlockedUntilMs = 0L
        broadcastStatus()
        dimPlayNext(stream)
    }

    private fun dimSeekTrack(stream: DimensionStream, positionMs: Long) {
        if (stream.track == null || !stream.playing) return
        val clamped = positionMs.coerceIn(0, if (stream.duration > 0) stream.duration else Long.MAX_VALUE)
        val now = System.currentTimeMillis()
        stream.finishReportBlockedUntilMs = now + SEEK_FINISH_GRACE_MS
        stream.startTime = now - clamped
        if (stream.paused) stream.pausedPos = clamped
        val seekSpecific = if (stream.specific.isNotEmpty()) stream.specific
                           else stream.resolved ?: ""
        val packet = MusicSyncPacket(
            trackId = stream.track!!,
            startPositionMs = clamped,
            serverTimeMs = now,
            action = MusicSyncPacket.Action.SEEK,
            specificSound = seekSpecific
        )
        val server = currentServer() ?: return
        for (p in server.playerList.players) {
            if (shouldPlayerHearDim(p, stream)) {
                PacketHandler.sendToPlayer(p, packet)
            }
        }
        broadcastStatus()
    }

    private fun dimScheduleNext(stream: DimensionStream) {
        val musicEvent = determineDimMusicEvent(stream)
        val dimDelay = dimensionDelays[stream.dimensionId]
        val timing = if (dimDelay != null) {
            MusicTiming(dimDelay.first, dimDelay.second)
        } else {
            getDimMusicTiming(stream) ?: getDimFallbackTiming(stream, musicEvent)
        }

        stream.delayTicks = if (timing.maxDelay <= timing.minDelay) {
            timing.minDelay
        } else {
            timing.minDelay + Random.nextInt(timing.maxDelay - timing.minDelay)
        }
        stream.ticksSince = 0
        stream.waiting = true
    }

    private fun getDimFallbackTiming(stream: DimensionStream, musicEvent: String): MusicTiming {
        if (stream.dimensionId == "minecraft:the_end" && musicEvent == "minecraft:music.end") {
            return END_BETWEEN_BIOME_TIMING
        }
        return MUSIC_TIMING[musicEvent] ?: DEFAULT_TIMING
    }

    private fun getDimMusicTiming(stream: DimensionStream): MusicTiming? {
        val server = currentServer() ?: return null
        val player = server.playerList.players.firstOrNull {
            it.entityLevel().dimension().location().toString() == stream.dimensionId
        } ?: return null
        return getBiomeMusicTiming(player)
    }

    private fun dimPlayNext(stream: DimensionStream) {
        val server = currentServer()
        val dimVotes = server?.playerList?.players
            ?.filter { it.entityLevel().dimension().location().toString() == stream.dimensionId }
            ?.map { computeMusicEventForPlayer(it) }
            .orEmpty()
        val musicEvent = choosePreferredMusicEvent(determineDimMusicEvent(stream), dimVotes, stream.recent)
        stream.recent.add(musicEvent)
        if (stream.recent.size > maxRecentTracks) stream.recent.removeAt(0)
        dimPlayTrack(stream, musicEvent)
    }

    private fun dimOnTrackFinished(stream: DimensionStream) {
        broadcastDimSync(stream, MusicSyncPacket.Action.STOP)
        stream.track = null
        stream.replayTrack = null
        stream.playing = false
        dimScheduleNext(stream)
        broadcastStatus()
    }

    private fun broadcastDimSync(stream: DimensionStream, action: MusicSyncPacket.Action) {
        val track = stream.track ?: return
        val packet = MusicSyncPacket(
            trackId = track,
            startPositionMs = if (action == MusicSyncPacket.Action.PLAY) 0 else System.currentTimeMillis() - stream.startTime,
            serverTimeMs = System.currentTimeMillis(),
            action = action,
            specificSound = if (action == MusicSyncPacket.Action.PLAY) stream.specific else ""
        )
        val server = currentServer() ?: return
        for (player in server.playerList.players) {
            if (shouldPlayerHearDim(player, stream)) {
                PacketHandler.sendToPlayer(player, packet)
            }
        }
    }

    private fun sendDimSyncToPlayer(stream: DimensionStream, player: ServerPlayer) {
        stream.track?.let { track ->
            if (track.startsWith("custom:")) {
                sendCustomTrackToPlayer(track.removePrefix("custom:"), player)
            }
            val syncSpecific = if (stream.specific.isNotEmpty()) stream.specific
                               else stream.resolved ?: ""
            val position = if (stream.paused) stream.pausedPos else System.currentTimeMillis() - stream.startTime
            val packet = MusicSyncPacket(
                trackId = track,
                startPositionMs = position,
                serverTimeMs = System.currentTimeMillis(),
                action = if (stream.playing && !stream.paused) MusicSyncPacket.Action.PLAY else MusicSyncPacket.Action.PAUSE,
                specificSound = syncSpecific
            )
            PacketHandler.sendToPlayer(player, packet)
        }
    }

    private fun resyncDimPlayers() {
        val server = currentServer() ?: return
        for (stream in dimensionStreams.values) {
            if (!stream.active) continue
            for (player in server.playerList.players) {
                if (shouldPlayerHearDim(player, stream)) {
                    if (stream.playing && stream.track != null) {
                        sendDimSyncToPlayer(stream, player)
                    } else {
                        val stopPacket = MusicSyncPacket(
                            trackId = "", startPositionMs = 0,
                            serverTimeMs = System.currentTimeMillis(),
                            action = MusicSyncPacket.Action.STOP
                        )
                        PacketHandler.sendToPlayer(player, stopPacket)
                    }
                }
            }
            if (stream.waiting && stream.ticksSince == 0) {
                stream.delayTicks = 100
            }
        }
        for (player in server.playerList.players) {
            if (shouldPlayerHearPrimary(player) && dimensionStreams.values.none { shouldPlayerHearDim(player, it) }) {
                sendPrimarySyncToPlayer(player)
            }
        }
        broadcastStatus()
    }

    private fun handleDimSyncToggle(player: ServerPlayer) {
        if (isInPriorityMode()) {
            broadcastStatusToPlayer(player)
            return
        }

        val dim = player.entityLevel().dimension().location().toString()
        val stream = getStreamForDim(dim) ?: run {
            broadcastStatusToPlayer(player)
            return
        }

        val server = currentServer()
        val hasOverworldPlayers = server?.playerList?.players?.any {
            it.entityLevel().dimension().location().toString() == "minecraft:overworld"
        } == true

        if (player.uuid in syncOverworld) {
            syncOverworld.remove(player.uuid)
            if (stream.playing && stream.track != null) {
                sendDimSyncToPlayer(stream, player)
            } else {
                val stopPacket = MusicSyncPacket(
                    trackId = "", startPositionMs = 0, serverTimeMs = System.currentTimeMillis(),
                    action = MusicSyncPacket.Action.STOP
                )
                PacketHandler.sendToPlayer(player, stopPacket)
            }
        } else if (hasOverworldPlayers) {
            syncOverworld.add(player.uuid)
            sendPrimarySyncToPlayer(player)
        }
        broadcastStatusToPlayer(player)
    }

    fun onPlayerChangedDimension(fromDimKey: net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, toDimKey: net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, player: ServerPlayer) {
        val fromDim = fromDimKey.location().toString()
        val toDim = toDimKey.location().toString()
        val playlistProtected = currentMode == MusicStatusPacket.PlayMode.PLAYLIST &&
            fromDim != "minecraft:the_end" && toDim != "minecraft:the_end"
        if (fromDim == "minecraft:the_end" && toDim == "minecraft:overworld") {
            creditsPlayers[player.uuid] = System.currentTimeMillis()
        }

        if (!playlistProtected) {
            val stopPacket = MusicSyncPacket(
                trackId = "", startPositionMs = 0, serverTimeMs = System.currentTimeMillis(),
                action = MusicSyncPacket.Action.STOP
            )
            PacketHandler.sendToPlayer(player, stopPacket)
        }

        val fromStream = getStreamForDim(fromDim)
        if (fromStream != null) {
            fromStream.active = countPlayersInDimensionExcluding(fromDim, player) > 0
            val keepPausedStream = !fromStream.active && fromStream.paused && fromStream.track != null
            if (!fromStream.active && !playlistProtected && !keepPausedStream) {
                fromStream.reset()
                broadcastStatus()
            }
        }
        if (fromDim == "minecraft:overworld") {
            val remainingOW = countPlayersInDimensionExcluding("minecraft:overworld", player)
            if (remainingOW == 0 && !playlistProtected) {
                handleOverworldEmpty(player)
            }
        }

        val toStream = getOrCreateStream(toDim)
        if (toStream != null) {
            val wasEmpty = !toStream.active
            toStream.active = countPlayersInDimensionExcluding(toDim, player) + 1 > 0
            if (wasEmpty && toStream.active) {
                toStream.delayTicks = 100
                toStream.ticksSince = 0
                toStream.waiting = true
            }
        }

        if (toDim == "minecraft:overworld") {
            if (!isPlaying && currentMode == MusicStatusPacket.PlayMode.AUTONOMOUS && !waitingForNextTrack) {
                nextMusicDelayTicks = 100
                ticksSinceLastMusic = 0
                waitingForNextTrack = true
            }
        }

        sendAppropriateSyncToPlayer(player, toDim)
        broadcastStatusToPlayer(player, toDim)

        checkPlayerMusicStates()
    }

    fun onPlayerJoin(player: ServerPlayer) {
        if (player.uuid !in playerJoinOrder) playerJoinOrder.add(player.uuid)

        val dim = player.entityLevel().dimension().location().toString()
        if (dim == "minecraft:overworld") {
            val musicEvent = computeMusicEventForPlayer(player)
            playerMusicStates[player.uuid] = PlayerMusicState(musicEvent, System.currentTimeMillis())
            lastDominantMusicEvent = determineDominantMusicEvent()
        }

        if (dim == "minecraft:overworld") {
            if (!isPlaying && currentMode == MusicStatusPacket.PlayMode.AUTONOMOUS && !waitingForNextTrack) {
                nextMusicDelayTicks = 100
                ticksSinceLastMusic = 0
                waitingForNextTrack = true
            }
        }

        val stream = getOrCreateStream(dim)
        if (stream != null) {
            val wasEmpty = !stream.active
            updateDimPopulation(stream)
            if (wasEmpty && stream.active && !stream.playing && !stream.waiting) {
                stream.delayTicks = 100
                stream.ticksSince = 0
                stream.waiting = true
            }
        }

        sendTrackManifest(player)
        sendSyncToPlayer(player)
    }

    fun onPlayerRespawn(player: ServerPlayer) {
        if (player.uuid !in playerJoinOrder) playerJoinOrder.add(player.uuid)

        val dim = player.entityLevel().dimension().location().toString()
        selectedListenDimensions.remove(player.uuid)
        sendStopToPlayer(player)

        var cleanedAnyInactive = false
        for (stream in dimensionStreams.values) {
            updateDimPopulation(stream)
            if (!stream.active && stream.track != null) {
                stream.reset()
                cleanedAnyInactive = true
            }
        }

        if (dim == "minecraft:overworld") {
            if (!isPlaying && currentMode == MusicStatusPacket.PlayMode.AUTONOMOUS && !waitingForNextTrack) {
                nextMusicDelayTicks = 100
                ticksSinceLastMusic = 0
                waitingForNextTrack = true
            }
        }

        sendAppropriateSyncToPlayer(player, dim)
        broadcastStatusToPlayer(player, dim)
        if (cleanedAnyInactive) broadcastStatus()
        checkPlayerMusicStates()
    }

    private fun getAuthorityForPrimary(): java.util.UUID? {
        val server = currentServer() ?: return null
        val players = server.playerList.players
        val onlineUuids = players.map { it.uuid }.toSet()
        val ordered = playerJoinOrder.firstOrNull { uuid ->
            uuid in onlineUuids && players.any { it.uuid == uuid && shouldPlayerHearPrimary(it) }
        }
        if (ordered != null) return ordered
        return players.firstOrNull { shouldPlayerHearPrimary(it) }?.uuid
    }

    private fun getAuthorityForDim(stream: DimensionStream): java.util.UUID? {
        val server = currentServer() ?: return null
        val players = server.playerList.players
        val onlineUuids = players.map { it.uuid }.toSet()
        val ordered = playerJoinOrder.firstOrNull { uuid ->
            uuid in onlineUuids && players.any { it.uuid == uuid && shouldPlayerHearDim(it, stream) }
        }
        if (ordered != null) return ordered
        return players.firstOrNull { shouldPlayerHearDim(it, stream) }?.uuid
    }

    fun onPlayerLeave(leavingPlayer: ServerPlayer) {
        val server = currentServer() ?: return

        if (server.playerList.players.size <= 1) {
            shutdownForNoPlayers()
            return
        }

        playerMusicStates.remove(leavingPlayer.uuid)
        playersDownloading.remove(leavingPlayer.uuid)
        lastSeekTime.remove(leavingPlayer.uuid)
        lastSkipTime.remove(leavingPlayer.uuid)
        creditsPlayers.remove(leavingPlayer.uuid)
        syncOverworld.remove(leavingPlayer.uuid)
        selectedListenDimensions.remove(leavingPlayer.uuid)
        playerJoinOrder.remove(leavingPlayer.uuid)
        playerManifestAllowlist.remove(leavingPlayer.uuid)
        lastTrackRequestTime.remove(leavingPlayer.uuid)

        val dim = leavingPlayer.entityLevel().dimension().location().toString()
        val stream = getStreamForDim(dim)
        if (stream != null) {
            val remaining = server.playerList.players.count {
                it.uuid != leavingPlayer.uuid &&
                it.entityLevel().dimension().location().toString() == dim
            }
            if (remaining == 0) {
                stream.reset()
                broadcastStatus()
            }
        }
        if (dim == "minecraft:overworld") {
            val remainingOW = server.playerList.players.count {
                it.uuid != leavingPlayer.uuid &&
                it.entityLevel().dimension().location().toString() == "minecraft:overworld"
            }
            if (remainingOW == 0) {
                handleOverworldEmpty(leavingPlayer)
            }
        }

        onPlayerMusicStateChanged()
    }

    internal fun forceSyncAll() {
        val server = currentServer() ?: return
        for (player in server.playerList.players) {
            sendSyncToPlayer(player)
        }
    }

    private fun sendSyncToPlayer(player: ServerPlayer) {
        sendAppropriateSyncToPlayer(player)
        broadcastStatusToPlayer(player)
    }

    private fun sendStopToPlayer(player: ServerPlayer) {
        val stopPacket = MusicSyncPacket(
            trackId = "",
            startPositionMs = 0,
            serverTimeMs = System.currentTimeMillis(),
            action = MusicSyncPacket.Action.STOP
        )
        PacketHandler.sendToPlayer(player, stopPacket)
    }

    private fun sendAppropriateSyncToPlayer(player: ServerPlayer, dimensionIdOverride: String? = null) {
        val dim = dimensionIdOverride ?: player.entityLevel().dimension().location().toString()
        val stream = getStreamForDim(dim)
        when {
            stream != null && shouldPlayerHearDimForDim(player, dim, stream) -> {
                if (stream.track != null) sendDimSyncToPlayer(stream, player) else sendStopToPlayer(player)
            }
            shouldPlayerHearPrimaryForDim(player, dim) -> {
                if (currentTrack != null) sendPrimarySyncToPlayer(player) else sendStopToPlayer(player)
            }
            else -> sendStopToPlayer(player)
        }
    }

    private fun sendPrimarySyncToPlayer(player: ServerPlayer) {
        currentTrack?.let { track ->
            if (track.startsWith("custom:")) {
                sendCustomTrackToPlayer(track.removePrefix("custom:"), player)
            }

            val position = if (isPaused) pausedPosition else System.currentTimeMillis() - trackStartTime
            val syncSpecific = if (specificSound.isNotEmpty()) specificSound
                               else resolvedTrackName ?: ""

            val packet = MusicSyncPacket(
                trackId = track,
                startPositionMs = position,
                serverTimeMs = System.currentTimeMillis(),
                action = if (isPlaying && !isPaused) MusicSyncPacket.Action.PLAY else MusicSyncPacket.Action.PAUSE,
                specificSound = syncSpecific
            )
            PacketHandler.sendToPlayer(player, packet)
        }
    }

    private fun broadcastSync(action: MusicSyncPacket.Action) {
        val track = currentTrack ?: return
        val packet = MusicSyncPacket(
            trackId = track,
            startPositionMs = if (action == MusicSyncPacket.Action.PLAY) 0 else System.currentTimeMillis() - trackStartTime,
            serverTimeMs = System.currentTimeMillis(),
            action = action,
            specificSound = if (action == MusicSyncPacket.Action.PLAY) specificSound else ""
        )
        val server = currentServer() ?: return
        for (p in server.playerList.players) {
            if (shouldPlayerHearPrimary(p)) {
                PacketHandler.sendToPlayer(p, packet)
            }
        }
    }

    internal fun seekTrack(positionMs: Long) {
        if (currentTrack == null || !isPlaying) return
        val clamped = positionMs.coerceIn(0, if (trackDuration > 0) trackDuration else Long.MAX_VALUE)
        val now = System.currentTimeMillis()
        finishReportBlockedUntilMs = now + SEEK_FINISH_GRACE_MS
        trackStartTime = now - clamped
        if (isPaused) pausedPosition = clamped
        val track = currentTrack ?: return
        val seekSpecific = if (specificSound.isNotEmpty()) specificSound
                           else resolvedTrackName ?: ""
        val packet = MusicSyncPacket(
            trackId = track,
            startPositionMs = clamped,
            serverTimeMs = now,
            action = MusicSyncPacket.Action.SEEK,
            specificSound = seekSpecific
        )
        val server = currentServer() ?: return
        for (p in server.playerList.players) {
            if (shouldPlayerHearPrimary(p)) {
                PacketHandler.sendToPlayer(p, packet)
            }
        }
        broadcastStatus()
    }

    private fun broadcastStatus() {
        val server = currentServer() ?: return
        for (player in server.playerList.players) broadcastStatusToPlayer(player)
    }

    private fun broadcastStatusToPlayer(player: ServerPlayer, dimensionIdOverride: String? = null) {
        val dim = dimensionIdOverride ?: player.entityLevel().dimension().location().toString()
        val priorityActive = isInPriorityMode()
        val dimStream = dimensionStreams.values.firstOrNull { shouldPlayerHearDimForDim(player, dim, it) }

        val currentPosition = if (dimStream != null) {
            when {
                dimStream.paused -> dimStream.pausedPos
                dimStream.playing && dimStream.track != null -> System.currentTimeMillis() - dimStream.startTime
                else -> 0
            }
        } else {
            when {
                isPaused -> pausedPosition
                isPlaying && currentTrack != null -> System.currentTimeMillis() - trackStartTime
                else -> 0
            }
        }

        val packet = MusicStatusPacket(
            currentTrack = if (dimStream != null) (dimStream.replayTrack ?: dimStream.track) else (currentReplayTrack ?: currentTrack),
            currentPositionMs = currentPosition,
            durationMs = if (dimStream != null) dimStream.duration else trackDuration,
            isPlaying = if (dimStream != null) (dimStream.playing && !dimStream.paused) else (isPlaying && !isPaused),
            queue = userPlaylist.toList(),
            mode = if (dimStream != null) MusicStatusPacket.PlayMode.AUTONOMOUS else currentMode,
            repeatMode = repeatMode,
            priorityActive = priorityActive,
            resolvedName = if (dimStream != null) (dimStream.resolved ?: "") else (resolvedTrackName ?: ""),
            waitingForNextTrack = if (dimStream != null) dimStream.waiting else waitingForNextTrack,
            ticksSinceLastMusic = if (dimStream != null) dimStream.ticksSince else ticksSinceLastMusic,
            nextMusicDelayTicks = if (dimStream != null) dimStream.delayTicks else nextMusicDelayTicks,
            customMinDelay = getDelayForPlayerDim(player, dimensionIdOverride).first,
            customMaxDelay = getDelayForPlayerDim(player, dimensionIdOverride).second,
            syncOverworld = player.uuid in syncOverworld,
            activeDimensions = getActiveDimensionStatuses()
        )
        PacketHandler.sendToPlayer(player, packet)
    }

    private fun getActiveDimensionStatuses(): List<MusicStatusPacket.DimensionStatus> {
        val server = currentServer() ?: return emptyList()
        val grouped = server.playerList.players
            .groupBy { it.entityLevel().dimension().location().toString() }
            .filterValues { it.isNotEmpty() }

        fun sortKey(id: String): String = when (id) {
            "minecraft:overworld" -> "0"
            "minecraft:the_nether" -> "1"
            "minecraft:the_end" -> "2"
            else -> "3:$id"
        }

        return grouped.entries
            .sortedBy { sortKey(it.key) }
            .map { (dimensionId, players) ->
                val stream = if (dimensionId == "minecraft:overworld") null else dimensionStreams[dimensionId]
                val posMs: Long
                val trackId: String?
                val resolved: String
                val playing: Boolean
                val waiting: Boolean
                val ticksSince: Int
                val delayTicks: Int
                val durationMs: Long
                if (stream != null) {
                    posMs = when {
                        stream.paused -> stream.pausedPos
                        stream.playing -> System.currentTimeMillis() - stream.startTime
                        else -> 0L
                    }
                    trackId = stream.replayTrack ?: stream.track
                    resolved = stream.resolved ?: ""
                    playing = stream.playing && !stream.paused
                    waiting = stream.waiting
                    ticksSince = stream.ticksSince
                    delayTicks = stream.delayTicks
                    durationMs = stream.duration
                } else {
                    posMs = when {
                        isPaused -> pausedPosition
                        isPlaying && currentTrack != null -> System.currentTimeMillis() - trackStartTime
                        else -> 0L
                    }
                    trackId = currentReplayTrack ?: currentTrack
                    resolved = resolvedTrackName ?: ""
                    playing = isPlaying && !isPaused
                    waiting = waitingForNextTrack
                    ticksSince = ticksSinceLastMusic
                    delayTicks = nextMusicDelayTicks
                    durationMs = trackDuration
                }
                MusicStatusPacket.DimensionStatus(
                    id = dimensionId,
                    players = players.map { it.gameProfile.name }.sortedBy { it.lowercase() },
                    currentTrack = trackId,
                    resolvedName = resolved,
                    isPlaying = playing,
                    currentPositionMs = posMs,
                    durationMs = durationMs,
                    waitingForNextTrack = waiting,
                    ticksSinceLastMusic = ticksSince,
                    nextMusicDelayTicks = delayTicks
                )
            }
    }

    fun handleClientInfo(packet: MusicClientInfoPacket, player: ServerPlayer) {
        when (packet.action) {
            MusicClientInfoPacket.Action.REPORT_DURATION -> {
                if (packet.trackId == currentTrack && packet.durationMs > 0) {
                    trackDuration = packet.durationMs
                    val receivedResolved = packet.resolvedName
                    if (receivedResolved.isNotEmpty()) {
                        if (resolvedTrackName == null) resolvedTrackName = receivedResolved
                        if (specificSound.isEmpty() || specificSound.startsWith("alias:")) {
                            specificSound = receivedResolved
                            currentReplayTrack = "$currentTrack|$specificSound"
                        }
                    }
                    val needsResync = receivedResolved.isNotEmpty()
                    if (needsResync) {
                        val authority = getAuthorityForPrimary()
                        if (player.uuid == authority) {
                            dev.mcrib884.musync.MuSyncLog.info("Track (authority): ${resolvedTrackName} (${packet.durationMs}ms)")
                            if (isPlaying && !isPaused) {
                                val position = System.currentTimeMillis() - trackStartTime
                                val resyncPacket = MusicSyncPacket(
                                    trackId = currentTrack!!,
                                    startPositionMs = position,
                                    serverTimeMs = System.currentTimeMillis(),
                                    action = MusicSyncPacket.Action.PLAY,
                                    specificSound = specificSound
                                )
                                val server = currentServer()
                                if (server != null) {
                                    for (p in server.playerList.players) {
                                        if (p.uuid != player.uuid && shouldPlayerHearPrimary(p)) {
                                            PacketHandler.sendToPlayer(p, resyncPacket)
                                        }
                                    }
                                }
                            }
                        } else {
                            dev.mcrib884.musync.MuSyncLog.debug("Track (non-authority): ${receivedResolved} (${packet.durationMs}ms)")
                        }
                    } else {
                        dev.mcrib884.musync.MuSyncLog.info("Track: ${resolvedTrackName ?: "?"} (${packet.durationMs}ms)")
                    }
                    broadcastStatus()
                } else {
                    for (stream in dimensionStreams.values) {
                        if (packet.trackId == stream.track && packet.durationMs > 0) {
                            stream.duration = packet.durationMs
                            val receivedResolved = packet.resolvedName
                            if (receivedResolved.isNotEmpty()) {
                                if (stream.resolved == null) stream.resolved = receivedResolved
                                if (stream.specific.isEmpty() || stream.specific.startsWith("alias:")) {
                                    stream.specific = receivedResolved
                                    stream.replayTrack = "${stream.track}|$receivedResolved"
                                }
                            }
                            val needsResync = receivedResolved.isNotEmpty()
                            if (needsResync) {
                                val authority = getAuthorityForDim(stream)
                                if (player.uuid == authority) {
                                    dev.mcrib884.musync.MuSyncLog.info("${stream.dimensionId} track (authority): ${stream.resolved} (${packet.durationMs}ms)")
                                    if (stream.playing && !stream.paused) {
                                        val position = System.currentTimeMillis() - stream.startTime
                                        val resyncPacket = MusicSyncPacket(
                                            trackId = stream.track!!,
                                            startPositionMs = position,
                                            serverTimeMs = System.currentTimeMillis(),
                                            action = MusicSyncPacket.Action.PLAY,
                                            specificSound = stream.specific
                                        )
                                        val server = currentServer()
                                        if (server != null) {
                                            for (p in server.playerList.players) {
                                                if (p.uuid != player.uuid && shouldPlayerHearDim(p, stream)) {
                                                    PacketHandler.sendToPlayer(p, resyncPacket)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    dev.mcrib884.musync.MuSyncLog.debug("${stream.dimensionId} track (non-authority): ${receivedResolved} (${packet.durationMs}ms)")
                                }
                            } else {
                                dev.mcrib884.musync.MuSyncLog.info("${stream.dimensionId} track: ${stream.resolved ?: "?"} (${packet.durationMs}ms)")
                            }
                            broadcastStatus()
                            break
                        }
                    }
                }
            }
            MusicClientInfoPacket.Action.TRACK_FINISHED -> {
                val authority = getAuthorityForPrimary()
                if (packet.trackId == currentTrack && isPlaying) {
                    if (authority != null && player.uuid != authority) {
                        dev.mcrib884.musync.MuSyncLog.debug("Ignoring TRACK_FINISHED from non-authority ${player.name.string}")
                        return
                    }
                    if (System.currentTimeMillis() < finishReportBlockedUntilMs) {
                        dev.mcrib884.musync.MuSyncLog.debug("Ignoring TRACK_FINISHED during seek grace for primary track ${packet.trackId}")
                        return
                    }
                    val elapsed = System.currentTimeMillis() - trackStartTime
                    if (elapsed < MIN_VALID_TRACK_FINISH_MS) {
                        dev.mcrib884.musync.MuSyncLog.debug("Ignoring early TRACK_FINISHED for primary track ${packet.trackId} after ${elapsed}ms")
                        return
                    }
                    dev.mcrib884.musync.MuSyncLog.info("Client reported track finished: ${packet.trackId}")
                    pendingClientEndSession = currentTrackSession
                } else {
                    for (stream in dimensionStreams.values) {
                        if (packet.trackId == stream.track && stream.playing) {
                            val dimAuth = getAuthorityForDim(stream)
                            if (dimAuth != null && player.uuid != dimAuth) {
                                dev.mcrib884.musync.MuSyncLog.debug("Ignoring dim TRACK_FINISHED from non-authority ${player.name.string}")
                                return
                            }
                            if (System.currentTimeMillis() < stream.finishReportBlockedUntilMs) {
                                dev.mcrib884.musync.MuSyncLog.debug("Ignoring TRACK_FINISHED during seek grace for ${stream.dimensionId} track ${packet.trackId}")
                                return
                            }
                            val elapsed = System.currentTimeMillis() - stream.startTime
                            if (elapsed < MIN_VALID_TRACK_FINISH_MS) {
                                dev.mcrib884.musync.MuSyncLog.debug("Ignoring early TRACK_FINISHED for ${stream.dimensionId} track ${packet.trackId} after ${elapsed}ms")
                                return
                            }
                            dev.mcrib884.musync.MuSyncLog.info("Client reported ${stream.dimensionId} track finished: ${packet.trackId}")
                            stream.clientEnd = true
                            break
                        }
                    }
                }
            }
            MusicClientInfoPacket.Action.LOAD_FAILED -> {
                val authority = getAuthorityForPrimary()
                if (packet.trackId == currentTrack && isPlaying) {
                    if (authority != null && player.uuid != authority) {
                        dev.mcrib884.musync.MuSyncLog.debug("Ignoring LOAD_FAILED from non-authority ${player.name.string}")
                        return
                    }
                    dev.mcrib884.musync.MuSyncLog.warn("Client failed to load primary track: ${packet.trackId}")
                    pendingClientEndSession = currentTrackSession
                } else {
                    for (stream in dimensionStreams.values) {
                        if (packet.trackId == stream.track && stream.playing) {
                            val dimAuth = getAuthorityForDim(stream)
                            if (dimAuth != null && player.uuid != dimAuth) {
                                dev.mcrib884.musync.MuSyncLog.debug("Ignoring dim LOAD_FAILED from non-authority ${player.name.string}")
                                return
                            }
                            dev.mcrib884.musync.MuSyncLog.warn("Client failed to load ${stream.dimensionId} track: ${packet.trackId}")
                            stream.clientEnd = true
                            break
                        }
                    }
                }
            }
        }
    }

    fun onServerStarted() {
        CustomTrackManager.scan(serverDir(currentServer()))
        loadDimensionDelays()
    }

    fun onServerStopping() {
        currentTrack = null
        currentReplayTrack = null
        isPlaying = false
        isPaused = false
        resolvedTrackName = null
        specificSound = ""
        finishReportBlockedUntilMs = 0L
        waitingForNextTrack = false
        ticksSinceLastMusic = 0
        currentTrackSession = 0L
        pendingClientEndSession = -1L
        playerMusicStates.clear()
        lastDominantMusicEvent = null
        lastDragonFightActive = false
        opStateCheckCounter = 0
        dimBiomeCheckCounter = 0
        recentTracks.clear()
        trackHistory.clear()
        userPlaylist.clear()
        lastSeekTime.clear()
        creditsPlayers.clear()
        dimensionStreams.clear()
        syncOverworld.clear()
        selectedListenDimensions.clear()
        playerJoinOrder.clear()
        lastTrackRequestTime.clear()
        playerManifestAllowlist.clear()
        playersDownloading.clear()
        wasInPriority = false
        saveDimensionDelays()
        currentMode = MusicStatusPacket.PlayMode.AUTONOMOUS
    }

    fun getStatusInfo(): Triple<String, Boolean, List<String>> {
        return Triple(currentReplayTrack ?: currentTrack ?: "None", isPlaying && !isPaused, userPlaylist.toList())
    }

    fun getDetailedStatus(): Map<String, Any> {
        val currentPosition = if (isPaused) pausedPosition
                              else if (currentTrack != null) System.currentTimeMillis() - trackStartTime
                              else 0
        return mapOf(
            "track" to (currentReplayTrack ?: currentTrack ?: "None"),
            "resolvedName" to (resolvedTrackName ?: ""),
            "playing" to (isPlaying && !isPaused),
            "paused" to isPaused,
            "position" to currentPosition,
            "duration" to trackDuration,
            "mode" to currentMode.name,
            "queue" to userPlaylist.toList(),
            "queueSize" to userPlaylist.size,
            "waitingForNextTrack" to waitingForNextTrack,
            "ticksSinceLastMusic" to ticksSinceLastMusic,
            "nextMusicDelayTicks" to nextMusicDelayTicks,
            "customMinDelay" to (dimensionDelays["minecraft:overworld"]?.first ?: -1),
            "customMaxDelay" to (dimensionDelays["minecraft:overworld"]?.second ?: -1)
        )
    }

    fun setCustomDelay(dimensionId: String, minTicks: Int, maxTicks: Int) {
        dimensionDelays[dimensionId] = Pair(minTicks, maxTicks)
        saveDimensionDelays()
    }

    fun resetCustomDelay(dimensionId: String) {
        dimensionDelays.remove(dimensionId)
        saveDimensionDelays()
    }

    private fun getDelayForPlayerDim(player: ServerPlayer, dimOverride: String?): Pair<Int, Int> {
        val dim = dimOverride ?: selectedListenDimensions[player.uuid] ?: player.entityLevel().dimension().location().toString()
        val delay = dimensionDelays[dim]
        return if (delay != null) Pair(delay.first, delay.second) else Pair(-1, -1)
    }

    private fun getDelaysFile(): java.io.File {
        val server = currentServer()
        val serverDir = serverDir(server)
        val musyncDir = java.io.File(serverDir, "musync")
        if (!musyncDir.exists()) musyncDir.mkdirs()
        return java.io.File(musyncDir, "dimension_delays.properties")
    }

    private fun saveDimensionDelays() {
        try {
            val file = getDelaysFile()
            val props = java.util.Properties()
            for ((dim, delay) in dimensionDelays) {
                props.setProperty(dim, "${delay.first},${delay.second}")
            }
            file.outputStream().use { props.store(it, "MuSync per-dimension delay overrides (ticks)") }
        } catch (e: Exception) {
            dev.mcrib884.musync.MuSyncLog.warn("Failed to save dimension delays: ${e.message}")
        }
    }

    private fun loadDimensionDelays() {
        try {
            val file = getDelaysFile()
            if (!file.exists()) return
            val props = java.util.Properties()
            file.inputStream().use { props.load(it) }
            dimensionDelays.clear()
            for (key in props.stringPropertyNames()) {
                val value = props.getProperty(key) ?: continue
                val parts = value.split(",")
                if (parts.size == 2) {
                    val min = parts[0].trim().toIntOrNull()
                    val max = parts[1].trim().toIntOrNull()
                    if (min != null && max != null && max >= min) {
                        dimensionDelays[key] = Pair(min, max)
                    }
                }
            }
            if (dimensionDelays.isNotEmpty()) {
                dev.mcrib884.musync.MuSyncLog.info("Loaded ${dimensionDelays.size} dimension delay override(s)")
            }
        } catch (e: Exception) {
            dev.mcrib884.musync.MuSyncLog.warn("Failed to load dimension delays: ${e.message}")
        }
    }

    fun sendOpenGui(player: ServerPlayer) {
        val packet = MusicSyncPacket(
            trackId = "", startPositionMs = 0, serverTimeMs = 0,
            action = MusicSyncPacket.Action.OPEN_GUI
        )
        PacketHandler.sendToPlayer(player, packet)
    }

    fun getCustomTracks(): List<String> =
        (CustomTrackManager.getTrackNames() + CustomTrackManager.getInternalTrackNames()).distinct().sorted()

    private fun hotloadTracks() {
        val server = currentServer() ?: return
        val serverDir = serverDir(server)
        
        java.util.concurrent.CompletableFuture.runAsync {
            CustomTrackManager.scan(serverDir)
            server.execute {
                for (player in server.playerList.players) {
                    sendTrackManifest(player)
                }
                dev.mcrib884.musync.MuSyncLog.info("Hotloaded custom tracks: ${CustomTrackManager.getTrackCount()} indexed")
            }
        }
    }

    private fun sendTrackManifest(player: ServerPlayer) {
        val manifest = CustomTrackManager.getManifest().take(MAX_MANIFEST_SEND)
        val manifestVersion = manifestVersionCounter.getAndIncrement()
        playerManifestAllowlist[player.uuid] = ManifestAllowlist(manifestVersion, manifest.map { it.first }.toSet())
        val packet = dev.mcrib884.musync.network.TrackManifestPacket(manifestVersion, manifest)
        PacketHandler.sendToPlayer(player, packet)
        dev.mcrib884.musync.MuSyncLog.info("Sent track manifest to ${player.name.string}: ${manifest.size} tracks (version=$manifestVersion)")
    }

    fun handleTrackRequest(manifestVersion: Long, trackNames: List<String>, player: ServerPlayer) {
        if (trackNames.isEmpty()) return

        val allowlistEntry = playerManifestAllowlist[player.uuid]
        if (allowlistEntry == null || allowlistEntry.tracks.isEmpty()) {
            dev.mcrib884.musync.MuSyncLog.warn("Ignoring track request from ${player.name.string} before manifest sync")
            return
        }

        if (manifestVersion != allowlistEntry.version) {
            dev.mcrib884.musync.MuSyncLog.warn("Ignoring track request from ${player.name.string} for stale manifest version $manifestVersion (expected ${allowlistEntry.version})")
            sendTrackManifest(player)
            return
        }

        val now = System.currentTimeMillis()
        val lastRequest = lastTrackRequestTime[player.uuid] ?: 0L
        if (now - lastRequest < TRACK_REQUEST_COOLDOWN_MS) {
            dev.mcrib884.musync.MuSyncLog.warn("Rate limiting track request from ${player.name.string}")
            return
        }

        val orderedUnique = LinkedHashSet<String>()
        for (name in trackNames) {
            val internal = CustomTrackManager.toInternalName(name) ?: continue
            if (internal !in allowlistEntry.tracks) continue
            orderedUnique.add(internal)
            if (orderedUnique.size >= MAX_TRACKS_PER_REQUEST) break
        }
        val validNames = orderedUnique.toList()
        if (validNames.isEmpty()) return

        lastTrackRequestTime[player.uuid] = now

        dev.mcrib884.musync.MuSyncLog.info("${player.name.string} requested ${validNames.size} missing tracks")
        playersDownloading.add(player.uuid)
        trackSendExecutor.submit {
            try {
                for (name in validNames) {
                    val file = CustomTrackManager.getTrackFile(name)
                    if (file == null || !file.exists()) {
                        dev.mcrib884.musync.MuSyncLog.warn("Track not found for request: $name")
                        continue
                    }
                    val chunkSize = dev.mcrib884.musync.network.CustomTrackDataPacket.CHUNK_SIZE
                    val fileSizeLong = file.length()
                    if (fileSizeLong <= 0L || fileSizeLong > PacketIO.MAX_TRACK_SIZE_BYTES || fileSizeLong > Int.MAX_VALUE.toLong()) {
                        dev.mcrib884.musync.MuSyncLog.warn("Skipping invalid track size for request '$name': $fileSizeLong")
                        continue
                    }
                    val fileSize = fileSizeLong.toInt()
                    val totalChunks = (fileSize + chunkSize - 1) / chunkSize
                    file.inputStream().use { input ->
                        for (i in 0 until totalChunks) {
                            val remaining = fileSize - (i * chunkSize)
                            val readSize = minOf(chunkSize, remaining)
                            val chunk = ByteArray(readSize)
                            var offset = 0
                            while (offset < readSize) {
                                val bytesRead = input.read(chunk, offset, readSize - offset)
                                if (bytesRead < 0) break
                                offset += bytesRead
                            }
                            if (offset != readSize) {
                                dev.mcrib884.musync.MuSyncLog.error("Failed to read full chunk $i/$totalChunks of $name")
                                break
                            }
                            val packet = dev.mcrib884.musync.network.CustomTrackDataPacket(
                                trackName = name, chunkIndex = i, totalChunks = totalChunks, data = chunk
                            )
                            try {
                                PacketHandler.sendToPlayer(player, packet)
                            } catch (e: Exception) {
                                dev.mcrib884.musync.MuSyncLog.error("Failed to send chunk $i/$totalChunks of $name: ${e.message}")
                                break
                            }
                            try {
                                Thread.sleep(10)
                            } catch (_: InterruptedException) {
                                Thread.currentThread().interrupt()
                                return@submit
                            }
                        }
                    }
                    dev.mcrib884.musync.MuSyncLog.info("Sent track $name to ${player.name.string} ($totalChunks chunks, $fileSize bytes)")
                    try {
                        Thread.sleep(50)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return@submit
                    }
                }
                dev.mcrib884.musync.MuSyncLog.info("Finished sending ${validNames.size} tracks to ${player.name.string}")
            } finally {
                playersDownloading.remove(player.uuid)
            }
        }
    }

    private fun sendCustomTrackToPlayers(trackName: String, players: Iterable<ServerPlayer>) {
        for (player in players) {
            sendCustomTrackToPlayer(trackName, player)
        }
    }

    private fun sendCustomTrackToPlayer(trackName: String, player: ServerPlayer) {
        val file = CustomTrackManager.getTrackFile(trackName) ?: return
        val chunkSize = dev.mcrib884.musync.network.CustomTrackDataPacket.CHUNK_SIZE
        val fileSizeLong = file.length()
        if (fileSizeLong <= 0L || fileSizeLong > PacketIO.MAX_TRACK_SIZE_BYTES || fileSizeLong > Int.MAX_VALUE.toLong()) {
            dev.mcrib884.musync.MuSyncLog.warn("Skipping custom track with invalid size '$trackName': $fileSizeLong")
            return
        }
        val fileSize = fileSizeLong.toInt()
        val totalChunks = (fileSize + chunkSize - 1) / chunkSize

        trackSendExecutor.submit {
            try {
                file.inputStream().buffered().use { input ->
                    for (i in 0 until totalChunks) {
                        val remaining = fileSize - (i * chunkSize)
                        val readSize = minOf(chunkSize, remaining)
                        val chunk = ByteArray(readSize)
                        var offset = 0
                        while (offset < readSize) {
                            val bytesRead = input.read(chunk, offset, readSize - offset)
                            if (bytesRead < 0) break
                            offset += bytesRead
                        }
                        if (offset != readSize) {
                            dev.mcrib884.musync.MuSyncLog.error("Failed to stream full chunk $i/$totalChunks of '$trackName' to ${player.name.string}")
                            return@submit
                        }
                        val packet = dev.mcrib884.musync.network.CustomTrackDataPacket(
                            trackName = trackName, chunkIndex = i, totalChunks = totalChunks, data = chunk
                        )
                        PacketHandler.sendToPlayer(player, packet)
                        try {
                            Thread.sleep(10)
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                            return@submit
                        }
                    }
                }
            } catch (e: Exception) {
                dev.mcrib884.musync.MuSyncLog.error("Failed to stream track '$trackName' to ${player.name.string}: ${e.message}")
            }
        }
    }

}
