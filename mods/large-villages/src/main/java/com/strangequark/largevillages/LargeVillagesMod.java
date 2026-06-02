package com.strangequark.largevillages;

import com.strangequark.largevillages.worldgen.ModStructureTypes;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LargeVillagesMod implements ModInitializer {
    public static final String MOD_ID = "quarkmod_large_villages";
    public static final String REGISTRY_NAMESPACE = "quarkmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(REGISTRY_NAMESPACE, path);
    }

    @Override
    public void onInitialize() {
        ModStructureTypes.registerStructureTypes();
        LOGGER.info("QuarkMod Large Villages init success");
    }
}
