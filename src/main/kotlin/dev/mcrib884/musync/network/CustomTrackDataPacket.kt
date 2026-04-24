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
//? if >=1.20.2 {
/*import net.minecraftforge.event.network.CustomPayloadEvent*/
//?} else {
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier
//?}
//?}

data class CustomTrackDataPacket(
    val trackName: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val data: ByteArray
//? if neoforge {
/*) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE*/
//?} else {
) {
//?}
    companion object {
        const val CHUNK_SIZE = 32 * 1024
        private const val MAX_TOTAL_CHUNKS = Int.MAX_VALUE

        //? if neoforge {
        /*val TYPE = CustomPacketPayload.Type<CustomTrackDataPacket>(ResourceLocation.fromNamespaceAndPath("musync", "custom_track_data"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, CustomTrackDataPacket> = StreamCodec.of(
            { buf, packet -> encode(packet, buf) }, ::decode
        )*/
        //?}

        fun encode(packet: CustomTrackDataPacket, buf: FriendlyByteBuf) {
            require(packet.data.size <= CHUNK_SIZE) { "Chunk exceeds $CHUNK_SIZE bytes: ${packet.data.size}" }
            PacketIO.writeUtfBounded(buf, packet.trackName, PacketIO.MAX_TRACK_NAME_LENGTH)
            buf.writeInt(packet.chunkIndex)
            buf.writeInt(packet.totalChunks)
            buf.writeByteArray(packet.data)
        }

        fun decode(buf: FriendlyByteBuf): CustomTrackDataPacket {
            val trackName = PacketIO.readUtfBounded(buf, PacketIO.MAX_TRACK_NAME_LENGTH)
            val chunkIndex = buf.readInt().coerceAtLeast(0)
            val totalChunks = buf.readInt().coerceIn(0, MAX_TOTAL_CHUNKS)
            return CustomTrackDataPacket(
                trackName = trackName,
                chunkIndex = chunkIndex,
                totalChunks = totalChunks,
                data = buf.readByteArray(CHUNK_SIZE)
            )
        }

        //? if forge {
        //? if >=1.20.2 {
        /*fun handle(packet: CustomTrackDataPacket, ctx: net.minecraftforge.event.network.CustomPayloadEvent.Context) {
            ctx.enqueueWork {
                dev.mcrib884.musync.client.CustomTrackCache.handleChunk(
                    packet.trackName, packet.chunkIndex, packet.totalChunks, packet.data
                )
            }
            ctx.setPacketHandled(true)
        }*/
        //?} else {
        fun handle(packet: CustomTrackDataPacket, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {
                dev.mcrib884.musync.client.CustomTrackCache.handleChunk(
                    packet.trackName, packet.chunkIndex, packet.totalChunks, packet.data
                )
            }
            ctx.get().packetHandled = true
        }
        //?}
        //?} else if neoforge {
        /*fun handleNeo(packet: CustomTrackDataPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                dev.mcrib884.musync.client.CustomTrackCache.handleChunk(
                    packet.trackName, packet.chunkIndex, packet.totalChunks, packet.data
                )
            }
        }*/
        //?}
    }
}

