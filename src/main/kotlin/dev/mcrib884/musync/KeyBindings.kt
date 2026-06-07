package dev.mcrib884.musync

import net.minecraft.client.KeyMapping

object KeyBindings {
	@Volatile var MUSIC_GUI_KEY: KeyMapping? = null
	@Volatile var MUSIC_SKIP_KEY: KeyMapping? = null
	@Volatile var MUSIC_PAUSE_KEY: KeyMapping? = null
	@Volatile var MUSIC_STOP_KEY: KeyMapping? = null
	@Volatile var MUSIC_PREV_KEY: KeyMapping? = null
}
