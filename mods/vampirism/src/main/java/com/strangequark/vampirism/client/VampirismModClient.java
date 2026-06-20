package com.strangequark.vampirism.client;

import com.strangequark.vampirism.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class VampirismModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.VAMPIRE, VampireEntityRenderer::new);
    }
}
