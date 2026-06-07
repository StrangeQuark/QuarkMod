package com.strangequark.dreamdimension;

import com.strangequark.dreamdimension.block.ModBlocks;
import com.strangequark.dreamdimension.effect.ModStatusEffects;
import com.strangequark.dreamdimension.loot.DreamLootTables;
import com.strangequark.dreamdimension.network.DreamTransitionPayload;
import com.strangequark.dreamdimension.potion.ModPotions;
import com.strangequark.dreamdimension.state.ModAttachments;
import com.strangequark.dreamdimension.village.DreamVillagers;
import com.strangequark.dreamdimension.world.DreamDimensionEvents;
import com.strangequark.dreamdimension.world.structure.ModStructureTypes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DreamDimensionMod implements ModInitializer {
    public static final String MOD_ID = "quarkmod_dream_dimension";
    public static final String REGISTRY_NAMESPACE = "quarkmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(REGISTRY_NAMESPACE, path);
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(DreamTransitionPayload.ID, DreamTransitionPayload.CODEC);
        ModAttachments.registerAttachments();
        ModStructureTypes.registerStructureTypes();
        ModBlocks.registerModBlocks();
        ModStatusEffects.registerStatusEffects();
        ModPotions.registerPotions();
        ModPotions.registerBrewingRecipes();
        DreamVillagers.register();
        DreamLootTables.register();
        DreamDimensionEvents.registerEvents();

        LOGGER.info("QuarkMod Dream Dimension init success");
    }
}
