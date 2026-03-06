package dev.mcrib884.musync.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

data class TrackRequestPacket(
    val trackNames: List<String>
) {
    companion object {
        fun encode(packet: TrackRequestPacket, buf: FriendlyByteBuf) {
            buf.writeInt(packet.trackNames.size)
            for (name in packet.trackNames) {
                buf.writeUtf(name)
            }
        }

        private const val MAX_TRACK_COUNT = 100
        private const val MAX_NAME_LENGTH = 256

        fun decode(buf: FriendlyByteBuf): TrackRequestPacket {
            val count = buf.readInt().coerceIn(0, MAX_TRACK_COUNT)
            val names = mutableListOf<String>()
            for (i in 0 until count) {
                val name = buf.readUtf(MAX_NAME_LENGTH)
                if (name.length <= MAX_NAME_LENGTH) names.add(name)
            }
            return TrackRequestPacket(names)
        }

        fun handle(packet: TrackRequestPacket, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {
                val sender = ctx.get().sender ?: return@enqueueWork
                dev.mcrib884.musync.server.MusicManager.handleTrackRequest(packet.trackNames, sender)
            }
            ctx.get().packetHandled = true
        }
    }
}
