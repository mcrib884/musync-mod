package dev.mcrib884.musync.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

data class TrackManifestPacket(
    val tracks: List<Pair<String, Int>>
) {
    companion object {
        fun encode(packet: TrackManifestPacket, buf: FriendlyByteBuf) {
            buf.writeInt(packet.tracks.size)
            for ((name, size) in packet.tracks) {
                buf.writeUtf(name)
                buf.writeInt(size)
            }
        }

        fun decode(buf: FriendlyByteBuf): TrackManifestPacket {
            val count = buf.readInt()
            val tracks = mutableListOf<Pair<String, Int>>()
            for (i in 0 until count) {
                val name = buf.readUtf()
                val size = buf.readInt()
                tracks.add(name to size)
            }
            return TrackManifestPacket(tracks)
        }

        fun handle(packet: TrackManifestPacket, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {
                dev.mcrib884.musync.client.ClientTrackManager.handleManifest(packet.tracks)
            }
            ctx.get().packetHandled = true
        }
    }
}
