package com.strangequark.villagebuilder;

import com.strangequark.villagebuilder.blueprint.TemplateRepository;
import com.strangequark.villagebuilder.command.VillageBuilderCommands;
import com.strangequark.villagebuilder.item.ModItems;
import com.strangequark.villagebuilder.network.VillageBuilderNetworking;
import com.strangequark.villagebuilder.plan.VillagePlanState;
import com.strangequark.villagebuilder.village.CityVillagers;
import com.strangequark.villagebuilder.village.ModBlocks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VillageBuilderMod implements ModInitializer {
    public static final String MOD_ID = "quarkmod_village_builder";
    public static final String REGISTRY_NAMESPACE = "quarkmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(REGISTRY_NAMESPACE, path);
    }

    @Override
    public void onInitialize() {
        VillageBuilderNetworking.registerPayloads();
        ModBlocks.register();
        ModItems.register();
        CityVillagers.register();
        VillageBuilderCommands.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            TemplateRepository.reload(server);
            VillagePlanState.get(server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> VillageBuilderNetworking.syncPlayer(handler.player));
        LOGGER.info("QuarkMod Village Builder init success");
    }
}
