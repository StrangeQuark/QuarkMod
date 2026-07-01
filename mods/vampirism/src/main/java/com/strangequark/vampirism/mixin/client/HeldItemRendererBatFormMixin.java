package com.strangequark.vampirism.mixin.client;

import com.strangequark.vampirism.vampire.VampireData;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
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

    @Inject(method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"))
    private void quarkmod_vampirism$applyBloodDrainFirstPersonPose(
            AbstractClientPlayerEntity player,
            float tickProgress,
            float pitch,
            Hand hand,
            float swingProgress,
            ItemStack stack,
            float equipProgress,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        boolean directFeeding = VampireData.isDirectFeeding(player);
        boolean siphoningBlood = VampireData.isSiphoningBlood(player);
        if (!directFeeding && !siphoningBlood) {
            return;
        }

        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        int side = arm == Arm.RIGHT ? 1 : -1;
        float pulse = MathHelper.sin((player.age + tickProgress) * 0.55F) * 0.02F;

        if (directFeeding) {
            matrices.translate(side * -0.12F, -0.16F + pulse, -0.22F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-22.0F + pulse * 180.0F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 20.0F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -8.0F));
            return;
        }

        matrices.translate(side * -0.06F, -0.08F + pulse, -0.12F);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-12.0F + pulse * 120.0F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 10.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -4.0F));
    }
}
