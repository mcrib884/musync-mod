package dev.mcrib884.musync.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = Minecraft.class, priority = 2000)
public class MinecraftMusicMixin {
}