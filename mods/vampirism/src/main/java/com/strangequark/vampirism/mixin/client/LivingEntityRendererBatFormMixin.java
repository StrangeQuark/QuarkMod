package com.strangequark.vampirism.mixin.client;

import com.strangequark.vampirism.client.BatFormPlayerRenderState;
import com.strangequark.vampirism.entity.VampiricBatEntity;
import com.strangequark.vampirism.vampire.PlayerBatForm;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BatEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.state.BatEntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererBatFormMixin {
    @Unique
    private BatEntityModel quarkmod_vampirism$playerBatModel;

    @Shadow
    protected abstract void setupTransforms(LivingEntityRenderState state, MatrixStack matrices, float bodyYaw, float baseHeight);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void quarkmod_vampirism$initBatModel(EntityRendererFactory.Context context, EntityModel<?> model, float shadowRadius, CallbackInfo ci) {
        if ((Object) this instanceof PlayerEntityRenderer) {
            this.quarkmod_vampirism$playerBatModel = new BatEntityModel(context.getPart(EntityModelLayers.BAT));
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"), cancellable = true)
    private void quarkmod_vampirism$renderPlayerAsBat(
            LivingEntityRenderState state,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        if (!(state instanceof BatFormPlayerRenderState batFormState) || !batFormState.quarkmod_vampirism$isBatForm()) {
            return;
        }

        if (this.quarkmod_vampirism$playerBatModel == null) {
            return;
        }

        ci.cancel();
        BatEntityRenderState batState = batFormState.quarkmod_vampirism$getBatRenderState();
        matrices.push();
        float baseScale = state.baseScale;
        matrices.scale(baseScale, baseScale, baseScale);
        this.setupTransforms(state, matrices, state.bodyYaw, baseScale);
        matrices.scale(-1.0F, -1.0F, 1.0F);
        matrices.scale(2.0F, 2.0F, 2.0F);
        matrices.translate(0.0F, -1.501F, 0.0F);

        this.quarkmod_vampirism$playerBatModel.setAngles(batState);
        this.quarkmod_vampirism$playerBatModel.render(
                matrices,
                vertexConsumers.getBuffer(this.quarkmod_vampirism$playerBatModel.getLayer(VampiricBatEntity.TEXTURE)),
                light,
                OverlayTexture.DEFAULT_UV
        );
        this.quarkmod_vampirism$playerBatModel.render(
                matrices,
                vertexConsumers.getBuffer(RenderLayer.getEyes(VampiricBatEntity.EYES_TEXTURE)),
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV
        );
        matrices.pop();
    }
}
