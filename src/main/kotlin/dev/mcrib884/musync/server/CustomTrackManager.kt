package dev.mcrib884.musync.server

import dev.mcrib884.musync.network.PacketIO
import dev.mcrib884.musync.network.TrackManifestEntry
import java.io.File
import java.security.MessageDigest
import java.util.Locale

object CustomTrackManager {

    private val SAFE_BASE = Regex("^[\\p{L}\\p{N}_\\-]+$")
    private val SAFE_INTERNAL = Regex("^[\\p{L}\\p{N}_\\-]+\\.(ogg|wav|mp3)$")
    private val SUPPORTED_EXTENSIONS = setOf("ogg", "wav", "mp3")
    private val UNSAFE_CHARS = Regex("[^\\p{L}\\p{N}_\\-]")

    private data class TrackInfo(val file: File, val size: Long, val sha256: String)
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

        val audioFiles = (folder.listFiles { f ->
            f.isFile && f.extension.lowercase() in SUPPORTED_EXTENSIONS
        } ?: run {
            tracks = emptyMap()
            baseAliases = emptyMap()
            return
        }).sortedBy { it.name.lowercase() }

        for (file in audioFiles) {
            val baseName = normalizeBaseName(file.nameWithoutExtension)
            if (!SAFE_BASE.matches(baseName)) {
                dev.mcrib884.musync.MuSyncLog.warn("Skipping unsafe custom track name: ${file.name}")
                continue
            }
            val size = file.length()
            if (size <= 0L || size > PacketIO.MAX_TRACK_SIZE_BYTES || size > Int.MAX_VALUE.toLong()) {
                dev.mcrib884.musync.MuSyncLog.warn("Skipping custom track with invalid size: ${file.name} ($size bytes)")
                continue
            }
            val sha256 = sha256Of(file)
            if (sha256 == null) {
                dev.mcrib884.musync.MuSyncLog.warn("Skipping custom track with unreadable hash: ${file.name}")
                continue
            }
            val extension = file.extension.lowercase()
            val internalName = uniqueInternalName(baseName, extension, nextTracks)
            nextTracks[internalName] = TrackInfo(file, size, sha256)
            aliasCandidates.getOrPut(baseName) { mutableListOf() }.add(internalName)
            dev.mcrib884.musync.MuSyncLog.info("Indexed custom track: $internalName (${size} bytes)")
        }

        tracks = nextTracks
        baseAliases = aliasCandidates
            .filterValues { it.size == 1 }
            .mapValues { it.value.first() }
        dev.mcrib884.musync.MuSyncLog.info("Indexed ${tracks.size} custom tracks from ${folder.absolutePath}")
    }

    private fun normalizeBaseName(name: String): String {
        return name
            .lowercase(Locale.ROOT)
            .replace(" ", "_")
            .replace(UNSAFE_CHARS, "_")
            .replace(Regex("_+"), "_")
            .trim('_')
    }

    private fun uniqueInternalName(baseName: String, extension: String, existing: Map<String, TrackInfo>): String {
        var candidate = "$baseName.$extension"
        var index = 2
        while (candidate in existing) {
            candidate = "${baseName}_$index.$extension"
            index++
        }
        if (candidate != "$baseName.$extension") {
            dev.mcrib884.musync.MuSyncLog.warn("Custom track name collision for $baseName.$extension, using $candidate")
        }
        return candidate
    }

    private fun resolveInternalName(name: String): String? {
        val normalized = name.lowercase(Locale.ROOT).replace(" ", "_")
        val maybeExt = normalized.substringAfterLast('.', "")
        val hasExt = maybeExt in SUPPORTED_EXTENSIONS && normalized.contains('.')

        if (hasExt) {
            val rawBase = normalized.substringBeforeLast('.')
            val base = normalizeBaseName(rawBase)
            val candidate = "$base.$maybeExt"
            if (SAFE_INTERNAL.matches(candidate) && tracks.containsKey(candidate)) return candidate
            return null
        }

        val key = normalizeBaseName(normalized)
        return baseAliases[key]
    }

    fun getTrackNames(): List<String> = baseAliases.keys.sorted()

    fun getInternalTrackNames(): List<String> = tracks.keys.sorted()

    fun getTrackFile(name: String): File? = resolveInternalName(name)?.let { tracks[it]?.file }

    fun hasTrack(name: String): Boolean = resolveInternalName(name) != null

    fun toInternalName(name: String): String? = resolveInternalName(name)

    fun getTrackCount(): Int = tracks.size

    fun getManifest(): List<TrackManifestEntry> {
        return tracks.entries
            .sortedBy { it.key }
            .map { (name, info) -> TrackManifestEntry(name, info.size, info.sha256) }
    }

    private fun sha256Of(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            dev.mcrib884.musync.MuSyncLog.error("Failed to hash custom track ${file.name}: ${e.message}")
            null
        }
    }
}
