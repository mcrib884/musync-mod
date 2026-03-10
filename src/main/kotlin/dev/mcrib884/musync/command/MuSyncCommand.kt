package dev.mcrib884.musync.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.suggestion.SuggestionProvider
import dev.mcrib884.musync.network.*
import dev.mcrib884.musync.entityLevel
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
//? if neoforge {
/*import net.minecraft.core.registries.BuiltInRegistries*/
//?} else {
import net.minecraftforge.registries.ForgeRegistries
//?}

object MuSyncCommand {

    //? if neoforge {
    /*private fun soundEventKeys() = BuiltInRegistries.SOUND_EVENT.keySet()
    private fun soundEventContains(key: ResourceLocation) = BuiltInRegistries.SOUND_EVENT.containsKey(key)*/
    //?} else {
    private fun soundEventKeys() = ForgeRegistries.SOUND_EVENTS.keys
    private fun soundEventContains(key: ResourceLocation) = ForgeRegistries.SOUND_EVENTS.containsKey(key)
    //?}

    private val TRACK_MAP = mutableMapOf(

        "minecraft" to "minecraft:music.game|music/game/calm1",
        "clark" to "minecraft:music.game|music/game/calm2",
        "sweden" to "minecraft:music.game|music/game/calm3",
        "subwoofer_lullaby" to "minecraft:music.game|music/game/hal1",
        "living_mice" to "minecraft:music.game|music/game/hal2",
        "haggstrom" to "minecraft:music.game|music/game/hal3",
        "danny" to "minecraft:music.game|music/game/hal4",
        "key" to "minecraft:music.game|music/game/nuance1",
        "oxygene" to "minecraft:music.game|music/game/nuance2",
        "dry_hands" to "minecraft:music.game|music/game/piano1",
        "wet_hands" to "minecraft:music.game|music/game/piano2",
        "mice_on_venus" to "minecraft:music.game|music/game/piano3",

        "biome_fest" to "minecraft:music.creative|music/game/creative/creative1",
        "blind_spots" to "minecraft:music.creative|music/game/creative/creative2",
        "haunt_muskie" to "minecraft:music.creative|music/game/creative/creative3",
        "aria_math" to "minecraft:music.creative|music/game/creative/creative4",
        "dreiton" to "minecraft:music.creative|music/game/creative/creative5",
        "taswell" to "minecraft:music.creative|music/game/creative/creative6",

        "mutation" to "minecraft:music.menu|music/menu/menu1",
        "moog_city_2" to "minecraft:music.menu|music/menu/menu2",
        "beginning_2" to "minecraft:music.menu|music/menu/menu3",
        "floating_trees" to "minecraft:music.menu|music/menu/menu4",

        "rubedo" to "minecraft:music.nether.nether_wastes|music/game/nether/rubedo",
        "chrysopoeia" to "minecraft:music.nether.crimson_forest|music/game/nether/chrysopoeia",
        "so_below" to "minecraft:music.nether.soul_sand_valley|music/game/nether/so_below",

        "concrete_halls" to "minecraft:music.nether.nether_wastes|music/game/nether/concrete_halls",
        "dead_voxel" to "minecraft:music.nether.nether_wastes|music/game/nether/dead_voxel",
        "warmth" to "minecraft:music.nether.nether_wastes|music/game/nether/warmth",
        "ballad_of_the_cats" to "minecraft:music.nether.nether_wastes|music/game/nether/ballad_of_the_cats",

        //? if >=1.21 {
        /*"the_end" to "minecraft:music.end|music/game/end/the_end",*/
        //?} else {
        "the_end" to "minecraft:music.end|music/game/end/end",
        //?}
        "boss" to "minecraft:music.dragon|music/game/end/boss",
        "alpha" to "minecraft:music.credits|music/game/end/credits",

        "stand_tall" to "minecraft:music.game|music/game/stand_tall",
        "left_to_bloom" to "minecraft:music.game|music/game/left_to_bloom",
        "one_more_day" to "minecraft:music.game|music/game/one_more_day",
        "infinite_amethyst" to "minecraft:music.game|music/game/infinite_amethyst",
        "wending" to "minecraft:music.game|music/game/wending",
        "ancestry" to "minecraft:music.game|music/game/ancestry",

        "comforting_memories" to "minecraft:music.game|music/game/comforting_memories",
        "floating_dream" to "minecraft:music.game|music/game/floating_dream",

        "an_ordinary_day" to "minecraft:music.game|music/game/an_ordinary_day",
        "echo_in_the_wind" to "minecraft:music.game|music/game/echo_in_the_wind",

        "a_familiar_room" to "minecraft:music.game|music/game/a_familiar_room",
        "bromeliad" to "minecraft:music.game|music/game/bromeliad",
        "crescent_dunes" to "minecraft:music.game|music/game/crescent_dunes",
        "firebugs" to "minecraft:music.game|music/game/firebugs",
        "labyrinthine" to "minecraft:music.game|music/game/labyrinthine",
        "eld_unknown" to "minecraft:music.game|music/game/eld_unknown",

        "deeper" to "minecraft:music.overworld.deep_dark|music/game/deeper",
        "featherfall" to "minecraft:music.overworld.deep_dark|music/game/featherfall",

        "axolotl" to "minecraft:music.under_water|music/game/water/axolotl",
        "dragon_fish" to "minecraft:music.under_water|music/game/water/dragon_fish",
        "shuniji" to "minecraft:music.under_water|music/game/water/shuniji",
    )

    private val DISPLAY_NAMES = dev.mcrib884.musync.TrackNames.DISPLAY_NAMES

    private val TRACK_SUGGESTIONS = SuggestionProvider<CommandSourceStack> { _, builder ->
        val allTracks = TRACK_MAP.keys.toMutableSet()

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
                .requires { it.hasPermission(2) }
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

                                    if (dev.mcrib884.musync.server.CustomTrackManager.hasTrack(input)) {
                                        dev.mcrib884.musync.server.MusicManager.playTrack("custom:$input")
                                        //? if >=1.20 {
                                        ctx.source.sendSuccess(
                                            { Component.literal("Now playing custom: $input") },
                                            true
                                        )
                                        //?} else {
                                        /*ctx.source.sendSuccess(
                                            Component.literal("Now playing custom: $input"),
                                            true
                                        )*/
                                        //?}
                                        return@executes 1
                                    }

                                    val trackValue = TRACK_MAP[input]
                                    if (trackValue != null) {

                                        if (trackValue.contains("|")) {
                                            val parts = trackValue.split("|", limit = 2)
                                            dev.mcrib884.musync.server.MusicManager.playTrack(parts[0], parts[1])
                                            //? if >=1.20 {
                                            ctx.source.sendSuccess(
                                                { Component.literal("Now playing: ${formatOggName(parts[1])}") },
                                                true
                                            )
                                            //?} else {
                                            /*ctx.source.sendSuccess(
                                                Component.literal("Now playing: ${formatOggName(parts[1])}"),
                                                true
                                            )*/
                                            //?}
                                        } else {
                                            dev.mcrib884.musync.server.MusicManager.playTrack(trackValue)
                                            //? if >=1.20 {
                                            ctx.source.sendSuccess(
                                                { Component.literal("Now playing: ${formatTrackName(trackValue)}") },
                                                true
                                            )
                                            //?} else {
                                            /*ctx.source.sendSuccess(
                                                Component.literal("Now playing: ${formatTrackName(trackValue)}"),
                                                true
                                            )*/
                                            //?}
                                        }
                                        return@executes 1
                                    }

                                    val resLoc = ResourceLocation.tryParse(input)
                                    if (resLoc != null && soundEventContains(resLoc)) {
                                        dev.mcrib884.musync.server.MusicManager.playTrack(resLoc.toString())
                                        //? if >=1.20 {
                                        ctx.source.sendSuccess(
                                            { Component.literal("Now playing: ${formatTrackName(resLoc.toString())}") },
                                            true
                                        )
                                        //?} else {
                                        /*ctx.source.sendSuccess(
                                            Component.literal("Now playing: ${formatTrackName(resLoc.toString())}"),
                                            true
                                        )*/
                                        //?}
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
                            //? if >=1.20 {
                            ctx.source.sendSuccess(
                                { Component.literal("Music stopped") },
                                true
                            )
                            //?} else {
                            /*ctx.source.sendSuccess(
                                Component.literal("Music stopped"),
                                true
                            )*/
                            //?}
                            1
                        }
                )
                .then(
                    Commands.literal("skip")
                        .executes { ctx ->
                            if (isDownloading(ctx)) return@executes 0
                            dev.mcrib884.musync.server.MusicManager.skipTrack()
                            //? if >=1.20 {
                            ctx.source.sendSuccess(
                                { Component.literal("Skipping to next track") },
                                true
                            )
                            //?} else {
                            /*ctx.source.sendSuccess(
                                Component.literal("Skipping to next track"),
                                true
                            )*/
                            //?}
                            1
                        }
                )
                .then(
                    Commands.literal("pause")
                        .executes { ctx ->
                            if (isDownloading(ctx)) return@executes 0
                            dev.mcrib884.musync.server.MusicManager.pauseMusic()
                            //? if >=1.20 {
                            ctx.source.sendSuccess(
                                { Component.literal("Music paused") },
                                true
                            )
                            //?} else {
                            /*ctx.source.sendSuccess(
                                Component.literal("Music paused"),
                                true
                            )*/
                            //?}
                            1
                        }
                )
                .then(
                    Commands.literal("resume")
                        .executes { ctx ->
                            if (isDownloading(ctx)) return@executes 0
                            dev.mcrib884.musync.server.MusicManager.resumeMusic()
                            //? if >=1.20 {
                            ctx.source.sendSuccess(
                                { Component.literal("Music resumed") },
                                true
                            )
                            //?} else {
                            /*ctx.source.sendSuccess(
                                Component.literal("Music resumed"),
                                true
                            )*/
                            //?}
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

                                    if (dev.mcrib884.musync.server.CustomTrackManager.hasTrack(input)) {
                                        dev.mcrib884.musync.server.MusicManager.addToQueue("custom:$input")
                                        //? if >=1.20 {
                                        ctx.source.sendSuccess(
                                            { Component.literal("Added to queue: $input (custom)") },
                                            true
                                        )
                                        //?} else {
                                        /*ctx.source.sendSuccess(
                                            Component.literal("Added to queue: $input (custom)"),
                                            true
                                        )*/
                                        //?}
                                        return@executes 1
                                    }

                                    val trackValue = TRACK_MAP[input]
                                    if (trackValue != null) {
                                        if (trackValue.contains("|")) {
                                            val parts = trackValue.split("|", limit = 2)
                                            dev.mcrib884.musync.server.MusicManager.addToQueue(parts[0])
                                            //? if >=1.20 {
                                            ctx.source.sendSuccess(
                                                { Component.literal("Added to queue: ${formatOggName(parts[1])}") },
                                                true
                                            )
                                            //?} else {
                                            /*ctx.source.sendSuccess(
                                                Component.literal("Added to queue: ${formatOggName(parts[1])}"),
                                                true
                                            )*/
                                            //?}
                                        } else {
                                            dev.mcrib884.musync.server.MusicManager.addToQueue(trackValue)
                                            //? if >=1.20 {
                                            ctx.source.sendSuccess(
                                                { Component.literal("Added to queue: ${formatTrackName(trackValue)}") },
                                                true
                                            )
                                            //?} else {
                                            /*ctx.source.sendSuccess(
                                                Component.literal("Added to queue: ${formatTrackName(trackValue)}"),
                                                true
                                            )*/
                                            //?}
                                        }
                                        return@executes 1
                                    }

                                    val resLoc = ResourceLocation.tryParse(input)
                                    if (resLoc != null && soundEventContains(resLoc)) {
                                        dev.mcrib884.musync.server.MusicManager.addToQueue(resLoc.toString())
                                        //? if >=1.20 {
                                        ctx.source.sendSuccess(
                                            { Component.literal("Added to queue: ${formatTrackName(resLoc.toString())}") },
                                            true
                                        )
                                        //?} else {
                                        /*ctx.source.sendSuccess(
                                            Component.literal("Added to queue: ${formatTrackName(resLoc.toString())}"),
                                            true
                                        )*/
                                        //?}
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

                            //? if >=1.20 {
                            ctx.source.sendSuccess(
                                { Component.literal("========== MuSync ==========") },
                                false
                            )
                            //?} else {
                            /*ctx.source.sendSuccess(
                                Component.literal("========== MuSync =========="),
                                false
                            )*/
                            //?}

                            val trackDisplay = when {
                                track == "None" -> "No track"
                                resolvedName.isNotEmpty() -> formatOggName(resolvedName)
                                else -> formatTrackName(track)
                            }
                            //? if >=1.20 {
                            ctx.source.sendSuccess(
                                { Component.literal("Track: $trackDisplay") },
                                false
                            )
                            //?} else {
                            /*ctx.source.sendSuccess(
                                Component.literal("Track: $trackDisplay"),
                                false
                            )*/
                            //?}

                            if (track != "None" && resolvedName.isNotEmpty()) {
                                //? if >=1.20 {
                                ctx.source.sendSuccess(
                                    { Component.literal("Pool: ${formatTrackName(track)}") },
                                    false
                                )
                                //?} else {
                                /*ctx.source.sendSuccess(
                                    Component.literal("Pool: ${formatTrackName(track)}"),
                                    false
                                )*/
                                //?}
                            }

                            val statusText = when {
                                paused -> "Paused"
                                !playing && waiting -> "Waiting"
                                !playing -> "Stopped"
                                else -> "Playing"
                            }
                            //? if >=1.20 {
                            ctx.source.sendSuccess(
                                { Component.literal("Status: $statusText") },
                                false
                            )
                            //?} else {
                            /*ctx.source.sendSuccess(
                                Component.literal("Status: $statusText"),
                                false
                            )*/
                            //?}

                            if (track != "None" && (playing || paused)) {
                                if (duration > 0) {
                                    val progressBar = createProgressBar(position, duration, 20)
                                    val posStr = formatTime(position)
                                    val durStr = formatTime(duration)
                                    //? if >=1.20 {
                                    ctx.source.sendSuccess(
                                        { Component.literal("[$progressBar] $posStr / $durStr") },
                                        false
                                    )
                                    //?} else {
                                    /*ctx.source.sendSuccess(
                                        Component.literal("[$progressBar] $posStr / $durStr"),
                                        false
                                    )*/
                                    //?}
                                } else {
                                    val posStr = formatTime(position)
                                    //? if >=1.20 {
                                    ctx.source.sendSuccess(
                                        { Component.literal("$posStr / Loading...") },
                                        false
                                    )
                                    //?} else {
                                    /*ctx.source.sendSuccess(
                                        Component.literal("$posStr / Loading..."),
                                        false
                                    )*/
                                    //?}
                                }
                            }

                            if (waiting && !playing && !paused) {
                                val ticksRemaining = (ticksTotal - ticksElapsed).coerceAtLeast(0)
                                val elapsedSec = ticksElapsed / 20.0
                                val totalSec = ticksTotal / 20.0
                                val remainSec = ticksRemaining / 20.0
                                //? if >=1.20 {
                                ctx.source.sendSuccess(
                                    { Component.literal("Delay: ${formatTickTime(ticksElapsed)} / ${formatTickTime(ticksTotal)} (${formatTickTime(ticksRemaining)} remaining)") },
                                    false
                                )
                                //?} else {
                                /*ctx.source.sendSuccess(
                                    Component.literal("Delay: ${formatTickTime(ticksElapsed)} / ${formatTickTime(ticksTotal)} (${formatTickTime(ticksRemaining)} remaining)"),
                                    false
                                )*/
                                //?}
                                //? if >=1.20 {
                                ctx.source.sendSuccess(
                                    { Component.literal("       ($ticksElapsed / $ticksTotal ticks, $ticksRemaining left)") },
                                    false
                                )
                                //?} else {
                                /*ctx.source.sendSuccess(
                                    Component.literal("       ($ticksElapsed / $ticksTotal ticks, $ticksRemaining left)"),
                                    false
                                )*/
                                //?}
                            }

                            val modeDisplay = when (mode) {
                                "AUTONOMOUS" -> "Auto"
                                "PLAYLIST" -> "Playlist"
                                else -> mode
                            }
                            //? if >=1.20 {
                            ctx.source.sendSuccess(
                                { Component.literal("Mode: $modeDisplay") },
                                false
                            )
                            //?} else {
                            /*ctx.source.sendSuccess(
                                Component.literal("Mode: $modeDisplay"),
                                false
                            )*/
                            //?}

                            if (custMin >= 0 && custMax >= 0) {
                                //? if >=1.20 {
                                ctx.source.sendSuccess(
                                    { Component.literal("Custom delay: $custMin-$custMax ticks (${formatTickTime(custMin)}-${formatTickTime(custMax)})") },
                                    false
                                )
                                //?} else {
                                /*ctx.source.sendSuccess(
                                    Component.literal("Custom delay: $custMin-$custMax ticks (${formatTickTime(custMin)}-${formatTickTime(custMax)})"),
                                    false
                                )*/
                                //?}
                            }

                            if (queue.isNotEmpty()) {
                                //? if >=1.20 {
                                ctx.source.sendSuccess(
                                    { Component.literal("Queue (${queue.size}):") },
                                    false
                                )
                                //?} else {
                                /*ctx.source.sendSuccess(
                                    Component.literal("Queue (${queue.size}):"),
                                    false
                                )*/
                                //?}
                                queue.take(5).forEachIndexed { i, t ->
                                    //? if >=1.20 {
                                    ctx.source.sendSuccess(
                                        { Component.literal("  ${i + 1}. ${formatTrackName(t.toString())}") },
                                        false
                                    )
                                    //?} else {
                                    /*ctx.source.sendSuccess(
                                        Component.literal("  ${i + 1}. ${formatTrackName(t.toString())}"),
                                        false
                                    )*/
                                    //?}
                                }
                                if (queue.size > 5) {
                                    //? if >=1.20 {
                                    ctx.source.sendSuccess(
                                        { Component.literal("  ... and ${queue.size - 5} more") },
                                        false
                                    )
                                    //?} else {
                                    /*ctx.source.sendSuccess(
                                        Component.literal("  ... and ${queue.size - 5} more"),
                                        false
                                    )*/
                                    //?}
                                }
                            }

                            //? if >=1.20 {
                            ctx.source.sendSuccess(
                                { Component.literal("=============================") },
                                false
                            )
                            //?} else {
                            /*ctx.source.sendSuccess(
                                Component.literal("============================="),
                                false
                            )*/
                            //?}
                            1
                        }
                )
                .then(
                    Commands.literal("sync")
                        .executes { ctx ->
                            if (isDownloading(ctx)) return@executes 0
                            dev.mcrib884.musync.server.MusicManager.forceSyncAll()
                            //? if >=1.20 {
                            ctx.source.sendSuccess(
                                { Component.literal("Resynced all clients") },
                                true
                            )
                            //?} else {
                            /*ctx.source.sendSuccess(
                                Component.literal("Resynced all clients"),
                                true
                            )*/
                            //?}
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
                                    //? if >=1.20 {
                                    ctx.source.sendSuccess(
                                        { Component.literal("Delay reset to vanilla defaults (per-pool timing)") },
                                        true
                                    )
                                    //?} else {
                                    /*ctx.source.sendSuccess(
                                        Component.literal("Delay reset to vanilla defaults (per-pool timing)"),
                                        true
                                    )*/
                                    //?}
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
                                            //? if >=1.20 {
                                            ctx.source.sendSuccess(
                                                { Component.literal("Custom delay set: $min-$max ticks (${formatTickTime(min)}-${formatTickTime(max)})") },
                                                true
                                            )
                                            //?} else {
                                            /*ctx.source.sendSuccess(
                                                Component.literal("Custom delay set: $min-$max ticks (${formatTickTime(min)}-${formatTickTime(max)})"),
                                                true
                                            )*/
                                            //?}
                                            1
                                        }
                                )
                        )
                        .executes { ctx ->
                            val status = dev.mcrib884.musync.server.MusicManager.getDetailedStatus()
                            val custMin = status["customMinDelay"] as Int
                            val custMax = status["customMaxDelay"] as Int
                            if (custMin >= 0 && custMax >= 0) {
                                //? if >=1.20 {
                                ctx.source.sendSuccess(
                                    { Component.literal("Current custom delay: $custMin-$custMax ticks (${formatTickTime(custMin)}-${formatTickTime(custMax)})") },
                                    false
                                )
                                //?} else {
                                /*ctx.source.sendSuccess(
                                    Component.literal("Current custom delay: $custMin-$custMax ticks (${formatTickTime(custMin)}-${formatTickTime(custMax)})"),
                                    false
                                )*/
                                //?}
                            } else {
                                //? if >=1.20 {
                                ctx.source.sendSuccess(
                                    { Component.literal("Using vanilla defaults (per-pool timing). Use /musync delay <min> <max> to override.") },
                                    false
                                )
                                //?} else {
                                /*ctx.source.sendSuccess(
                                    Component.literal("Using vanilla defaults (per-pool timing). Use /musync delay <min> <max> to override."),
                                    false
                                )*/
                                //?}
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
        return dev.mcrib884.musync.TrackNames.formatDisplayName(trackId)
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

    fun getAllTrackIds(): List<String> = TRACK_MAP.values.toList()

    fun getAllTrackNames(): List<String> = TRACK_MAP.keys.toList()

    fun getAllTracksForBrowser(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()

        val knownSpecificSounds = mutableSetOf<String>()
        val aliasedSoundEvents = mutableSetOf<String>()
        for (value in TRACK_MAP.values) {
            val parts = value.split("|", limit = 2)
            aliasedSoundEvents.add(parts[0])
            if (value.contains("|")) {
                val oggPath = parts[1]
                knownSpecificSounds.add("minecraft:$oggPath")
            }
        }

        for (key in TRACK_MAP.keys) {
            val displayName = dev.mcrib884.musync.TrackNames.formatTrack(TRACK_MAP.getValue(key))
            result.add(key to displayName)
        }

        soundEventKeys().forEach { loc ->
            val fullId = loc.toString()
            val path = loc.path

            if (path.contains("music") || path.startsWith("music_disc.")) {
                var skipPoolEntry = false
                if (fullId in aliasedSoundEvents) {
                    try {
                        val events = net.minecraft.client.Minecraft.getInstance().soundManager.getSoundEvent(loc)
                        val resolvedFiles = linkedSetOf<String>()
                        events?.list?.forEach { weighted ->
                            val sound = weighted.getSound(net.minecraft.util.RandomSource.create())
                            if (sound.type != net.minecraft.client.resources.sounds.Sound.Type.FILE) return@forEach
                            resolvedFiles.add("${sound.location.namespace}:${sound.location.path}")
                        }
                        skipPoolEntry = resolvedFiles.size == 1 && resolvedFiles.first() in knownSpecificSounds
                    } catch (_: Exception) { }
                }
                if (skipPoolEntry) return@forEach

                val displayName = DISPLAY_NAMES[fullId]
                    ?: formatDiscoveredTrackName(fullId)
                result.add(fullId to displayName)
            }
        }

        try {
            val mc = net.minecraft.client.Minecraft.getInstance()
            val soundManager = mc.soundManager
            soundEventKeys().forEach { loc ->
                if (!loc.path.contains("music") && !loc.path.startsWith("music_disc.")) return@forEach
                val fullEventId = loc.toString()
                val poolName = DISPLAY_NAMES[fullEventId] ?: formatDiscoveredTrackName(fullEventId)

                try {
                    val events = soundManager.getSoundEvent(loc) ?: return@forEach
                    val soundList = events.list
                    for (weighted in soundList) {
                        val sound = weighted.getSound(net.minecraft.util.RandomSource.create())
                        if (sound.type != net.minecraft.client.resources.sounds.Sound.Type.FILE) continue
                        val soundLoc = sound.location
                        val qualifiedPath = "${soundLoc.namespace}:${soundLoc.path}"

                        if (qualifiedPath in knownSpecificSounds) continue

                        val key = "$fullEventId|$qualifiedPath"
                        val friendlyOgg = dev.mcrib884.musync.TrackNames.formatOggName(qualifiedPath)
                        val prefix = if (soundLoc.namespace != "minecraft") "[${soundLoc.namespace}] " else ""
                        val displayName = "$prefix$friendlyOgg ($poolName)"
                        result.add(key to displayName)
                        knownSpecificSounds.add(qualifiedPath)
                    }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }

        try {
            dev.mcrib884.musync.client.ClientTrackManager.getServerCustomTrackNames().forEach { name ->
                if (!TRACK_MAP.containsKey(name)) {
                    val displayName = "[Custom] " + name.replace("_", " ").replaceFirstChar { it.uppercase() }
                    result.add(name to displayName)
                }
            }
        } catch (_: Exception) {

            dev.mcrib884.musync.server.MusicManager.getCustomTracks().forEach { name ->
                if (!TRACK_MAP.containsKey(name)) {
                    val displayName = "[Custom] " + name.replace("_", " ").replaceFirstChar { it.uppercase() }
                    result.add(name to displayName)
                }
            }
        }

        return result.sortedBy { it.second }
    }

    fun toBrowserTrackKey(trackId: String): String {
        if (trackId.startsWith("custom:")) return trackId.removePrefix("custom:")

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

        if (dev.mcrib884.musync.server.CustomTrackManager.hasTrack(key)) {
            return "custom:$key"
        }
        val value = TRACK_MAP[key] ?: return null

        return if (value.contains("|")) value.split("|", limit = 2)[0] else value
    }

    fun getSpecificSound(friendlyName: String): String {
        val key = friendlyName.lowercase().replace(" ", "_")
        val value = TRACK_MAP[key] ?: return ""
        return if (value.contains("|")) value.split("|", limit = 2)[1] else ""
    }
}
