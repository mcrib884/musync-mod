package dev.mcrib884.musync.mixin;

import dev.mcrib884.musync.client.ClientMusicPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Unique
    private int musync$jukeboxCheckCounter = 0;

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {

        if (++musync$jukeboxCheckCounter < 40) {
            return;
        }
        musync$jukeboxCheckCounter = 0;

        Minecraft self = (Minecraft) (Object) this;
        LocalPlayer player = self.player;

        if (player == null || player.level() == null) {
            return;
        }

        BlockPos playerPos = player.blockPosition();
        int range = 65;
        boolean nearJukebox = false;

        int chunkRange = (range >> 4) + 1;
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;

        outer:
        for (int cx = -chunkRange; cx <= chunkRange; cx++) {
            for (int cz = -chunkRange; cz <= chunkRange; cz++) {
                var chunk = player.level().getChunk(playerChunkX + cx, playerChunkZ + cz);
                for (var blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof JukeboxBlockEntity jukebox) {
                        if (jukebox.getBlockPos().closerThan(playerPos, range)) {
                            if (!jukebox.getItem(0).isEmpty()) {
                                nearJukebox = true;
                                break outer;
                            }
                        }
                    }
                }
            }
        }

        ClientMusicPlayer.INSTANCE.setInJukeboxRange(nearJukebox);
    }
}
