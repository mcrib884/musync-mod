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

data class MusicStatusPacket(
    val currentTrack: String?,
    val currentPositionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val queue: List<String>,
    val mode: PlayMode,
    val priorityActive: Boolean = false,
    val resolvedName: String = "",
    val waitingForNextTrack: Boolean = false,
    val ticksSinceLastMusic: Int = 0,
    val nextMusicDelayTicks: Int = 0,
    val customMinDelay: Int = -1,
    val customMaxDelay: Int = -1,
    val syncOverworld: Boolean = false,
    val activeDimensions: List<DimensionStatus> = emptyList()
//? if neoforge {
/*) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE*/
//?} else {
) {
//?}
    data class DimensionStatus(
        val id: String,
        val players: List<String>,
        val currentTrack: String? = null,
        val resolvedName: String = "",
        val isPlaying: Boolean = false,
        val currentPositionMs: Long = 0,
        val durationMs: Long = 0,
        val waitingForNextTrack: Boolean = false,
        val ticksSinceLastMusic: Int = 0,
        val nextMusicDelayTicks: Int = 0
    )

    enum class PlayMode {
        AUTONOMOUS, PLAYLIST, SINGLE_TRACK
    }

    companion object {
        //? if neoforge {
        /*val TYPE = CustomPacketPayload.Type<MusicStatusPacket>(ResourceLocation.fromNamespaceAndPath("musync", "music_status"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, MusicStatusPacket> = StreamCodec.of(
            { buf, packet -> encode(packet, buf) }, ::decode
        )*/
        //?}

        fun encode(packet: MusicStatusPacket, buf: FriendlyByteBuf) {
            buf.writeNullable(packet.currentTrack, FriendlyByteBuf::writeUtf)
            buf.writeLong(packet.currentPositionMs)
            buf.writeLong(packet.durationMs)
            buf.writeBoolean(packet.isPlaying)
            buf.writeCollection(packet.queue, FriendlyByteBuf::writeUtf)
            buf.writeEnum(packet.mode)
            buf.writeBoolean(packet.priorityActive)
            buf.writeUtf(packet.resolvedName)
            buf.writeBoolean(packet.waitingForNextTrack)
            buf.writeInt(packet.ticksSinceLastMusic)
            buf.writeInt(packet.nextMusicDelayTicks)
            buf.writeInt(packet.customMinDelay)
            buf.writeInt(packet.customMaxDelay)
            buf.writeBoolean(packet.syncOverworld)
            buf.writeInt(packet.activeDimensions.size)
            for (dimension in packet.activeDimensions) {
                buf.writeUtf(dimension.id)
                buf.writeCollection(dimension.players, FriendlyByteBuf::writeUtf)
                buf.writeNullable(dimension.currentTrack, FriendlyByteBuf::writeUtf)
                buf.writeUtf(dimension.resolvedName)
                buf.writeBoolean(dimension.isPlaying)
                buf.writeLong(dimension.currentPositionMs)
                buf.writeLong(dimension.durationMs)
                buf.writeBoolean(dimension.waitingForNextTrack)
                buf.writeInt(dimension.ticksSinceLastMusic)
                buf.writeInt(dimension.nextMusicDelayTicks)
            }
        }

        fun decode(buf: FriendlyByteBuf): MusicStatusPacket {
            return MusicStatusPacket(
                currentTrack = buf.readNullable(FriendlyByteBuf::readUtf),
                currentPositionMs = buf.readLong(),
                durationMs = buf.readLong(),
                isPlaying = buf.readBoolean(),
                queue = buf.readList(FriendlyByteBuf::readUtf),
                mode = buf.readEnum(PlayMode::class.java),
                priorityActive = buf.readBoolean(),
                resolvedName = buf.readUtf(),
                waitingForNextTrack = buf.readBoolean(),
                ticksSinceLastMusic = buf.readInt(),
                nextMusicDelayTicks = buf.readInt(),
                customMinDelay = buf.readInt(),
                customMaxDelay = buf.readInt(),
                syncOverworld = buf.readBoolean(),
                activeDimensions = buildList {
                    repeat(buf.readInt()) {
                        add(
                            DimensionStatus(
                                id = buf.readUtf(),
                                players = buf.readList(FriendlyByteBuf::readUtf),
                                currentTrack = buf.readNullable(FriendlyByteBuf::readUtf),
                                resolvedName = buf.readUtf(),
                                isPlaying = buf.readBoolean(),
                                currentPositionMs = buf.readLong(),
                                durationMs = buf.readLong(),
                                waitingForNextTrack = buf.readBoolean(),
                                ticksSinceLastMusic = buf.readInt(),
                                nextMusicDelayTicks = buf.readInt()
                            )
                        )
                    }
                }
            )
        }

        //? if neoforge {
        /*fun handleNeo(packet: MusicStatusPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                dev.mcrib884.musync.client.ClientMusicPlayer.updateStatus(packet)
            }
        }*/
        //?} else {
        fun handle(packet: MusicStatusPacket, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {

                dev.mcrib884.musync.client.ClientMusicPlayer.updateStatus(packet)
            }
            ctx.get().packetHandled = true
        }
        //?}
    }
}
