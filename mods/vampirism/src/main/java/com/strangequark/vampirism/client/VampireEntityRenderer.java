package com.strangequark.vampirism.client;

import com.strangequark.vampirism.entity.VampireEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

public class VampireEntityRenderer extends BipedEntityRenderer<VampireEntity, PlayerEntityRenderState, PlayerEntityModel> {
    public VampireEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), false), 0.5F);
        this.addFeature(new VampirePlayerEyesFeatureRenderer(this));
    }

    @Override
    public PlayerEntityRenderState createRenderState() {
        return new PlayerEntityRenderState();
    }

    @Override
    public void updateRenderState(VampireEntity entity, PlayerEntityRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.skinTextures = new SkinTextures(entity.getSkinTexture(), "", null, null, SkinTextures.Model.WIDE, true);
        state.hatVisible = true;
        state.jacketVisible = true;
        state.leftPantsLegVisible = true;
        state.rightPantsLegVisible = true;
        state.leftSleeveVisible = true;
        state.rightSleeveVisible = true;
        state.capeVisible = false;
        ((VampirePlayerRenderState) state).quarkmod_vampirism$setVampire(true);
    }

    @Override
    public Identifier getTexture(PlayerEntityRenderState state) {
        return state.skinTextures == null ? VampireEntity.DEFAULT_SKIN : state.skinTextures.texture();
    }
}
