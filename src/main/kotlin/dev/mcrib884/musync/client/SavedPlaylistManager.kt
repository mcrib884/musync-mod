package dev.mcrib884.musync.client

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import java.io.File

object SavedPlaylistManager {
        private val gson = GsonBuilder().setPrettyPrinting().create()

    private fun getFile(): File {
        val configDir = File(Minecraft.getInstance().gameDirectory, "config")
        if (!configDir.exists()) configDir.mkdirs()
        return File(configDir, "musync-playlists.json")
    }

    private fun normalizeName(name: String): String? {
        val trimmed = name.trim()
        return if (trimmed.isEmpty()) null else trimmed.take(64)
    }

    private fun readAll(): LinkedHashMap<String, MutableList<String>> {
        val file = getFile()
        if (!file.exists()) return linkedMapOf()

        return try {
            file.reader().use { reader ->
                val parsed = JsonParser.parseReader(reader)
                val root = if (parsed.isJsonObject) parsed.asJsonObject else JsonObject()
                val playlistsNode = root.getAsJsonObject("playlists") ?: root
                val playlists = linkedMapOf<String, MutableList<String>>()
                for ((name, value) in playlistsNode.entrySet()) {
                    if (!value.isJsonArray) continue
                    val tracks = mutableListOf<String>()
                    for (element in value.asJsonArray) {
                        if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                            tracks.add(element.asString)
                        }
                    }
                    playlists[name] = tracks
                }
                playlists
            }
        } catch (e: Exception) {
            dev.mcrib884.musync.MuSyncLog.error("Failed to read saved playlists: ${e.message}")
            linkedMapOf()
        }
    }

    private fun writeAll(playlists: Map<String, List<String>>) {
        val file = getFile()
        val root = JsonObject()
        val playlistsObject = JsonObject()
        for ((name, tracks) in playlists) {
            val array = JsonArray()
            tracks.forEach(array::add)
            playlistsObject.add(name, array)
        }
        root.add("playlists", playlistsObject)

        try {
            file.writer().use { writer -> gson.toJson(root, writer) }
        } catch (e: Exception) {
            dev.mcrib884.musync.MuSyncLog.error("Failed to write saved playlists: ${e.message}")
        }
    }

    fun getPlaylistNames(): List<String> {
        return readAll().keys.sortedBy { it.lowercase() }
    }

    fun findPlaylistName(name: String): String? {
        val normalized = normalizeName(name) ?: return null
        return readAll().keys.firstOrNull { it.equals(normalized, ignoreCase = true) }
    }

    fun getPlaylist(name: String): List<String> {
        val resolved = findPlaylistName(name) ?: return emptyList()
        return readAll()[resolved]?.toList() ?: emptyList()
    }

    fun savePlaylist(name: String, tracks: List<String>): String? {
        val normalized = normalizeName(name) ?: return null
        if (tracks.isEmpty()) return null

        val playlists = readAll()
        val existing = playlists.keys.firstOrNull { it.equals(normalized, ignoreCase = true) }
        if (existing != null && existing != normalized) {
            playlists.remove(existing)
        }
        playlists[normalized] = tracks.toMutableList()
        writeAll(playlists)
        return normalized
    }

    fun deletePlaylist(name: String): Boolean {
        val resolved = findPlaylistName(name) ?: return false
        val playlists = readAll()
        val removed = playlists.remove(resolved) != null
        if (removed) writeAll(playlists)
        return removed
    }
}