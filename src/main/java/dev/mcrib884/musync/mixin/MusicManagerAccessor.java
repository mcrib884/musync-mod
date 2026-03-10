package dev.mcrib884.musync.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nullable;

@Mixin(MusicManager.class)
public interface MusicManagerAccessor {

    @Accessor("currentMusic")
    @Nullable
    SoundInstance getCurrentMusic();

    @Accessor("currentMusic")
    void setCurrentMusic(@Nullable SoundInstance currentMusic);

    @Accessor("nextSongDelay")
    void setNextSongDelay(int nextSongDelay);
}