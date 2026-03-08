package dev.mcrib884.musync.network

import net.minecraft.network.FriendlyByteBuf
//? if neoforge {
/*import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext*/
//?} else {
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier
//?}

data class TrackManifestPacket(
    val tracks: List<Pair<String, Int>>
//? if neoforge {
/*) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE*/
//?} else {
) {
//?}
    companion object {
        //? if neoforge {
        /*val TYPE = CustomPacketPayload.Type<TrackManifestPacket>(ResourceLocation.fromNamespaceAndPath("musync", "track_manifest"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, TrackManifestPacket> = StreamCodec.of(
            { buf, packet -> encode(packet, buf) }, ::decode
        )*/
        //?}

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

        //? if neoforge {
        /*fun handleNeo(packet: TrackManifestPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                dev.mcrib884.musync.client.ClientTrackManager.handleManifest(packet.tracks)
            }
        }*/
        //?} else {
        fun handle(packet: TrackManifestPacket, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {
                dev.mcrib884.musync.client.ClientTrackManager.handleManifest(packet.tracks)
            }
            ctx.get().packetHandled = true
        }
        //?}
    }
}
