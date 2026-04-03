package dev.mcrib884.musync.network

import net.minecraft.network.FriendlyByteBuf

object PacketIO {
    const val MAX_TRACK_ID_LENGTH = 512
    const val MAX_TRACK_NAME_LENGTH = 256
    const val MAX_TRACK_HASH_LENGTH = 128
    const val MAX_SOUND_ID_LENGTH = 512
    const val MAX_DIMENSION_ID_LENGTH = 128
    const val MAX_PLAYER_NAME_LENGTH = 64
    const val MAX_QUEUE_ENTRIES = 512
    const val MAX_MANIFEST_ENTRIES = 512
    const val MAX_DIMENSION_ENTRIES = 32
    const val MAX_PLAYERS_PER_DIMENSION = 128
    const val MAX_TRACK_SIZE_BYTES = Int.MAX_VALUE.toLong()

    fun writeUtfBounded(buf: FriendlyByteBuf, value: String, maxLength: Int) {
        buf.writeUtf(value, maxLength)
    }

    fun readUtfBounded(buf: FriendlyByteBuf, maxLength: Int): String {
        return buf.readUtf(maxLength)
    }

    fun writeNullableUtf(buf: FriendlyByteBuf, value: String?, maxLength: Int = MAX_TRACK_ID_LENGTH) {
        if (value == null) {
            buf.writeBoolean(false)
        } else {
            buf.writeBoolean(true)
            writeUtfBounded(buf, value, maxLength)
        }
    }

    fun readNullableUtf(buf: FriendlyByteBuf, maxLength: Int): String? {
        return if (buf.readBoolean()) readUtfBounded(buf, maxLength) else null
    }

    fun writeUtfList(
        buf: FriendlyByteBuf,
        values: List<String>,
        maxLength: Int = MAX_TRACK_ID_LENGTH,
        maxEntries: Int = MAX_QUEUE_ENTRIES
    ) {
        val clamped = if (values.size > maxEntries) values.take(maxEntries) else values
        buf.writeInt(clamped.size)
        for (value in clamped) {
            writeUtfBounded(buf, value, maxLength)
        }
    }

    fun readUtfList(buf: FriendlyByteBuf, maxEntries: Int, maxLength: Int): List<String> {
        val count = buf.readInt().coerceIn(0, maxEntries)
        val values = ArrayList<String>(count)
        repeat(count) {
            values.add(readUtfBounded(buf, maxLength))
        }
        return values
    }
}
