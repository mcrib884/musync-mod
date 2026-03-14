package dev.mcrib884.musync

import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
//? if fabric {
/*import dev.mcrib884.musync.MuSyncFabric*/
//?} else if neoforge {
/*import net.neoforged.neoforge.server.ServerLifecycleHooks*/
//?} else {
import net.minecraftforge.server.ServerLifecycleHooks
//?}
//? if forge {
import net.minecraftforge.registries.ForgeRegistries
//?} else if >=1.20 {
/*import net.minecraft.core.registries.BuiltInRegistries*/
//?} else {
/*import net.minecraft.core.Registry*/
//?}

//? if >=1.21 {
/*internal fun resLoc(namespace: String, path: String): ResourceLocation =
	ResourceLocation.fromNamespaceAndPath(namespace, path)*/
//?} else {
internal fun resLoc(namespace: String, path: String): ResourceLocation =
	ResourceLocation(namespace, path)
//?}

//? if fabric {
/*internal fun currentServer(): MinecraftServer? = MuSyncFabric.server*/
//?} else {
internal fun currentServer(): MinecraftServer? = ServerLifecycleHooks.getCurrentServer()
//?}

internal fun serverDir(server: MinecraftServer?): java.io.File {
	server ?: return java.io.File(".")
	//? if >=1.21 {
	/*return server.serverDirectory.toFile()*/
	//?} else {
	return server.serverDirectory
	//?}
}

//? if forge {
internal fun soundEventKeys(): Set<ResourceLocation> = ForgeRegistries.SOUND_EVENTS.keys
internal fun soundEventContains(key: ResourceLocation): Boolean = ForgeRegistries.SOUND_EVENTS.containsKey(key)
//?} else if >=1.20 {
/*internal fun soundEventKeys(): Set<ResourceLocation> = BuiltInRegistries.SOUND_EVENT.keySet()
internal fun soundEventContains(key: ResourceLocation): Boolean = BuiltInRegistries.SOUND_EVENT.containsKey(key)*/
//?} else {
/*internal fun soundEventKeys(): Set<ResourceLocation> = Registry.SOUND_EVENT.keySet()
internal fun soundEventContains(key: ResourceLocation): Boolean = Registry.SOUND_EVENT.containsKey(key)*/
//?}

//? if >=1.20 {
internal fun createSoundEvent(location: ResourceLocation): net.minecraft.sounds.SoundEvent =
	net.minecraft.sounds.SoundEvent.createVariableRangeEvent(location)
//?} else {
/*internal fun createSoundEvent(location: ResourceLocation): net.minecraft.sounds.SoundEvent =
	net.minecraft.sounds.SoundEvent(location)*/
//?}

//? if >=1.20 {
internal fun CommandSourceStack.sendSuccessCompat(message: () -> Component, broadcast: Boolean) =
	sendSuccess(message, broadcast)
//?} else {
/*internal fun CommandSourceStack.sendSuccessCompat(message: () -> Component, broadcast: Boolean) =
	sendSuccess(message(), broadcast)*/
//?}

//? if >=1.20 {
internal fun musicEventLocation(music: net.minecraft.sounds.Music): ResourceLocation =
	music.event.value().location
//?} else {
/*internal fun musicEventLocation(music: net.minecraft.sounds.Music): ResourceLocation =
	music.event.location*/
//?}

//? if >=1.20 {
internal fun setMusicVolume(mc: net.minecraft.client.Minecraft, volume: Float) {
	mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MUSIC).set(volume.toDouble())
}
//?} else {
/*internal fun setMusicVolume(mc: net.minecraft.client.Minecraft, volume: Float) {
	mc.options.setSoundCategoryVolume(net.minecraft.sounds.SoundSource.MUSIC, volume)
}*/
//?}
