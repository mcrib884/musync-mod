package dev.mcrib884.musync

import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
//? if neoforge {
/*import net.neoforged.neoforge.server.ServerLifecycleHooks*/
//?} else {
import net.minecraftforge.server.ServerLifecycleHooks
//?}
//? if neoforge {
/*import net.minecraft.core.registries.BuiltInRegistries*/
//?} else {
import net.minecraftforge.registries.ForgeRegistries
//?}

// ── ResourceLocation compat ─────────────────────────────────────────────────

//? if >=1.21 {
/*internal fun resLoc(namespace: String, path: String): ResourceLocation =
	ResourceLocation.fromNamespaceAndPath(namespace, path)*/
//?} else {
internal fun resLoc(namespace: String, path: String): ResourceLocation =
	ResourceLocation(namespace, path)
//?}

// ── Server instance & directory compat ──────────────────────────────────────

internal fun currentServer(): MinecraftServer? = ServerLifecycleHooks.getCurrentServer()

internal fun serverDir(server: MinecraftServer?): java.io.File {
	server ?: return java.io.File(".")
	//? if >=1.21 {
	/*return server.serverDirectory.toFile()*/
	//?} else {
	return server.serverDirectory
	//?}
}

// ── Registry compat ─────────────────────────────────────────────────────────

//? if neoforge {
/*internal fun soundEventKeys(): Set<ResourceLocation> = BuiltInRegistries.SOUND_EVENT.keySet()
internal fun soundEventContains(key: ResourceLocation): Boolean = BuiltInRegistries.SOUND_EVENT.containsKey(key)*/
//?} else {
internal fun soundEventKeys(): Set<ResourceLocation> = ForgeRegistries.SOUND_EVENTS.keys
internal fun soundEventContains(key: ResourceLocation): Boolean = ForgeRegistries.SOUND_EVENTS.containsKey(key)
//?}

// ── SoundEvent creation compat ──────────────────────────────────────────────

//? if >=1.20 {
internal fun createSoundEvent(location: ResourceLocation): net.minecraft.sounds.SoundEvent =
	net.minecraft.sounds.SoundEvent.createVariableRangeEvent(location)
//?} else {
/*internal fun createSoundEvent(location: ResourceLocation): net.minecraft.sounds.SoundEvent =
	net.minecraft.sounds.SoundEvent(location)*/
//?}

// ── Command sendSuccess compat ──────────────────────────────────────────────

//? if >=1.20 {
internal fun CommandSourceStack.sendSuccessCompat(message: () -> Component, broadcast: Boolean) =
	sendSuccess(message, broadcast)
//?} else {
/*internal fun CommandSourceStack.sendSuccessCompat(message: () -> Component, broadcast: Boolean) =
	sendSuccess(message(), broadcast)*/
//?}

// ── Biome music event location compat ───────────────────────────────────────

//? if >=1.20 {
internal fun musicEventLocation(music: net.minecraft.sounds.Music): ResourceLocation =
	music.event.value().location
//?} else {
/*internal fun musicEventLocation(music: net.minecraft.sounds.Music): ResourceLocation =
	music.event.location*/
//?}

// ── Music volume compat ─────────────────────────────────────────────────────

//? if >=1.20 {
internal fun setMusicVolume(mc: net.minecraft.client.Minecraft, volume: Float) {
	mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MUSIC).set(volume.toDouble())
}
//?} else {
/*internal fun setMusicVolume(mc: net.minecraft.client.Minecraft, volume: Float) {
	mc.options.setSoundCategoryVolume(net.minecraft.sounds.SoundSource.MUSIC, volume)
}*/
//?}
