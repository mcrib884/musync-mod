package dev.mcrib884.musync.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

data class MusicStatusPacket(
    val currentTrack: String?,
    val currentPositionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val queue: List<String>,
    val mode: PlayMode,
    val resolvedName: String = "",
    val waitingForNextTrack: Boolean = false,
    val ticksSinceLastMusic: Int = 0,
    val nextMusicDelayTicks: Int = 0,
    val customMinDelay: Int = -1,
    val customMaxDelay: Int = -1,
    val syncOverworld: Boolean = false
) {
    enum class PlayMode {
        AUTONOMOUS, PLAYLIST, SINGLE_TRACK
    }

    companion object {
        fun encode(packet: MusicStatusPacket, buf: FriendlyByteBuf) {
            buf.writeNullable(packet.currentTrack, FriendlyByteBuf::writeUtf)
            buf.writeLong(packet.currentPositionMs)
            buf.writeLong(packet.durationMs)
            buf.writeBoolean(packet.isPlaying)
            buf.writeCollection(packet.queue, FriendlyByteBuf::writeUtf)
            buf.writeEnum(packet.mode)
            buf.writeUtf(packet.resolvedName)
            buf.writeBoolean(packet.waitingForNextTrack)
            buf.writeInt(packet.ticksSinceLastMusic)
            buf.writeInt(packet.nextMusicDelayTicks)
            buf.writeInt(packet.customMinDelay)
            buf.writeInt(packet.customMaxDelay)
            buf.writeBoolean(packet.syncOverworld)
        }

        fun decode(buf: FriendlyByteBuf): MusicStatusPacket {
            return MusicStatusPacket(
                currentTrack = buf.readNullable(FriendlyByteBuf::readUtf),
                currentPositionMs = buf.readLong(),
                durationMs = buf.readLong(),
                isPlaying = buf.readBoolean(),
                queue = buf.readList(FriendlyByteBuf::readUtf),
                mode = buf.readEnum(PlayMode::class.java),
                resolvedName = buf.readUtf(),
                waitingForNextTrack = buf.readBoolean(),
                ticksSinceLastMusic = buf.readInt(),
                nextMusicDelayTicks = buf.readInt(),
                customMinDelay = buf.readInt(),
                customMaxDelay = buf.readInt(),
                syncOverworld = buf.readBoolean()
            )
        }

        fun handle(packet: MusicStatusPacket, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {

                dev.mcrib884.musync.client.ClientMusicPlayer.updateStatus(packet)
            }
            ctx.get().packetHandled = true
        }
    }
}
