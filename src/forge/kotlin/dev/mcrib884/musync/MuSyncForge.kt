package dev.mcrib884.musync

import com.mojang.blaze3d.platform.InputConstants
import dev.mcrib884.musync.client.ClientMusicPlayer
import dev.mcrib884.musync.client.ClientOnlyController
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
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.server.ServerStartedEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.api.distmarker.Dist
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod(MOD_ID)
class MuSyncForge {

    init {
        val modEventBus = MOD_BUS
        modEventBus.addListener(::onCommonSetup)

        MinecraftForge.EVENT_BUS.addListener<ServerStartedEvent> { _ -> MusicManager.onServerStarted() }
        MinecraftForge.EVENT_BUS.addListener<ServerStoppingEvent> { _ -> MusicManager.onServerStopping() }
        MinecraftForge.EVENT_BUS.addListener<TickEvent.ServerTickEvent> { event ->
            if (event.phase == TickEvent.Phase.END) MusicManager.onServerTick()
        }
        MinecraftForge.EVENT_BUS.addListener<PlayerEvent.PlayerLoggedInEvent> { event ->
            (event.entity as? ServerPlayer)?.let { MusicManager.onPlayerJoin(it) }
        }
        MinecraftForge.EVENT_BUS.addListener<PlayerEvent.PlayerLoggedOutEvent> { event ->
            (event.entity as? ServerPlayer)?.let { MusicManager.onPlayerLeave(it) }
        }
        MinecraftForge.EVENT_BUS.addListener<PlayerEvent.PlayerRespawnEvent> { event ->
            (event.entity as? ServerPlayer)?.let { MusicManager.onPlayerRespawn(it) }
        }
        MinecraftForge.EVENT_BUS.addListener<PlayerEvent.PlayerChangedDimensionEvent> { event ->
            (event.entity as? ServerPlayer)?.let { MusicManager.onPlayerChangedDimension(event.from, event.to, it) }
        }

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener<RegisterKeyMappingsEvent> { event ->
                KeyBindings.MUSIC_GUI_KEY = KeyMapping("key.musync.gui", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, "key.categories.musync")
                event.register(KeyBindings.MUSIC_GUI_KEY)
                KeyBindings.MUSIC_SKIP_KEY = KeyMapping("key.musync.skip", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, "key.categories.musync")
                event.register(KeyBindings.MUSIC_SKIP_KEY)
                KeyBindings.MUSIC_PAUSE_KEY = KeyMapping("key.musync.pause_resume", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, "key.categories.musync")
                event.register(KeyBindings.MUSIC_PAUSE_KEY)
                KeyBindings.MUSIC_STOP_KEY = KeyMapping("key.musync.stop", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, "key.categories.musync")
                event.register(KeyBindings.MUSIC_STOP_KEY)
                KeyBindings.MUSIC_PREV_KEY = KeyMapping("key.musync.previous", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, "key.categories.musync")
                event.register(KeyBindings.MUSIC_PREV_KEY)
            }

            MinecraftForge.EVENT_BUS.addListener<TickEvent.ClientTickEvent> { event ->
                if (event.phase == TickEvent.Phase.END) {
                    ClientMusicPlayer.onClientTick()
                    ClientOnlyController.onClientTick()
                    val mc = Minecraft.getInstance()
                    if (mc.player != null && mc.screen == null) {
                        if (KeyBindings.MUSIC_GUI_KEY.consumeClick()) {
                            if (ClientMusicPlayer.musyncActive) {
                                if (ClientTrackManager.isDownloading) mc.setScreen(TrackDownloadScreen())
                                else mc.setScreen(MusicControlScreen())
                            } else {
                                ClientOnlyController.scanLocalTracks()
                                mc.setScreen(MusicControlScreen())
                            }
                        }
                        if (ClientMusicPlayer.musyncActive && !ClientTrackManager.isDownloading) {
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
                            if (KeyBindings.MUSIC_PREV_KEY.consumeClick()) {
                                PacketHandler.sendToServer(MusicControlPacket(MusicControlPacket.Action.PREVIOUS, null, null, targetDim = targetDim))
                            }
                        } else if (!ClientMusicPlayer.musyncActive) {
                            if (KeyBindings.MUSIC_SKIP_KEY.consumeClick()) ClientOnlyController.skip()
                            if (KeyBindings.MUSIC_PAUSE_KEY.consumeClick()) ClientOnlyController.togglePause()
                            if (KeyBindings.MUSIC_STOP_KEY.consumeClick()) ClientOnlyController.stop()
                            if (KeyBindings.MUSIC_PREV_KEY.consumeClick()) ClientOnlyController.previous()
                        } else {
                            KeyBindings.MUSIC_SKIP_KEY.consumeClick()
                            KeyBindings.MUSIC_PAUSE_KEY.consumeClick()
                            KeyBindings.MUSIC_STOP_KEY.consumeClick()
                            KeyBindings.MUSIC_PREV_KEY.consumeClick()
                        }
                    }
                }
            }

            //? if >=1.20 {
            MinecraftForge.EVENT_BUS.addListener<net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut> { _ ->
            //?} else {
            /*MinecraftForge.EVENT_BUS.addListener<net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggedOutEvent> { _ ->*/
            //?}
                ClientMusicPlayer.fullReset()
                ClientTrackManager.reset()
                dev.mcrib884.musync.client.CustomTrackCache.clear()
            }
        }

        MinecraftForge.EVENT_BUS.addListener<RegisterCommandsEvent>(::onRegisterCommands)
        initializeMod()
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        event.enqueueWork { PacketHandler.register() }
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        MuSyncCommand.register(event.dispatcher)
    }
}
