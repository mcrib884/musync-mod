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

data class TrackRequestPacket(
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
            buf.writeInt(packet.trackNames.size)
            for (name in packet.trackNames) {
                buf.writeUtf(name)
            }
        }

        private const val MAX_TRACK_COUNT = 100
        fun decode(buf: FriendlyByteBuf): TrackRequestPacket {
            val count = buf.readInt().coerceIn(0, MAX_TRACK_COUNT)
            val names = mutableListOf<String>()
            for (i in 0 until count) {
                names.add(buf.readUtf(PacketIO.MAX_TRACK_NAME_LENGTH))
            }
            return TrackRequestPacket(names)
        }

        //? if neoforge {
        /*fun handleNeo(packet: TrackRequestPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val sender = ctx.player() as? net.minecraft.server.level.ServerPlayer ?: return@enqueueWork
                dev.mcrib884.musync.server.MusicManager.handleTrackRequest(packet.trackNames, sender)
            }
        }*/
        //?} else {
        fun handle(packet: TrackRequestPacket, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {
                val sender = ctx.get().sender ?: return@enqueueWork
                dev.mcrib884.musync.server.MusicManager.handleTrackRequest(packet.trackNames, sender)
            }
            ctx.get().packetHandled = true
        }
        //?}
    }
}
