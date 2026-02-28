package dev.mcrib884.musync.network

import dev.mcrib884.musync.MOD_ID
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.simple.SimpleChannel

object PacketHandler {
    private const val PROTOCOL_VERSION = "1"

    val INSTANCE: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation(MOD_ID, "main"),
        { PROTOCOL_VERSION },
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    )

    private var packetId = 0

    fun register() {

        INSTANCE.registerMessage(
            packetId++,
            MusicSyncPacket::class.java,
            MusicSyncPacket::encode,
            MusicSyncPacket::decode,
            MusicSyncPacket::handle
        )

        INSTANCE.registerMessage(
            packetId++,
            MusicControlPacket::class.java,
            MusicControlPacket::encode,
            MusicControlPacket::decode,
            MusicControlPacket::handle
        )

        INSTANCE.registerMessage(
            packetId++,
            MusicStatusPacket::class.java,
            MusicStatusPacket::encode,
            MusicStatusPacket::decode,
            MusicStatusPacket::handle
        )

        INSTANCE.registerMessage(
            packetId++,
            MusicClientInfoPacket::class.java,
            MusicClientInfoPacket::encode,
            MusicClientInfoPacket::decode,
            MusicClientInfoPacket::handle
        )

        INSTANCE.registerMessage(
            packetId++,
            CustomTrackDataPacket::class.java,
            CustomTrackDataPacket::encode,
            CustomTrackDataPacket::decode,
            CustomTrackDataPacket::handle
        )

        INSTANCE.registerMessage(
            packetId++,
            TrackManifestPacket::class.java,
            TrackManifestPacket::encode,
            TrackManifestPacket::decode,
            TrackManifestPacket::handle
        )

        INSTANCE.registerMessage(
            packetId++,
            TrackRequestPacket::class.java,
            TrackRequestPacket::encode,
            TrackRequestPacket::decode,
            TrackRequestPacket::handle
        )
    }
}
