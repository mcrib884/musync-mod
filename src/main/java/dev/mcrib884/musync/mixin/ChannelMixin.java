package dev.mcrib884.musync.mixin;

import dev.mcrib884.musync.client.PausedSourceTracker;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(com.mojang.blaze3d.audio.Channel.class)
public class ChannelMixin {

    @Shadow
    private int source;

    @Inject(method = "unpause", at = @At("HEAD"), cancellable = true)
    private void musync$blockUnpause(CallbackInfo ci) {
        if (PausedSourceTracker.isMuSyncPaused(this.source)) {
            ci.cancel();
        }
    }

    @Inject(method = "pumpBuffers", at = @At("HEAD"), cancellable = true)
    private void musync$skipPumpWhenPaused(CallbackInfo ci) {
        if (PausedSourceTracker.isMuSyncPaused(this.source)) {
            ci.cancel();
        }
    }

    @Redirect(
        method = "pumpBuffers",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL10;alSourcePlay(I)V", remap = false)
    )
    private void musync$preventPausedResume(int source) {
        if (!PausedSourceTracker.isMuSyncPaused(source)) {
            AL10.alSourcePlay(source);
        }
    }
}
