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

data class MusicControlPacket(
    val action: Action,
    val trackId: String?,
    val queuePosition: Int?,
    val seekMs: Long = 0,
    val targetDim: String? = null
//? if neoforge {
/*) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE*/
//?} else {
) {
//?}
    enum class Action {
        PLAY_TRACK, STOP, SKIP, PAUSE, RESUME, REQUEST_SYNC, ADD_TO_QUEUE, REMOVE_FROM_QUEUE, CLEAR_QUEUE, SET_DELAY, SEEK, TOGGLE_NETHER_SYNC, FORCE_SYNC_ALL
    }

    companion object {
        //? if neoforge {
        /*val TYPE = CustomPacketPayload.Type<MusicControlPacket>(ResourceLocation.fromNamespaceAndPath("musync", "music_control"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, MusicControlPacket> = StreamCodec.of(
            { buf, packet -> encode(packet, buf) }, ::decode
        )*/
        //?}

        fun encode(packet: MusicControlPacket, buf: FriendlyByteBuf) {
            buf.writeEnum(packet.action)
            buf.writeNullable(packet.trackId, FriendlyByteBuf::writeUtf)
            buf.writeNullable(packet.queuePosition, FriendlyByteBuf::writeInt)
            buf.writeLong(packet.seekMs)
            buf.writeNullable(packet.targetDim, FriendlyByteBuf::writeUtf)
        }

        fun decode(buf: FriendlyByteBuf): MusicControlPacket {
            return MusicControlPacket(
                action = buf.readEnum(Action::class.java),
                trackId = buf.readNullable(FriendlyByteBuf::readUtf),
                queuePosition = buf.readNullable(FriendlyByteBuf::readInt),
                seekMs = buf.readLong(),
                targetDim = buf.readNullable(FriendlyByteBuf::readUtf)
            )
        }

        //? if neoforge {
        /*fun handleNeo(packet: MusicControlPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val sender = ctx.player() as? net.minecraft.server.level.ServerPlayer
                if (sender != null) {
                    dev.mcrib884.musync.server.MusicManager.handleControlPacket(packet, sender)
                }
            }
        }*/
        //?} else {
        fun handle(packet: MusicControlPacket, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {

                val sender = ctx.get().sender
                if (sender != null) {
                    dev.mcrib884.musync.server.MusicManager.handleControlPacket(packet, sender)
                }
            }
            ctx.get().packetHandled = true
        }
        //?}
    }
}
