package com.strangequark.dreamdimension.client;

import com.strangequark.dreamdimension.DreamDimensionMod;
import com.strangequark.dreamdimension.block.ModBlocks;
import com.strangequark.dreamdimension.network.DreamTransitionPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class DreamDimensionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DimensionRenderingRegistry.registerDimensionEffects(DreamDimensionMod.id("dream"), new DreamEffects());
        BlockRenderLayerMap.putBlocks(
                BlockRenderLayer.TRANSLUCENT,
                ModBlocks.ETHEREAL_ORE,
                ModBlocks.ETHEREAL_LOG,
                ModBlocks.ETHEREAL_LEAVES
        );
        ClientPlayNetworking.registerGlobalReceiver(
                DreamTransitionPayload.ID,
                (payload, context) -> DreamTransitionOverlay.start(payload.durationTicks())
        );
        ClientTickEvents.END_CLIENT_TICK.register(client -> DreamTransitionOverlay.tick());
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.SLEEP,
                DreamDimensionMod.id("dream_transition_overlay"),
                DreamTransitionOverlay::render
        );
    }

    private static class DreamEffects extends DimensionEffects {
        private static final int SKY_COLOR = 0x686884;
        private static final Vec3d FOG_COLOR = new Vec3d(0.50D, 0.47D, 0.62D);

        private DreamEffects() {
            super(SkyType.NORMAL, false, false);
        }

        @Override
        public boolean isSunRisingOrSetting(float skyAngle) {
            return false;
        }

        @Override
        public int getSkyColor(float skyAngle) {
            return SKY_COLOR;
        }

        @Override
        public Vec3d adjustFogColor(Vec3d color, float sunHeight) {
            return FOG_COLOR;
        }

        @Override
        public boolean useThickFog(int cameraX, int cameraY) {
            return false;
        }
    }

    private static final class DreamTransitionOverlay {
        private static int totalTicks;
        private static int elapsedTicks;
        private static boolean active;
        private static boolean holdAfterFade;

        private DreamTransitionOverlay() {
        }

        private static void start(int durationTicks) {
            if (durationTicks == 0) {
                totalTicks = 0;
                elapsedTicks = 0;
                active = false;
                holdAfterFade = false;
                return;
            }

            totalTicks = Math.max(1, Math.abs(durationTicks));
            elapsedTicks = 0;
            active = true;
            holdAfterFade = durationTicks < 0;
        }

        private static void tick() {
            if (!active) {
                return;
            }

            if (elapsedTicks < totalTicks) {
                elapsedTicks++;
            } else if (!holdAfterFade) {
                active = false;
            }
        }

        private static void render(DrawContext context, RenderTickCounter tickCounter) {
            if (!active || totalTicks <= 0) {
                return;
            }

            int width = context.getScaledWindowWidth();
            int height = context.getScaledWindowHeight();
            float tickProgress = tickCounter.getTickProgress(false);
            float progress = MathHelper.clamp((elapsedTicks + tickProgress) / totalTicks, 0.0F, 1.0F);
            float fadeIn = MathHelper.clamp(progress / 0.62F, 0.0F, 1.0F);
            float smoothedFade = fadeIn * fadeIn * (3.0F - 2.0F * fadeIn);

            context.createNewRootLayer();
            context.fill(0, 0, width, height, argb(smoothedFade * 0.98F, 0, 0, 0));
        }

        private static int argb(float alpha, int red, int green, int blue) {
            int alphaChannel = MathHelper.clamp((int) (alpha * 255.0F), 0, 255);
            return alphaChannel << 24 | red << 16 | green << 8 | blue;
        }
    }
}
