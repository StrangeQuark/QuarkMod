package com.strangequark.dreamdimension;

import com.strangequark.dreamdimension.item.ModItems;
import com.strangequark.dreamdimension.state.ModAttachments;
import com.strangequark.dreamdimension.world.DreamDimensionEvents;
import net.fabricmc.api.ModInitializer;
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
        ModAttachments.registerAttachments();
        ModItems.registerModItems();
        DreamDimensionEvents.registerEvents();

        LOGGER.info("QuarkMod Dream Dimension init success");
    }
}
