package com.strangequark;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuarkMod implements ModInitializer {
	public static final String MOD_ID = "quarkmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final RegistryKey<LootTable> SPAWNER_LOOT_KEY =
            RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of("minecraft", "blocks/spawner"));

    @Override
	public void onInitialize() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if(source.isBuiltin() && key.equals(SPAWNER_LOOT_KEY)) {
                LootPool.Builder pool = LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .with(ItemEntry.builder(Items.SPAWNER));

                tableBuilder.pool(pool);
            }
        });

        LOGGER.info("Quarkmod init success");
	}
}