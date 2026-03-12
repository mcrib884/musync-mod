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
import net.minecraft.server.level.ServerPlayer
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
import net.neoforged.bus.api.IEventBus

@Mod(MOD_ID)
class MuSyncNeoForge(modBus: IEventBus) {

    init {
        modBus.addListener(::onCommonSetup)
        modBus.addListener(PacketHandler::registerNeoForge)

        NeoForge.EVENT_BUS.addListener<ServerStartedEvent> { _ -> MusicManager.onServerStarted() }
        NeoForge.EVENT_BUS.addListener<ServerStoppingEvent> { _ -> MusicManager.onServerStopping() }
        NeoForge.EVENT_BUS.addListener<ServerTickEvent.Post> { _ -> MusicManager.onServerTick() }
        NeoForge.EVENT_BUS.addListener<PlayerEvent.PlayerLoggedInEvent> { event ->
            (event.entity as? ServerPlayer)?.let { MusicManager.onPlayerJoin(it) }
        }
        NeoForge.EVENT_BUS.addListener<PlayerEvent.PlayerLoggedOutEvent> { event ->
            (event.entity as? ServerPlayer)?.let { MusicManager.onPlayerLeave(it) }
        }
        NeoForge.EVENT_BUS.addListener<PlayerEvent.PlayerChangedDimensionEvent> { event ->
            (event.entity as? ServerPlayer)?.let { MusicManager.onPlayerChangedDimension(event.from, event.to, it) }
        }

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener<RegisterKeyMappingsEvent> { event ->
                KeyBindings.MUSIC_GUI_KEY = KeyMapping("key.musync.gui", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, "key.categories.musync")
                event.register(KeyBindings.MUSIC_GUI_KEY)
                KeyBindings.MUSIC_SKIP_KEY = KeyMapping("key.musync.skip", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, "key.categories.musync")
                event.register(KeyBindings.MUSIC_SKIP_KEY)
                KeyBindings.MUSIC_PAUSE_KEY = KeyMapping("key.musync.pause_resume", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, "key.categories.musync")
                event.register(KeyBindings.MUSIC_PAUSE_KEY)
                KeyBindings.MUSIC_STOP_KEY = KeyMapping("key.musync.stop", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, "key.categories.musync")
                event.register(KeyBindings.MUSIC_STOP_KEY)
            }

            NeoForge.EVENT_BUS.addListener<ClientTickEvent.Post> { _ ->
                ClientMusicPlayer.onClientTick()
                val mc = Minecraft.getInstance()
                if (mc.player != null && mc.screen == null) {
                    if (KeyBindings.MUSIC_GUI_KEY.consumeClick()) {
                        if (ClientTrackManager.isDownloading) mc.setScreen(TrackDownloadScreen())
                        else mc.setScreen(MusicControlScreen())
                    }
                    val isOp = mc.player?.hasPermissions(2) == true
                    if (isOp && !ClientTrackManager.isDownloading) {
                        val targetDim = mc.player?.entityLevel()?.dimension()?.location()?.toString()
                        if (KeyBindings.MUSIC_SKIP_KEY.consumeClick()) {
                            PacketHandler.sendToServer(MusicControlPacket(MusicControlPacket.Action.SKIP, null, null, targetDim = targetDim))
                        }
                        if (KeyBindings.MUSIC_PAUSE_KEY.consumeClick()) {
                            val status = ClientMusicPlayer.getCurrentStatus()
                            val action = if (status != null && status.isPlaying)
                                MusicControlPacket.Action.PAUSE else MusicControlPacket.Action.RESUME
                            PacketHandler.sendToServer(MusicControlPacket(action, null, null, targetDim = targetDim))
                        }
                        if (KeyBindings.MUSIC_STOP_KEY.consumeClick()) {
                            PacketHandler.sendToServer(MusicControlPacket(MusicControlPacket.Action.STOP, null, null, targetDim = targetDim))
                        }
                    } else {
                        KeyBindings.MUSIC_SKIP_KEY.consumeClick()
                        KeyBindings.MUSIC_PAUSE_KEY.consumeClick()
                        KeyBindings.MUSIC_STOP_KEY.consumeClick()
                    }
                }
            }

            NeoForge.EVENT_BUS.addListener<net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut> { _ ->
                ClientMusicPlayer.fullReset()
                ClientTrackManager.reset()
                dev.mcrib884.musync.client.CustomTrackCache.clear()
            }
        }

        NeoForge.EVENT_BUS.addListener<RegisterCommandsEvent>(::onRegisterCommands)
        initializeMod()
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        MuSyncCommand.register(event.dispatcher)
    }
}
