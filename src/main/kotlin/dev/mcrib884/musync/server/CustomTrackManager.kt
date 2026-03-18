package dev.mcrib884.musync.server

import java.io.File

object CustomTrackManager {

    private val SAFE_BASE = Regex("^[a-z0-9_\\-]+$")
    private val SAFE_INTERNAL = Regex("^[a-z0-9_\\-]+\\.(ogg|wav)$")

    private data class TrackInfo(val file: File, val size: Long)
    @Volatile
    private var tracks: Map<String, TrackInfo> = emptyMap()
    @Volatile
    private var baseAliases: Map<String, String> = emptyMap()

    fun scan(serverDir: File) {
        val nextTracks = mutableMapOf<String, TrackInfo>()
        val aliasCandidates = mutableMapOf<String, MutableList<String>>()

        val folder = File(serverDir, "customtracks")
        if (!folder.exists()) {
            folder.mkdirs()
            dev.mcrib884.musync.MuSyncLog.info("Created customtracks/ folder at ${folder.absolutePath}")
            tracks = emptyMap()
            baseAliases = emptyMap()
            return
        }

        val supportedExtensions = setOf("ogg", "wav")
        val audioFiles = (folder.listFiles { f ->
            f.isFile && f.extension.lowercase() in supportedExtensions
        } ?: run {
            tracks = emptyMap()
            baseAliases = emptyMap()
            return
        }).sortedBy { it.name.lowercase() }

        for (file in audioFiles) {
            val baseName = file.nameWithoutExtension.lowercase().replace(" ", "_")
            if (!SAFE_BASE.matches(baseName)) {
                dev.mcrib884.musync.MuSyncLog.warn("Skipping unsafe custom track name: ${file.name}")
                continue
            }
            val extension = file.extension.lowercase()
            val internalName = "$baseName.$extension"
            nextTracks[internalName] = TrackInfo(file, file.length())
            aliasCandidates.getOrPut(baseName) { mutableListOf() }.add(internalName)
            dev.mcrib884.musync.MuSyncLog.info("Indexed custom track: $internalName (${file.length()} bytes)")
        }

        tracks = nextTracks
        baseAliases = aliasCandidates
            .filterValues { it.size == 1 }
            .mapValues { it.value.first() }
        dev.mcrib884.musync.MuSyncLog.info("Indexed ${tracks.size} custom tracks from ${folder.absolutePath}")
    }

    private fun normalizeKey(name: String): String {
        return name.lowercase().replace(" ", "_")
    }

    private fun resolveInternalName(name: String): String? {
        val key = normalizeKey(name)
        if (SAFE_INTERNAL.matches(key) && tracks.containsKey(key)) return key
        return baseAliases[key]
    }

    fun getTrackNames(): List<String> = baseAliases.keys.sorted()

    fun getInternalTrackNames(): List<String> = tracks.keys.sorted()

    fun getTrackFile(name: String): File? = resolveInternalName(name)?.let { tracks[it]?.file }

    fun hasTrack(name: String): Boolean = resolveInternalName(name) != null

    fun toInternalName(name: String): String? = resolveInternalName(name)

    fun getTrackCount(): Int = tracks.size

    fun getManifest(): List<Pair<String, Int>> {
        return tracks.entries
            .sortedBy { it.key }
            .map { (name, info) -> name to info.size.toInt() }
    }
}
