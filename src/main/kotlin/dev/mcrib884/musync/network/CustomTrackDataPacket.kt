package dev.mcrib884.musync.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

data class CustomTrackDataPacket(
    val trackName: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val data: ByteArray
) {
    companion object {
        const val CHUNK_SIZE = 256 * 1024

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

        fun handle(packet: CustomTrackDataPacket, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {
                dev.mcrib884.musync.client.CustomTrackCache.handleChunk(
                    packet.trackName, packet.chunkIndex, packet.totalChunks, packet.data
                )
            }
            ctx.get().packetHandled = true
        }
    }
}
