package dev.mcrib884.musync.client

import dev.mcrib884.musync.network.PacketHandler
import dev.mcrib884.musync.network.TrackRequestPacket
import net.minecraft.client.Minecraft
import java.io.File

object ClientTrackManager {

    private val SAFE_INTERNAL_NAME = Regex("^[a-z0-9_\\-]+\\.(ogg|wav)$")
    private const val MAX_TRACKS_PER_REQUEST = 3

    private fun normalizeInternalName(name: String): String? {
        val normalized = name.lowercase().replace(" ", "_")
        if (!SAFE_INTERNAL_NAME.matches(normalized)) {
            dev.mcrib884.musync.MuSyncLog.warn("Rejected unsafe track name: $name")
            return null
        }
        return normalized
    }

    fun displayTrackName(internalName: String): String {
        return internalName.substringBeforeLast(".")
    }

    private var serverManifest: List<Pair<String, Int>> = emptyList()
    private var serverManifestVersion: Long = -1L

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

    private val failedTracks = linkedSetOf<String>()
    private val pendingRequested = linkedSetOf<String>()
    private var lastProgressAtMs: Long = 0L
    private var stallRetries: Int = 0
    private const val DOWNLOAD_STALL_TIMEOUT_MS = 20_000L
    private const val MAX_STALL_RETRIES = 2

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
            dev.mcrib884.musync.MuSyncLog.error("Failed to save MuSync settings: ${e.message}")
        }
    }

    private fun getCacheFolder(): File =
        File(Minecraft.getInstance().gameDirectory, "musynccache")

    private fun getLocalFolder(): File {
        val gameDir = Minecraft.getInstance().gameDirectory
        return File(gameDir, "customtracks")
    }

    private fun scanFolderTracks(folder: File): Map<String, File> {
        if (!folder.exists()) return emptyMap()

        val supportedExtensions = setOf("ogg", "wav")
        return folder.listFiles { f ->
            f.isFile && f.extension.lowercase() in supportedExtensions
        }?.mapNotNull { file ->
            val key = normalizeInternalName(file.name) ?: return@mapNotNull null
            key to file
        }?.toMap() ?: emptyMap()
    }

    private fun scanLocalTracks(): Map<String, Int> {
        val folder = getLocalFolder().apply {
            if (!exists()) mkdirs()
        }
        return scanFolderTracks(folder).mapValues { it.value.length().toInt() }
    }

    fun handleManifest(manifestVersion: Long, manifest: List<Pair<String, Int>>) {
        ensureSettingsLoaded()
        serverManifest = manifest
        serverManifestVersion = manifestVersion
        failedTracks.clear()
        pendingRequested.clear()
        val localTracks = scanLocalTracks()
        val cacheFolder = if (cacheEnabled) getCacheFolder() else null
        val cachedTracks = if (cacheFolder != null && cacheFolder.exists()) scanFolderTracks(cacheFolder) else emptyMap()

        val missing = mutableListOf<Pair<String, Int>>()
        for ((name, serverSize) in manifest) {
            val internalName = normalizeInternalName(name)
            if (internalName == null) {
                failedTracks.add(name)
                continue
            }
            val localSize = localTracks[internalName]
            if (localSize != null && localSize == serverSize) continue

            if (cacheFolder != null && cacheFolder.exists()) {
                val cached = cachedTracks[internalName]?.takeIf { it.length().toInt() == serverSize }
                if (cached != null) {
                    try {
                        val localFolder = getLocalFolder()
                        if (!localFolder.exists()) localFolder.mkdirs()
                        cached.copyTo(File(localFolder, cached.name), overwrite = true)
                        dev.mcrib884.musync.MuSyncLog.info("Restored cached track '$internalName' from musynccache (${cached.length()} bytes)")
                        continue
                    } catch (e: Exception) {
                        dev.mcrib884.musync.MuSyncLog.error("Failed to restore cached track '$internalName': ${e.message}")
                    }
                }
            }

            missing.add(internalName to serverSize)
        }

        if (missing.isEmpty()) {
            tracksToDownload = emptyList()
            currentDownloadIndex = 0
            currentTrackChunksReceived = 0
            currentTrackTotalChunks = 0
            totalBytesToDownload = 0
            totalBytesReceived = 0
            stallRetries = 0
            pendingRequested.clear()
            lastProgressAtMs = 0L
            dev.mcrib884.musync.MuSyncLog.info("All ${manifest.size} custom tracks are synced")
            cacheAllLocalTracks(manifest)
            isDownloading = false
            downloadComplete = failedTracks.isEmpty()
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
        stallRetries = 0
        pendingRequested.clear()
        lastProgressAtMs = System.currentTimeMillis()

        dev.mcrib884.musync.MuSyncLog.info("Need to download ${missing.size} custom tracks (${formatSize(totalBytesToDownload)})")
        requestNextBatch()
    }

    private fun requestNextBatch() {
        if (!isDownloading) return
        if (pendingRequested.isNotEmpty()) return
        if (currentDownloadIndex !in tracksToDownload.indices) return

        val requestNames = mutableListOf<String>()
        var index = currentDownloadIndex
        while (index < tracksToDownload.size && requestNames.size < MAX_TRACKS_PER_REQUEST) {
            val name = tracksToDownload[index].first
            if (name !in failedTracks) requestNames.add(name)
            index++
        }
        if (requestNames.isEmpty()) return

        pendingRequested.addAll(requestNames)
        lastProgressAtMs = System.currentTimeMillis()
        PacketHandler.sendToServer(TrackRequestPacket(serverManifestVersion, requestNames))
    }

    fun onChunkProgress(trackName: String, chunkIndex: Int, totalChunks: Int, acceptedBytes: Int) {
        if (!isDownloading) return
        if (currentDownloadIndex !in tracksToDownload.indices) return
        if (tracksToDownload[currentDownloadIndex].first != trackName) return

        lastProgressAtMs = System.currentTimeMillis()
        currentTrackTotalChunks = totalChunks
        currentTrackChunksReceived = chunkIndex + 1
        totalBytesReceived += acceptedBytes.toLong()
    }

    fun onTrackDownloaded(trackName: String, totalChunks: Int, data: ByteArray) {
        if (!isDownloading) return

        val saved = saveTrackToDisk(trackName, data)
        if (!saved) {
            failedTracks.add(trackName)
            dev.mcrib884.musync.MuSyncLog.warn("Track marked failed and skipped: $trackName")
        }
        pendingRequested.remove(trackName)

        if (currentDownloadIndex !in tracksToDownload.indices) return
        if (tracksToDownload[currentDownloadIndex].first != trackName) return

        currentTrackChunksReceived = totalChunks
        currentTrackTotalChunks = totalChunks

        advanceDownloadCursor()
    }

    fun onTrackFailed(trackName: String, reason: String) {
        if (!isDownloading) return
        if (currentDownloadIndex !in tracksToDownload.indices) return
        if (tracksToDownload[currentDownloadIndex].first != trackName) return
        pendingRequested.remove(trackName)
        failedTracks.add(trackName)
        dev.mcrib884.musync.MuSyncLog.warn("Track download failed for '$trackName': $reason")
        advanceDownloadCursor()
    }

    private fun advanceDownloadCursor() {
        currentDownloadIndex++
        currentTrackChunksReceived = 0
        currentTrackTotalChunks = 0
        lastProgressAtMs = System.currentTimeMillis()

        if (currentDownloadIndex < tracksToDownload.size) {
            if (pendingRequested.isEmpty()) {
                requestNextBatch()
            }
            return
        }

        isDownloading = false
        downloadComplete = failedTracks.isEmpty()
        stallRetries = 0

        if (downloadComplete) {
            dev.mcrib884.musync.MuSyncLog.info("All custom tracks downloaded and synced!")
        } else {
            dev.mcrib884.musync.MuSyncLog.warn("Custom track sync finished with ${failedTracks.size} failed track(s): ${failedTracks.joinToString(",")}")
            Minecraft.getInstance().execute {
                val mc = Minecraft.getInstance()
                val screen = mc.screen
                if (screen is TrackDownloadScreen) {
                    screen.onDownloadComplete()
                } else {
                    val next = TrackDownloadScreen()
                    mc.setScreen(next)
                    next.onDownloadComplete()
                }
            }
        }

        java.util.concurrent.CompletableFuture.runAsync {
            cacheAllLocalTracks(serverManifest)
        }

        Minecraft.getInstance().execute {
            val screen = Minecraft.getInstance().screen
            if (screen is TrackDownloadScreen) {
                screen.onDownloadComplete()
            }
        }
    }

    fun onClientTick() {
        if (!isDownloading) return
        if (currentDownloadIndex !in tracksToDownload.indices) return

        val now = System.currentTimeMillis()
        if (now - lastProgressAtMs < DOWNLOAD_STALL_TIMEOUT_MS) return

        val remaining = if (pendingRequested.isNotEmpty()) pendingRequested.toList() else tracksToDownload.drop(currentDownloadIndex).map { it.first }.take(MAX_TRACKS_PER_REQUEST)
        if (remaining.isEmpty()) {
            isDownloading = false
            downloadComplete = failedTracks.isEmpty()
            return
        }

        if (stallRetries < MAX_STALL_RETRIES) {
            stallRetries++
            lastProgressAtMs = now
            currentTrackChunksReceived = 0
            currentTrackTotalChunks = 0
            dev.mcrib884.musync.MuSyncLog.warn("Track download stalled, retrying remaining ${remaining.size} track(s) (attempt $stallRetries/$MAX_STALL_RETRIES)")
            PacketHandler.sendToServer(TrackRequestPacket(serverManifestVersion, remaining))
            return
        }

        dev.mcrib884.musync.MuSyncLog.error("Track download aborted after stall retries; remaining tracks: ${remaining.joinToString(",")}")
        remaining.forEach { failedTracks.add(it) }
        pendingRequested.clear()
        isDownloading = false
        downloadComplete = false

        Minecraft.getInstance().execute {
            val mc = Minecraft.getInstance()
            val screen = mc.screen
            if (screen is TrackDownloadScreen) {
                screen.onDownloadComplete()
            } else {
                val next = TrackDownloadScreen()
                mc.setScreen(next)
                next.onDownloadComplete()
            }
        }
    }

    private fun saveTrackToDisk(trackName: String, data: ByteArray): Boolean {
        val safeName = normalizeInternalName(trackName)
        if (safeName == null) {
            dev.mcrib884.musync.MuSyncLog.warn("Refusing to save track with unsafe name: $trackName")
            return false
        }
        try {
            val folder = getLocalFolder()
            if (!folder.exists()) folder.mkdirs()

            val expectedExtension = safeName.substringAfterLast('.', "")
            val extension = if (data.size >= 4) {
                val magic = String(data, 0, 4, Charsets.US_ASCII)
                when {
                    magic == "RIFF" -> "wav"
                    magic == "OggS" -> "ogg"
                    else -> ""
                }
            } else ""
            if (extension.isEmpty() || extension != expectedExtension) {
                dev.mcrib884.musync.MuSyncLog.warn("Refusing to save track due to extension/content mismatch: $trackName")
                return false
            }

            val file = File(folder, safeName)
            if (!isInsideFolder(file, folder)) {
                dev.mcrib884.musync.MuSyncLog.error("Path traversal detected for track: $trackName")
                return false
            }
            file.writeBytes(data)
            dev.mcrib884.musync.MuSyncLog.info("Saved custom track to disk: ${file.name} (${data.size} bytes)")

            if (cacheEnabled) {
                try {
                    val cf = getCacheFolder()
                    if (!cf.exists()) cf.mkdirs()
                    val cacheFile = File(cf, safeName)
                    if (isInsideFolder(cacheFile, cf)) {
                        cacheFile.writeBytes(data)
                        dev.mcrib884.musync.MuSyncLog.info("Saved custom track to cache: ${cacheFile.name} (${data.size} bytes)")
                    }
                } catch (e2: Exception) {
                    dev.mcrib884.musync.MuSyncLog.error("Failed to cache track $trackName: ${e2.message}")
                }
            }
            return true
        } catch (e: Exception) {
            dev.mcrib884.musync.MuSyncLog.error("Failed to save track $trackName: ${e.message}")
            return false
        }
    }

    private fun isInsideFolder(file: File, folder: File): Boolean {
        return try {
            file.canonicalFile.toPath().startsWith(folder.canonicalFile.toPath())
        } catch (_: Exception) {
            false
        }
    }

    private fun cacheAllLocalTracks(manifest: List<Pair<String, Int>>) {
        val folder = getLocalFolder()
        if (!folder.exists()) return

        val supportedExtensions = setOf("ogg", "wav")
        val audioFiles = folder.listFiles { f ->
            f.isFile && f.extension.lowercase() in supportedExtensions
        } ?: return

        val serverTrackNames = manifest.mapNotNull { normalizeInternalName(it.first) }.toSet()

        for (file in audioFiles) {
            val internalName = normalizeInternalName(file.name) ?: continue

            if (internalName in serverTrackNames && !CustomTrackCache.has(internalName)) {
                try {
                    val bytes = file.readBytes()
                    CustomTrackCache.put(internalName, bytes)
                    dev.mcrib884.musync.MuSyncLog.info("Cached local track: $internalName (${bytes.size} bytes)")
                } catch (e: Exception) {
                    dev.mcrib884.musync.MuSyncLog.error("Failed to cache local track $internalName: ${e.message}")
                }
            }
        }
    }

    fun currentTrackName(): String {
        return if (currentDownloadIndex in tracksToDownload.indices) {
            displayTrackName(tracksToDownload[currentDownloadIndex].first)
        } else ""
    }

    fun currentTrackSize(): Int {
        return if (currentDownloadIndex in tracksToDownload.indices) {
            tracksToDownload[currentDownloadIndex].second
        } else 0
    }

    fun reset() {
        serverManifest = emptyList()
        serverManifestVersion = -1L
        tracksToDownload = emptyList()
        currentDownloadIndex = 0
        currentTrackChunksReceived = 0
        currentTrackTotalChunks = 0
        totalBytesToDownload = 0
        totalBytesReceived = 0
        failedTracks.clear()
        pendingRequested.clear()
        lastProgressAtMs = 0L
        stallRetries = 0
        isDownloading = false
        downloadComplete = false
    }

    fun getServerCustomTrackNames(): List<String> {
        return serverManifest
            .mapNotNull { normalizeInternalName(it.first) }
            .distinct()
            .sorted()
    }

    fun getFailedTracks(): List<String> = failedTracks.toList()

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
