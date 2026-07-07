package com.strangequark.loottweaks;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.item.Item;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.Registries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LootTweaksMod implements ModInitializer {
    public static final String MOD_ID = "quarkmod_loot_tweaks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (source.isBuiltin() && key.getValue().getPath().startsWith("chests/")) {
                List<Item> spawnEggs = Registries.ITEM.stream()
                        .filter(item -> item.toString().endsWith("_spawn_egg"))
                        .toList();

                LootPool.Builder pool = LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(RandomChanceLootCondition.builder(0.1f));

                for (Item egg : spawnEggs) {
                    pool.with(ItemEntry.builder(egg).weight(1));
                }

                tableBuilder.pool(pool);
            }
        });

        LOGGER.info("QuarkMod Loot Tweaks init success");
    }
}
