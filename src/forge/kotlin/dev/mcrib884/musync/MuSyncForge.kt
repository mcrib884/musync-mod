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
import net.minecraftforge.network.PacketDistributor
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod(MOD_ID)
class MuSyncForge {

    companion object {
        lateinit var MUSIC_GUI_KEY: KeyMapping
        lateinit var MUSIC_SKIP_KEY: KeyMapping
        lateinit var MUSIC_PAUSE_KEY: KeyMapping
        lateinit var MUSIC_STOP_KEY: KeyMapping
    }

    init {
        val modEventBus = MOD_BUS
        modEventBus.addListener(::onCommonSetup)

        MinecraftForge.EVENT_BUS.addListener<ServerStartedEvent>(MusicManager::onServerStarted)
        MinecraftForge.EVENT_BUS.addListener<ServerStoppingEvent>(MusicManager::onServerStopping)
        MinecraftForge.EVENT_BUS.addListener<TickEvent.ServerTickEvent>(MusicManager::onServerTick)
        MinecraftForge.EVENT_BUS.addListener<PlayerEvent.PlayerLoggedInEvent>(MusicManager::onPlayerJoin)
        MinecraftForge.EVENT_BUS.addListener<PlayerEvent.PlayerLoggedOutEvent>(MusicManager::onPlayerLeave)
        MinecraftForge.EVENT_BUS.addListener<PlayerEvent.PlayerChangedDimensionEvent>(MusicManager::onPlayerChangedDimension)

        if (FMLEnvironment.dist == Dist.CLIENT) {

            modEventBus.addListener<RegisterKeyMappingsEvent> { event ->
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

            MinecraftForge.EVENT_BUS.addListener<TickEvent.ClientTickEvent> { event ->
                if (event.phase == TickEvent.Phase.END) {
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
                            if (MUSIC_SKIP_KEY.consumeClick()) {
                                PacketHandler.INSTANCE.send(
                                    PacketDistributor.SERVER.noArg(),
                                    MusicControlPacket(MusicControlPacket.Action.SKIP, null, null)
                                )
                            }
                            if (MUSIC_PAUSE_KEY.consumeClick()) {
                                val status = ClientMusicPlayer.getCurrentStatus()
                                val action = if (status != null && status.isPlaying)
                                    MusicControlPacket.Action.PAUSE else MusicControlPacket.Action.RESUME
                                PacketHandler.INSTANCE.send(
                                    PacketDistributor.SERVER.noArg(),
                                    MusicControlPacket(action, null, null)
                                )
                            }
                            if (MUSIC_STOP_KEY.consumeClick()) {
                                PacketHandler.INSTANCE.send(
                                    PacketDistributor.SERVER.noArg(),
                                    MusicControlPacket(MusicControlPacket.Action.STOP, null, null)
                                )
                            }
                        } else {

                            MUSIC_SKIP_KEY.consumeClick()
                            MUSIC_PAUSE_KEY.consumeClick()
                            MUSIC_STOP_KEY.consumeClick()
                        }
                    }
                }
            }

            //? if >=1.20 {
            MinecraftForge.EVENT_BUS.addListener<net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn> { _ ->
            //?} else {
            /*MinecraftForge.EVENT_BUS.addListener<net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggedInEvent> { _ ->*/
            //?}
                ClientMusicPlayer.musyncActive = true
            }

            //? if >=1.20 {
            MinecraftForge.EVENT_BUS.addListener<net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut> { _ ->
            //?} else {
            /*MinecraftForge.EVENT_BUS.addListener<net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggedOutEvent> { _ ->*/
            //?}
                ClientMusicPlayer.fullReset()
                dev.mcrib884.musync.client.ClientTrackManager.reset()
                dev.mcrib884.musync.client.CustomTrackCache.clear()
            }
        }

        MinecraftForge.EVENT_BUS.addListener<RegisterCommandsEvent>(::onRegisterCommands)

        initializeMod()
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        event.enqueueWork {
            PacketHandler.register()
        }
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        MuSyncCommand.register(event.dispatcher)
    }
}
