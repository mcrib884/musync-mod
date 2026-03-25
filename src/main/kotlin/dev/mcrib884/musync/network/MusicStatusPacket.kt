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

data class MusicStatusPacket(
    val currentTrack: String?,
    val currentPositionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val queue: List<String>,
    val mode: PlayMode,
    val repeatMode: RepeatMode = RepeatMode.OFF,
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

    enum class RepeatMode {
        OFF, REPEAT_TRACK, REPEAT_PLAYLIST, SHUFFLE, SHUFFLE_REPEAT
    }

    companion object {
        //? if neoforge {
        /*val TYPE = CustomPacketPayload.Type<MusicStatusPacket>(ResourceLocation.fromNamespaceAndPath("musync", "music_status"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, MusicStatusPacket> = StreamCodec.of(
            { buf, packet -> encode(packet, buf) }, ::decode
        )*/
        //?}

        fun encode(packet: MusicStatusPacket, buf: FriendlyByteBuf) {
            PacketIO.writeNullableUtf(buf, packet.currentTrack, PacketIO.MAX_TRACK_ID_LENGTH)
            buf.writeLong(packet.currentPositionMs)
            buf.writeLong(packet.durationMs)
            buf.writeBoolean(packet.isPlaying)
            PacketIO.writeUtfList(buf, packet.queue, PacketIO.MAX_TRACK_ID_LENGTH)
            buf.writeEnum(packet.mode)
            buf.writeEnum(packet.repeatMode)
            buf.writeBoolean(packet.priorityActive)
            PacketIO.writeUtfBounded(buf, packet.resolvedName, PacketIO.MAX_SOUND_ID_LENGTH)
            buf.writeBoolean(packet.waitingForNextTrack)
            buf.writeInt(packet.ticksSinceLastMusic)
            buf.writeInt(packet.nextMusicDelayTicks)
            buf.writeInt(packet.customMinDelay)
            buf.writeInt(packet.customMaxDelay)
            buf.writeBoolean(packet.syncOverworld)
            val clampedDimensions = packet.activeDimensions.take(PacketIO.MAX_DIMENSION_ENTRIES)
            buf.writeInt(clampedDimensions.size)
            for (dimension in clampedDimensions) {
                PacketIO.writeUtfBounded(buf, dimension.id, PacketIO.MAX_DIMENSION_ID_LENGTH)
                PacketIO.writeUtfList(buf, dimension.players, PacketIO.MAX_PLAYER_NAME_LENGTH)
                PacketIO.writeNullableUtf(buf, dimension.currentTrack, PacketIO.MAX_TRACK_ID_LENGTH)
                PacketIO.writeUtfBounded(buf, dimension.resolvedName, PacketIO.MAX_SOUND_ID_LENGTH)
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
                currentTrack = PacketIO.readNullableUtf(buf, PacketIO.MAX_TRACK_ID_LENGTH),
                currentPositionMs = buf.readLong(),
                durationMs = buf.readLong(),
                isPlaying = buf.readBoolean(),
                queue = PacketIO.readUtfList(buf, PacketIO.MAX_QUEUE_ENTRIES, PacketIO.MAX_TRACK_ID_LENGTH),
                mode = buf.readEnum(PlayMode::class.java),
                repeatMode = buf.readEnum(RepeatMode::class.java),
                priorityActive = buf.readBoolean(),
                resolvedName = buf.readUtf(PacketIO.MAX_SOUND_ID_LENGTH),
                waitingForNextTrack = buf.readBoolean(),
                ticksSinceLastMusic = buf.readInt(),
                nextMusicDelayTicks = buf.readInt(),
                customMinDelay = buf.readInt(),
                customMaxDelay = buf.readInt(),
                syncOverworld = buf.readBoolean(),
                activeDimensions = buildList {
                    repeat(buf.readInt().coerceIn(0, PacketIO.MAX_DIMENSION_ENTRIES)) {
                        add(
                            DimensionStatus(
                                id = buf.readUtf(PacketIO.MAX_DIMENSION_ID_LENGTH),
                                players = PacketIO.readUtfList(buf, PacketIO.MAX_PLAYERS_PER_DIMENSION, PacketIO.MAX_PLAYER_NAME_LENGTH),
                                currentTrack = PacketIO.readNullableUtf(buf, PacketIO.MAX_TRACK_ID_LENGTH),
                                resolvedName = buf.readUtf(PacketIO.MAX_SOUND_ID_LENGTH),
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

        //? if forge {
        fun handle(packet: MusicStatusPacket, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {

                dev.mcrib884.musync.client.ClientMusicPlayer.updateStatus(packet)
            }
            ctx.get().packetHandled = true
        }
        //?} else if neoforge {
        /*fun handleNeo(packet: MusicStatusPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                dev.mcrib884.musync.client.ClientMusicPlayer.updateStatus(packet)
            }
        }*/
        //?}
    }
}
