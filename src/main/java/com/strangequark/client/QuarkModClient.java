package com.strangequark.client;

import com.strangequark.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class QuarkModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.GRAPPLING_HOOK, GrapplingHookEntityRenderer::new);
    }
}
