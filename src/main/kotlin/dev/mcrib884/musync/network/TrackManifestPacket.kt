package dev.mcrib884.musync.network

import net.minecraft.network.FriendlyByteBuf
//? if neoforge {
/*import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
//? if >=1.21.11 {
import net.minecraft.resources.Identifier as ResourceLocation
//?} else {
import net.minecraft.resources.ResourceLocation
//?}
import net.neoforged.neoforge.network.handling.IPayloadContext*/
//?} else if forge {
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier
//?}

data class TrackManifestEntry(
    val name: String,
    val size: Long,
    val sha256: String
)

data class TrackManifestPacket(
    val manifestVersion: Long,
    val tracks: List<TrackManifestEntry>
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
            for (entry in packet.tracks) {
                PacketIO.writeUtfBounded(buf, entry.name, PacketIO.MAX_TRACK_NAME_LENGTH)
                buf.writeLong(entry.size)
                PacketIO.writeUtfBounded(buf, entry.sha256, PacketIO.MAX_TRACK_HASH_LENGTH)
            }
        }

        fun decode(buf: FriendlyByteBuf): TrackManifestPacket {
            val manifestVersion = buf.readLong()
            val count = buf.readInt().coerceIn(0, PacketIO.MAX_MANIFEST_ENTRIES)
            val tracks = mutableListOf<TrackManifestEntry>()
            for (i in 0 until count) {
                val name = PacketIO.readUtfBounded(buf, PacketIO.MAX_TRACK_NAME_LENGTH)
                val size = buf.readLong().coerceAtLeast(0L)
                val sha256 = PacketIO.readUtfBounded(buf, PacketIO.MAX_TRACK_HASH_LENGTH)
                tracks.add(TrackManifestEntry(name, size, sha256))
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
