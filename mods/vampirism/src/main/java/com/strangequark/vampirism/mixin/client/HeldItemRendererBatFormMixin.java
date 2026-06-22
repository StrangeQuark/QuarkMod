package com.strangequark.vampirism.mixin.client;

import com.strangequark.vampirism.vampire.VampireData;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererBatFormMixin {
    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"), cancellable = true)
    private void quarkmod_vampirism$hideFirstPersonHandsInBatForm(
            float tickProgress,
            MatrixStack matrices,
            VertexConsumerProvider.Immediate vertexConsumers,
            ClientPlayerEntity player,
            int light,
            CallbackInfo ci
    ) {
        if (VampireData.isBatForm(player)) {
            ci.cancel();
        }
    }
}
