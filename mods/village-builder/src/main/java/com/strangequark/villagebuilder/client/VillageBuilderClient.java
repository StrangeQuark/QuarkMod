package com.strangequark.villagebuilder.client;

import com.strangequark.villagebuilder.network.PlanSyncPayload;
import com.strangequark.villagebuilder.network.OpenPickerPayload;
import com.strangequark.villagebuilder.network.SelectionSyncPayload;
import com.strangequark.villagebuilder.network.TemplateSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public final class VillageBuilderClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(TemplateSyncPayload.ID, (payload, context) -> context.client().execute(() -> ClientBlueprints.setTemplates(payload.templates())));
        ClientPlayNetworking.registerGlobalReceiver(PlanSyncPayload.ID, (payload, context) -> context.client().execute(() -> ClientBlueprints.setPlans(payload.plans())));
        ClientPlayNetworking.registerGlobalReceiver(SelectionSyncPayload.ID, (payload, context) -> context.client().execute(() -> ClientBlueprints.setSelection(payload.index(), payload.rotation())));
        ClientPlayNetworking.registerGlobalReceiver(OpenPickerPayload.ID, (payload, context) -> context.client().execute(() -> context.client().setScreen(new BlueprintPickerScreen())));
        WorldRenderEvents.AFTER_ENTITIES.register(BlueprintRenderer::render);
    }
}
