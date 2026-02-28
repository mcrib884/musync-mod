package dev.mcrib884.musync.server

import dev.mcrib884.musync.network.*
import net.minecraft.server.level.ServerPlayer
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.server.ServerStartedEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.server.ServerLifecycleHooks
import net.minecraftforge.network.PacketDistributor
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random
import java.io.File

object MusicManager {
    private var currentTrack: String? = null
    private var trackStartTime: Long = 0
    private var trackDuration: Long = 0
    private var isPlaying: Boolean = false
    private var isPaused: Boolean = false
    private var pausedPosition: Long = 0
    private var currentMode: MusicStatusPacket.PlayMode = MusicStatusPacket.PlayMode.AUTONOMOUS
    private var resolvedTrackName: String? = null
    private var specificSound: String = ""

    private val userPlaylist: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    private var ticksSinceLastMusic: Int = 0
    private var nextMusicDelayTicks: Int = 0
    private var waitingForNextTrack: Boolean = false

    private var recentTracks: MutableList<String> = mutableListOf()
    private val maxRecentTracks = 6

    private var clientReportedEnd: Boolean = false

    private data class OpPlayerState(val musicEvent: String, val lastChanged: Long)
    private val opPlayerStates = mutableMapOf<java.util.UUID, OpPlayerState>()
    private var lastDominantMusicEvent: String? = null
    private var lastDragonFightActive: Boolean = false

    private var customMinDelay: Int? = null
    private var customMaxDelay: Int? = null
    private var opStateCheckCounter: Int = 0
    private val OP_STATE_CHECK_INTERVAL = 20

    private var syncCheckCounter: Int = 0
    private val SYNC_CHECK_INTERVAL = 100

    private val lastSeekTime = mutableMapOf<java.util.UUID, Long>()
    private val SEEK_THROTTLE_MS = 200L

    private val playersDownloading = mutableSetOf<java.util.UUID>()

    private val creditsPlayers = mutableMapOf<java.util.UUID, Long>()
    private val CREDITS_DURATION_MS = 480_000L

    private class DimensionStream(val dimensionId: String) {
        var track: String? = null
        var startTime: Long = 0
        var duration: Long = 0
        var playing: Boolean = false
        var paused: Boolean = false
        var pausedPos: Long = 0
        var specific: String = ""
        var resolved: String? = null
        var waiting: Boolean = false
        var ticksSince: Int = 0
        var delayTicks: Int = 0
        var recent: MutableList<String> = mutableListOf()
        var clientEnd: Boolean = false
        var syncCounter: Int = 0
        var active: Boolean = false

        fun reset() {
            track = null; startTime = 0; duration = 0; playing = false
            paused = false; pausedPos = 0; specific = ""; resolved = null
            waiting = false; ticksSince = 0; delayTicks = 0
            recent.clear(); clientEnd = false; syncCounter = 0; active = false
        }
    }

    private val dimensionStreams = mutableMapOf<String, DimensionStream>()

    private val syncOverworld = mutableSetOf<java.util.UUID>()
    private var overworldActive: Boolean = false
    private var wasInPriority: Boolean = false

    fun isPlayerDownloading(uuid: java.util.UUID): Boolean = uuid in playersDownloading

    private fun isPlayerOp(player: ServerPlayer): Boolean {
        return player.server.playerList.isOp(player.gameProfile)
    }

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

    private fun getBiomeMusicEvent(player: ServerPlayer): String? {
        return try {
            val music = player.level().getBiome(player.blockPosition()).value()
                .getBackgroundMusic().orElse(null) ?: return null
            music.event.value().location.toString()
        } catch (_: Exception) { null }
    }

    private fun getBiomeMusicTiming(player: ServerPlayer): MusicTiming? {
        return try {
            val music = player.level().getBiome(player.blockPosition()).value()
                .getBackgroundMusic().orElse(null) ?: return null
            MusicTiming(music.minDelay, music.maxDelay, music.replaceCurrentMusic())
        } catch (_: Exception) { null }
    }

    fun handleControlPacket(packet: MusicControlPacket, player: ServerPlayer) {
        if (player.uuid in playersDownloading) return

        if (packet.action == MusicControlPacket.Action.TOGGLE_NETHER_SYNC) {
            handleDimSyncToggle(player)
            return
        }

        if (!isPlayerOp(player)) return

        when (packet.action) {
            MusicControlPacket.Action.PLAY_TRACK -> {
                packet.trackId?.let { friendlyName ->
                    val trackId = dev.mcrib884.musync.command.MuSyncCommand.getTrackId(friendlyName)
                    val specific = dev.mcrib884.musync.command.MuSyncCommand.getSpecificSound(friendlyName)
                    if (trackId != null) {
                        playTrack(trackId, specific)
                    } else {
                        playTrack(friendlyName)
                    }
                }
            }
            MusicControlPacket.Action.STOP -> stopMusic()
            MusicControlPacket.Action.SKIP -> skipTrack()
            MusicControlPacket.Action.PAUSE -> pauseMusic()
            MusicControlPacket.Action.RESUME -> resumeMusic()
            MusicControlPacket.Action.REQUEST_SYNC -> sendSyncToPlayer(player)
            MusicControlPacket.Action.ADD_TO_QUEUE -> {
                packet.trackId?.let { friendlyName ->
                    val trackId = dev.mcrib884.musync.command.MuSyncCommand.getTrackId(friendlyName)
                    val specific = dev.mcrib884.musync.command.MuSyncCommand.getSpecificSound(friendlyName)
                    if (trackId != null) {
                        if (specific.isNotEmpty()) addToQueue("$trackId|$specific") else addToQueue(trackId)
                    } else {
                        addToQueue(friendlyName)
                    }
                }
            }
            MusicControlPacket.Action.REMOVE_FROM_QUEUE -> {
                packet.queuePosition?.let { removeFromQueue(it) }
            }
            MusicControlPacket.Action.SET_DELAY -> {
                val raw = packet.trackId ?: "reset"
                if (raw == "reset") {
                    resetCustomDelay()
                } else {
                    val parts = raw.split(":")
                    if (parts.size == 2) {
                        val min = parts[0].toIntOrNull()
                        val max = parts[1].toIntOrNull()
                        if (min != null && max != null && max >= min) setCustomDelay(min, max)
                    }
                }
                broadcastStatus()
            }
            MusicControlPacket.Action.SEEK -> {
                val seekMs = packet.queuePosition?.toLong() ?: return
                val now = System.currentTimeMillis()
                val lastSeek = lastSeekTime[player.uuid] ?: 0L
                if (now - lastSeek < SEEK_THROTTLE_MS) return
                lastSeekTime[player.uuid] = now
                seekTrack(seekMs)
            }
            MusicControlPacket.Action.TOGGLE_NETHER_SYNC -> {}
        }
    }

    internal fun playTrack(trackId: String, specific: String = "") {
        val actualTrackId: String
        val actualSpecific: String
        if (specific.isEmpty() && trackId.contains("|")) {
            val parts = trackId.split("|", limit = 2)
            actualTrackId = parts[0]
            actualSpecific = parts[1]
        } else {
            actualTrackId = trackId
            actualSpecific = specific
        }

        currentTrack = actualTrackId
        specificSound = actualSpecific
        trackStartTime = System.currentTimeMillis()
        trackDuration = 0
        isPlaying = true
        isPaused = false
        waitingForNextTrack = false
        resolvedTrackName = if (actualSpecific.isNotEmpty()) actualSpecific else null

        if (actualTrackId.startsWith("custom:")) {
            sendCustomTrackToAll(actualTrackId.removePrefix("custom:"))
        }

        broadcastSync(MusicSyncPacket.Action.PLAY)
        broadcastStatus()
    }

    internal fun stopMusic() {
        if (currentTrack != null) broadcastSync(MusicSyncPacket.Action.STOP)
        currentTrack = null
        isPlaying = false
        isPaused = false
        currentMode = MusicStatusPacket.PlayMode.AUTONOMOUS
        scheduleNextAutonomousTrack()
        broadcastStatus()
    }

    internal fun skipTrack() {
        if (userPlaylist.isNotEmpty()) {
            val nextTrack = userPlaylist.poll()
            currentMode = MusicStatusPacket.PlayMode.PLAYLIST
            playTrack(nextTrack)
        } else {
            playNextAutoTrack()
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
            val server = ServerLifecycleHooks.getCurrentServer() ?: return
            for (p in server.playerList.players) {
                if (shouldPlayerHearPrimary(p)) {
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with { p }, packet)
                }
            }
            broadcastStatus()
        }
    }

    internal fun addToQueue(trackId: String) {
        userPlaylist.add(trackId)
        if (!isPlaying || currentTrack == null) {
            val nextTrack = userPlaylist.poll()
            currentMode = MusicStatusPacket.PlayMode.PLAYLIST
            playTrack(nextTrack)
        }
        broadcastStatus()
    }

    private fun removeFromQueue(position: Int) {
        val list = userPlaylist.toList()
        if (position in list.indices) {
            userPlaylist.remove(list[position])
            broadcastStatus()
        }
    }

    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return

        if (creditsPlayers.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val expired = creditsPlayers.entries.filter { now - it.value >= CREDITS_DURATION_MS }
            if (expired.isNotEmpty()) {
                expired.forEach { creditsPlayers.remove(it.key) }
                checkOpPlayerStates()
            }
        }

        opStateCheckCounter++
        if (opStateCheckCounter >= OP_STATE_CHECK_INTERVAL) {
            opStateCheckCounter = 0
            checkOpPlayerStates()
        }

        if (isPlaying && !isPaused && currentTrack != null) {
            syncCheckCounter++
            if (syncCheckCounter >= SYNC_CHECK_INTERVAL) {
                syncCheckCounter = 0
                broadcastSyncCheck()
            }
        } else {
            syncCheckCounter = 0
        }

        if (clientReportedEnd) {
            clientReportedEnd = false
            onTrackFinished()
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
                } else if (stream.waiting && !stream.paused) {
                    stream.ticksSince++
                    if (stream.ticksSince >= stream.delayTicks) {
                        stream.waiting = false
                        stream.ticksSince = 0
                        dimPlayNext(stream)
                    }
                }

                if (stream.playing && !stream.paused && stream.track != null) {
                    stream.syncCounter++
                    if (stream.syncCounter >= SYNC_CHECK_INTERVAL) {
                        stream.syncCounter = 0
                        broadcastDimSyncCheck(stream)
                    }
                } else {
                    stream.syncCounter = 0
                }
            }
        }

        if (!inPriority) {
            val wasActive = overworldActive
            updateOverworldPopulation()
            if (wasActive && !overworldActive) {
                if (currentTrack != null) broadcastSync(MusicSyncPacket.Action.STOP)
                currentTrack = null
                isPlaying = false
                isPaused = false
                waitingForNextTrack = false
                ticksSinceLastMusic = 0
                clientReportedEnd = false
                broadcastStatus()
            } else if (!wasActive && overworldActive && !isPlaying && !waitingForNextTrack) {
                nextMusicDelayTicks = 100
                ticksSinceLastMusic = 0
                waitingForNextTrack = true
            }
        }
    }

    private fun onTrackFinished() {
        if (userPlaylist.isNotEmpty()) {
            val nextTrack = userPlaylist.poll()
            currentMode = MusicStatusPacket.PlayMode.PLAYLIST
            playTrack(nextTrack)
        } else {
            broadcastSync(MusicSyncPacket.Action.STOP)
            currentTrack = null
            isPlaying = false
            currentMode = MusicStatusPacket.PlayMode.AUTONOMOUS
            broadcastStatus()
            scheduleNextAutonomousTrack()
        }
    }

    private fun scheduleNextAutonomousTrack() {
        val musicEvent = determineMusicEventForPlayers()
        val timing = if (customMinDelay != null && customMaxDelay != null) {
            MusicTiming(customMinDelay!!, customMaxDelay!!)
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
        val server = ServerLifecycleHooks.getCurrentServer() ?: return null
        val player = server.playerList.players.firstOrNull {
            isPlayerOp(it) && it.level().dimension().location().toString() == "minecraft:overworld"
        } ?: server.playerList.players.firstOrNull {
            it.level().dimension().location().toString() == "minecraft:overworld"
        } ?: return null
        return getBiomeMusicTiming(player)
    }

    private fun playNextAutoTrack() {
        val musicEvent = determineMusicEventForPlayers()
        recentTracks.add(musicEvent)
        if (recentTracks.size > maxRecentTracks) recentTracks.removeAt(0)
        currentMode = MusicStatusPacket.PlayMode.AUTONOMOUS
        playTrack(musicEvent)
    }

    private fun isGlobalDragonFight(): Boolean {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return false
        val anyInEnd = server.playerList.players.any {
            it.level().dimension().location().toString() == "minecraft:the_end"
        }
        if (!anyInEnd) return false
        return try {
            val endLevel = server.getLevel(net.minecraft.world.level.Level.END) ?: return false
            endLevel.getEntities(net.minecraft.world.entity.EntityType.ENDER_DRAGON) { true }.isNotEmpty()
        } catch (_: Exception) { false }
    }

    private fun computeMusicEventForPlayer(player: ServerPlayer): String {
        if (player.uuid in creditsPlayers) return "minecraft:music.credits"

        val dimension = player.level().dimension().location().toString()

        if (dimension == "minecraft:the_end") {
            try {
                val endLevel = player.server.getLevel(net.minecraft.world.level.Level.END)
                if (endLevel != null) {
                    val dragons = endLevel.getEntities(net.minecraft.world.entity.EntityType.ENDER_DRAGON) { true }
                    if (dragons.isNotEmpty()) return "minecraft:music.dragon"
                }
            } catch (_: Exception) {}
            return "minecraft:music.end"
        }

        val biomeMusic = getBiomeMusicEvent(player)
        if (biomeMusic != null) return biomeMusic

        if (player.isUnderWater) return "minecraft:music.under_water"

        if (dimension == "minecraft:the_nether") return "minecraft:music.nether.nether_wastes"

        if (player.abilities.instabuild) return "minecraft:music.creative"

        return "minecraft:music.game"
    }

    private fun checkOpPlayerStates() {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        val players = server.playerList.players
        if (players.isEmpty()) return

        var anyChanged = false

        val trackedPlayers = players.filter {
            isPlayerOp(it) && it.level().dimension().location().toString() == "minecraft:overworld"
        }

        if (trackedPlayers.isEmpty()) {
            if (opPlayerStates.isNotEmpty()) {
                opPlayerStates.clear()
                anyChanged = true
            }
        } else {
            for (player in trackedPlayers) {
                val musicEvent = computeMusicEventForPlayer(player)
                val prevState = opPlayerStates[player.uuid]
                if (prevState == null || prevState.musicEvent != musicEvent) {
                    opPlayerStates[player.uuid] = OpPlayerState(musicEvent, System.currentTimeMillis())
                    anyChanged = true
                }
            }
            val activeUuids = trackedPlayers.map { it.uuid }.toSet()
            val removed = opPlayerStates.keys.removeAll { it !in activeUuids }
            if (removed) anyChanged = true
        }

        val dragonActive = isGlobalDragonFight()
        if (dragonActive != lastDragonFightActive) {
            lastDragonFightActive = dragonActive
            anyChanged = true
        }

        if (anyChanged) onOpStateChanged()
    }

    private fun getMusicCategory(musicEvent: String): String {
        return when {
            musicEvent.contains("dragon") || musicEvent.contains("end") || musicEvent.contains("credits") -> "end"
            musicEvent.contains("nether") -> "nether"
            else -> "overworld"
        }
    }

    private fun onOpStateChanged() {
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
                    val nextTrack = userPlaylist.poll()
                    if (nextTrack != null) {
                        currentMode = MusicStatusPacket.PlayMode.PLAYLIST
                        playTrack(nextTrack)
                    }
                }
            } else {
                if (isPriority || isDimensionChange) {
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

    private fun determineDominantMusicEvent(): String {
        if (isGlobalDragonFight()) return "minecraft:music.dragon"
        if (creditsPlayers.isNotEmpty()) return "minecraft:music.credits"
        if (opPlayerStates.isEmpty()) return "minecraft:music.game"

        val states = opPlayerStates.values.toList()

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
        if (opPlayerStates.isNotEmpty()) return determineDominantMusicEvent()
        return "minecraft:music.game"
    }

    private fun isInPriorityMode(): Boolean {
        return isGlobalDragonFight() || creditsPlayers.isNotEmpty() ||
                userPlaylist.isNotEmpty() || currentMode == MusicStatusPacket.PlayMode.PLAYLIST
    }

    private fun shouldPlayerHearPrimary(player: ServerPlayer): Boolean {
        if (isInPriorityMode()) return true
        val dim = player.level().dimension().location().toString()
        return dim == "minecraft:overworld" || player.uuid in syncOverworld
    }

    private fun shouldPlayerHearDim(player: ServerPlayer, stream: DimensionStream): Boolean {
        if (isInPriorityMode()) return false
        val dim = player.level().dimension().location().toString()
        return dim == stream.dimensionId && player.uuid !in syncOverworld
    }

    private fun getStreamForDim(dimensionId: String): DimensionStream? {
        if (dimensionId == "minecraft:overworld") return null
        return dimensionStreams[dimensionId]
    }

    private fun getOrCreateStream(dimensionId: String): DimensionStream? {
        if (dimensionId == "minecraft:overworld") return null
        return dimensionStreams.getOrPut(dimensionId) { DimensionStream(dimensionId) }
    }

    private fun determineDimMusicEvent(stream: DimensionStream): String {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return getDefaultDimMusic(stream.dimensionId)
        val dimOps = server.playerList.players.filter {
            isPlayerOp(it) && it.level().dimension().location().toString() == stream.dimensionId
        }
        val targetPlayer = dimOps.firstOrNull() ?: server.playerList.players.firstOrNull {
            it.level().dimension().location().toString() == stream.dimensionId
        }
        return if (targetPlayer != null) computeDimBiomeMusic(targetPlayer) else getDefaultDimMusic(stream.dimensionId)
    }

    private fun computeDimBiomeMusic(player: ServerPlayer): String {
        return getBiomeMusicEvent(player) ?: getDefaultDimMusic(player.level().dimension().location().toString())
    }

    private fun getDefaultDimMusic(dimensionId: String): String = when (dimensionId) {
        "minecraft:the_nether" -> "minecraft:music.nether.nether_wastes"
        "minecraft:the_end" -> "minecraft:music.end"
        else -> "minecraft:music.game"
    }

    private fun updateDimPopulation(stream: DimensionStream) {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        stream.active = server.playerList.players.any {
            it.level().dimension().location().toString() == stream.dimensionId
        }
    }

    private fun updateOverworldPopulation() {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        overworldActive = server.playerList.players.any {
            it.level().dimension().location().toString() == "minecraft:overworld"
        }
    }

    private fun dimPlayTrack(stream: DimensionStream, trackId: String) {
        stream.track = trackId
        stream.specific = ""
        stream.startTime = System.currentTimeMillis()
        stream.duration = 0
        stream.playing = true
        stream.paused = false
        stream.waiting = false
        stream.resolved = null
        stream.clientEnd = false
        broadcastDimSync(stream, MusicSyncPacket.Action.PLAY)
        broadcastStatus()
    }

    private fun dimStopMusic(stream: DimensionStream) {
        if (stream.track != null) broadcastDimSync(stream, MusicSyncPacket.Action.STOP)
        stream.reset()
    }

    private fun dimScheduleNext(stream: DimensionStream) {
        val musicEvent = determineDimMusicEvent(stream)
        val timing = if (customMinDelay != null && customMaxDelay != null) {
            MusicTiming(customMinDelay!!, customMaxDelay!!)
        } else {
            getDimMusicTiming(stream) ?: MUSIC_TIMING[musicEvent] ?: DEFAULT_TIMING
        }

        stream.delayTicks = if (timing.maxDelay <= timing.minDelay) {
            timing.minDelay
        } else {
            timing.minDelay + Random.nextInt(timing.maxDelay - timing.minDelay)
        }
        stream.ticksSince = 0
        stream.waiting = true
    }

    private fun getDimMusicTiming(stream: DimensionStream): MusicTiming? {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return null
        val player = server.playerList.players.firstOrNull {
            it.level().dimension().location().toString() == stream.dimensionId
        } ?: return null
        return getBiomeMusicTiming(player)
    }

    private fun dimPlayNext(stream: DimensionStream) {
        val musicEvent = determineDimMusicEvent(stream)
        stream.recent.add(musicEvent)
        if (stream.recent.size > maxRecentTracks) stream.recent.removeAt(0)
        dimPlayTrack(stream, musicEvent)
    }

    private fun dimOnTrackFinished(stream: DimensionStream) {
        broadcastDimSync(stream, MusicSyncPacket.Action.STOP)
        stream.track = null
        stream.playing = false
        broadcastStatus()
        dimScheduleNext(stream)
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
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        for (player in server.playerList.players) {
            if (shouldPlayerHearDim(player, stream)) {
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with { player }, packet)
            }
        }
    }

    private fun broadcastDimSyncCheck(stream: DimensionStream) {
        val track = stream.track ?: return
        if (!stream.playing || stream.paused) return
        val expectedPosition = System.currentTimeMillis() - stream.startTime
        val packet = MusicSyncPacket(
            trackId = track,
            startPositionMs = expectedPosition,
            serverTimeMs = System.currentTimeMillis(),
            action = MusicSyncPacket.Action.SYNC_CHECK,
            specificSound = ""
        )
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        for (player in server.playerList.players) {
            if (shouldPlayerHearDim(player, stream)) {
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with { player }, packet)
            }
        }
    }

    private fun sendDimSyncToPlayer(stream: DimensionStream, player: ServerPlayer) {
        stream.track?.let { track ->
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
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with { player }, packet)
        }
    }

    private fun resyncDimPlayers() {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
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
                        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with { player }, stopPacket)
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
        val dim = player.level().dimension().location().toString()
        val stream = getStreamForDim(dim) ?: return

        if (player.uuid in syncOverworld) {
            syncOverworld.remove(player.uuid)
            if (stream.playing && stream.track != null) {
                sendDimSyncToPlayer(stream, player)
            } else {
                val stopPacket = MusicSyncPacket(
                    trackId = "", startPositionMs = 0, serverTimeMs = System.currentTimeMillis(),
                    action = MusicSyncPacket.Action.STOP
                )
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with { player }, stopPacket)
            }
        } else {
            syncOverworld.add(player.uuid)
            sendPrimarySyncToPlayer(player)
        }
        broadcastStatusToPlayer(player)
    }

    fun onPlayerChangedDimension(event: PlayerEvent.PlayerChangedDimensionEvent) {
        val fromDim = event.from.location().toString()
        val toDim = event.to.location().toString()
        if (fromDim == "minecraft:the_end" && toDim == "minecraft:overworld") {
            creditsPlayers[event.entity.uuid] = System.currentTimeMillis()
        }

        val serverPlayer = event.entity as? ServerPlayer

        val toStream = getOrCreateStream(toDim)
        if (toStream != null && serverPlayer != null) {
            val wasEmpty = !toStream.active
            updateDimPopulation(toStream)
            if (wasEmpty && toStream.active) dimScheduleNext(toStream)
            if (serverPlayer.uuid in syncOverworld) {
                sendPrimarySyncToPlayer(serverPlayer)
            } else {
                val stopPacket = MusicSyncPacket(
                    trackId = "", startPositionMs = 0, serverTimeMs = System.currentTimeMillis(),
                    action = MusicSyncPacket.Action.STOP
                )
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with { serverPlayer }, stopPacket)
                if (toStream.playing && toStream.track != null) {
                    sendDimSyncToPlayer(toStream, serverPlayer)
                }
            }
        }

        if (toDim == "minecraft:overworld" && serverPlayer != null) {
            updateOverworldPopulation()
            sendPrimarySyncToPlayer(serverPlayer)
            broadcastStatusToPlayer(serverPlayer)
        }

        val fromStream = getStreamForDim(fromDim)
        if (fromStream != null) {
            updateDimPopulation(fromStream)
            if (!fromStream.active) dimStopMusic(fromStream)
        }

        if (fromDim == "minecraft:overworld") updateOverworldPopulation()

        checkOpPlayerStates()
    }

    fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return

        val dim = player.level().dimension().location().toString()
        if (isPlayerOp(player) && dim == "minecraft:overworld") {
            val musicEvent = computeMusicEventForPlayer(player)
            opPlayerStates[player.uuid] = OpPlayerState(musicEvent, System.currentTimeMillis())
            lastDominantMusicEvent = determineDominantMusicEvent()
        }

        if (dim == "minecraft:overworld") {
            overworldActive = true
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
                dimScheduleNext(stream)
            }
        }

        sendTrackManifest(player)
        sendSyncToPlayer(player)
    }

    fun onPlayerLeave(event: PlayerEvent.PlayerLoggedOutEvent) {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        val leavingPlayer = event.entity

        opPlayerStates.remove(leavingPlayer.uuid)
        playersDownloading.remove(leavingPlayer.uuid)
        lastSeekTime.remove(leavingPlayer.uuid)
        creditsPlayers.remove(leavingPlayer.uuid)
        syncOverworld.remove(leavingPlayer.uuid)

        if (server.playerList.players.size <= 1) pauseMusic()

        val dim = leavingPlayer.level().dimension().location().toString()
        val stream = getStreamForDim(dim)
        if (stream != null) {
            val remaining = server.playerList.players.count {
                it.uuid != leavingPlayer.uuid &&
                it.level().dimension().location().toString() == dim
            }
            if (remaining == 0) dimStopMusic(stream)
        }
        if (dim == "minecraft:overworld") {
            val remainingOW = server.playerList.players.count {
                it.uuid != leavingPlayer.uuid &&
                it.level().dimension().location().toString() == "minecraft:overworld"
            }
            if (remainingOW == 0) overworldActive = false
        }

        if (opPlayerStates.isNotEmpty()) {
            val newEvent = determineDominantMusicEvent()
            if (newEvent != lastDominantMusicEvent) {
                lastDominantMusicEvent = newEvent
                userPlaylist.clear()
                broadcastStatus()
            }
        }
    }

    private fun sendSyncToPlayer(player: ServerPlayer) {
        val dim = player.level().dimension().location().toString()
        val stream = getStreamForDim(dim)
        if (stream != null && shouldPlayerHearDim(player, stream)) {
            sendDimSyncToPlayer(stream, player)
        } else {
            sendPrimarySyncToPlayer(player)
        }
        broadcastStatusToPlayer(player)
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
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with { player }, packet)
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
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        for (p in server.playerList.players) {
            if (shouldPlayerHearPrimary(p)) {
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with { p }, packet)
            }
        }
    }

    private fun broadcastSyncCheck() {
        val track = currentTrack ?: return
        val expectedPosition = System.currentTimeMillis() - trackStartTime
        val packet = MusicSyncPacket(
            trackId = track,
            startPositionMs = expectedPosition,
            serverTimeMs = System.currentTimeMillis(),
            action = MusicSyncPacket.Action.SYNC_CHECK,
            specificSound = ""
        )
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        for (p in server.playerList.players) {
            if (shouldPlayerHearPrimary(p)) {
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with { p }, packet)
            }
        }
    }

    internal fun seekTrack(positionMs: Long) {
        if (currentTrack == null || !isPlaying) return
        val clamped = positionMs.coerceIn(0, if (trackDuration > 0) trackDuration else Long.MAX_VALUE)
        trackStartTime = System.currentTimeMillis() - clamped
        if (isPaused) pausedPosition = clamped
        val track = currentTrack ?: return
        val seekSpecific = if (specificSound.isNotEmpty()) specificSound
                           else resolvedTrackName ?: ""
        val packet = MusicSyncPacket(
            trackId = track,
            startPositionMs = clamped,
            serverTimeMs = System.currentTimeMillis(),
            action = MusicSyncPacket.Action.PLAY,
            specificSound = seekSpecific
        )
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        for (p in server.playerList.players) {
            if (shouldPlayerHearPrimary(p)) {
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with { p }, packet)
            }
        }
        broadcastStatus()
    }

    private fun broadcastStatus() {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        for (player in server.playerList.players) broadcastStatusToPlayer(player)
    }

    private fun broadcastStatusToPlayer(player: ServerPlayer) {
        val dimStream = dimensionStreams.values.firstOrNull { shouldPlayerHearDim(player, it) }

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
            currentTrack = if (dimStream != null) dimStream.track else currentTrack,
            currentPositionMs = currentPosition,
            durationMs = if (dimStream != null) dimStream.duration else trackDuration,
            isPlaying = if (dimStream != null) (dimStream.playing && !dimStream.paused) else (isPlaying && !isPaused),
            queue = userPlaylist.toList(),
            mode = if (dimStream != null) MusicStatusPacket.PlayMode.AUTONOMOUS else currentMode,
            resolvedName = if (dimStream != null) (dimStream.resolved ?: "") else (resolvedTrackName ?: ""),
            waitingForNextTrack = if (dimStream != null) dimStream.waiting else waitingForNextTrack,
            ticksSinceLastMusic = if (dimStream != null) dimStream.ticksSince else ticksSinceLastMusic,
            nextMusicDelayTicks = if (dimStream != null) dimStream.delayTicks else nextMusicDelayTicks,
            customMinDelay = customMinDelay ?: -1,
            customMaxDelay = customMaxDelay ?: -1,
            syncOverworld = player.uuid in syncOverworld
        )
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with { player }, packet)
    }

    fun handleClientInfo(packet: MusicClientInfoPacket, player: ServerPlayer) {
        when (packet.action) {
            MusicClientInfoPacket.Action.REPORT_DURATION -> {
                if (packet.trackId == currentTrack && packet.durationMs > 0) {
                    trackDuration = packet.durationMs
                    if (packet.resolvedName.isNotEmpty()) resolvedTrackName = packet.resolvedName
                    println("[MuSync] Track: ${resolvedTrackName ?: "?"} (${packet.durationMs}ms)")
                    broadcastStatus()
                } else {
                    for (stream in dimensionStreams.values) {
                        if (packet.trackId == stream.track && packet.durationMs > 0) {
                            stream.duration = packet.durationMs
                            if (packet.resolvedName.isNotEmpty()) stream.resolved = packet.resolvedName
                            println("[MuSync] ${stream.dimensionId} track: ${stream.resolved ?: "?"} (${packet.durationMs}ms)")
                            broadcastStatus()
                            break
                        }
                    }
                }
            }
            MusicClientInfoPacket.Action.TRACK_FINISHED -> {
                if (packet.trackId == currentTrack && isPlaying) {
                    println("[MuSync] Client reported track finished: ${packet.trackId}")
                    clientReportedEnd = true
                } else {
                    for (stream in dimensionStreams.values) {
                        if (packet.trackId == stream.track && stream.playing) {
                            println("[MuSync] Client reported ${stream.dimensionId} track finished: ${packet.trackId}")
                            stream.clientEnd = true
                            break
                        }
                    }
                }
            }
        }
    }

    fun onServerStarted(event: ServerStartedEvent) {
        val server = ServerLifecycleHooks.getCurrentServer()
        val serverDir = server?.serverDirectory ?: java.io.File(".")
        CustomTrackManager.scan(serverDir)
    }

    fun onServerStopping(event: ServerStoppingEvent) {
        currentTrack = null
        isPlaying = false
        isPaused = false
        waitingForNextTrack = false
        ticksSinceLastMusic = 0
        clientReportedEnd = false
        opPlayerStates.clear()
        lastDominantMusicEvent = null
        lastDragonFightActive = false
        opStateCheckCounter = 0
        recentTracks.clear()
        userPlaylist.clear()
        lastSeekTime.clear()
        creditsPlayers.clear()
        dimensionStreams.clear()
        syncOverworld.clear()
        overworldActive = false
        wasInPriority = false
        customMinDelay = null
        customMaxDelay = null
    }

    fun getStatusInfo(): Triple<String, Boolean, List<String>> {
        return Triple(currentTrack ?: "None", isPlaying && !isPaused, userPlaylist.toList())
    }

    fun getDetailedStatus(): Map<String, Any> {
        val currentPosition = if (isPaused) pausedPosition
                              else if (currentTrack != null) System.currentTimeMillis() - trackStartTime
                              else 0
        return mapOf(
            "track" to (currentTrack ?: "None"),
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
            "customMinDelay" to (customMinDelay ?: -1),
            "customMaxDelay" to (customMaxDelay ?: -1)
        )
    }

    fun setCustomDelay(minTicks: Int, maxTicks: Int) {
        customMinDelay = minTicks
        customMaxDelay = maxTicks
    }

    fun resetCustomDelay() {
        customMinDelay = null
        customMaxDelay = null
    }

    fun sendOpenGui(player: ServerPlayer) {
        val packet = MusicSyncPacket(
            trackId = "", startPositionMs = 0, serverTimeMs = 0,
            action = MusicSyncPacket.Action.OPEN_GUI
        )
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with { player }, packet)
    }

    fun getCustomTracks(): List<String> = CustomTrackManager.getTrackNames()

    private fun sendTrackManifest(player: ServerPlayer) {
        val manifest = CustomTrackManager.getManifest()
        if (manifest.isEmpty()) return
        val packet = dev.mcrib884.musync.network.TrackManifestPacket(manifest)
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with { player }, packet)
        println("[MuSync] Sent track manifest to ${player.name.string}: ${manifest.size} tracks")
    }

    fun handleTrackRequest(trackNames: List<String>, player: ServerPlayer) {
        if (trackNames.isEmpty()) return
        println("[MuSync] ${player.name.string} requested ${trackNames.size} missing tracks")
        playersDownloading.add(player.uuid)
        Thread({
            for (name in trackNames) {
                val data = CustomTrackManager.getTrackData(name)
                if (data == null) {
                    println("[MuSync] Track not found for request: $name")
                    continue
                }
                val chunkSize = dev.mcrib884.musync.network.CustomTrackDataPacket.CHUNK_SIZE
                val totalChunks = (data.size + chunkSize - 1) / chunkSize
                for (i in 0 until totalChunks) {
                    val start = i * chunkSize
                    val end = minOf(start + chunkSize, data.size)
                    val chunk = data.copyOfRange(start, end)
                    val packet = dev.mcrib884.musync.network.CustomTrackDataPacket(
                        trackName = name, chunkIndex = i, totalChunks = totalChunks, data = chunk
                    )
                    try {
                        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with { player }, packet)
                    } catch (e: Exception) {
                        println("[MuSync] Failed to send chunk $i/$totalChunks of $name: ${e.message}")
                        return@Thread
                    }
                    Thread.sleep(10)
                }
                println("[MuSync] Sent track $name to ${player.name.string} ($totalChunks chunks, ${data.size} bytes)")
                Thread.sleep(50)
            }
            println("[MuSync] Finished sending ${trackNames.size} tracks to ${player.name.string}")
            playersDownloading.remove(player.uuid)
        }, "MuSync-TrackSync-${player.name.string}").start()
    }

    private fun sendCustomTrackToAll(trackName: String) {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        for (player in server.playerList.players) sendCustomTrackToPlayer(trackName, player)
    }

    private fun sendCustomTrackToPlayer(trackName: String, player: ServerPlayer) {
        val data = CustomTrackManager.getTrackData(trackName) ?: return
        val chunkSize = dev.mcrib884.musync.network.CustomTrackDataPacket.CHUNK_SIZE
        val totalChunks = (data.size + chunkSize - 1) / chunkSize
        for (i in 0 until totalChunks) {
            val start = i * chunkSize
            val end = minOf(start + chunkSize, data.size)
            val chunk = data.copyOfRange(start, end)
            val packet = dev.mcrib884.musync.network.CustomTrackDataPacket(
                trackName = trackName, chunkIndex = i, totalChunks = totalChunks, data = chunk
            )
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with { player }, packet)
        }
    }

    fun readNextFile(): List<String> {
        return try {
            val runDir = ServerLifecycleHooks.getCurrentServer()?.serverDirectory ?: File(".")
            val nextFile = File(runDir, "next.txt")
            if (nextFile.exists()) nextFile.readLines().filter { it.isNotBlank() } else emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun processNextInstructions() {
        val instructions = readNextFile()
        for (line in instructions) {
            val parts = line.split(" ", limit = 2)
            when (parts[0].lowercase()) {
                "play" -> {
                    if (parts.size > 1) {
                        val trackName = parts[1].lowercase().replace(" ", "_")
                        val trackId = dev.mcrib884.musync.command.MuSyncCommand.getTrackId(trackName)
                        if (trackId != null) playTrack(trackId)
                    }
                }
                "stop" -> stopMusic()
                "pause" -> pauseMusic()
                "resume" -> resumeMusic()
                "skip" -> skipTrack()
                "queue" -> {
                    if (parts.size > 1) {
                        val trackName = parts[1].lowercase().replace(" ", "_")
                        val trackId = dev.mcrib884.musync.command.MuSyncCommand.getTrackId(trackName)
                        if (trackId != null) addToQueue(trackId)
                    }
                }
            }
        }
    }
}
