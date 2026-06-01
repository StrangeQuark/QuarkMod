package com.strangequark.client;

import com.strangequark.entity.GrapplingHookEntity;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public class GrapplingHookEntityRenderer extends EntityRenderer<GrapplingHookEntity, GrapplingHookEntityRenderState> {
    private static final int LINE_SEGMENTS = 16;
    private static final int ROPE_COLOR = 0xFF3A2A1A;
    private static final float HOOK_SCALE = 0.45F;
    private final ItemModelManager itemModelManager;

    public GrapplingHookEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.itemModelManager = context.getItemModelManager();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(GrapplingHookEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        if (state.hooked) {
            Vec3d normal = state.anchorSide.getDoubleVector();
            matrices.translate(normal.x * 0.06D, normal.y * 0.06D, normal.z * 0.06D);
        }
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.yaw - 90.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(state.pitch));
        matrices.scale(HOOK_SCALE, HOOK_SCALE, HOOK_SCALE);
        state.itemRenderState.render(matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();

        if (state.hasLine) {
            renderLine(state.lineToOwner, matrices, vertexConsumers);
        }

        super.render(state, matrices, vertexConsumers, light);
    }

    @Override
    public GrapplingHookEntityRenderState createRenderState() {
        return new GrapplingHookEntityRenderState();
    }

    @Override
    public void updateRenderState(GrapplingHookEntity entity, GrapplingHookEntityRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        this.itemModelManager.updateForNonLivingEntity(state.itemRenderState, entity.getStack(), ItemDisplayContext.GROUND, entity);
        state.hooked = entity.isHooked();

        if (entity.isHooked()) {
            Direction side = entity.getAnchorSide();
            state.anchorSide = side;
            state.yaw = side.getAxis().isHorizontal() ? side.getPositiveHorizontalDegrees() : entity.getLerpedYaw(tickDelta);
            state.pitch = side == Direction.UP ? -90.0F : side == Direction.DOWN ? 90.0F : 0.0F;
        } else {
            state.anchorSide = Direction.UP;
            state.yaw = entity.getLerpedYaw(tickDelta);
            state.pitch = entity.getLerpedPitch(tickDelta);
        }

        Entity owner = entity.getOwner();
        if (owner == null && entity.getHookOwnerId() != -1) {
            owner = entity.getWorld().getEntityById(entity.getHookOwnerId());
        }
        if (owner == null) {
            state.hasLine = false;
            state.lineToOwner = Vec3d.ZERO;
            return;
        }

        Vec3d hookPos = entity.getLerpedPos(tickDelta).add(0.0D, entity.getHeight() * 0.45D, 0.0D);
        Vec3d ownerPos = owner.getLeashPos(tickDelta);
        state.lineToOwner = ownerPos.subtract(hookPos);
        state.hasLine = true;
    }

    @Override
    protected boolean canBeCulled(GrapplingHookEntity entity) {
        return false;
    }

    private static void renderLine(Vec3d line, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getLineStrip());
        MatrixStack.Entry entry = matrices.peek();
        float length = (float) line.length();
        float sag = MathHelper.clamp(length * 0.015F, 0.02F, 0.16F);

        for (int segment = 0; segment <= LINE_SEGMENTS; segment++) {
            float progress = (float) segment / LINE_SEGMENTS;
            float x = (float) line.x * progress;
            float y = (float) line.y * progress - sag * MathHelper.sin(progress * MathHelper.PI);
            float z = (float) line.z * progress;
            consumer.vertex(entry, x, y, z)
                    .color(ROPE_COLOR)
                    .normal(entry, 0.0F, 1.0F, 0.0F);
        }
    }
}
