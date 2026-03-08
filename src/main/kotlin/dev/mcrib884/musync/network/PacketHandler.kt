package dev.mcrib884.musync.network

import dev.mcrib884.musync.MOD_ID
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
//? if neoforge {
/*import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.minecraft.network.protocol.common.custom.CustomPacketPayload*/
//?} else {
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.simple.SimpleChannel
//?}

object PacketHandler {
    private const val PROTOCOL_VERSION = "1"

    //? if forge {
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
    //?}

    //? if neoforge {
    /*fun registerNeoForge(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(PROTOCOL_VERSION)
        registrar.playToClient(MusicSyncPacket.TYPE, MusicSyncPacket.STREAM_CODEC, MusicSyncPacket::handleNeo)
        registrar.playToServer(MusicControlPacket.TYPE, MusicControlPacket.STREAM_CODEC, MusicControlPacket::handleNeo)
        registrar.playToClient(MusicStatusPacket.TYPE, MusicStatusPacket.STREAM_CODEC, MusicStatusPacket::handleNeo)
        registrar.playToServer(MusicClientInfoPacket.TYPE, MusicClientInfoPacket.STREAM_CODEC, MusicClientInfoPacket::handleNeo)
        registrar.playToClient(CustomTrackDataPacket.TYPE, CustomTrackDataPacket.STREAM_CODEC, CustomTrackDataPacket::handleNeo)
        registrar.playToClient(TrackManifestPacket.TYPE, TrackManifestPacket.STREAM_CODEC, TrackManifestPacket::handleNeo)
        registrar.playToServer(TrackRequestPacket.TYPE, TrackRequestPacket.STREAM_CODEC, TrackRequestPacket::handleNeo)
    }*/
    //?}

    fun sendToPlayer(player: ServerPlayer, packet: Any) {
        //? if neoforge {
        /*PacketDistributor.sendToPlayer(player, packet as CustomPacketPayload)*/
        //?} else {
        INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with { player }, packet)
        //?}
    }

    fun sendToServer(packet: Any) {
        //? if neoforge {
        /*PacketDistributor.sendToServer(packet as CustomPacketPayload)*/
        //?} else {
        INSTANCE.send(net.minecraftforge.network.PacketDistributor.SERVER.noArg(), packet)
        //?}
    }
}
