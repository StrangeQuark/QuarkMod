package com.strangequark.vampirism.mixin.client;

import com.strangequark.vampirism.client.VampirePlayerEyesFeatureRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererVampireEyesMixin extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityRenderState, PlayerEntityModel> {
    protected PlayerEntityRendererVampireEyesMixin(EntityRendererFactory.Context context, PlayerEntityModel model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void quarkmod_vampirism$addVampireEyesFeature(EntityRendererFactory.Context context, boolean slim, CallbackInfo ci) {
        this.addFeature(new VampirePlayerEyesFeatureRenderer(this));
    }
}
