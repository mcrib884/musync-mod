package dev.mcrib884.musync

import java.util.Locale

object TrackNameUtil {

    private val SAFE_BASE = Regex("^[\\p{L}\\p{N}_\\-]+$")
    private val SAFE_INTERNAL = Regex("^[\\p{L}\\p{N}_\\-]+\\.(ogg|wav|mp3)$")
    private val UNSAFE_CHARS = Regex("[^\\p{L}\\p{N}_\\-]")
    val SUPPORTED_EXTENSIONS = setOf("ogg", "wav", "mp3")

    fun normalizeBaseName(name: String): String {
        return name
            .lowercase(Locale.ROOT)
            .replace(" ", "_")
            .replace(UNSAFE_CHARS, "_")
            .replace(Regex("_+"), "_")
            .trim('_')
    }

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

    fun isSafeBaseName(name: String): Boolean = SAFE_BASE.matches(name)
}
