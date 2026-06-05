package com.strangequark.dreamdimension.client;

import com.strangequark.dreamdimension.DreamDimensionMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.util.math.Vec3d;

public class DreamDimensionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DimensionRenderingRegistry.registerDimensionEffects(DreamDimensionMod.id("dream"), new DreamEffects());
    }

    private static class DreamEffects extends DimensionEffects {
        private static final int SKY_COLOR = 0xA8A4C8;
        private static final Vec3d FOG_COLOR = new Vec3d(0.58D, 0.60D, 0.72D);

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
}
