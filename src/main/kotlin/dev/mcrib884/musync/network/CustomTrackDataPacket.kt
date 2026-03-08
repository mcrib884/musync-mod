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

        //? if neoforge {
        /*val TYPE = CustomPacketPayload.Type<CustomTrackDataPacket>(ResourceLocation.fromNamespaceAndPath("musync", "custom_track_data"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, CustomTrackDataPacket> = StreamCodec.of(
            { buf, packet -> encode(packet, buf) }, ::decode
        )*/
        //?}

        fun encode(packet: CustomTrackDataPacket, buf: FriendlyByteBuf) {
            buf.writeUtf(packet.trackName)
            buf.writeInt(packet.chunkIndex)
            buf.writeInt(packet.totalChunks)
            buf.writeByteArray(packet.data)
        }

        fun decode(buf: FriendlyByteBuf): CustomTrackDataPacket {
            return CustomTrackDataPacket(
                trackName = buf.readUtf(),
                chunkIndex = buf.readInt(),
                totalChunks = buf.readInt(),
                data = buf.readByteArray()
            )
        }

        //? if neoforge {
        /*fun handleNeo(packet: CustomTrackDataPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                dev.mcrib884.musync.client.CustomTrackCache.handleChunk(
                    packet.trackName, packet.chunkIndex, packet.totalChunks, packet.data
                )
            }
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
    }
}
