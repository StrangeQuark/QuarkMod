package com.strangequark.ancientmaze.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.strangequark.ancientmaze.AncientMazeMod;
import com.strangequark.ancientmaze.network.EchoProspectorPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.OptionalDouble;

public final class AncientMazeClient implements ClientModInitializer {
    private static final int HIGHLIGHT_LIFETIME_TICKS = 20 * 12;
    private static final RenderLayer ECHO_FILL_LAYER = echoFillLayer();
    private static final RenderLayer ECHO_LINE_LAYER = echoLineLayer();

    private static List<BlockPos> echoPositions = List.of();
    private static int echoTicksRemaining;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                EchoProspectorPayload.ID,
                (payload, context) -> showEchoHighlights(payload.positions())
        );
        ClientTickEvents.END_CLIENT_TICK.register(client -> tickEchoHighlights(client.world == null));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearEchoHighlights());
        WorldRenderEvents.LAST.register(AncientMazeClient::renderEchoHighlights);
    }

    private static void showEchoHighlights(List<BlockPos> positions) {
        echoPositions = List.copyOf(positions);
        echoTicksRemaining = HIGHLIGHT_LIFETIME_TICKS;
    }

    private static void tickEchoHighlights(boolean noWorld) {
        if (noWorld || echoTicksRemaining <= 0) {
            clearEchoHighlights();
            return;
        }
        echoTicksRemaining--;
    }

    private static void clearEchoHighlights() {
        echoPositions = List.of();
        echoTicksRemaining = 0;
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
        float fade = Math.min(1.0F, echoTicksRemaining / 40.0F);
        float fillAlpha = 0.18F * fade;
        float lineAlpha = 0.78F * fade;

        BufferBuilder fill = Tessellator.getInstance().begin(ECHO_FILL_LAYER.getDrawMode(), ECHO_FILL_LAYER.getVertexFormat());
        for (BlockPos pos : echoPositions) {
            Box box = new Box(pos).expand(0.03D).offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            VertexRendering.drawFilledBox(matrices, fill, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 0.1F, 0.85F, 1.0F, fillAlpha);
        }
        drawBuffer(ECHO_FILL_LAYER, fill);

        BufferBuilder lines = Tessellator.getInstance().begin(ECHO_LINE_LAYER.getDrawMode(), ECHO_LINE_LAYER.getVertexFormat());
        for (BlockPos pos : echoPositions) {
            Box box = new Box(pos).expand(0.03D).offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            VertexRendering.drawBox(matrices, lines, box, 0.2F, 0.95F, 1.0F, lineAlpha);
        }
        drawBuffer(ECHO_LINE_LAYER, lines);
    }

    private static void drawBuffer(RenderLayer layer, BufferBuilder buffer) {
        BuiltBuffer builtBuffer = buffer.endNullable();
        if (builtBuffer != null) {
            layer.draw(builtBuffer);
        }
    }

    private static RenderLayer echoFillLayer() {
        RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                .withLocation(AncientMazeMod.id("pipeline/echo_prospector_fill"))
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

    private static RenderLayer echoLineLayer() {
        RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                .withLocation(AncientMazeMod.id("pipeline/echo_prospector_lines"))
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .build());
        return RenderLayer.of(
                "quarkmod_echo_prospector_lines",
                1536,
                false,
                false,
                pipeline,
                RenderLayer.MultiPhaseParameters.builder()
                        .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(2.5D)))
                        .build(false)
        );
    }
}
