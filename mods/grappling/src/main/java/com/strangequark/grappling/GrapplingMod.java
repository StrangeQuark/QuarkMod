package com.strangequark.grappling;

import com.strangequark.grappling.entity.ModEntities;
import com.strangequark.grappling.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrapplingMod implements ModInitializer {
    public static final String MOD_ID = "quarkmod_grappling";
    public static final String REGISTRY_NAMESPACE = "quarkmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(REGISTRY_NAMESPACE, path);
    }

    @Override
    public void onInitialize() {
        ModEntities.registerModEntities();
        ModItems.registerModItems();

        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (source.isBuiltin() && key.equals(LootTables.PILLAGER_OUTPOST_CHEST)) {
                LootPool.Builder pool = LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .with(ItemEntry.builder(ModItems.GRAPPLING_HOOK_REEL));

                tableBuilder.pool(pool);
            }
        });

        LOGGER.info("QuarkMod Grappling init success");
    }
}
