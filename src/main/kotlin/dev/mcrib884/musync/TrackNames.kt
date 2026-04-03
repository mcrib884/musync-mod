package dev.mcrib884.musync

object TrackNames {

    private data class BuiltinTrackMeta(
        val title: String,
        val eventId: String,
        val pathPrefix: String? = null
    )

    private val BUILTIN_ALIAS_META: Map<String, BuiltinTrackMeta> = mapOf(
        "minecraft" to BuiltinTrackMeta("Minecraft", "minecraft:music.game", "music/game/calm1"),
        "clark" to BuiltinTrackMeta("Clark", "minecraft:music.game", "music/game/calm2"),
        "sweden" to BuiltinTrackMeta("Sweden", "minecraft:music.game", "music/game/calm3"),
        "subwoofer_lullaby" to BuiltinTrackMeta("Subwoofer Lullaby", "minecraft:music.game", "music/game/hal1"),
        "living_mice" to BuiltinTrackMeta("Living Mice", "minecraft:music.game", "music/game/hal2"),
        "haggstrom" to BuiltinTrackMeta("Haggstrom", "minecraft:music.game", "music/game/hal3"),
        "danny" to BuiltinTrackMeta("Danny", "minecraft:music.game", "music/game/hal4"),
        "key" to BuiltinTrackMeta("Key", "minecraft:music.game", "music/game/nuance1"),
        "oxygene" to BuiltinTrackMeta("Oxygene", "minecraft:music.game", "music/game/nuance2"),
        "dry_hands" to BuiltinTrackMeta("Dry Hands", "minecraft:music.game", "music/game/piano1"),
        "wet_hands" to BuiltinTrackMeta("Wet Hands", "minecraft:music.game", "music/game/piano2"),
        "mice_on_venus" to BuiltinTrackMeta("Mice on Venus", "minecraft:music.game", "music/game/piano3"),
        "biome_fest" to BuiltinTrackMeta("Biome Fest", "minecraft:music.creative", "music/game/creative/creative1"),
        "blind_spots" to BuiltinTrackMeta("Blind Spots", "minecraft:music.creative", "music/game/creative/creative2"),
        "haunt_muskie" to BuiltinTrackMeta("Haunt Muskie", "minecraft:music.creative", "music/game/creative/creative3"),
        "aria_math" to BuiltinTrackMeta("Aria Math", "minecraft:music.creative", "music/game/creative/creative4"),
        "dreiton" to BuiltinTrackMeta("Dreiton", "minecraft:music.creative", "music/game/creative/creative5"),
        "taswell" to BuiltinTrackMeta("Taswell", "minecraft:music.creative", "music/game/creative/creative6"),
        "mutation" to BuiltinTrackMeta("Mutation", "minecraft:music.menu", "music/menu/menu1"),
        "moog_city_2" to BuiltinTrackMeta("Moog City 2", "minecraft:music.menu", "music/menu/menu2"),
        "beginning_2" to BuiltinTrackMeta("Beginning 2", "minecraft:music.menu", "music/menu/menu3"),
        "floating_trees" to BuiltinTrackMeta("Floating Trees", "minecraft:music.menu", "music/menu/menu4"),
        "rubedo" to BuiltinTrackMeta("Rubedo", "minecraft:music.nether.warped_forest", "music/game/nether/rubedo"),
        "chrysopoeia" to BuiltinTrackMeta("Chrysopoeia", "minecraft:music.nether.crimson_forest", "music/game/nether/chrysopoeia"),
        "so_below" to BuiltinTrackMeta("So Below", "minecraft:music.nether.soul_sand_valley", "music/game/nether/so_below"),
        "concrete_halls" to BuiltinTrackMeta("Concrete Halls", "minecraft:music.nether.nether_wastes", "music/game/nether/concrete_halls"),
        "dead_voxel" to BuiltinTrackMeta("Dead Voxel", "minecraft:music.nether.nether_wastes", "music/game/nether/dead_voxel"),
        "warmth" to BuiltinTrackMeta("Warmth", "minecraft:music.nether.nether_wastes", "music/game/nether/warmth"),
        "ballad_of_the_cats" to BuiltinTrackMeta("Ballad of the Cats", "minecraft:music.nether.nether_wastes", "music/game/nether/ballad_of_the_cats"),
        "the_end" to BuiltinTrackMeta("The End", "minecraft:music.end", "music/game/end/the_end"),
        "boss" to BuiltinTrackMeta("Boss", "minecraft:music.dragon", "music/game/end/boss"),
        "alpha" to BuiltinTrackMeta("Alpha", "minecraft:music.credits", "music/game/end/credits"),
        "stand_tall" to BuiltinTrackMeta("Stand Tall", "minecraft:music.game", "music/game/stand_tall"),
        "left_to_bloom" to BuiltinTrackMeta("Left to Bloom", "minecraft:music.game", "music/game/left_to_bloom"),
        "one_more_day" to BuiltinTrackMeta("One More Day", "minecraft:music.game", "music/game/one_more_day"),
        "infinite_amethyst" to BuiltinTrackMeta("Infinite Amethyst", "minecraft:music.game", "music/game/infinite_amethyst"),
        "wending" to BuiltinTrackMeta("Wending", "minecraft:music.game", "music/game/wending"),
        "ancestry" to BuiltinTrackMeta("Ancestry", "minecraft:music.game", "music/game/ancestry"),
        "comforting_memories" to BuiltinTrackMeta("Comforting Memories", "minecraft:music.game", "music/game/comforting_memories"),
        "floating_dream" to BuiltinTrackMeta("Floating Dream", "minecraft:music.game", "music/game/floating_dream"),
        "an_ordinary_day" to BuiltinTrackMeta("An Ordinary Day", "minecraft:music.game", "music/game/an_ordinary_day"),
        "echo_in_the_wind" to BuiltinTrackMeta("Echo in the Wind", "minecraft:music.game", "music/game/echo_in_the_wind"),
        "a_familiar_room" to BuiltinTrackMeta("A Familiar Room", "minecraft:music.game", "music/game/a_familiar_room"),
        "bromeliad" to BuiltinTrackMeta("Bromeliad", "minecraft:music.game", "music/game/bromeliad"),
        "crescent_dunes" to BuiltinTrackMeta("Crescent Dunes", "minecraft:music.game", "music/game/crescent_dunes"),
        "firebugs" to BuiltinTrackMeta("Firebugs", "minecraft:music.game", "music/game/firebugs"),
        "labyrinthine" to BuiltinTrackMeta("Labyrinthine", "minecraft:music.game", "music/game/labyrinthine"),
        "eld_unknown" to BuiltinTrackMeta("Eld Unknown", "minecraft:music.game", "music/game/eld_unknown"),
        "deeper" to BuiltinTrackMeta("Deeper", "minecraft:music.overworld.deep_dark", "music/game/deeper"),
        "featherfall" to BuiltinTrackMeta("Featherfall", "minecraft:music.overworld.deep_dark", "music/game/featherfall"),
        "axolotl" to BuiltinTrackMeta("Axolotl", "minecraft:music.under_water", "music/game/water/axolotl"),
        "dragon_fish" to BuiltinTrackMeta("Dragon Fish", "minecraft:music.under_water", "music/game/water/dragon_fish"),
        "shuniji" to BuiltinTrackMeta("Shuniji", "minecraft:music.under_water", "music/game/water/shuniji")
    )

    private val titleByNormalizedPath: Map<String, String> = BUILTIN_ALIAS_META.values
        .mapNotNull { meta ->
            val prefix = meta.pathPrefix ?: return@mapNotNull null
            normalizeSoundPath(prefix) to meta.title
        }
        .toMap()

    private val titleByAlias: Map<String, String> = BUILTIN_ALIAS_META.mapValues { it.value.title }

    private val titleByEventAndAlias: Map<String, String> = BUILTIN_ALIAS_META.entries.associate { (alias, meta) ->
        "${meta.eventId.lowercase()}|$alias" to meta.title
    }

    private fun normalizeSoundPath(path: String): String {
        val clean = path.substringAfter(':')
        return clean
            .removeSuffix(".ogg")
            .removeSuffix(".wav")
            .removeSuffix(".mp3")
            .lowercase()
            .replace('\\', '/')
            .replace('-', '_')
            .trim('/')
    }

    private fun titleFromResolvedResource(path: String): String? {
        val normalized = normalizeSoundPath(path)
        titleByNormalizedPath[normalized]?.let { return it }
        return titleByNormalizedPath.entries.firstOrNull { normalized.endsWith(it.key) }?.value
    }

    private fun titleFromEventAlias(eventId: String, alias: String): String? {
        val normalizedAlias = alias.lowercase().replace(' ', '_')
        return titleByEventAndAlias["${eventId.lowercase()}|$normalizedAlias"]
            ?: titleByAlias[normalizedAlias]
    }

    fun resourcePathForAlias(alias: String): String? {
        val normalizedAlias = alias.lowercase().replace(' ', '_')
        return BUILTIN_ALIAS_META[normalizedAlias]?.pathPrefix
    }

    val OGG_NAMES: Map<String, String> = mapOf(
        "music/game/calm1" to "Minecraft",
        "music/game/calm2" to "Clark",
        "music/game/calm3" to "Sweden",
        "music/game/hal1" to "Subwoofer Lullaby",
        "music/game/hal2" to "Living Mice",
        "music/game/hal3" to "Haggstrom",
        "music/game/hal4" to "Danny",
        "music/game/nuance1" to "Key",
        "music/game/nuance2" to "Oxygene",
        "music/game/piano1" to "Dry Hands",
        "music/game/piano2" to "Wet Hands",
        "music/game/piano3" to "Mice on Venus",
        "music/game/creative/creative1" to "Biome Fest",
        "music/game/creative/creative2" to "Blind Spots",
        "music/game/creative/creative3" to "Haunt Muskie",
        "music/game/creative/creative4" to "Aria Math",
        "music/game/creative/creative5" to "Dreiton",
        "music/game/creative/creative6" to "Taswell",
        "music/menu/menu1" to "Mutation",
        "music/menu/menu2" to "Moog City 2",
        "music/menu/menu3" to "Beginning 2",
        "music/menu/menu4" to "Floating Trees",
        "music/game/nether/rubedo" to "Rubedo",
        "music/game/nether/chrysopoeia" to "Chrysopoeia",
        "music/game/nether/so_below" to "So Below",
        "music/game/nether/concrete_halls" to "Concrete Halls",
        "music/game/nether/dead_voxel" to "Dead Voxel",
        "music/game/nether/warmth" to "Warmth",
        "music/game/nether/ballad_of_the_cats" to "Ballad of the Cats",
        "music/game/end/end" to "The End",
        "music/game/end/the_end" to "The End",
        "music/game/end/boss" to "Boss",
        "music/game/end/credits" to "Alpha",
        "music/game/stand_tall" to "Stand Tall",
        "music/game/left_to_bloom" to "Left to Bloom",
        "music/game/one_more_day" to "One More Day",
        "music/game/infinite_amethyst" to "Infinite Amethyst",
        "music/game/wending" to "Wending",
        "music/game/ancestry" to "Ancestry",
        "music/game/comforting_memories" to "Comforting Memories",
        "music/game/floating_dream" to "Floating Dream",
        "music/game/an_ordinary_day" to "An Ordinary Day",
        "music/game/echo_in_the_wind" to "Echo in the Wind",
        "music/game/a_familiar_room" to "A Familiar Room",
        "music/game/bromeliad" to "Bromeliad",
        "music/game/crescent_dunes" to "Crescent Dunes",
        "music/game/firebugs" to "Firebugs",
        "music/game/labyrinthine" to "Labyrinthine",
        "music/game/eld_unknown" to "Eld Unknown",
        "music/game/deeper" to "Deeper",
        "music/game/featherfall" to "Featherfall",
        "music/game/water/axolotl" to "Axolotl",
        "music/game/water/dragon_fish" to "Dragon Fish",
        "music/game/water/shuniji" to "Shuniji",
        "music/game/swamp/aerie" to "Aerie",
        "music/game/swamp/firebugs" to "Firebugs (Swamp)",
    )

    val POOL_NAMES: Map<String, String> = mapOf(
        "minecraft:music.game" to "Game",
        "minecraft:music.creative" to "Creative",
        "minecraft:music.menu" to "Menu",
        "minecraft:music.under_water" to "Underwater",
        "minecraft:music.end" to "The End",
        "minecraft:music.dragon" to "Dragon Fight",
        "minecraft:music.credits" to "Credits",
        "minecraft:music.nether.basalt_deltas" to "Basalt Deltas",
        "minecraft:music.nether.crimson_forest" to "Crimson Forest",
        "minecraft:music.nether.nether_wastes" to "Nether Wastes",
        "minecraft:music.nether.soul_sand_valley" to "Soul Sand Valley",
        "minecraft:music.nether.warped_forest" to "Warped Forest",
        "minecraft:music.overworld.meadow" to "Meadow",
        "minecraft:music.overworld.grove" to "Grove",
        "minecraft:music.overworld.forest" to "Forest",
        "minecraft:music.overworld.desert" to "Desert",
        "minecraft:music.overworld.badlands" to "Badlands",
        "minecraft:music.overworld.jungle" to "Jungle",
        "minecraft:music.overworld.bamboo_jungle" to "Bamboo Jungle",
        "minecraft:music.overworld.cherry_grove" to "Cherry Grove",
        "minecraft:music.overworld.deep_dark" to "Deep Dark",
        "minecraft:music.overworld.dripstone_caves" to "Dripstone Caves",
        "minecraft:music.overworld.lush_caves" to "Lush Caves",
        "minecraft:music.overworld.swamp" to "Swamp",
        "minecraft:music.overworld.old_growth_taiga" to "Old Growth Taiga",
        "minecraft:music.overworld.snowy_slopes" to "Snowy Slopes",
        "minecraft:music.overworld.jagged_peaks" to "Jagged Peaks",
        "minecraft:music.overworld.frozen_peaks" to "Frozen Peaks",
        "minecraft:music.overworld.stony_peaks" to "Stony Peaks",
    )

    val DISPLAY_NAMES: Map<String, String> = mapOf(
        "minecraft:music.game" to "Game Music",
        "minecraft:music.menu" to "Menu Music",
        "minecraft:music.creative" to "Creative Music",
        "minecraft:music.under_water" to "Underwater Music",
        "minecraft:music.nether.basalt_deltas" to "Nether: Basalt Deltas",
        "minecraft:music.nether.crimson_forest" to "Nether: Crimson Forest",
        "minecraft:music.nether.nether_wastes" to "Nether: Nether Wastes",
        "minecraft:music.nether.soul_sand_valley" to "Nether: Soul Sand Valley",
        "minecraft:music.nether.warped_forest" to "Nether: Warped Forest",
        "minecraft:music.end" to "The End",
        "minecraft:music.credits" to "Credits",
        "minecraft:music.dragon" to "Dragon Fight",
        "minecraft:music.overworld.bamboo_jungle" to "Bamboo Jungle",
        "minecraft:music.overworld.cherry_grove" to "Cherry Grove",
        "minecraft:music.overworld.desert" to "Desert",
        "minecraft:music.overworld.forest" to "Forest",
        "minecraft:music.overworld.grove" to "Grove",
        "minecraft:music.overworld.jagged_peaks" to "Jagged Peaks",
        "minecraft:music.overworld.frozen_peaks" to "Frozen Peaks",
        "minecraft:music.overworld.stony_peaks" to "Stony Peaks",
        "minecraft:music.overworld.snowy_slopes" to "Snowy Slopes",
        "minecraft:music.overworld.meadow" to "Meadow",
        "minecraft:music.overworld.old_growth_taiga" to "Old Growth Taiga",
        "minecraft:music.overworld.dripstone_caves" to "Dripstone Caves",
        "minecraft:music.overworld.lush_caves" to "Lush Caves",
        "minecraft:music.overworld.deep_dark" to "Deep Dark",
        "minecraft:music.overworld.swamp" to "Swamp",
        "minecraft:music.overworld.jungle" to "Jungle",
        "minecraft:music.overworld.badlands" to "Badlands",
    )

    fun formatCustomTrackName(internalName: String): String {
        return internalName
            .substringBeforeLast(".")
            .replace("_", " ")
            .replaceFirstChar { it.uppercase() }
    }

    fun formatOggName(oggPath: String): String {
        val cleanPath = if (oggPath.contains(":")) oggPath.substringAfter(":") else oggPath
        if (!cleanPath.contains("/") && (cleanPath.endsWith(".ogg") || cleanPath.endsWith(".wav") || cleanPath.endsWith(".mp3"))) {
            return formatCustomTrackName(cleanPath)
        }
        return OGG_NAMES[cleanPath]
            ?: cleanPath.substringAfterLast("/").replace("_", " ").replaceFirstChar { it.uppercase() }
    }

    fun formatPoolName(id: String): String {
        return POOL_NAMES[id] ?: id.substringAfterLast(":")
    }

    fun formatDisplayName(trackId: String): String {
        return DISPLAY_NAMES[trackId] ?: trackId.substringAfterLast(":")
    }

    fun formatTrack(id: String): String {
        if (id.contains("|")) {
            val eventId = id.substringBefore("|")
            val detail = id.substringAfter("|", "")
            if (detail.startsWith("alias:")) {
                val alias = detail.removePrefix("alias:")
                val byAlias = titleFromEventAlias(eventId, alias)
                if (byAlias != null) return byAlias
                return alias
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            }

            val resolvedByMetadata = titleFromResolvedResource(detail)
            if (resolvedByMetadata != null) return resolvedByMetadata
            return formatOggName(detail)
        }
        if (id.startsWith("custom:")) {
            return "[Custom] " + formatCustomTrackName(id.removePrefix("custom:"))
        }
        val path = if (id.contains(":")) id.substringAfter(":") else id
        if (path.startsWith("music_disc.")) {
            val discName = path.removePrefix("music_disc.").replace("_", " ").replaceFirstChar { it.uppercase() }
            return "Music Disc: $discName"
        }
        return POOL_NAMES[id] ?: id.removePrefix("minecraft:music.")
            .replace(".", " ").replace("_", " ").replaceFirstChar { it.uppercase() }
    }
}
