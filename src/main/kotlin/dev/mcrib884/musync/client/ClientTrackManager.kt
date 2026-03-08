package dev.mcrib884.musync.client

import dev.mcrib884.musync.network.PacketHandler
import dev.mcrib884.musync.network.TrackRequestPacket
import net.minecraft.client.Minecraft
import java.io.File

object ClientTrackManager {

    private val logger = org.apache.logging.log4j.LogManager.getLogger("MuSync")
    private val SAFE_NAME = Regex("^[a-z0-9_\\-]+$")

    private fun sanitizeName(name: String): String? {
        val cleaned = name.lowercase().replace(" ", "_")
        if (!SAFE_NAME.matches(cleaned)) {
            logger.warn("Rejected unsafe track name: $name")
            return null
        }
        return cleaned
    }

    private var serverManifest: List<Pair<String, Int>> = emptyList()

    var tracksToDownload: List<Pair<String, Int>> = emptyList()
        private set

    var currentDownloadIndex: Int = 0
        private set

    var currentTrackChunksReceived: Int = 0
        private set
    var currentTrackTotalChunks: Int = 0
        private set

    var isDownloading: Boolean = false
        private set
    var downloadComplete: Boolean = false
        private set

    var totalBytesToDownload: Long = 0
        private set
    var totalBytesReceived: Long = 0
        private set

    // ---- Persistent disk cache settings ----
    var cacheEnabled: Boolean = true
        get() {
            ensureSettingsLoaded()
            return field
        }
        private set
    private var settingsLoaded = false

    fun setCacheEnabled(value: Boolean) {
        ensureSettingsLoaded()
        cacheEnabled = value
        saveCacheSetting(value)
    }

    private fun ensureSettingsLoaded() {
        if (settingsLoaded) return
        settingsLoaded = true
        cacheEnabled = loadCacheSetting()
    }

    private fun getSettingsFile(): File =
        File(Minecraft.getInstance().gameDirectory, "musync_settings.properties")

    private fun loadCacheSetting(): Boolean {
        return try {
            val file = getSettingsFile()
            if (!file.exists()) return true
            val props = java.util.Properties()
            file.inputStream().use { props.load(it) }
            props.getProperty("cacheEnabled", "true") != "false"
        } catch (e: Exception) { true }
    }

    private fun saveCacheSetting(value: Boolean) {
        try {
            val file = getSettingsFile()
            val props = java.util.Properties()
            if (file.exists()) file.inputStream().use { props.load(it) }
            props.setProperty("cacheEnabled", value.toString())
            file.outputStream().use { props.store(it, "MuSync client settings") }
        } catch (e: Exception) {
            logger.error("Failed to save MuSync settings: ${e.message}")
        }
    }

    private fun getCacheFolder(): File =
        File(Minecraft.getInstance().gameDirectory, "musynccache")

    private fun getLocalFolder(): File {
        val gameDir = Minecraft.getInstance().gameDirectory
        return File(gameDir, "customtracks")
    }

    private fun scanLocalTracks(): Map<String, Int> {
        val folder = getLocalFolder()
        if (!folder.exists()) {
            folder.mkdirs()
            return emptyMap()
        }

        val result = mutableMapOf<String, Int>()
        val supportedExtensions = setOf("ogg", "wav")
        val audioFiles = folder.listFiles { f ->
            f.isFile && f.extension.lowercase() in supportedExtensions
        } ?: return emptyMap()

        for (file in audioFiles) {
            val name = file.nameWithoutExtension.lowercase().replace(" ", "_")
            result[name] = file.length().toInt()
        }
        return result
    }

    fun handleManifest(manifest: List<Pair<String, Int>>) {
        ensureSettingsLoaded()
        serverManifest = manifest
        val localTracks = scanLocalTracks()
        val cacheFolder = if (cacheEnabled) getCacheFolder() else null

        val missing = mutableListOf<Pair<String, Int>>()
        for ((name, serverSize) in manifest) {
            val localSize = localTracks[name]
            if (localSize != null && localSize == serverSize) continue

            // Check the persistent musynccache folder for an exact name+size match
            if (cacheFolder != null && cacheFolder.exists()) {
                val cached = cacheFolder.listFiles { f ->
                    f.isFile && f.nameWithoutExtension.lowercase().replace(" ", "_") == name
                }?.firstOrNull { it.length().toInt() == serverSize }
                if (cached != null) {
                    try {
                        val localFolder = getLocalFolder()
                        if (!localFolder.exists()) localFolder.mkdirs()
                        cached.copyTo(File(localFolder, cached.name), overwrite = true)
                        logger.info("Restored cached track '$name' from musynccache (${cached.length()} bytes)")
                        continue
                    } catch (e: Exception) {
                        logger.error("Failed to restore cached track '$name': ${e.message}")
                    }
                }
            }

            missing.add(name to serverSize)
        }

        if (missing.isEmpty()) {

            logger.info("All ${manifest.size} custom tracks are synced")
            cacheAllLocalTracks(manifest)
            isDownloading = false
            downloadComplete = true
            return
        }

        tracksToDownload = missing
        currentDownloadIndex = 0
        currentTrackChunksReceived = 0
        currentTrackTotalChunks = 0
        totalBytesToDownload = missing.sumOf { it.second.toLong() }
        totalBytesReceived = 0
        isDownloading = true
        downloadComplete = false

        logger.info("Need to download ${missing.size} custom tracks (${formatSize(totalBytesToDownload)})")

        val requestNames = missing.map { it.first }
        PacketHandler.sendToServer(
            TrackRequestPacket(requestNames)
        )
    }

    fun onChunkReceived(trackName: String, chunkIndex: Int, totalChunks: Int, chunkData: ByteArray) {
        if (!isDownloading) return

        currentTrackTotalChunks = totalChunks
        currentTrackChunksReceived = chunkIndex + 1
        totalBytesReceived += chunkData.size

        if (chunkIndex + 1 == totalChunks) {

            val assembled = CustomTrackCache.get(trackName)
            if (assembled != null) {
                saveTrackToDisk(trackName, assembled)
            }

            currentDownloadIndex++
            currentTrackChunksReceived = 0
            currentTrackTotalChunks = 0

            if (currentDownloadIndex >= tracksToDownload.size) {

                isDownloading = false
                downloadComplete = true
                logger.info("All custom tracks downloaded and synced!")

                cacheAllLocalTracks(serverManifest)

                Minecraft.getInstance().execute {
                    val screen = Minecraft.getInstance().screen
                    if (screen is TrackDownloadScreen) {
                        screen.onDownloadComplete()
                    }
                }
            }
        }
    }

    private fun saveTrackToDisk(trackName: String, data: ByteArray) {
        val safeName = sanitizeName(trackName)
        if (safeName == null) {
            logger.warn("Refusing to save track with unsafe name: $trackName")
            return
        }
        try {
            val folder = getLocalFolder()
            if (!folder.exists()) folder.mkdirs()

            val extension = if (data.size >= 4) {
                val magic = String(data, 0, 4, Charsets.US_ASCII)
                when {
                    magic == "RIFF" -> "wav"
                    magic == "OggS" -> "ogg"
                    else -> "ogg"
                }
            } else "ogg"

            val file = File(folder, "$safeName.$extension")
            val canonical = file.canonicalPath
            if (!canonical.startsWith(folder.canonicalPath)) {
                logger.error("Path traversal detected for track: $trackName")
                return
            }
            file.writeBytes(data)
            logger.info("Saved custom track to disk: ${file.name} (${data.size} bytes)")

            // Also persist to musynccache if caching is enabled
            if (cacheEnabled) {
                try {
                    val cf = getCacheFolder()
                    if (!cf.exists()) cf.mkdirs()
                    val cacheFile = File(cf, "$safeName.$extension")
                    val cacheCanonical = cacheFile.canonicalPath
                    if (cacheCanonical.startsWith(cf.canonicalPath)) {
                        cacheFile.writeBytes(data)
                        logger.info("Saved custom track to cache: ${cacheFile.name} (${data.size} bytes)")
                    }
                } catch (e2: Exception) {
                    logger.error("Failed to cache track $trackName: ${e2.message}")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to save track $trackName: ${e.message}")
        }
    }

    private fun cacheAllLocalTracks(manifest: List<Pair<String, Int>>) {
        val folder = getLocalFolder()
        if (!folder.exists()) return

        val supportedExtensions = setOf("ogg", "wav")
        val audioFiles = folder.listFiles { f ->
            f.isFile && f.extension.lowercase() in supportedExtensions
        } ?: return

        val serverTrackNames = manifest.map { it.first }.toSet()

        for (file in audioFiles) {
            val name = file.nameWithoutExtension.lowercase().replace(" ", "_")

            if (name in serverTrackNames && !CustomTrackCache.has(name)) {
                try {
                    val bytes = file.readBytes()
                    CustomTrackCache.put(name, bytes)
                    logger.info("Cached local track: $name (${bytes.size} bytes)")
                } catch (e: Exception) {
                    logger.error("Failed to cache local track $name: ${e.message}")
                }
            }
        }
    }

    fun currentTrackName(): String {
        return if (currentDownloadIndex in tracksToDownload.indices) {
            tracksToDownload[currentDownloadIndex].first
        } else ""
    }

    fun currentTrackSize(): Int {
        return if (currentDownloadIndex in tracksToDownload.indices) {
            tracksToDownload[currentDownloadIndex].second
        } else 0
    }

    fun reset() {
        serverManifest = emptyList()
        tracksToDownload = emptyList()
        currentDownloadIndex = 0
        currentTrackChunksReceived = 0
        currentTrackTotalChunks = 0
        totalBytesToDownload = 0
        totalBytesReceived = 0
        isDownloading = false
        downloadComplete = false
    }

    fun getServerCustomTrackNames(): List<String> {
        return serverManifest.map { it.first }
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
