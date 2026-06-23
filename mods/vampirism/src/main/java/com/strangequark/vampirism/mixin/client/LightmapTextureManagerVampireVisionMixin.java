package com.strangequark.vampirism.mixin.client;

import com.strangequark.vampirism.vampire.VampireData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerVampireVisionMixin {
    private static final float VAMPIRE_NIGHT_VISION_STRENGTH = 0.1F;

    @Shadow
    @Final
    private MinecraftClient client;

    @ModifyArg(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;",
                    ordinal = 3
            ),
            index = 0
    )
    private float quarkmod_vampirism$applyPartialVampireNightVision(float nightVisionStrength) {
        if (this.client.player == null || !VampireData.isVampire(this.client.player)) {
            return nightVisionStrength;
        }

        return Math.max(nightVisionStrength, VAMPIRE_NIGHT_VISION_STRENGTH);
    }
}
