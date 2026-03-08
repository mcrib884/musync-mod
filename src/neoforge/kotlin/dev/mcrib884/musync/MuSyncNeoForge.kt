package dev.mcrib884.musync

import com.mojang.blaze3d.platform.InputConstants
import dev.mcrib884.musync.client.ClientMusicPlayer
import dev.mcrib884.musync.client.ClientTrackManager
import dev.mcrib884.musync.client.MusicControlScreen
import dev.mcrib884.musync.client.TrackDownloadScreen
import dev.mcrib884.musync.command.MuSyncCommand
import dev.mcrib884.musync.network.MusicControlPacket
import dev.mcrib884.musync.network.PacketHandler
import dev.mcrib884.musync.server.MusicManager
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.api.distmarker.Dist
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.bus.api.IEventBus

@Mod(MOD_ID)
class MuSyncNeoForge(modBus: IEventBus) {

    companion object {
        lateinit var MUSIC_GUI_KEY: KeyMapping
        lateinit var MUSIC_SKIP_KEY: KeyMapping
        lateinit var MUSIC_PAUSE_KEY: KeyMapping
        lateinit var MUSIC_STOP_KEY: KeyMapping
    }

    init {
        modBus.addListener(::onCommonSetup)
        modBus.addListener(PacketHandler::registerNeoForge)

        NeoForge.EVENT_BUS.addListener<ServerStartedEvent>(MusicManager::onServerStarted)
        NeoForge.EVENT_BUS.addListener<ServerStoppingEvent>(MusicManager::onServerStopping)
        NeoForge.EVENT_BUS.addListener<ServerTickEvent.Post>(MusicManager::onServerTick)
        NeoForge.EVENT_BUS.addListener<PlayerEvent.PlayerLoggedInEvent>(MusicManager::onPlayerJoin)
        NeoForge.EVENT_BUS.addListener<PlayerEvent.PlayerLoggedOutEvent>(MusicManager::onPlayerLeave)
        NeoForge.EVENT_BUS.addListener<PlayerEvent.PlayerChangedDimensionEvent>(MusicManager::onPlayerChangedDimension)

        if (FMLEnvironment.dist == Dist.CLIENT) {

            modBus.addListener<RegisterKeyMappingsEvent> { event ->
                MUSIC_GUI_KEY = KeyMapping(
                    "key.musync.gui",
                    InputConstants.Type.KEYSYM,
                    InputConstants.UNKNOWN.value,
                    "key.categories.musync"
                )
                event.register(MUSIC_GUI_KEY)

                MUSIC_SKIP_KEY = KeyMapping(
                    "key.musync.skip",
                    InputConstants.Type.KEYSYM,
                    InputConstants.UNKNOWN.value,
                    "key.categories.musync"
                )
                event.register(MUSIC_SKIP_KEY)

                MUSIC_PAUSE_KEY = KeyMapping(
                    "key.musync.pause_resume",
                    InputConstants.Type.KEYSYM,
                    InputConstants.UNKNOWN.value,
                    "key.categories.musync"
                )
                event.register(MUSIC_PAUSE_KEY)

                MUSIC_STOP_KEY = KeyMapping(
                    "key.musync.stop",
                    InputConstants.Type.KEYSYM,
                    InputConstants.UNKNOWN.value,
                    "key.categories.musync"
                )
                event.register(MUSIC_STOP_KEY)
            }

            NeoForge.EVENT_BUS.addListener<ClientTickEvent.Post> { _ ->
                ClientMusicPlayer.onClientTick()

                val mc = Minecraft.getInstance()
                if (mc.player != null && mc.screen == null) {
                    if (MUSIC_GUI_KEY.consumeClick()) {

                        if (ClientTrackManager.isDownloading) {
                            mc.setScreen(TrackDownloadScreen())
                        } else {
                            mc.setScreen(MusicControlScreen())
                        }
                    }

                    val isOp = mc.player?.hasPermissions(2) == true
                    if (isOp && !ClientTrackManager.isDownloading) {
                        val targetDim = mc.player?.entityLevel()?.dimension()?.location()?.toString()
                        if (MUSIC_SKIP_KEY.consumeClick()) {
                            PacketHandler.sendToServer(
                                MusicControlPacket(MusicControlPacket.Action.SKIP, null, null, targetDim = targetDim)
                            )
                        }
                        if (MUSIC_PAUSE_KEY.consumeClick()) {
                            val status = ClientMusicPlayer.getCurrentStatus()
                            val action = if (status != null && status.isPlaying)
                                MusicControlPacket.Action.PAUSE else MusicControlPacket.Action.RESUME
                            PacketHandler.sendToServer(
                                MusicControlPacket(action, null, null, targetDim = targetDim)
                            )
                        }
                        if (MUSIC_STOP_KEY.consumeClick()) {
                            PacketHandler.sendToServer(
                                MusicControlPacket(MusicControlPacket.Action.STOP, null, null, targetDim = targetDim)
                            )
                        }
                    } else {

                        MUSIC_SKIP_KEY.consumeClick()
                        MUSIC_PAUSE_KEY.consumeClick()
                        MUSIC_STOP_KEY.consumeClick()
                    }
                }
            }

            NeoForge.EVENT_BUS.addListener<net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn> { _ ->
                ClientMusicPlayer.musyncActive = true
            }

            NeoForge.EVENT_BUS.addListener<net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut> { _ ->
                ClientMusicPlayer.fullReset()
                dev.mcrib884.musync.client.ClientTrackManager.reset()
                dev.mcrib884.musync.client.CustomTrackCache.clear()
            }
        }

        NeoForge.EVENT_BUS.addListener<RegisterCommandsEvent>(::onRegisterCommands)

        initializeMod()
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        // Networking is registered via RegisterPayloadHandlersEvent
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        MuSyncCommand.register(event.dispatcher)
    }
}
