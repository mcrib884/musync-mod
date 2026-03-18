package dev.mcrib884.musync

import com.mojang.blaze3d.platform.InputConstants
import dev.mcrib884.musync.client.ClientMusicPlayer
import dev.mcrib884.musync.client.ClientOnlyController
import dev.mcrib884.musync.client.ClientTrackManager
import dev.mcrib884.musync.client.MusicControlScreen
import dev.mcrib884.musync.client.TrackDownloadScreen
import dev.mcrib884.musync.network.MusicControlPacket
import dev.mcrib884.musync.network.PacketHandler
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft

class MuSyncFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        KeyBindings.MUSIC_GUI_KEY = KeyBindingHelper.registerKeyBinding(
            KeyMapping("key.musync.gui", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, "key.categories.musync")
        )
        KeyBindings.MUSIC_SKIP_KEY = KeyBindingHelper.registerKeyBinding(
            KeyMapping("key.musync.skip", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, "key.categories.musync")
        )
        KeyBindings.MUSIC_PAUSE_KEY = KeyBindingHelper.registerKeyBinding(
            KeyMapping("key.musync.pause_resume", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, "key.categories.musync")
        )
        KeyBindings.MUSIC_STOP_KEY = KeyBindingHelper.registerKeyBinding(
            KeyMapping("key.musync.stop", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, "key.categories.musync")
        )

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            ClientMusicPlayer.onClientTick()
            ClientOnlyController.onClientTick()
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
                } else if (!ClientMusicPlayer.musyncActive) {
                    if (KeyBindings.MUSIC_SKIP_KEY.consumeClick()) ClientOnlyController.skip()
                    if (KeyBindings.MUSIC_PAUSE_KEY.consumeClick()) ClientOnlyController.togglePause()
                    if (KeyBindings.MUSIC_STOP_KEY.consumeClick()) ClientOnlyController.stop()
                } else {
                    KeyBindings.MUSIC_SKIP_KEY.consumeClick()
                    KeyBindings.MUSIC_PAUSE_KEY.consumeClick()
                    KeyBindings.MUSIC_STOP_KEY.consumeClick()
                }
            }
        })

        ClientPlayConnectionEvents.DISCONNECT.register(ClientPlayConnectionEvents.Disconnect { _, _ ->
            ClientMusicPlayer.fullReset()
            ClientTrackManager.reset()
            dev.mcrib884.musync.client.CustomTrackCache.clear()
        })

        PacketHandler.registerFabricClient()
    }
}
