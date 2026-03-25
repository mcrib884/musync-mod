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

data class MusicClientInfoPacket(
    val action: Action,
    val trackId: String,
    val durationMs: Long,
    val resolvedName: String = ""
//? if neoforge {
/*) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE*/
//?} else {
) {
//?}
    enum class Action {
        REPORT_DURATION,
        TRACK_FINISHED,
        LOAD_FAILED
    }

    companion object {
        //? if neoforge {
        /*val TYPE = CustomPacketPayload.Type<MusicClientInfoPacket>(ResourceLocation.fromNamespaceAndPath("musync", "music_client_info"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, MusicClientInfoPacket> = StreamCodec.of(
            { buf, packet -> encode(packet, buf) }, ::decode
        )*/
        //?}

        fun encode(packet: MusicClientInfoPacket, buf: FriendlyByteBuf) {
            buf.writeEnum(packet.action)
            PacketIO.writeUtfBounded(buf, packet.trackId, PacketIO.MAX_TRACK_ID_LENGTH)
            buf.writeLong(packet.durationMs)
            PacketIO.writeUtfBounded(buf, packet.resolvedName, PacketIO.MAX_SOUND_ID_LENGTH)
        }

        fun decode(buf: FriendlyByteBuf): MusicClientInfoPacket {
            return MusicClientInfoPacket(
                action = buf.readEnum(Action::class.java),
                trackId = buf.readUtf(PacketIO.MAX_TRACK_ID_LENGTH),
                durationMs = buf.readLong(),
                resolvedName = buf.readUtf(PacketIO.MAX_SOUND_ID_LENGTH)
            )
        }

        //? if forge {
        fun handle(packet: MusicClientInfoPacket, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {
                val sender = ctx.get().sender
                if (sender != null) {
                    dev.mcrib884.musync.server.MusicManager.handleClientInfo(packet, sender)
                }
            }
            ctx.get().packetHandled = true
        }
        //?} else if neoforge {
        /*fun handleNeo(packet: MusicClientInfoPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val sender = ctx.player() as? net.minecraft.server.level.ServerPlayer
                if (sender != null) {
                    dev.mcrib884.musync.server.MusicManager.handleClientInfo(packet, sender)
                }
            }
        }*/
        //?}
    }
}
