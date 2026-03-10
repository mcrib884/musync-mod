package dev.mcrib884.musync.mixin;

import dev.mcrib884.musync.client.ClientMusicPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Minecraft.class, priority = 2000)
public class MinecraftMusicMixin {

    @Inject(method = "getSituationalMusic", at = @At("HEAD"), cancellable = true)
    private void musync$disableSituationalMusic(CallbackInfoReturnable<Music> cir) {
        if (ClientMusicPlayer.INSTANCE.getMusyncActive()) {
            cir.setReturnValue(null);
            cir.cancel();
        }
    }
}