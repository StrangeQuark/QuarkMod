package com.strangequark.villagebuilder.client;

import com.strangequark.villagebuilder.item.ModItems;
import com.strangequark.villagebuilder.network.PlanSyncPayload;
import com.strangequark.villagebuilder.network.TemplateSyncPayload;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class BlueprintRenderer {
    private static final double AIR_PLACEMENT_RANGE = 5.0D;

    private BlueprintRenderer() { }
    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || context.matrixStack() == null || context.consumers() == null) return;
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        MatrixStack matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        if (client.player.getMainHandStack().isOf(ModItems.PLANNER_WAND)) {
            renderPreview(client, matrices, lines, context.consumers(), camera);
            for (PlanSyncPayload.PlanWire plan : ClientBlueprints.plans()) if (plan.world().equals(client.world.getRegistryKey().getValue())) draw(matrices, lines, camera, box(plan.anchor(), plan.sizeX(), plan.sizeY(), plan.sizeZ(), plan.rotation()), 0.15F, 0.5F, 1.0F, 0.8F);
        }
    }
    private static void renderPreview(MinecraftClient client, MatrixStack matrices, VertexConsumer lines, VertexConsumerProvider consumers, Vec3d camera) {
        TemplateSyncPayload.TemplateWire template = ClientBlueprints.selected();
        if (template == null) return;
        BlockPos anchor = previewAnchor(client);
        draw(matrices, lines, camera, box(anchor, template.sizeX(), template.sizeY(), template.sizeZ(), ClientBlueprints.rotation()), 0.25F, 1.0F, 0.35F, 0.9F);
        for (TemplateSyncPayload.BlockWire block : template.blocks()) {
            BlockPos local = BlockPos.fromLong(block.pos());
            BlockPos rotated = rotate(local, template.sizeX(), template.sizeZ(), ClientBlueprints.rotation());
            BlockPos worldPos = anchor.add(rotated);
            matrices.push();
            matrices.translate(worldPos.getX() - camera.x, worldPos.getY() - camera.y, worldPos.getZ() - camera.z);
            client.getBlockRenderManager().renderBlockAsEntity(block.blockState(), matrices, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
            matrices.pop();
            draw(matrices, lines, camera, new Box(worldPos), 0.25F, 1.0F, 0.35F, 0.5F);
        }
    }
    private static BlockPos previewAnchor(MinecraftClient client) {
        if (client.crosshairTarget instanceof BlockHitResult hit) return hit.getBlockPos().offset(hit.getSide());
        HitResult hit = client.player.raycast(AIR_PLACEMENT_RANGE, 1.0F, false);
        return BlockPos.ofFloored(hit.getPos());
    }
    private static Box box(BlockPos anchor, int x, int y, int z, int rotation) { return new Box(anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getX() + (rotation % 2 == 0 ? x : z), anchor.getY() + y, anchor.getZ() + (rotation % 2 == 0 ? z : x)); }
    private static BlockPos rotate(BlockPos pos, int x, int z, int rotation) { return switch (Math.floorMod(rotation, 4)) { case 1 -> new BlockPos(z - 1 - pos.getZ(), pos.getY(), pos.getX()); case 2 -> new BlockPos(x - 1 - pos.getX(), pos.getY(), z - 1 - pos.getZ()); case 3 -> new BlockPos(pos.getZ(), pos.getY(), x - 1 - pos.getX()); default -> pos; }; }
    private static void draw(MatrixStack matrices, VertexConsumer lines, Vec3d camera, Box box, float red, float green, float blue, float alpha) { VertexRendering.drawBox(matrices, lines, box.offset(-camera.x, -camera.y, -camera.z), red, green, blue, alpha); }
}
