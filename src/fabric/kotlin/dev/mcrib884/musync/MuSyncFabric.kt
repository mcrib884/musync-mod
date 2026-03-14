package dev.mcrib884.musync

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import dev.mcrib884.musync.command.MuSyncCommand
import dev.mcrib884.musync.network.PacketHandler
import dev.mcrib884.musync.server.MusicManager

class MuSyncFabric : ModInitializer {
    companion object {
        @Volatile
        var server: MinecraftServer? = null
            private set

        internal fun clearServer() {
            server = null
        }

        internal fun setServer(current: MinecraftServer) {
            server = current
        }
    }

    override fun onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register { started ->
            setServer(started)
            MusicManager.onServerStarted()
        }

        ServerLifecycleEvents.SERVER_STOPPING.register {
            MusicManager.onServerStopping()
            clearServer()
        }

        ServerTickEvents.END_SERVER_TICK.register {
            MusicManager.onServerTick()
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            MusicManager.onPlayerJoin(handler.player)
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            MusicManager.onPlayerLeave(handler.player)
        }

        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ ->
            MusicManager.onPlayerRespawn(newPlayer)
        }

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register { player, origin, destination ->
            MusicManager.onPlayerChangedDimension(origin.dimension(), destination.dimension(), player)
        }

        CommandRegistrationCallback.EVENT.register(CommandRegistrationCallback { dispatcher, _, _ ->
            MuSyncCommand.register(dispatcher)
        })

        PacketHandler.registerFabricServer()
        initializeMod()
    }
}
