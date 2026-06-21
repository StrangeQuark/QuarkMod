package com.strangequark.vampirism.client;

import com.strangequark.vampirism.entity.VampiricBatEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BatEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.state.BatEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class VampiricBatEntityRenderer extends MobEntityRenderer<VampiricBatEntity, BatEntityRenderState, BatEntityModel> {
    private static final float SCALE = 2.0F;
    private static final RenderLayer EYES = RenderLayer.getEyes(VampiricBatEntity.EYES_TEXTURE);

    public VampiricBatEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new BatEntityModel(context.getPart(EntityModelLayers.BAT)), 0.5F);
        this.addFeature(new VampiricBatEyesFeatureRenderer(this));
    }

    @Override
    public BatEntityRenderState createRenderState() {
        return new BatEntityRenderState();
    }

    @Override
    public void updateRenderState(VampiricBatEntity entity, BatEntityRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.roosting = entity.isRoosting();
        state.flyingAnimationState.copyFrom(entity.flyingAnimationState);
        state.roostingAnimationState.copyFrom(entity.roostingAnimationState);
    }

    @Override
    public Identifier getTexture(BatEntityRenderState state) {
        return VampiricBatEntity.TEXTURE;
    }

    @Override
    protected void scale(BatEntityRenderState state, MatrixStack matrices) {
        matrices.scale(SCALE, SCALE, SCALE);
    }

    private static class VampiricBatEyesFeatureRenderer extends EyesFeatureRenderer<BatEntityRenderState, BatEntityModel> {
        private VampiricBatEyesFeatureRenderer(FeatureRendererContext<BatEntityRenderState, BatEntityModel> context) {
            super(context);
        }

        @Override
        public RenderLayer getEyesTexture() {
            return EYES;
        }
    }
}
