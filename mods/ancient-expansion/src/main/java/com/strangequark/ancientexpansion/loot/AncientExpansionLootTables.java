package com.strangequark.ancientexpansion.loot;

import com.strangequark.ancientexpansion.item.ModItems;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class AncientExpansionLootTables {
    private static final RegistryKey<LootTable> WARDEN_LOOT_TABLE = RegistryKey.of(
            RegistryKeys.LOOT_TABLE,
            Identifier.ofVanilla("entities/warden")
    );

    private AncientExpansionLootTables() {
    }

    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (source.isBuiltin() && key.equals(WARDEN_LOOT_TABLE)) {
                tableBuilder.pool(LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1.0F))
                        .conditionally(RandomChanceLootCondition.builder(0.5F))
                        .with(ItemEntry.builder(ModItems.ANCIENT_FLINT_AND_STEEL)));
            }
        });
    }
}
