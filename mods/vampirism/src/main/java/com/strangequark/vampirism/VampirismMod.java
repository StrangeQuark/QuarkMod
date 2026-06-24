package com.strangequark.vampirism;

import com.strangequark.vampirism.behavior.VampireBehavior;
import com.strangequark.vampirism.block.ModBlocks;
import com.strangequark.vampirism.entity.ModEntities;
import com.strangequark.vampirism.item.ModItems;
import com.strangequark.vampirism.vampire.CoffinSleepHandler;
import com.strangequark.vampirism.vampire.PlayerBatForm;
import com.strangequark.vampirism.vampire.VampireData;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VampirismMod implements ModInitializer {
    public static final String MOD_ID = "quarkmod_vampirism";
    public static final String REGISTRY_NAMESPACE = "quarkmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(REGISTRY_NAMESPACE, path);
    }

    @Override
    public void onInitialize() {
        VampireData.register();
        CoffinSleepHandler.register();
        PlayerBatForm.register();
        ModEntities.registerModEntities();
        ModBlocks.registerModBlocks();
        ModItems.registerModItems();
        VampireBehavior.register();

        LOGGER.info("QuarkMod Vampirism init success");
    }
}
