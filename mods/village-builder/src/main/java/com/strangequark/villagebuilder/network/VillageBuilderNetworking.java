package com.strangequark.villagebuilder.network;

import com.strangequark.villagebuilder.blueprint.TemplateRepository;
import com.strangequark.villagebuilder.plan.VillagePlanState;
import com.strangequark.villagebuilder.plan.PlannerSelections;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class VillageBuilderNetworking {
    private VillageBuilderNetworking() { }
    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(TemplateSyncPayload.ID, TemplateSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlanSyncPayload.ID, PlanSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SelectionSyncPayload.ID, SelectionSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenPickerPayload.ID, OpenPickerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectTemplatePayload.ID, SelectTemplatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SelectTemplatePayload.ID, (payload, context) -> {
            PlannerSelections.Selection selection = PlannerSelections.select(context.player(), payload.templateId());
            if (selection != null) syncSelection(context.player(), selection.index(), selection.rotation());
        });
    }
    public static void syncTemplates(MinecraftServer server) { TemplateSyncPayload payload = TemplateSyncPayload.from(TemplateRepository.all()); for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) ServerPlayNetworking.send(player, payload); }
    public static void syncPlans(MinecraftServer server) { PlanSyncPayload payload = PlanSyncPayload.from(VillagePlanState.get(server).all()); for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) ServerPlayNetworking.send(player, payload); }
    public static void syncPlayer(ServerPlayerEntity player) { ServerPlayNetworking.send(player, TemplateSyncPayload.from(TemplateRepository.all())); ServerPlayNetworking.send(player, PlanSyncPayload.from(VillagePlanState.get(player.getServer()).all())); }
    public static void syncSelection(ServerPlayerEntity player, int index, int rotation) { ServerPlayNetworking.send(player, new SelectionSyncPayload(index, rotation)); }
    public static void openPicker(ServerPlayerEntity player) { ServerPlayNetworking.send(player, new OpenPickerPayload()); }
}
