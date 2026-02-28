package dev.mcrib884.musync.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

data class MusicSyncPacket(
    val trackId: String,
    val startPositionMs: Long,
    val serverTimeMs: Long,
    val action: Action,
    val specificSound: String = ""
) {
    enum class Action {
        PLAY, STOP, PAUSE, RESUME, SKIP, OPEN_GUI, SYNC_CHECK
    }

    companion object {
        fun encode(packet: MusicSyncPacket, buf: FriendlyByteBuf) {
            buf.writeUtf(packet.trackId)
            buf.writeLong(packet.startPositionMs)
            buf.writeLong(packet.serverTimeMs)
            buf.writeEnum(packet.action)
            buf.writeUtf(packet.specificSound)
        }

        fun decode(buf: FriendlyByteBuf): MusicSyncPacket {
            return MusicSyncPacket(
                trackId = buf.readUtf(),
                startPositionMs = buf.readLong(),
                serverTimeMs = buf.readLong(),
                action = buf.readEnum(Action::class.java),
                specificSound = buf.readUtf()
            )
        }

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
    }
}
