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

data class TrackRequestPacket(
    val manifestVersion: Long,
    val trackNames: List<String>
//? if neoforge {
/*) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE*/
//?} else {
) {
//?}
    companion object {
        //? if neoforge {
        /*val TYPE = CustomPacketPayload.Type<TrackRequestPacket>(ResourceLocation.fromNamespaceAndPath("musync", "track_request"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, TrackRequestPacket> = StreamCodec.of(
            { buf, packet -> encode(packet, buf) }, ::decode
        )*/
        //?}

        fun encode(packet: TrackRequestPacket, buf: FriendlyByteBuf) {
            buf.writeLong(packet.manifestVersion)
            buf.writeInt(packet.trackNames.size)
            for (name in packet.trackNames) {
                PacketIO.writeUtfBounded(buf, name, PacketIO.MAX_TRACK_NAME_LENGTH)
            }
        }

        private const val MAX_TRACK_COUNT = PacketIO.MAX_MANIFEST_ENTRIES
        fun decode(buf: FriendlyByteBuf): TrackRequestPacket {
            val manifestVersion = buf.readLong()
            val count = buf.readInt().coerceIn(0, MAX_TRACK_COUNT)
            val names = mutableListOf<String>()
            for (i in 0 until count) {
                names.add(PacketIO.readUtfBounded(buf, PacketIO.MAX_TRACK_NAME_LENGTH))
            }
            return TrackRequestPacket(manifestVersion, names)
        }

        //? if forge {
        fun handle(packet: TrackRequestPacket, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {
                val sender = ctx.get().sender ?: return@enqueueWork
                dev.mcrib884.musync.server.MusicManager.handleTrackRequest(packet.manifestVersion, packet.trackNames, sender)
            }
            ctx.get().packetHandled = true
        }
        //?} else if neoforge {
        /*fun handleNeo(packet: TrackRequestPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val sender = ctx.player() as? net.minecraft.server.level.ServerPlayer ?: return@enqueueWork
                dev.mcrib884.musync.server.MusicManager.handleTrackRequest(packet.manifestVersion, packet.trackNames, sender)
            }
        }*/
        //?}
    }
}
