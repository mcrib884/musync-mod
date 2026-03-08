package dev.mcrib884.musync.client

import net.minecraft.client.resources.sounds.AbstractSoundInstance
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.client.sounds.WeighedSoundEvents
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.util.valueproviders.ConstantFloat

class SpecificMusicInstance(
    location: ResourceLocation,
    private val specificSoundPath: String
) : AbstractSoundInstance(location, SoundSource.MUSIC, SoundInstance.createUnseededRandom()) {

    init {
        this.looping = false
        this.delay = 0
        this.volume = 1.0f
        this.pitch = 1.0f
        this.relative = true
        this.attenuation = SoundInstance.Attenuation.NONE
    }

    override fun resolve(manager: SoundManager): WeighedSoundEvents? {

        val events = manager.getSoundEvent(this.location)

        this.sound = Sound(
            //? if >=1.21 {
            /*ResourceLocation.parse(specificSoundPath),*/
            //?} else {
            specificSoundPath,
            //?}
            ConstantFloat.of(1.0f),
            ConstantFloat.of(1.0f),
            1,
            Sound.Type.FILE,
            true,
            false,
            16
        )
        return events
    }
}
