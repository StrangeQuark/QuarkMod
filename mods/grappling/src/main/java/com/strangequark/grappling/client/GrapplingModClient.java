package com.strangequark.grappling.client;

import com.strangequark.grappling.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class GrapplingModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.GRAPPLING_HOOK, GrapplingHookEntityRenderer::new);
    }
}
