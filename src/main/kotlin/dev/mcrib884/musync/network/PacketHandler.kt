package dev.mcrib884.musync.network

import dev.mcrib884.musync.MOD_ID
import dev.mcrib884.musync.playerServer
//? if fabric {
/*import dev.mcrib884.musync.client.ClientMusicPlayer
import dev.mcrib884.musync.client.ClientTrackManager
import dev.mcrib884.musync.client.MusicControlScreen
import dev.mcrib884.musync.client.CustomTrackCache
import dev.mcrib884.musync.resLoc
import dev.mcrib884.musync.server.MusicManager
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
//? if <1.21 {
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
//?}
//? if >=1.21 {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
//?}
import net.minecraft.client.Minecraft*/
//?}
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier as ResourceLocation*/
//?} else {
import net.minecraft.resources.ResourceLocation
//?}
import net.minecraft.server.level.ServerPlayer
//? if neoforge {
/*import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.minecraft.network.protocol.common.custom.CustomPacketPayload*/
//?} else if forge {
//? if >=1.20.2 {
/*import net.minecraftforge.network.ChannelBuilder
import net.minecraftforge.network.SimpleChannel*/
//?} else {
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.simple.SimpleChannel
//?}
//?}

object PacketHandler {
    private const val PROTOCOL_VERSION = "1"

    //? if fabric {
    /*private val MUSIC_SYNC_ID = resLoc(MOD_ID, "music_sync")
    private val MUSIC_CONTROL_ID = resLoc(MOD_ID, "music_control")
    private val MUSIC_STATUS_ID = resLoc(MOD_ID, "music_status")
    private val MUSIC_CLIENT_INFO_ID = resLoc(MOD_ID, "music_client_info")
    private val CUSTOM_TRACK_DATA_ID = resLoc(MOD_ID, "custom_track_data")
    private val TRACK_MANIFEST_ID = resLoc(MOD_ID, "track_manifest")
    private val TRACK_REQUEST_ID = resLoc(MOD_ID, "track_request")

    //? if >=1.21 {
    private data class MusicSyncPayload(val packet: MusicSyncPacket) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            val TYPE = CustomPacketPayload.Type<MusicSyncPayload>(MUSIC_SYNC_ID)
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, MusicSyncPayload> = StreamCodec.of(
                { buf, payload -> MusicSyncPacket.encode(payload.packet, buf) },
                { buf -> MusicSyncPayload(MusicSyncPacket.decode(buf)) }
            )
        }
    }

    private data class MusicControlPayload(val packet: MusicControlPacket) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            val TYPE = CustomPacketPayload.Type<MusicControlPayload>(MUSIC_CONTROL_ID)
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, MusicControlPayload> = StreamCodec.of(
                { buf, payload -> MusicControlPacket.encode(payload.packet, buf) },
                { buf -> MusicControlPayload(MusicControlPacket.decode(buf)) }
            )
        }
    }

    private data class MusicStatusPayload(val packet: MusicStatusPacket) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            val TYPE = CustomPacketPayload.Type<MusicStatusPayload>(MUSIC_STATUS_ID)
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, MusicStatusPayload> = StreamCodec.of(
                { buf, payload -> MusicStatusPacket.encode(payload.packet, buf) },
                { buf -> MusicStatusPayload(MusicStatusPacket.decode(buf)) }
            )
        }
    }

    private data class MusicClientInfoPayload(val packet: MusicClientInfoPacket) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            val TYPE = CustomPacketPayload.Type<MusicClientInfoPayload>(MUSIC_CLIENT_INFO_ID)
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, MusicClientInfoPayload> = StreamCodec.of(
                { buf, payload -> MusicClientInfoPacket.encode(payload.packet, buf) },
                { buf -> MusicClientInfoPayload(MusicClientInfoPacket.decode(buf)) }
            )
        }
    }

    private data class CustomTrackDataPayload(val packet: CustomTrackDataPacket) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            val TYPE = CustomPacketPayload.Type<CustomTrackDataPayload>(CUSTOM_TRACK_DATA_ID)
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, CustomTrackDataPayload> = StreamCodec.of(
                { buf, payload -> CustomTrackDataPacket.encode(payload.packet, buf) },
                { buf -> CustomTrackDataPayload(CustomTrackDataPacket.decode(buf)) }
            )
        }
    }

    private data class TrackManifestPayload(val packet: TrackManifestPacket) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            val TYPE = CustomPacketPayload.Type<TrackManifestPayload>(TRACK_MANIFEST_ID)
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, TrackManifestPayload> = StreamCodec.of(
                { buf, payload -> TrackManifestPacket.encode(payload.packet, buf) },
                { buf -> TrackManifestPayload(TrackManifestPacket.decode(buf)) }
            )
        }
    }

    private data class TrackRequestPayload(val packet: TrackRequestPacket) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            val TYPE = CustomPacketPayload.Type<TrackRequestPayload>(TRACK_REQUEST_ID)
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, TrackRequestPayload> = StreamCodec.of(
                { buf, payload -> TrackRequestPacket.encode(payload.packet, buf) },
                { buf -> TrackRequestPayload(TrackRequestPacket.decode(buf)) }
            )
        }
    }

    private var fabricPayloadTypesRegistered = false

    private fun ensureFabricPayloadTypesRegistered() {
        if (fabricPayloadTypesRegistered) return
        PayloadTypeRegistry.playC2S().register(MusicControlPayload.TYPE, MusicControlPayload.STREAM_CODEC)
        PayloadTypeRegistry.playC2S().register(MusicClientInfoPayload.TYPE, MusicClientInfoPayload.STREAM_CODEC)
        PayloadTypeRegistry.playC2S().register(TrackRequestPayload.TYPE, TrackRequestPayload.STREAM_CODEC)
        PayloadTypeRegistry.playS2C().register(MusicSyncPayload.TYPE, MusicSyncPayload.STREAM_CODEC)
        PayloadTypeRegistry.playS2C().register(MusicStatusPayload.TYPE, MusicStatusPayload.STREAM_CODEC)
        PayloadTypeRegistry.playS2C().register(CustomTrackDataPayload.TYPE, CustomTrackDataPayload.STREAM_CODEC)
        PayloadTypeRegistry.playS2C().register(TrackManifestPayload.TYPE, TrackManifestPayload.STREAM_CODEC)
        fabricPayloadTypesRegistered = true
    }
    //?}

    fun registerFabricServer() {
        //? if >=1.21 {
        ensureFabricPayloadTypesRegistered()
        ServerPlayNetworking.registerGlobalReceiver(MusicControlPayload.TYPE) { payload, context ->
            playerServer(context.player()).execute {
                MusicManager.handleControlPacket(payload.packet, context.player())
            }
        }
        ServerPlayNetworking.registerGlobalReceiver(MusicClientInfoPayload.TYPE) { payload, context ->
            playerServer(context.player()).execute {
                MusicManager.handleClientInfo(payload.packet, context.player())
            }
        }
        ServerPlayNetworking.registerGlobalReceiver(TrackRequestPayload.TYPE) { payload, context ->
            playerServer(context.player()).execute {
                MusicManager.handleTrackRequest(payload.packet.manifestVersion, payload.packet.trackNames, context.player())
            }
        }
        //?} else {
        ServerPlayNetworking.registerGlobalReceiver(MUSIC_CONTROL_ID) { server, player, _, buf, _ ->
            val packet = MusicControlPacket.decode(buf)
            server.execute { MusicManager.handleControlPacket(packet, player) }
        }
        ServerPlayNetworking.registerGlobalReceiver(MUSIC_CLIENT_INFO_ID) { server, player, _, buf, _ ->
            val packet = MusicClientInfoPacket.decode(buf)
            server.execute { MusicManager.handleClientInfo(packet, player) }
        }
        ServerPlayNetworking.registerGlobalReceiver(TRACK_REQUEST_ID) { server, player, _, buf, _ ->
            val packet = TrackRequestPacket.decode(buf)
            server.execute { MusicManager.handleTrackRequest(packet.manifestVersion, packet.trackNames, player) }
        }
        //?}
    }

    fun registerFabricClient() {
        //? if >=1.21 {
        ensureFabricPayloadTypesRegistered()
        ClientPlayNetworking.registerGlobalReceiver(MusicSyncPayload.TYPE) { payload, context ->
            val mc = Minecraft.getInstance()
            mc.execute {
                val packet = payload.packet
                if (packet.action == MusicSyncPacket.Action.OPEN_GUI) {
                    mc.setScreen(MusicControlScreen())
                } else {
                    ClientMusicPlayer.handleSyncPacket(packet)
                }
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(MusicStatusPayload.TYPE) { payload, _ ->
            val mc = Minecraft.getInstance()
            mc.execute { ClientMusicPlayer.updateStatus(payload.packet) }
        }
        ClientPlayNetworking.registerGlobalReceiver(CustomTrackDataPayload.TYPE) { payload, _ ->
            val mc = Minecraft.getInstance()
            mc.execute {
                val packet = payload.packet
                CustomTrackCache.handleChunk(packet.trackName, packet.chunkIndex, packet.totalChunks, packet.data)
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(TrackManifestPayload.TYPE) { payload, _ ->
            val mc = Minecraft.getInstance()
            mc.execute { ClientTrackManager.handleManifest(payload.packet.manifestVersion, payload.packet.tracks) }
        }
        //?} else {
        ClientPlayNetworking.registerGlobalReceiver(MUSIC_SYNC_ID) { client, _, buf, _ ->
            val packet = MusicSyncPacket.decode(buf)
            client.execute {
                if (packet.action == MusicSyncPacket.Action.OPEN_GUI) {
                    val mc = Minecraft.getInstance()
                    mc.setScreen(MusicControlScreen())
                } else {
                    ClientMusicPlayer.handleSyncPacket(packet)
                }
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(MUSIC_STATUS_ID) { client, _, buf, _ ->
            val packet = MusicStatusPacket.decode(buf)
            client.execute { ClientMusicPlayer.updateStatus(packet) }
        }
        ClientPlayNetworking.registerGlobalReceiver(CUSTOM_TRACK_DATA_ID) { client, _, buf, _ ->
            val packet = CustomTrackDataPacket.decode(buf)
            client.execute {
                CustomTrackCache.handleChunk(packet.trackName, packet.chunkIndex, packet.totalChunks, packet.data)
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(TRACK_MANIFEST_ID) { client, _, buf, _ ->
            val packet = TrackManifestPacket.decode(buf)
            client.execute { ClientTrackManager.handleManifest(packet.manifestVersion, packet.tracks) }
        }
        //?}
    }*/
    //?}

    //? if forge {
    //? if >=1.20.2 {
    /*val INSTANCE: SimpleChannel = ChannelBuilder.named(ResourceLocation(MOD_ID, "main"))
        .networkProtocolVersion(1)
        .simpleChannel()*/
    //?} else {
    val INSTANCE: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation(MOD_ID, "main"),
        { PROTOCOL_VERSION },
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    )
    //?}

    private var packetId = 0

    fun register() {

        //? if >=1.20.2 {
        /*INSTANCE.messageBuilder(MusicSyncPacket::class.java, packetId++)
            .encoder(MusicSyncPacket::encode)
            .decoder(MusicSyncPacket::decode)
            .consumerMainThread(MusicSyncPacket::handle)
            .add()*/
        //?} else {
        INSTANCE.registerMessage(
            packetId++,
            MusicSyncPacket::class.java,
            MusicSyncPacket::encode,
            MusicSyncPacket::decode,
            MusicSyncPacket::handle
        )
        //?}

        //? if >=1.20.2 {
        /*INSTANCE.messageBuilder(MusicControlPacket::class.java, packetId++)
            .encoder(MusicControlPacket::encode)
            .decoder(MusicControlPacket::decode)
            .consumerMainThread(MusicControlPacket::handle)
            .add()*/
        //?} else {
        INSTANCE.registerMessage(
            packetId++,
            MusicControlPacket::class.java,
            MusicControlPacket::encode,
            MusicControlPacket::decode,
            MusicControlPacket::handle
        )
        //?}

        //? if >=1.20.2 {
        /*INSTANCE.messageBuilder(MusicStatusPacket::class.java, packetId++)
            .encoder(MusicStatusPacket::encode)
            .decoder(MusicStatusPacket::decode)
            .consumerMainThread(MusicStatusPacket::handle)
            .add()*/
        //?} else {
        INSTANCE.registerMessage(
            packetId++,
            MusicStatusPacket::class.java,
            MusicStatusPacket::encode,
            MusicStatusPacket::decode,
            MusicStatusPacket::handle
        )
        //?}

        //? if >=1.20.2 {
        /*INSTANCE.messageBuilder(MusicClientInfoPacket::class.java, packetId++)
            .encoder(MusicClientInfoPacket::encode)
            .decoder(MusicClientInfoPacket::decode)
            .consumerMainThread(MusicClientInfoPacket::handle)
            .add()*/
        //?} else {
        INSTANCE.registerMessage(
            packetId++,
            MusicClientInfoPacket::class.java,
            MusicClientInfoPacket::encode,
            MusicClientInfoPacket::decode,
            MusicClientInfoPacket::handle
        )
        //?}

        //? if >=1.20.2 {
        /*INSTANCE.messageBuilder(CustomTrackDataPacket::class.java, packetId++)
            .encoder(CustomTrackDataPacket::encode)
            .decoder(CustomTrackDataPacket::decode)
            .consumerMainThread(CustomTrackDataPacket::handle)
            .add()*/
        //?} else {
        INSTANCE.registerMessage(
            packetId++,
            CustomTrackDataPacket::class.java,
            CustomTrackDataPacket::encode,
            CustomTrackDataPacket::decode,
            CustomTrackDataPacket::handle
        )
        //?}

        //? if >=1.20.2 {
        /*INSTANCE.messageBuilder(TrackManifestPacket::class.java, packetId++)
            .encoder(TrackManifestPacket::encode)
            .decoder(TrackManifestPacket::decode)
            .consumerMainThread(TrackManifestPacket::handle)
            .add()*/
        //?} else {
        INSTANCE.registerMessage(
            packetId++,
            TrackManifestPacket::class.java,
            TrackManifestPacket::encode,
            TrackManifestPacket::decode,
            TrackManifestPacket::handle
        )
        //?}

        //? if >=1.20.2 {
        /*INSTANCE.messageBuilder(TrackRequestPacket::class.java, packetId++)
            .encoder(TrackRequestPacket::encode)
            .decoder(TrackRequestPacket::decode)
            .consumerMainThread(TrackRequestPacket::handle)
            .add()*/
        //?} else {
        INSTANCE.registerMessage(
            packetId++,
            TrackRequestPacket::class.java,
            TrackRequestPacket::encode,
            TrackRequestPacket::decode,
            TrackRequestPacket::handle
        )
        //?}
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
        //? if fabric {
        //? if >=1.21 {
        /*when (packet) {
            is MusicSyncPacket -> ServerPlayNetworking.send(player, MusicSyncPayload(packet))
            is MusicStatusPacket -> ServerPlayNetworking.send(player, MusicStatusPayload(packet))
            is CustomTrackDataPacket -> ServerPlayNetworking.send(player, CustomTrackDataPayload(packet))
            is TrackManifestPacket -> ServerPlayNetworking.send(player, TrackManifestPayload(packet))
            else -> throw IllegalArgumentException("Unsupported S2C packet: ${packet::class.java.name}")
        }*/
        //?} else {
        /*when (packet) {
            is MusicSyncPacket -> {
                val buf = PacketByteBufs.create()
                MusicSyncPacket.encode(packet, buf)
                ServerPlayNetworking.send(player, MUSIC_SYNC_ID, buf)
            }
            is MusicStatusPacket -> {
                val buf = PacketByteBufs.create()
                MusicStatusPacket.encode(packet, buf)
                ServerPlayNetworking.send(player, MUSIC_STATUS_ID, buf)
            }
            is CustomTrackDataPacket -> {
                val buf = PacketByteBufs.create()
                CustomTrackDataPacket.encode(packet, buf)
                ServerPlayNetworking.send(player, CUSTOM_TRACK_DATA_ID, buf)
            }
            is TrackManifestPacket -> {
                val buf = PacketByteBufs.create()
                TrackManifestPacket.encode(packet, buf)
                ServerPlayNetworking.send(player, TRACK_MANIFEST_ID, buf)
            }
            else -> throw IllegalArgumentException("Unsupported S2C packet: ${packet::class.java.name}")
        }*/
        //?}
        //?} else if neoforge {
        /*PacketDistributor.sendToPlayer(player, packet as CustomPacketPayload)*/
        //?} else {
        //? if >=1.20.2 {
        /*INSTANCE.send(packet, net.minecraftforge.network.PacketDistributor.PLAYER.with(player))*/
        //?} else {
        INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with { player }, packet)
        //?}
        //?}
    }

    fun sendToServer(packet: Any) {
        //? if fabric {
        //? if >=1.21 {
        /*when (packet) {
            is MusicControlPacket -> ClientPlayNetworking.send(MusicControlPayload(packet))
            is MusicClientInfoPacket -> ClientPlayNetworking.send(MusicClientInfoPayload(packet))
            is TrackRequestPacket -> ClientPlayNetworking.send(TrackRequestPayload(packet))
            else -> throw IllegalArgumentException("Unsupported C2S packet: ${packet::class.java.name}")
        }*/
        //?} else {
        /*when (packet) {
            is MusicControlPacket -> {
                val buf = PacketByteBufs.create()
                MusicControlPacket.encode(packet, buf)
                ClientPlayNetworking.send(MUSIC_CONTROL_ID, buf)
            }
            is MusicClientInfoPacket -> {
                val buf = PacketByteBufs.create()
                MusicClientInfoPacket.encode(packet, buf)
                ClientPlayNetworking.send(MUSIC_CLIENT_INFO_ID, buf)
            }
            is TrackRequestPacket -> {
                val buf = PacketByteBufs.create()
                TrackRequestPacket.encode(packet, buf)
                ClientPlayNetworking.send(TRACK_REQUEST_ID, buf)
            }
            else -> throw IllegalArgumentException("Unsupported C2S packet: ${packet::class.java.name}")
        }*/
        //?}
        //?} else if neoforge {
        //? if >=1.21.11 {
        /*net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(packet as CustomPacketPayload)*/
        //?} else {
        /*PacketDistributor.sendToServer(packet as CustomPacketPayload)*/
        //?}
        //?} else {
        //? if >=1.20.2 {
        /*INSTANCE.send(packet, net.minecraftforge.network.PacketDistributor.SERVER.noArg())*/
        //?} else {
        INSTANCE.send(net.minecraftforge.network.PacketDistributor.SERVER.noArg(), packet)
        //?}
        //?}
    }
}
