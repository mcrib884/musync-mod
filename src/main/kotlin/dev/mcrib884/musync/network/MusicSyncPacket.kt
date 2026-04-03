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

data class MusicSyncPacket(
    val trackId: String,
    val startPositionMs: Long,
    val serverTimeMs: Long,
    val action: Action,
    val specificSound: String = ""
//? if neoforge {
/*) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE*/
//?} else {
) {
//?}
    enum class Action {
        PLAY, STOP, PAUSE, RESUME, SKIP, OPEN_GUI, SEEK
    }

    companion object {
        //? if neoforge {
        /*val TYPE = CustomPacketPayload.Type<MusicSyncPacket>(ResourceLocation.fromNamespaceAndPath("musync", "music_sync"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, MusicSyncPacket> = StreamCodec.of(
            { buf, packet -> encode(packet, buf) }, ::decode
        )*/
        //?}

        fun encode(packet: MusicSyncPacket, buf: FriendlyByteBuf) {
            PacketIO.writeUtfBounded(buf, packet.trackId, PacketIO.MAX_TRACK_ID_LENGTH)
            buf.writeLong(packet.startPositionMs)
            buf.writeLong(packet.serverTimeMs)
            buf.writeEnum(packet.action)
            PacketIO.writeUtfBounded(buf, packet.specificSound, PacketIO.MAX_SOUND_ID_LENGTH)
        }

        fun decode(buf: FriendlyByteBuf): MusicSyncPacket {
            return MusicSyncPacket(
                trackId = PacketIO.readUtfBounded(buf, PacketIO.MAX_TRACK_ID_LENGTH),
                startPositionMs = buf.readLong(),
                serverTimeMs = buf.readLong(),
                action = buf.readEnum(Action::class.java),
                specificSound = PacketIO.readUtfBounded(buf, PacketIO.MAX_SOUND_ID_LENGTH)
            )
        }

        //? if forge {
        fun handle(packet: MusicSyncPacket, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {

                if (packet.action == Action.OPEN_GUI) {
                    val mc = net.minecraft.client.Minecraft.getInstance()
                    mc.execute { mc.setScreen(dev.mcrib884.musync.client.MusicControlScreen()) }
                } else {
                    dev.mcrib884.musync.client.ClientMusicPlayer.handleSyncPacket(packet)
                }
            }
            ctx.get().packetHandled = true
        }
        //?} else if neoforge {
        /*fun handleNeo(packet: MusicSyncPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                if (packet.action == Action.OPEN_GUI) {
                    val mc = net.minecraft.client.Minecraft.getInstance()
                    mc.execute { mc.setScreen(dev.mcrib884.musync.client.MusicControlScreen()) }
                } else {
                    dev.mcrib884.musync.client.ClientMusicPlayer.handleSyncPacket(packet)
                }
            }
        }*/
        //?}
    }
}
