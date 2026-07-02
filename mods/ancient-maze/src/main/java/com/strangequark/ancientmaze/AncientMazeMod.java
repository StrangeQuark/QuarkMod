package com.strangequark.ancientmaze;

import com.strangequark.ancientmaze.worldgen.ModStructurePieces;
import com.strangequark.ancientmaze.worldgen.ModStructureTypes;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AncientMazeMod implements ModInitializer {
    public static final String MOD_ID = "quarkmod_ancient_maze";
    public static final String REGISTRY_NAMESPACE = "quarkmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(REGISTRY_NAMESPACE, path);
    }

    @Override
    public void onInitialize() {
        ModStructurePieces.registerStructurePieces();
        ModStructureTypes.registerStructureTypes();
        LOGGER.info("QuarkMod Ancient Maze init success");
    }
}
