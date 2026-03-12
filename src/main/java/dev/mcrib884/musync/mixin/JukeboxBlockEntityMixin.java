package dev.mcrib884.musync.mixin;

import dev.mcrib884.musync.client.JukeboxTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JukeboxBlockEntity.class)
public abstract class JukeboxBlockEntityMixin {

    //? if >=1.20 && <1.21 {
    @Inject(method = "startPlaying", at = @At("TAIL"))
    private void musync$onStartPlaying(CallbackInfo ci) {
        JukeboxBlockEntity self = (JukeboxBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level != null) {
            BlockPos pos = self.getBlockPos();
            JukeboxTracker.INSTANCE.onJukeboxStartPlaying(level.dimension(), pos);
        }
    }

    @Inject(method = "stopPlaying", at = @At("TAIL"))
    private void musync$onStopPlaying(CallbackInfo ci) {
        JukeboxBlockEntity self = (JukeboxBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level != null) {
            BlockPos pos = self.getBlockPos();
            JukeboxTracker.INSTANCE.onJukeboxStopPlaying(level.dimension(), pos);
        }
    }
    //? } else if <1.20 {
    /*
    @Inject(method = "setRecord", at = @At("TAIL"))
    private void musync$onSetRecord(net.minecraft.world.item.ItemStack stack, CallbackInfo ci) {
        JukeboxBlockEntity self = (JukeboxBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level != null) {
            BlockPos pos = self.getBlockPos();
            if (!stack.isEmpty()) {
                JukeboxTracker.INSTANCE.onJukeboxStartPlaying(level.dimension(), pos);
            } else {
                JukeboxTracker.INSTANCE.onJukeboxStopPlaying(level.dimension(), pos);
            }
        }
    }

    @Inject(method = "clearContent", at = @At("HEAD"))
    private void musync$onClearContent(CallbackInfo ci) {
        JukeboxBlockEntity self = (JukeboxBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level != null) {
            BlockPos pos = self.getBlockPos();
            JukeboxTracker.INSTANCE.onJukeboxStopPlaying(level.dimension(), pos);
        }
    }
    */
    //? }
}
