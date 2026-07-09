package com.strangequark.ancientexpansion;

import com.strangequark.ancientexpansion.enchantment.AncientEnchantmentLogic;
import com.strangequark.ancientexpansion.block.ModBlocks;
import com.strangequark.ancientexpansion.item.ModItems;
import com.strangequark.ancientexpansion.loot.AncientExpansionLootTables;
import com.strangequark.ancientexpansion.network.EchoProspectorPayload;
import com.strangequark.ancientexpansion.trial.AncientCityTrialManager;
import com.strangequark.ancientexpansion.village.AncientMazeVillagers;
import com.strangequark.ancientexpansion.worldgen.AncientCityTrialAltarPlacement;
import com.strangequark.ancientexpansion.worldgen.ModStructurePieces;
import com.strangequark.ancientexpansion.worldgen.ModStructureTypes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AncientExpansionMod implements ModInitializer {
    public static final String MOD_ID = "quarkmod_ancient_expansion";
    public static final String REGISTRY_NAMESPACE = "quarkmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(REGISTRY_NAMESPACE, path);
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(EchoProspectorPayload.ID, EchoProspectorPayload.CODEC);
        ModBlocks.registerModBlocks();
        ModItems.registerModItems();
        AncientExpansionLootTables.register();
        AncientMazeVillagers.register();
        AncientEnchantmentLogic.register();
        AncientCityTrialManager.register();
        AncientCityTrialAltarPlacement.register();
        ModStructurePieces.registerStructurePieces();
        ModStructureTypes.registerStructureTypes();
        LOGGER.info("QuarkMod Ancient Expansion init success");
    }
}
