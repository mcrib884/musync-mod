package dev.mcrib884.musync

import java.util.Locale

/**
 * Shared track name normalization used by both server and client.
 * All normalizers MUST produce identical output for a given input
 * so that cache lookups, manifest matching, and chunk routing all
 * use the same canonical key.
 */
object TrackNameUtil {

    private val SAFE_BASE = Regex("^[\\p{L}\\p{N}_\\-]+$")
    private val SAFE_INTERNAL = Regex("^[\\p{L}\\p{N}_\\-]+\\.(ogg|wav|mp3)$")
    private val UNSAFE_CHARS = Regex("[^\\p{L}\\p{N}_\\-]")
    val SUPPORTED_EXTENSIONS = setOf("ogg", "wav", "mp3")

    /**
     * Normalizes the base portion of a track filename (without extension).
     * Lowercases, replaces spaces and unsafe characters with underscores,
     * collapses consecutive underscores, and trims leading/trailing underscores.
     */
    fun normalizeBaseName(name: String): String {
        return name
            .lowercase(Locale.ROOT)
            .replace(" ", "_")
            .replace(UNSAFE_CHARS, "_")
            .replace(Regex("_+"), "_")
            .trim('_')
    }

    /**
     * Normalizes a full internal track name (base + extension) using the
     * same rules as the server. Returns null if the result is invalid.
     */
    fun normalizeInternalName(name: String): String? {
        val dotIdx = name.lastIndexOf('.')
        if (dotIdx <= 0) return null

        val rawBase = name.substring(0, dotIdx)
        val ext = name.substring(dotIdx + 1).lowercase(Locale.ROOT)

        if (ext !in SUPPORTED_EXTENSIONS) return null

        val base = normalizeBaseName(rawBase)
        if (base.isEmpty()) return null

        val normalized = "$base.$ext"
        if (!SAFE_INTERNAL.matches(normalized)) return null

        return normalized
    }

    /**
     * Checks whether a base name (without extension) is safe.
     */
    fun isSafeBaseName(name: String): Boolean = SAFE_BASE.matches(name)
}
