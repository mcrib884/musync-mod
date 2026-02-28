package dev.mcrib884.musync.server

import java.io.File

object CustomTrackManager {

    private val tracks = mutableMapOf<String, ByteArray>()

    private val durations = mutableMapOf<String, Long>()

    fun scan(serverDir: File) {
        tracks.clear()
        durations.clear()

        val folder = File(serverDir, "customtracks")
        if (!folder.exists()) {
            folder.mkdirs()
            println("[MuSync] Created customtracks/ folder at ${folder.absolutePath}")
            return
        }

        val supportedExtensions = setOf("ogg", "wav")
        val audioFiles = folder.listFiles { f ->
            f.isFile && f.extension.lowercase() in supportedExtensions
        } ?: return

        for (file in audioFiles) {
            val name = file.nameWithoutExtension.lowercase().replace(" ", "_")
            try {
                val bytes = file.readBytes()
                tracks[name] = bytes
                println("[MuSync] Loaded custom track: $name (${bytes.size} bytes, ${file.extension.lowercase()})")
            } catch (e: Exception) {
                println("[MuSync] Failed to load custom track ${file.name}: ${e.message}")
            }
        }

        println("[MuSync] Loaded ${tracks.size} custom tracks from ${folder.absolutePath}")
    }

    fun getTrackNames(): List<String> = tracks.keys.toList()

    fun getTrackData(name: String): ByteArray? = tracks[name]

    fun hasTrack(name: String): Boolean = tracks.containsKey(name)

    fun getTrackCount(): Int = tracks.size

    fun getManifest(): List<Pair<String, Int>> {
        return tracks.map { (name, data) -> name to data.size }
    }
}
