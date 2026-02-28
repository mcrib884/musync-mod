package dev.mcrib884.musync.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

data class MusicControlPacket(
    val action: Action,
    val trackId: String?,
    val queuePosition: Int?
) {
    enum class Action {
        PLAY_TRACK, STOP, SKIP, PAUSE, RESUME, REQUEST_SYNC, ADD_TO_QUEUE, REMOVE_FROM_QUEUE, SET_DELAY, SEEK, TOGGLE_NETHER_SYNC, FORCE_SYNC_ALL
    }

    companion object {
        fun encode(packet: MusicControlPacket, buf: FriendlyByteBuf) {
            buf.writeEnum(packet.action)
            buf.writeNullable(packet.trackId, FriendlyByteBuf::writeUtf)
            buf.writeNullable(packet.queuePosition, FriendlyByteBuf::writeInt)
        }

        fun decode(buf: FriendlyByteBuf): MusicControlPacket {
            return MusicControlPacket(
                action = buf.readEnum(Action::class.java),
                trackId = buf.readNullable(FriendlyByteBuf::readUtf),
                queuePosition = buf.readNullable(FriendlyByteBuf::readInt)
            )
        }

        fun handle(packet: MusicControlPacket, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {

                val sender = ctx.get().sender
                if (sender != null) {
                    dev.mcrib884.musync.server.MusicManager.handleControlPacket(packet, sender)
                }
            }
            ctx.get().packetHandled = true
        }
    }
}
