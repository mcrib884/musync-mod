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

        fun decode(buf: FriendlyByteBuf): TrackRequestPacket {
            val count = buf.readInt()
            val names = mutableListOf<String>()
            for (i in 0 until count) {
                names.add(buf.readUtf())
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
