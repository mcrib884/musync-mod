package dev.mcrib884.musync.mixin;

import dev.mcrib884.musync.client.ClientMusicPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(value = MusicManager.class, priority = 2000)
public class MusicManagerMixin {

    @Shadow @Nullable private SoundInstance currentMusic;
    @Shadow private int nextSongDelay;

    @Inject(method = "startPlaying", at = @At("HEAD"), cancellable = true)
    private void musync$blockStartPlaying(Music music, CallbackInfo ci) {
        if (ClientMusicPlayer.INSTANCE.getMusyncActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(CallbackInfo ci) {

        Minecraft mc = Minecraft.getInstance();
        if (ClientMusicPlayer.INSTANCE.getMusyncActive()) {

            if (this.currentMusic != null) {
                mc.getSoundManager().stop(this.currentMusic);
                this.currentMusic = null;
            }

            this.nextSongDelay = Integer.MAX_VALUE;
            ci.cancel();
            return;
        }
    }
}
