package dev.mcrib884.musync.network

import net.minecraft.network.FriendlyByteBuf
//? if neoforge {
/*import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext*/
//?} else if forge {
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier
//?}

data class TrackManifestPacket(
    val manifestVersion: Long,
    val tracks: List<Pair<String, Long>>
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
            buf.writeLong(packet.manifestVersion)
            buf.writeInt(packet.tracks.size)
            for ((name, size) in packet.tracks) {
                PacketIO.writeUtfBounded(buf, name, PacketIO.MAX_TRACK_NAME_LENGTH)
                buf.writeLong(size)
            }
        }

        fun decode(buf: FriendlyByteBuf): TrackManifestPacket {
            val manifestVersion = buf.readLong()
            val count = buf.readInt().coerceIn(0, PacketIO.MAX_MANIFEST_ENTRIES)
            val tracks = mutableListOf<Pair<String, Long>>()
            for (i in 0 until count) {
                val name = buf.readUtf(PacketIO.MAX_TRACK_NAME_LENGTH)
                val size = buf.readLong().coerceAtLeast(0L)
                tracks.add(name to size)
            }
            return TrackManifestPacket(manifestVersion, tracks)
        }

        //? if forge {
        fun handle(packet: TrackManifestPacket, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {
                dev.mcrib884.musync.client.ClientTrackManager.handleManifest(packet.manifestVersion, packet.tracks)
            }
            ctx.get().packetHandled = true
        }
        //?} else if neoforge {
        /*fun handleNeo(packet: TrackManifestPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                dev.mcrib884.musync.client.ClientTrackManager.handleManifest(packet.manifestVersion, packet.tracks)
            }
        }*/
        //?}
    }
}
