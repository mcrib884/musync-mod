package dev.mcrib884.musync

internal const val MOD_ID = "musync"

//? if >=1.20 {
@Suppress("NOTHING_TO_INLINE")
internal inline fun net.minecraft.world.entity.Entity.entityLevel(): net.minecraft.world.level.Level = this.level()
//?} else {
/*@Suppress("NOTHING_TO_INLINE")
internal inline fun net.minecraft.world.entity.Entity.entityLevel(): net.minecraft.world.level.Level = this.level*/
//?}

internal fun initializeMod() {
	println("MuSync has been initialized.")
}
