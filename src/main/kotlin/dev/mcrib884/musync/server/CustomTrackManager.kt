package dev.mcrib884.musync.server

import java.io.File

object CustomTrackManager {

    private val logger = org.apache.logging.log4j.LogManager.getLogger("MuSync")

    private data class TrackInfo(val file: File, val size: Long)
    private val tracks = mutableMapOf<String, TrackInfo>()

    const val MAX_TRACK_SIZE = 50L * 1024 * 1024

    fun scan(serverDir: File) {
        tracks.clear()

        val folder = File(serverDir, "customtracks")
        if (!folder.exists()) {
            folder.mkdirs()
            logger.info("Created customtracks/ folder at ${folder.absolutePath}")
            return
        }

        val supportedExtensions = setOf("ogg", "wav")
        val audioFiles = folder.listFiles { f ->
            f.isFile && f.extension.lowercase() in supportedExtensions
        } ?: return

        for (file in audioFiles) {
            if (file.length() > MAX_TRACK_SIZE) {
                logger.warn("Skipping oversized custom track: ${file.name} (${file.length()} bytes, max=${MAX_TRACK_SIZE})")
                continue
            }
            val name = file.nameWithoutExtension.lowercase().replace(" ", "_")
            tracks[name] = TrackInfo(file, file.length())
            logger.info("Indexed custom track: $name (${file.length()} bytes, ${file.extension.lowercase()})")
        }

        logger.info("Indexed ${tracks.size} custom tracks from ${folder.absolutePath}")
    }

    fun getTrackNames(): List<String> = tracks.keys.toList()

    fun getTrackFile(name: String): File? = tracks[name]?.file

    fun hasTrack(name: String): Boolean = tracks.containsKey(name)

    fun getTrackCount(): Int = tracks.size

    fun getManifest(): List<Pair<String, Int>> {
        return tracks.map { (name, info) -> name to info.size.toInt() }
    }
}
