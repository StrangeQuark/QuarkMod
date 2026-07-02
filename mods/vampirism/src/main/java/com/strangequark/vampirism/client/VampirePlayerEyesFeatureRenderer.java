package com.strangequark.vampirism.client;

import com.strangequark.vampirism.VampirismMod;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class VampirePlayerEyesFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private static final Identifier TEXTURE = VampirismMod.id("textures/entity/player_vampire_eyes.png");
    private static final RenderLayer EYES = RenderLayer.getEyes(TEXTURE);

    public VampirePlayerEyesFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            PlayerEntityRenderState state,
            float limbAngle,
            float limbDistance
    ) {
        if (!((VampirePlayerRenderState) state).quarkmod_vampirism$isVampire() || state.invisible) {
            return;
        }

        PlayerEntityModel model = this.getContextModel();
        boolean hatVisible = model.hat.visible;
        model.hat.visible = false;

        matrices.push();
        try {
            VertexConsumer vertexConsumer = vertexConsumers.getBuffer(EYES);
            model.head.render(matrices, vertexConsumer, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
        } finally {
            matrices.pop();
            model.hat.visible = hatVisible;
        }
    }
}
