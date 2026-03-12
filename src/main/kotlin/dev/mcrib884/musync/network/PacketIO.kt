package dev.mcrib884.musync.network

import net.minecraft.network.FriendlyByteBuf

object PacketIO {
    const val MAX_TRACK_ID_LENGTH = 512
    const val MAX_TRACK_NAME_LENGTH = 256
    const val MAX_SOUND_ID_LENGTH = 512
    const val MAX_DIMENSION_ID_LENGTH = 128
    const val MAX_PLAYER_NAME_LENGTH = 64
    const val MAX_QUEUE_ENTRIES = 512
    const val MAX_MANIFEST_ENTRIES = 512
    const val MAX_DIMENSION_ENTRIES = 32
    const val MAX_PLAYERS_PER_DIMENSION = 128
    const val MAX_TRACK_SIZE_BYTES = 50 * 1024 * 1024

    fun writeNullableUtf(buf: FriendlyByteBuf, value: String?) {
        if (value == null) {
            buf.writeBoolean(false)
        } else {
            buf.writeBoolean(true)
            buf.writeUtf(value)
        }
    }

    fun readNullableUtf(buf: FriendlyByteBuf, maxLength: Int): String? {
        return if (buf.readBoolean()) buf.readUtf(maxLength) else null
    }

    fun writeUtfList(buf: FriendlyByteBuf, values: List<String>) {
        buf.writeInt(values.size)
        for (value in values) {
            buf.writeUtf(value)
        }
    }

    fun readUtfList(buf: FriendlyByteBuf, maxEntries: Int, maxLength: Int): List<String> {
        val count = buf.readInt().coerceIn(0, maxEntries)
        val values = ArrayList<String>(count)
        repeat(count) {
            values.add(buf.readUtf(maxLength))
        }
        return values
    }
}