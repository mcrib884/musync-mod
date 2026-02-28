package dev.mcrib884.musync.client

import dev.mcrib884.musync.network.PacketHandler
import dev.mcrib884.musync.network.TrackRequestPacket
import net.minecraft.client.Minecraft
import net.minecraftforge.network.PacketDistributor
import java.io.File

object ClientTrackManager {

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
        serverManifest = manifest
        val localTracks = scanLocalTracks()

        val missing = mutableListOf<Pair<String, Int>>()
        for ((name, serverSize) in manifest) {
            val localSize = localTracks[name]
            if (localSize == null || localSize != serverSize) {
                missing.add(name to serverSize)
            }
        }

        if (missing.isEmpty()) {

            println("[MuSync] All ${manifest.size} custom tracks are synced")
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

        println("[MuSync] Need to download ${missing.size} custom tracks (${formatSize(totalBytesToDownload)})")

        val requestNames = missing.map { it.first }
        PacketHandler.INSTANCE.send(
            PacketDistributor.SERVER.noArg(),
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
                println("[MuSync] All custom tracks downloaded and synced!")

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

            val file = File(folder, "$trackName.$extension")
            file.writeBytes(data)
            println("[MuSync] Saved custom track to disk: ${file.name} (${data.size} bytes)")
        } catch (e: Exception) {
            println("[MuSync] Failed to save track $trackName: ${e.message}")
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
                    println("[MuSync] Cached local track: $name (${bytes.size} bytes)")
                } catch (e: Exception) {
                    println("[MuSync] Failed to cache local track $name: ${e.message}")
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
