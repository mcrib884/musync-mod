package dev.mcrib884.musync.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

data class MusicClientInfoPacket(
    val action: Action,
    val trackId: String,
    val durationMs: Long,
    val resolvedName: String = ""
) {
    enum class Action {
        REPORT_DURATION,
        TRACK_FINISHED
    }

    companion object {
        fun encode(packet: MusicClientInfoPacket, buf: FriendlyByteBuf) {
            buf.writeEnum(packet.action)
            buf.writeUtf(packet.trackId)
            buf.writeLong(packet.durationMs)
            buf.writeUtf(packet.resolvedName)
        }

        fun decode(buf: FriendlyByteBuf): MusicClientInfoPacket {
            return MusicClientInfoPacket(
                action = buf.readEnum(Action::class.java),
                trackId = buf.readUtf(),
                durationMs = buf.readLong(),
                resolvedName = buf.readUtf()
            )
        }

        fun handle(packet: MusicClientInfoPacket, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {
                val sender = ctx.get().sender
                if (sender != null) {
                    dev.mcrib884.musync.server.MusicManager.handleClientInfo(packet, sender)
                }
            }
            ctx.get().packetHandled = true
        }
    }
}
