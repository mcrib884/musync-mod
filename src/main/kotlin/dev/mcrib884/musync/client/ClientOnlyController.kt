package dev.mcrib884.musync.client

import dev.mcrib884.musync.createSoundEvent
import dev.mcrib884.musync.entityLevel
import dev.mcrib884.musync.musicEventLocation
import dev.mcrib884.musync.mixin.MusicManagerAccessor
import dev.mcrib884.musync.network.MusicStatusPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.player.LocalPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import org.lwjgl.openal.AL10
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random

object ClientOnlyController {

    private var currentTrack: String? = null
    private var resolvedName: String? = null
    private var playing: Boolean = false
    private var paused: Boolean = false
    private var pausedPosition: Long = 0
    private var trackStartTime: Long = 0
    private var trackDuration: Long = 0
    private var customSource: Int = -1
    private var vanillaInstance: SoundInstance? = null
    private var mode: MusicStatusPacket.PlayMode = MusicStatusPacket.PlayMode.AUTONOMOUS
    private val queue: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
    private var waitingForNext: Boolean = false
    private var ticksSinceLastMusic: Int = 0
    private var nextDelayTicks: Int = 0
    private var customMinDelay: Int = -1
    private var customMaxDelay: Int = -1
    private val recentTracks: MutableList<String> = mutableListOf()
    private var localCustomTracks: List<String> = emptyList()
    private var lastMusicVol: Float = -1f
    private var lastMasterVol: Float = -1f
    private var playStartTime: Long = 0
    private val safeInternalName = Regex("^[a-z0-9_\\-]+\\.(ogg|wav)$")

    val isActive: Boolean
        get() = !ClientMusicPlayer.musyncActive

    fun getStatus(): MusicStatusPacket {
        val pos = getCurrentPositionMs()
        return MusicStatusPacket(
            currentTrack = currentTrack,
            isPlaying = playing,
            currentPositionMs = pos,
            durationMs = trackDuration,
            mode = mode,
            queue = queue.toList(),
            waitingForNextTrack = waitingForNext,
            ticksSinceLastMusic = ticksSinceLastMusic,
            nextMusicDelayTicks = nextDelayTicks,
            resolvedName = resolvedName ?: "",
            syncOverworld = false,
            customMinDelay = customMinDelay,
            customMaxDelay = customMaxDelay,
            priorityActive = false,
            activeDimensions = emptyList()
        )
    }

    fun getCurrentPositionMs(): Long {
        if (!playing) return pausedPosition
        if (paused) return pausedPosition
        return System.currentTimeMillis() - trackStartTime
    }

    fun getLocalCustomTracks(): List<String> = localCustomTracks

    fun scanLocalTracks() {
        val mc = Minecraft.getInstance()
        val gameDir = mc.gameDirectory
        val tracksDir = File(gameDir, "customtracks")
        if (!tracksDir.isDirectory) {
            tracksDir.mkdirs()
            localCustomTracks = emptyList()
            return
        }
        localCustomTracks = tracksDir.listFiles()
            ?.filter { it.isFile && (it.extension.equals("ogg", ignoreCase = true) || it.extension.equals("wav", ignoreCase = true)) }
            ?.mapNotNull { normalizeInternalName(it.name) }
            ?.distinct()
            ?.sorted()
            ?: emptyList()
    }

    fun hasLocalTrack(name: String): Boolean {
        return resolveLocalTrackName(name) != null
    }

    private fun getLocalTrackFile(name: String): File? {
        val mc = Minecraft.getInstance()
        val tracksDir = File(mc.gameDirectory, "customtracks")
        val internalName = resolveLocalTrackName(name) ?: return null
        val file = File(tracksDir, internalName)
        return if (file.isFile) file else null
    }

    fun playTrack(trackId: String) {
        stopInternal()
        val localTrack = resolveLocalTrackName(trackId)
        currentTrack = if (localTrack != null) "custom:$localTrack" else trackId

        if (localTrack != null) {
            playLocalCustomTrack(localTrack)
        } else {
            playVanillaTrack(trackId)
        }

        mode = MusicStatusPacket.PlayMode.PLAYLIST
        waitingForNext = false
    }

    private fun playLocalCustomTrack(name: String) {
        val file = getLocalTrackFile(name) ?: return
        val data = file.readBytes()
        val stream = try {
            CustomTrackPlayer.prepareStream(data) ?: return
        } catch (e: Exception) {
            dev.mcrib884.musync.MuSyncLog.error("Failed to open local track $name: ${e.message}")
            return
        }
        trackDuration = stream.durationMs
        trackStartTime = System.currentTimeMillis()
        playStartTime = System.currentTimeMillis()
        playing = true
        paused = false
        pausedPosition = 0
        resolvedName = null

        val mc = Minecraft.getInstance()
        val musicVol = mc.options.getSoundSourceVolume(SoundSource.MUSIC)
        val masterVol = mc.options.getSoundSourceVolume(SoundSource.MASTER)
        customSource = CustomTrackPlayer.playStream(stream, gain = musicVol * masterVol)
    }

    private fun playVanillaTrack(trackId: String) {
        val mc = Minecraft.getInstance()
        val accessor = mc.musicManager as? MusicManagerAccessor ?: return
        suppressVanilla(mc)

        val resolved = resolveVanillaTrackId(trackId)
        val soundLocation = ResourceLocation.tryParse(resolved) ?: return

        trackDuration = 0
        trackStartTime = System.currentTimeMillis()
        playStartTime = System.currentTimeMillis()
        playing = true
        paused = false
        pausedPosition = 0
        resolvedName = null

        val instance = SimpleSoundInstance.forMusic(createSoundEvent(soundLocation))
        mc.soundManager.play(instance)
        vanillaInstance = instance
        accessor.currentMusic = instance
        accessor.setNextSongDelay(Int.MAX_VALUE)
    }

    private fun resolveVanillaTrackId(trackId: String): String {
        val trackMap = dev.mcrib884.musync.command.MuSyncCommand.resolveTrackValue(trackId)
        if (trackMap != null) {
            return trackMap.substringBefore("|")
        }
        return if (trackId.contains(":")) trackId else "minecraft:$trackId"
    }

    fun stopMusic() {
        stopInternal()
        mode = MusicStatusPacket.PlayMode.AUTONOMOUS
        scheduleNextTrack()
    }

    fun pause() {
        if (!playing || paused) return
        paused = true
        pausedPosition = System.currentTimeMillis() - trackStartTime
        if (customSource != -1) {
            try { AL10.alSourcePause(customSource) } catch (_: Exception) {}
        } else {
            val instance = vanillaInstance
            if (instance != null) MusicSeeker.pauseSound(instance)
        }
    }

    fun resume() {
        if (!paused || currentTrack == null) return
        paused = false
        trackStartTime = System.currentTimeMillis() - pausedPosition
        if (customSource != -1) {
            try { AL10.alSourcePlay(customSource) } catch (_: Exception) {}
        } else {
            val instance = vanillaInstance
            if (instance != null) {
                if (MusicSeeker.hasChannel(instance)) {
                    MusicSeeker.resumeSound(instance)
                } else {
                    PausedSourceTracker.clear()
                }
            }
        }
    }

    fun togglePause() {
        if (paused) resume() else pause()
    }

    fun skip() {
        stopInternal()
        val nextTrack = queue.poll()
        if (nextTrack != null) {
            playTrack(nextTrack)
        } else {
            mode = MusicStatusPacket.PlayMode.AUTONOMOUS
            playNextAutoTrack()
        }
    }

    fun stop() {
        stopInternal()
        mode = MusicStatusPacket.PlayMode.AUTONOMOUS
        scheduleNextTrack()
    }

    fun seek(ms: Long) {
        if (currentTrack == null) return
        if (customSource != -1) {
            CustomTrackPlayer.seek(customSource, ms)
            trackStartTime = System.currentTimeMillis() - ms
            if (paused) pausedPosition = ms
        }
    }

    fun addToQueue(trackId: String) {
        queue.add(trackId)
        if (!playing && currentTrack == null) {
            val next = queue.poll()
            if (next != null) playTrack(next)
        }
    }

    fun removeFromQueue(index: Int) {
        val list = queue.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            queue.clear()
            queue.addAll(list)
        }
    }

    fun clearQueue() {
        queue.clear()
    }

    fun setDelay(min: Int, max: Int) {
        customMinDelay = min
        customMaxDelay = max
    }

    fun resetDelay() {
        customMinDelay = -1
        customMaxDelay = -1
    }

    fun onClientTick() {
        if (!isActive) return

        val mc = Minecraft.getInstance()
        if (mc.player == null) return
        if (!playing && currentTrack == null && !waitingForNext && mode == MusicStatusPacket.PlayMode.AUTONOMOUS) {
            scheduleNextTrack()
        }

        if (customSource != -1 && !paused) {
            val musicVol = mc.options.getSoundSourceVolume(SoundSource.MUSIC)
            val masterVol = mc.options.getSoundSourceVolume(SoundSource.MASTER)
            if (musicVol != lastMusicVol || masterVol != lastMasterVol) {
                lastMusicVol = musicVol
                lastMasterVol = masterVol
                try { AL10.alSourcef(customSource, AL10.AL_GAIN, musicVol * masterVol) } catch (_: Exception) {}
            }
        }

        if (playing && !paused && currentTrack != null) {
            if (System.currentTimeMillis() - playStartTime < 3000L) return

            if (customSource != -1) {
                if (!CustomTrackPlayer.isPlaying(customSource)) {
                    onTrackFinished()
                }
            } else if (vanillaInstance != null) {
                val soundManager = mc.soundManager
                if (!soundManager.isActive(vanillaInstance!!)) {
                    onTrackFinished()
                }
            }
        }

        if (waitingForNext && !playing) {
            ticksSinceLastMusic++
            if (ticksSinceLastMusic >= nextDelayTicks) {
                waitingForNext = false
                val nextTrack = queue.poll()
                if (nextTrack != null) {
                    playTrack(nextTrack)
                } else {
                    playNextAutoTrack()
                }
            }
        }
    }

    private fun onTrackFinished() {
        val finishedTrack = currentTrack
        stopInternal()

        if (finishedTrack != null) {
            recentTracks.add(finishedTrack)
            if (recentTracks.size > 6) recentTracks.removeAt(0)
        }

        val nextTrack = queue.poll()
        if (nextTrack != null) {
            playTrack(nextTrack)
        } else {
            mode = MusicStatusPacket.PlayMode.AUTONOMOUS
            scheduleNextTrack()
        }
    }

    private fun scheduleNextTrack() {
        val minDelay = if (customMinDelay >= 0) customMinDelay else 12000
        val maxDelay = if (customMaxDelay >= 0) customMaxDelay else 24000
        nextDelayTicks = if (maxDelay <= minDelay) minDelay else minDelay + Random.nextInt(maxDelay - minDelay)
        ticksSinceLastMusic = 0
        waitingForNext = true
    }

    private fun playNextAutoTrack() {
        val musicEvent = choosePreferredAutoTrack()
        recentTracks.add(musicEvent)
        if (recentTracks.size > 6) recentTracks.removeAt(0)
        playVanillaAutoTrack(musicEvent)
    }

    private fun playVanillaAutoTrack(musicEvent: String) {
        val mc = Minecraft.getInstance()
        val accessor = mc.musicManager as? MusicManagerAccessor ?: return
        suppressVanilla(mc)

        val soundLocation = ResourceLocation.tryParse(musicEvent) ?: return

        currentTrack = musicEvent
        trackDuration = 0
        trackStartTime = System.currentTimeMillis()
        playStartTime = System.currentTimeMillis()
        playing = true
        paused = false
        pausedPosition = 0
        resolvedName = null
        mode = MusicStatusPacket.PlayMode.AUTONOMOUS

        val instance = SimpleSoundInstance.forMusic(createSoundEvent(soundLocation))
        mc.soundManager.play(instance)
        vanillaInstance = instance
        accessor.currentMusic = instance
        accessor.setNextSongDelay(Int.MAX_VALUE)
    }

    private fun computeClientMusicEvent(): String {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return "minecraft:music.game"
        val dimension = player.entityLevel().dimension().location().toString()

        if (dimension == "minecraft:the_end" && hasClientDragonFight(player)) return "minecraft:music.dragon"
        if (dimension == "minecraft:the_end") return getClientBiomeMusicEvent(player) ?: "minecraft:music.end"
        if (player.isUnderWater) return "minecraft:music.under_water"
        if (dimension != "minecraft:the_nether" && player.isCreative) return "minecraft:music.creative"
        return getClientBiomeMusicEvent(player) ?: "minecraft:music.game"
    }

    private fun choosePreferredAutoTrack(): String {
        val primary = computeClientMusicEvent()
        if (primary == "minecraft:music.dragon") {
            return primary
        }
        val candidates = linkedSetOf<String>()
        candidates.add(primary)
        if (primary != "minecraft:music.game") candidates.add("minecraft:music.game")
        return candidates.firstOrNull { it !in recentTracks } ?: primary
    }

    private fun getClientBiomeMusicEvent(player: LocalPlayer): String? {
        return try {
            val music = player.entityLevel().getBiome(player.blockPosition()).value()
                .getBackgroundMusic().orElse(null) ?: return null
            musicEventLocation(music).toString()
        } catch (_: Exception) { null }
    }

    private fun hasClientDragonFight(player: LocalPlayer): Boolean {
        return try {
            player.entityLevel()
                .getEntitiesOfClass(EnderDragon::class.java, player.boundingBox.inflate(4096.0))
                .isNotEmpty()
        } catch (_: Exception) { false }
    }

    private fun normalizeInternalName(name: String): String? {
        val normalized = name.lowercase().replace(" ", "_")
        return if (safeInternalName.matches(normalized)) normalized else null
    }

    private fun resolveLocalTrackName(name: String): String? {
        val normalized = name.removePrefix("custom:").lowercase().replace(" ", "_")
        val exact = normalizeInternalName(normalized)
        if (exact != null && exact in localCustomTracks) return exact
        val matches = localCustomTracks.filter { it.substringBeforeLast(".") == normalized }
        return if (matches.size == 1) matches.first() else null
    }

    private fun stopInternal() {
        val mc = Minecraft.getInstance()
        PausedSourceTracker.clear()

        if (customSource != -1) {
            CustomTrackPlayer.stop(customSource)
            customSource = -1
        }

        val instance = vanillaInstance
        if (instance != null) {
            mc.soundManager.stop(instance)
            vanillaInstance = null
        }

        currentTrack = null
        resolvedName = null
        playing = false
        paused = false
        pausedPosition = 0
        trackDuration = 0
    }

    private fun suppressVanilla(mc: Minecraft) {
        mc.soundManager.stop(null, SoundSource.MUSIC)
        val accessor = mc.musicManager as? MusicManagerAccessor ?: return
        val currentMusic = accessor.currentMusic
        if (currentMusic != null && currentMusic !== vanillaInstance) {
            mc.soundManager.stop(currentMusic)
            accessor.currentMusic = null
        }
        accessor.setNextSongDelay(Int.MAX_VALUE)
    }

    fun reset() {
        stopInternal()
        queue.clear()
        mode = MusicStatusPacket.PlayMode.AUTONOMOUS
        waitingForNext = false
        ticksSinceLastMusic = 0
        nextDelayTicks = 0
        customMinDelay = -1
        customMaxDelay = -1
        recentTracks.clear()
        lastMusicVol = -1f
        lastMasterVol = -1f
        playStartTime = 0
    }
}
