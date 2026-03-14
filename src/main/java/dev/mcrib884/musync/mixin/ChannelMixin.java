package dev.mcrib884.musync.mixin;

import dev.mcrib884.musync.client.PausedSourceTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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

}
