package com.strangequark.dreamdimension.mixin.client;

import com.strangequark.dreamdimension.DreamDimensionMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRendering.class)
public class SkyRenderingMixin {
    @Unique
    private static final RegistryKey<World> DREAM_WORLD = RegistryKey.of(RegistryKeys.WORLD, DreamDimensionMod.id("dream"));
    @Unique
    private static final float DREAM_MOON_SCALE = 2.0F;

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void hideDreamSun(float alpha, VertexConsumerProvider vertexConsumers, MatrixStack matrices, CallbackInfo ci) {
        if (isDreamWorld()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderMoon", at = @At("HEAD"))
    private void scaleDreamMoon(
            int phase,
            float alpha,
            VertexConsumerProvider vertexConsumers,
            MatrixStack matrices,
            CallbackInfo ci
    ) {
        if (isDreamWorld()) {
            matrices.push();
            matrices.scale(DREAM_MOON_SCALE, 1.0F, DREAM_MOON_SCALE);
        }
    }

    @Inject(method = "renderMoon", at = @At("RETURN"))
    private void restoreDreamMoonScale(
            int phase,
            float alpha,
            VertexConsumerProvider vertexConsumers,
            MatrixStack matrices,
            CallbackInfo ci
    ) {
        if (isDreamWorld()) {
            matrices.pop();
        }
    }

    @Unique
    private static boolean isDreamWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world != null && client.world.getRegistryKey().equals(DREAM_WORLD);
    }
}
