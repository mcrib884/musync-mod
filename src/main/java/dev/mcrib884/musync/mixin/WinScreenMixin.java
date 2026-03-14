package dev.mcrib884.musync.mixin;

import dev.mcrib884.musync.network.MusicControlPacket;
import dev.mcrib884.musync.network.PacketHandler;
import net.minecraft.client.gui.screens.WinScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WinScreen.class)
public class WinScreenMixin {

    @Inject(method = "onClose", at = @At("HEAD"))
    private void musync$onCreditsSkipped(CallbackInfo ci) {
        PacketHandler.INSTANCE.sendToServer(new MusicControlPacket(MusicControlPacket.Action.CREDITS_SKIP, null, null, 0L, null));
    }
}
