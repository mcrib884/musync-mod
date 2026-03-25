package dev.mcrib884.musync.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.suggestion.SuggestionProvider
import dev.mcrib884.musync.network.*
import dev.mcrib884.musync.entityLevel
//? if >=1.21.11 {
/*import dev.mcrib884.musync.location*/
//?}
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import dev.mcrib884.musync.sendSuccessCompat
import dev.mcrib884.musync.hasPermissionCompat
import dev.mcrib884.musync.soundEventKeys
import dev.mcrib884.musync.soundEventContains
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier as ResourceLocation*/
//?} else {
import net.minecraft.resources.ResourceLocation
//?}
import net.minecraft.server.level.ServerPlayer

object MuSyncCommand {

    private val TRACK_MAP = mapOf(

        "minecraft" to "minecraft:music.game",
        "clark" to "minecraft:music.game",
        "sweden" to "minecraft:music.game",
        "subwoofer_lullaby" to "minecraft:music.game",
        "living_mice" to "minecraft:music.game",
        "haggstrom" to "minecraft:music.game",
        "danny" to "minecraft:music.game",
        "key" to "minecraft:music.game",
        "oxygene" to "minecraft:music.game",
        "dry_hands" to "minecraft:music.game",
        "wet_hands" to "minecraft:music.game",
        "mice_on_venus" to "minecraft:music.game",

        "biome_fest" to "minecraft:music.creative",
        "blind_spots" to "minecraft:music.creative",
        "haunt_muskie" to "minecraft:music.creative",
        "aria_math" to "minecraft:music.creative",
        "dreiton" to "minecraft:music.creative",
        "taswell" to "minecraft:music.creative",

        "mutation" to "minecraft:music.menu",
        "moog_city_2" to "minecraft:music.menu",
        "beginning_2" to "minecraft:music.menu",
        "floating_trees" to "minecraft:music.menu",

        "rubedo" to "minecraft:music.nether.warped_forest",
        "chrysopoeia" to "minecraft:music.nether.crimson_forest",
        "so_below" to "minecraft:music.nether.soul_sand_valley",

        "concrete_halls" to "minecraft:music.nether.nether_wastes",
        "dead_voxel" to "minecraft:music.nether.nether_wastes",
        "warmth" to "minecraft:music.nether.nether_wastes",
        "ballad_of_the_cats" to "minecraft:music.nether.nether_wastes",

        "the_end" to "minecraft:music.end",
        "boss" to "minecraft:music.dragon",
        "alpha" to "minecraft:music.credits",

        "stand_tall" to "minecraft:music.game",
        "left_to_bloom" to "minecraft:music.game",
        "one_more_day" to "minecraft:music.game",
        "infinite_amethyst" to "minecraft:music.game",
        "wending" to "minecraft:music.game",
        "ancestry" to "minecraft:music.game",

        "comforting_memories" to "minecraft:music.game",
        "floating_dream" to "minecraft:music.game",

        "an_ordinary_day" to "minecraft:music.game",
        "echo_in_the_wind" to "minecraft:music.game",

        "a_familiar_room" to "minecraft:music.game",
        "bromeliad" to "minecraft:music.game",
        "crescent_dunes" to "minecraft:music.game",
        "firebugs" to "minecraft:music.game",
        "labyrinthine" to "minecraft:music.game",
        "eld_unknown" to "minecraft:music.game",

        "deeper" to "minecraft:music.overworld.deep_dark",
        "featherfall" to "minecraft:music.overworld.deep_dark",

        "axolotl" to "minecraft:music.under_water",
        "dragon_fish" to "minecraft:music.under_water",
        "shuniji" to "minecraft:music.under_water",
    )

    private val DISPLAY_NAMES = dev.mcrib884.musync.TrackNames.DISPLAY_NAMES

    private fun isAliasAvailable(aliasKey: String): Boolean {
        val eventId = TRACK_MAP[aliasKey]?.substringBefore("|") ?: return false
        val resource = ResourceLocation.tryParse(eventId) ?: return false
        return soundEventContains(resource)
    }

    private fun availableAliasKeys(): Set<String> {
        return TRACK_MAP.keys.filterTo(mutableSetOf()) { isAliasAvailable(it) }
    }

    private val TRACK_SUGGESTIONS = SuggestionProvider<CommandSourceStack> { _, builder ->
        val allTracks = availableAliasKeys().toMutableSet()

        dev.mcrib884.musync.server.MusicManager.getCustomTracks().forEach { allTracks.add(it) }

        soundEventKeys().forEach { loc ->
            if (loc.path.contains("music") || loc.path.startsWith("music_disc.")) {
                allTracks.add(loc.toString())
            }
        }
        SharedSuggestionProvider.suggest(allTracks, builder)
    }

    private fun isDownloading(ctx: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Boolean {
        val player = ctx.source.entity as? ServerPlayer ?: return false
        if (dev.mcrib884.musync.server.MusicManager.isPlayerDownloading(player.uuid)) {
            ctx.source.sendFailure(Component.literal("[MuSync] Please wait for track sync to complete"))
            return true
        }
        return false
    }

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("musync")
                .requires { it.hasPermissionCompat(2) }
                .then(
                    Commands.literal("gui")
                        .executes { ctx ->
                            val player = ctx.source.playerOrException
                            dev.mcrib884.musync.server.MusicManager.sendOpenGui(player)
                            1
                        }
                )
                .then(
                    Commands.literal("play")
                        .then(
                            Commands.argument("track", StringArgumentType.greedyString())
                                .suggests(TRACK_SUGGESTIONS)
                                .executes { ctx ->
                                    if (isDownloading(ctx)) return@executes 0
                                    val input = StringArgumentType.getString(ctx, "track").lowercase().replace(" ", "_")

                                    val customInput = input.removePrefix("custom:")
                                    val customInternal = dev.mcrib884.musync.server.CustomTrackManager.toInternalName(customInput)
                                    if (customInternal != null) {
                                        dev.mcrib884.musync.server.MusicManager.playTrack("custom:$customInternal")
                                                                                ctx.source.sendSuccessCompat(
                                            { Component.literal("Now playing: ${dev.mcrib884.musync.TrackNames.formatTrack("custom:$customInternal")}") },
                                            true
                                        )
                                        return@executes 1
                                    }

                                    val trackValue = resolveTrackValue(input)
                                    if (trackValue != null) {
                                        dev.mcrib884.musync.server.MusicManager.playTrack(trackValue)
                                        ctx.source.sendSuccessCompat(
                                            { Component.literal("Now playing: ${formatAliasName(input)}") },
                                            true
                                        )
                                        return@executes 1
                                    }

                                    val resLoc = ResourceLocation.tryParse(input)
                                    if (resLoc != null && soundEventContains(resLoc)) {
                                        dev.mcrib884.musync.server.MusicManager.playTrack(resLoc.toString())
                                        ctx.source.sendSuccessCompat(
                                            { Component.literal("Now playing: ${formatTrackName(resLoc.toString())}") },
                                            true
                                        )
                                        return@executes 1
                                    }

                                    ctx.source.sendFailure(
                                        Component.literal("Unknown track: '$input'. Use tab for suggestions.")
                                    )
                                    0
                                }
                        )
                )
                .then(
                    Commands.literal("stop")
                        .executes { ctx ->
                            if (isDownloading(ctx)) return@executes 0
                            dev.mcrib884.musync.server.MusicManager.stopMusic()
                                                        ctx.source.sendSuccessCompat(
                                { Component.literal("Music stopped") },
                                true
                            )
                            1
                        }
                )
                .then(
                    Commands.literal("skip")
                        .executes { ctx ->
                            if (isDownloading(ctx)) return@executes 0
                            dev.mcrib884.musync.server.MusicManager.skipTrack()
                                                        ctx.source.sendSuccessCompat(
                                { Component.literal("Skipping to next track") },
                                true
                            )
                            1
                        }
                )
                .then(
                    Commands.literal("pause")
                        .executes { ctx ->
                            if (isDownloading(ctx)) return@executes 0
                            dev.mcrib884.musync.server.MusicManager.pauseMusic()
                                                        ctx.source.sendSuccessCompat(
                                { Component.literal("Music paused") },
                                true
                            )
                            1
                        }
                )
                .then(
                    Commands.literal("resume")
                        .executes { ctx ->
                            if (isDownloading(ctx)) return@executes 0
                            dev.mcrib884.musync.server.MusicManager.resumeMusic()
                                                        ctx.source.sendSuccessCompat(
                                { Component.literal("Music resumed") },
                                true
                            )
                            1
                        }
                )
                .then(
                    Commands.literal("queue")
                        .then(
                            Commands.argument("track", StringArgumentType.greedyString())
                                .suggests(TRACK_SUGGESTIONS)
                                .executes { ctx ->
                                    if (isDownloading(ctx)) return@executes 0
                                    val input = StringArgumentType.getString(ctx, "track").lowercase().replace(" ", "_")

                                    val customInput = input.removePrefix("custom:")
                                    val customInternal = dev.mcrib884.musync.server.CustomTrackManager.toInternalName(customInput)
                                    if (customInternal != null) {
                                        dev.mcrib884.musync.server.MusicManager.addToQueue("custom:$customInternal")
                                        ctx.source.sendSuccessCompat(
                                            { Component.literal("Added to queue: ${dev.mcrib884.musync.TrackNames.formatTrack("custom:$customInternal")}") },
                                            true
                                        )
                                        return@executes 1
                                    }

                                    val trackValue = resolveTrackValue(input)
                                    if (trackValue != null) {
                                        dev.mcrib884.musync.server.MusicManager.addToQueue(trackValue)
                                        ctx.source.sendSuccessCompat(
                                            { Component.literal("Added to queue: ${formatAliasName(input)}") },
                                            true
                                        )
                                        return@executes 1
                                    }

                                    val resLoc = ResourceLocation.tryParse(input)
                                    if (resLoc != null && soundEventContains(resLoc)) {
                                        dev.mcrib884.musync.server.MusicManager.addToQueue(resLoc.toString())
                                        ctx.source.sendSuccessCompat(
                                            { Component.literal("Added to queue: ${formatTrackName(resLoc.toString())}") },
                                            true
                                        )
                                        return@executes 1
                                    }

                                    ctx.source.sendFailure(
                                        Component.literal("Unknown track: '$input'. Use tab for suggestions.")
                                    )
                                    0
                                }
                        )
                )
                .then(
                    Commands.literal("status")
                        .executes { ctx ->
                            val status = dev.mcrib884.musync.server.MusicManager.getDetailedStatus()
                            val track = status["track"] as String
                            val resolvedName = status["resolvedName"] as String
                            val playing = status["playing"] as Boolean
                            val paused = status["paused"] as Boolean
                            val position = status["position"] as Long
                            val duration = status["duration"] as Long
                            val mode = status["mode"] as String
                            val queue = status["queue"] as List<*>
                            val waiting = status["waitingForNextTrack"] as Boolean
                            val ticksElapsed = status["ticksSinceLastMusic"] as Int
                            val ticksTotal = status["nextMusicDelayTicks"] as Int
                            val custMin = status["customMinDelay"] as Int
                            val custMax = status["customMaxDelay"] as Int

                                                        ctx.source.sendSuccessCompat(
                                { Component.literal("========== MuSync ==========") },
                                false
                            )

                            val trackDisplay = when {
                                track == "None" -> "No track"
                                resolvedName.isNotEmpty() -> formatOggName(resolvedName)
                                else -> formatTrackName(track)
                            }
                                                        ctx.source.sendSuccessCompat(
                                { Component.literal("Track: $trackDisplay") },
                                false
                            )

                            if (track != "None" && resolvedName.isNotEmpty()) {
                                ctx.source.sendSuccessCompat(
                                    { Component.literal("Pool: ${formatTrackName(track)}") },
                                    false
                                )
                            }

                            val statusText = when {
                                paused -> "Paused"
                                !playing && waiting -> "Waiting"
                                !playing -> "Stopped"
                                else -> "Playing"
                            }
                                                        ctx.source.sendSuccessCompat(
                                { Component.literal("Status: $statusText") },
                                false
                            )

                            if (track != "None" && (playing || paused)) {
                                if (duration > 0) {
                                    val progressBar = createProgressBar(position, duration, 20)
                                    val posStr = formatTime(position)
                                    val durStr = formatTime(duration)
                                                                        ctx.source.sendSuccessCompat(
                                        { Component.literal("[$progressBar] $posStr / $durStr") },
                                        false
                                    )
                                } else {
                                    val posStr = formatTime(position)
                                                                        ctx.source.sendSuccessCompat(
                                        { Component.literal("$posStr / Loading...") },
                                        false
                                    )
                                }
                            }

                            if (waiting && !playing && !paused) {
                                val ticksRemaining = (ticksTotal - ticksElapsed).coerceAtLeast(0)
                                ctx.source.sendSuccessCompat(
                                    { Component.literal("Delay: ${formatTickTime(ticksElapsed)} / ${formatTickTime(ticksTotal)} (${formatTickTime(ticksRemaining)} remaining)") },
                                    false
                                )
                                ctx.source.sendSuccessCompat(
                                    { Component.literal("       ($ticksElapsed / $ticksTotal ticks, $ticksRemaining left)") },
                                    false
                                )
                            }

                            val modeDisplay = when (mode) {
                                "AUTONOMOUS" -> "Auto"
                                "PLAYLIST" -> "Playlist"
                                else -> mode
                            }
                                                        ctx.source.sendSuccessCompat(
                                { Component.literal("Mode: $modeDisplay") },
                                false
                            )

                            if (custMin >= 0 && custMax >= 0) {
                                ctx.source.sendSuccessCompat(
                                    { Component.literal("Custom delay: $custMin-$custMax ticks (${formatTickTime(custMin)}-${formatTickTime(custMax)})") },
                                    false
                                )
                            }

                            if (queue.isNotEmpty()) {
                                ctx.source.sendSuccessCompat(
                                    { Component.literal("Queue (${queue.size}):") },
                                    false
                                )
                                queue.take(5).forEachIndexed { i, t ->
                                    ctx.source.sendSuccessCompat(
                                        { Component.literal("  ${i + 1}. ${formatTrackName(t.toString())}") },
                                        false
                                    )
                                }
                                if (queue.size > 5) {
                                                                        ctx.source.sendSuccessCompat(
                                        { Component.literal("  ... and ${queue.size - 5} more") },
                                        false
                                    )
                                }
                            }

                                                        ctx.source.sendSuccessCompat(
                                { Component.literal("=============================") },
                                false
                            )
                            1
                        }
                )
                .then(
                    Commands.literal("sync")
                        .executes { ctx ->
                            if (isDownloading(ctx)) return@executes 0
                            dev.mcrib884.musync.server.MusicManager.forceSyncAll()
                                                        ctx.source.sendSuccessCompat(
                                { Component.literal("Resynced all clients") },
                                true
                            )
                            1
                        }
                )
                .then(
                    Commands.literal("delay")
                        .then(
                            Commands.literal("reset")
                                .executes { ctx ->
                                    if (isDownloading(ctx)) return@executes 0
                                    val dim = (ctx.source.entity as? net.minecraft.server.level.ServerPlayer)?.let {
                                        it.entityLevel().dimension().location().toString()
                                    } ?: "minecraft:overworld"
                                    dev.mcrib884.musync.server.MusicManager.resetCustomDelay(dim)
                                    ctx.source.sendSuccessCompat(
                                        { Component.literal("Delay reset to vanilla defaults (per-pool timing)") },
                                        true
                                    )
                                    1
                                }
                        )
                        .then(
                            Commands.argument("min", IntegerArgumentType.integer(0))
                                .then(
                                    Commands.argument("max", IntegerArgumentType.integer(0))
                                        .executes { ctx ->
                                            if (isDownloading(ctx)) return@executes 0
                                            val min = IntegerArgumentType.getInteger(ctx, "min")
                                            val max = IntegerArgumentType.getInteger(ctx, "max")
                                            if (max < min) {
                                                ctx.source.sendFailure(
                                                    Component.literal("Max ($max) must be >= min ($min)")
                                                )
                                                return@executes 0
                                            }
                                            dev.mcrib884.musync.server.MusicManager.setCustomDelay(
                                                (ctx.source.entity as? net.minecraft.server.level.ServerPlayer)?.let {
                                                    it.entityLevel().dimension().location().toString()
                                                } ?: "minecraft:overworld",
                                                min, max
                                            )
                                            ctx.source.sendSuccessCompat(
                                                { Component.literal("Custom delay set: $min-$max ticks (${formatTickTime(min)}-${formatTickTime(max)})") },
                                                true
                                            )
                                            1
                                        }
                                )
                        )
                        .executes { ctx ->
                            val status = dev.mcrib884.musync.server.MusicManager.getDetailedStatus()
                            val custMin = status["customMinDelay"] as Int
                            val custMax = status["customMaxDelay"] as Int
                            if (custMin >= 0 && custMax >= 0) {
                                ctx.source.sendSuccessCompat(
                                    { Component.literal("Current custom delay: $custMin-$custMax ticks (${formatTickTime(custMin)}-${formatTickTime(custMax)})") },
                                    false
                                )
                            } else {
                                ctx.source.sendSuccessCompat(
                                    { Component.literal("Using vanilla defaults (per-pool timing). Use /musync delay <min> <max> to override.") },
                                    false
                                )
                            }
                            1
                        }
                )
        )
    }

    private val OGG_NAMES = dev.mcrib884.musync.TrackNames.OGG_NAMES

    private fun formatOggName(oggPath: String): String {
        return dev.mcrib884.musync.TrackNames.formatOggName(oggPath)
    }

    private fun formatTrackName(trackId: String): String {
        return dev.mcrib884.musync.TrackNames.formatTrack(trackId)
    }

    private fun formatAliasName(alias: String): String {
        return alias.replace("_", " ").split(" ").joinToString(" ") { part ->
            if (part.isEmpty()) part else part.replaceFirstChar { it.uppercase() }
        }
    }

    private fun createProgressBar(current: Long, total: Long, length: Int): String {
        if (total <= 0) return " ".repeat(length)
        val progress = (current.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
        val filled = (progress * length).toInt()
        val empty = length - filled
        return "|".repeat(filled) + "-".repeat(empty)
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun formatTickTime(ticks: Int): String {
        val totalSeconds = ticks / 20
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    fun getAllTrackIds(): List<String> = TRACK_MAP.entries
        .filter { isAliasAvailable(it.key) }
        .map { it.value }
        .distinct()

    fun getAllTrackNames(): List<String> = availableAliasKeys().toList().sorted()

    fun getAllTracksForBrowser(): List<Pair<String, String>> {
        val entries = linkedMapOf<String, String>()

        val availableAliases = availableAliasKeys()

        val aliasedSoundEvents = mutableSetOf<String>()
        for (value in TRACK_MAP.filterKeys { it in availableAliases }.values) {
            val parts = value.split("|", limit = 2)
            aliasedSoundEvents.add(parts[0])
        }

        for (key in availableAliases) {
            val displayName = formatAliasName(key)
            entries.putIfAbsent(key, displayName)
        }

        soundEventKeys().forEach { loc ->
            val fullId = loc.toString()
            val path = loc.path

            if (path.contains("music") || path.startsWith("music_disc.")) {
                if (fullId in aliasedSoundEvents) return@forEach

                val displayName = DISPLAY_NAMES[fullId]
                    ?: formatDiscoveredTrackName(fullId)
                entries.putIfAbsent(fullId, displayName)
            }
        }

        dev.mcrib884.musync.client.ClientTrackManager.getServerCustomTrackNames().forEach { name ->
            val key = "custom:$name"
            if (!TRACK_MAP.containsKey(key)) {
                val displayName = "[Custom] " + dev.mcrib884.musync.TrackNames.formatCustomTrackName(name)
                entries.putIfAbsent(key, displayName)
            }
        }

        fun entryEventId(key: String): String {
            if (key.startsWith("custom:")) return "custom"
            val direct = TRACK_MAP[key] ?: key
            return direct.substringBefore("|")
        }

        fun categoryRank(key: String): Int {
            val eventId = entryEventId(key)
            if (key.startsWith("custom:")) return 8
            if (eventId.startsWith("minecraft:music.menu")) return 0
            if (eventId.startsWith("minecraft:music.game") || eventId.startsWith("minecraft:music.creative") || eventId.startsWith("minecraft:music.overworld")) return 1
            if (eventId.startsWith("minecraft:music.nether")) return 2
            if (eventId == "minecraft:music.end") return 3
            if (eventId == "minecraft:music.dragon") return 4
            if (eventId == "minecraft:music.credits") return 5
            if (eventId == "minecraft:music.under_water") return 6
            if (eventId.contains("music_disc")) return 7
            if (key.startsWith("[")) return 9
            return 10
        }

        return entries
            .map { it.key to it.value }
            .sortedWith(
                compareBy<Pair<String, String>>(
                    { categoryRank(it.first) },
                    { it.second.lowercase() },
                    { it.first.lowercase() }
                )
            )
    }

    fun toBrowserTrackKey(trackId: String): String {
        if (trackId.startsWith("custom:")) return trackId

        if (trackId.contains("|alias:")) {
            return trackId.substringAfter("|alias:")
        }

        val known = TRACK_MAP.entries.firstOrNull { it.value == trackId }
        if (known != null) return known.key

        return trackId
    }

    private fun formatDiscoveredTrackName(soundEventId: String): String {
        val namespace = soundEventId.substringBefore(":")
        val path = soundEventId.substringAfter(":")

        if (path.startsWith("music_disc.")) {
            val discName = path.removePrefix("music_disc.")
                .replace("_", " ").replaceFirstChar { it.uppercase() }
            return "Music Disc: $discName"
        }

        val cleanPath = path.removePrefix("music.")
            .replace(".", " - ")
            .replace("_", " ")
            .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

        return if (namespace != "minecraft") {
            "[$namespace] $cleanPath"
        } else {
            "$cleanPath (Pool)"
        }
    }

    fun getTrackId(friendlyName: String): String? {
        val key = friendlyName.lowercase().replace(" ", "_")

        val customKey = key.removePrefix("custom:")
        val internal = dev.mcrib884.musync.server.CustomTrackManager.toInternalName(customKey)
        if (internal != null) {
            return "custom:$internal"
        }
        val value = TRACK_MAP[key] ?: return null
        val eventId = value.substringBefore("|")
        val resource = ResourceLocation.tryParse(eventId) ?: return null
        return if (soundEventContains(resource)) eventId else null
    }

    fun resolveTrackValue(friendlyName: String): String? {
        val key = friendlyName.lowercase().replace(" ", "_")
        val customKey = key.removePrefix("custom:")
        val internal = dev.mcrib884.musync.server.CustomTrackManager.toInternalName(customKey)
        if (internal != null) {
            return "custom:$internal"
        }
        TRACK_MAP[key]?.let {
            val eventId = it.substringBefore("|")
            val resource = ResourceLocation.tryParse(eventId) ?: return null
            if (!soundEventContains(resource)) return null
            if (eventId.startsWith("minecraft:music")) {
                return "$eventId|alias:$key"
            }
            return eventId
        }
        val resourceLocation = ResourceLocation.tryParse(key) ?: return null
        return if (soundEventContains(resourceLocation)) resourceLocation.toString() else null
    }

    fun getSpecificSound(friendlyName: String): String {
        return ""
    }
}
