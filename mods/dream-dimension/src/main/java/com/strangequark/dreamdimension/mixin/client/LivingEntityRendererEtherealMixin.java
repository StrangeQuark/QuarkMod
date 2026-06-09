package com.strangequark.dreamdimension.mixin.client;

import com.strangequark.dreamdimension.DreamDimensionMod;
import com.strangequark.dreamdimension.client.EtherealRenderStateAccess;
import com.strangequark.dreamdimension.ethereal.EtherealEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererEtherealMixin<S extends LivingEntityRenderState> {
    private static final Identifier ETHEREAL_MOB_TEXTURE = DreamDimensionMod.id("textures/entity/ethereal_mob.png");

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("RETURN")
    )
    private void quarkmod$updateEtherealRenderState(
            LivingEntity entity,
            LivingEntityRenderState state,
            float tickProgress,
            CallbackInfo ci
    ) {
        boolean ethereal = EtherealEvents.isEtherealEntity(entity);
        ((EtherealRenderStateAccess) state).quarkmod$setEthereal(ethereal);
    }

    @Inject(
            method = "getRenderLayer(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/render/RenderLayer;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quarkmod$getEtherealRenderLayer(
            LivingEntityRenderState state,
            boolean visible,
            boolean translucent,
            boolean outline,
            CallbackInfoReturnable<RenderLayer> cir
    ) {
        if (((EtherealRenderStateAccess) state).quarkmod$isEthereal() && visible) {
            cir.setReturnValue(RenderLayer.getEntityTranslucentEmissive(ETHEREAL_MOB_TEXTURE));
        }
    }

    @Inject(
            method = "getMixColor(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quarkmod$getEtherealMixColor(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        if (((EtherealRenderStateAccess) state).quarkmod$isEthereal()) {
            int alpha = 205 + MathHelper.clamp((int) (MathHelper.sin(state.age * 0.16F) * 24.0F), -24, 24);
            cir.setReturnValue(alpha << 24 | 0xE8FEFF);
        }
    }
}
