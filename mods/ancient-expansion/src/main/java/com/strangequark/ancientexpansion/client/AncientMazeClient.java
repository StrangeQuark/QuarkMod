package com.strangequark.ancientexpansion.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.strangequark.ancientexpansion.AncientExpansionMod;
import com.strangequark.ancientexpansion.network.EchoProspectorPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.VibrationParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.BlockPositionSource;
import net.minecraft.world.event.EntityPositionSource;

import java.util.List;

public final class AncientMazeClient implements ClientModInitializer {
    private static final int HIGHLIGHT_LIFETIME_TICKS = 20 * 12;
    private static final int VIBRATION_ARRIVAL_TICKS = 28;
    private static final double VIBRATION_FIRST_PERSON_FORWARD_OFFSET = 1.35D;
    private static final RenderLayer ECHO_FILL_LAYER = echoFillLayer();

    private static List<BlockPos> pendingEchoPositions = List.of();
    private static List<BlockPos> echoPositions = List.of();
    private static int echoTicksRemaining;
    private static int echoOutwardTicksRemaining;
    private static int echoReturnTicksRemaining;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                EchoProspectorPayload.ID,
                (payload, context) -> context.client().execute(() -> showEchoHighlights(payload.positions()))
        );
        ClientTickEvents.END_CLIENT_TICK.register(client -> tickEchoHighlights(client.world == null));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearEchoHighlights());
        WorldRenderEvents.LAST.register(AncientMazeClient::renderEchoHighlights);
    }

    private static void showEchoHighlights(List<BlockPos> positions) {
        List<BlockPos> copiedPositions = List.copyOf(positions);
        if (copiedPositions.isEmpty()) {
            clearEchoHighlights();
            return;
        }

        pendingEchoPositions = copiedPositions;
        echoPositions = List.of();
        echoTicksRemaining = 0;
        echoReturnTicksRemaining = 0;
        echoOutwardTicksRemaining = VIBRATION_ARRIVAL_TICKS;
        spawnOutwardEchoVibrations(pendingEchoPositions);
    }

    private static void tickEchoHighlights(boolean noWorld) {
        if (noWorld) {
            clearEchoHighlights();
            return;
        }
        if (echoOutwardTicksRemaining > 0) {
            echoOutwardTicksRemaining--;
            if (echoOutwardTicksRemaining == 0) {
                echoReturnTicksRemaining = VIBRATION_ARRIVAL_TICKS;
                spawnReturnEchoVibrations(pendingEchoPositions);
            }
            return;
        }
        if (echoReturnTicksRemaining > 0) {
            echoReturnTicksRemaining--;
            if (echoReturnTicksRemaining == 0) {
                echoPositions = pendingEchoPositions;
                pendingEchoPositions = List.of();
                echoTicksRemaining = HIGHLIGHT_LIFETIME_TICKS;
            }
            return;
        }
        if (echoTicksRemaining > 0) {
            echoTicksRemaining--;
        } else if (!echoPositions.isEmpty()) {
            echoPositions = List.of();
        }
    }

    private static void clearEchoHighlights() {
        pendingEchoPositions = List.of();
        echoPositions = List.of();
        echoTicksRemaining = 0;
        echoOutwardTicksRemaining = 0;
        echoReturnTicksRemaining = 0;
    }

    private static void renderEchoHighlights(WorldRenderContext context) {
        if (echoTicksRemaining <= 0 || echoPositions.isEmpty()) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) {
            return;
        }

        Vec3d cameraPos = context.camera().getPos();
        float tickDelta = context.tickCounter().getTickProgress(false);
        float age = HIGHLIGHT_LIFETIME_TICKS - echoTicksRemaining + tickDelta;
        float fade = Math.min(1.0F, echoTicksRemaining / 40.0F);
        float pulse = 0.5F + 0.5F * (float) Math.sin(age * 0.1F);
        float fillAlpha = (0.075F + 0.065F * pulse) * fade;

        BufferBuilder fill = Tessellator.getInstance().begin(ECHO_FILL_LAYER.getDrawMode(), ECHO_FILL_LAYER.getVertexFormat());
        for (BlockPos pos : echoPositions) {
            Box box = new Box(pos).offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            VertexRendering.drawFilledBox(matrices, fill, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 0.25F, 1.0F, 0.95F, fillAlpha);
        }
        drawBuffer(ECHO_FILL_LAYER, fill);
    }

    private static Vec3d echoVibrationOrigin() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return Vec3d.ZERO;
        }
        return player.getEyePos().add(player.getRotationVec(1.0F).multiply(VIBRATION_FIRST_PERSON_FORWARD_OFFSET));
    }

    private static ClientPlayerEntity currentPlayer() {
        return MinecraftClient.getInstance().player;
    }

    private static void spawnOutwardEchoVibrations(List<BlockPos> positions) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || positions.isEmpty()) {
            return;
        }

        Vec3d origin = echoVibrationOrigin();
        for (BlockPos pos : positions) {
            client.world.addImportantParticleClient(
                    new VibrationParticleEffect(new BlockPositionSource(pos), VIBRATION_ARRIVAL_TICKS),
                    origin.x,
                    origin.y,
                    origin.z,
                    0.0D,
                    0.0D,
                    0.0D
            );
        }
    }

    private static void spawnReturnEchoVibrations(List<BlockPos> positions) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = currentPlayer();
        if (client.world == null || player == null || positions.isEmpty()) {
            return;
        }

        EntityPositionSource playerTarget = new EntityPositionSource(player, player.getStandingEyeHeight());
        for (BlockPos pos : positions) {
            Vec3d origin = Vec3d.ofCenter(pos);
            client.world.addImportantParticleClient(
                    new VibrationParticleEffect(playerTarget, VIBRATION_ARRIVAL_TICKS),
                    origin.x,
                    origin.y,
                    origin.z,
                    0.0D,
                    0.0D,
                    0.0D
            );
        }
    }

    private static void drawBuffer(RenderLayer layer, BufferBuilder buffer) {
        BuiltBuffer builtBuffer = buffer.endNullable();
        if (builtBuffer != null) {
            layer.draw(builtBuffer);
        }
    }

    private static RenderLayer echoFillLayer() {
        RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                .withLocation(AncientExpansionMod.id("pipeline/echo_prospector_fill"))
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLE_STRIP)
                .build());
        return RenderLayer.of(
                "quarkmod_echo_prospector_fill",
                1536,
                false,
                true,
                pipeline,
                RenderLayer.MultiPhaseParameters.builder().build(false)
        );
    }
}
