package com.strangequark;

import com.strangequark.enchantments.ExcavatorEnchantmentEffect;
import com.strangequark.enchantments.ModEnchantmentEffects;
import com.strangequark.enchantments.ModEnchantments;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetEnchantmentsLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class QuarkMod implements ModInitializer {
	public static final String MOD_ID = "quarkmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final RegistryKey<LootTable> SPAWNER_LOOT_KEY =
            RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of("minecraft", "blocks/spawner"));

    @Override
	public void onInitialize() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            // Allow spawners to be mined
            if(source.isBuiltin() && key.equals(SPAWNER_LOOT_KEY)) {
                LootPool.Builder pool = LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .with(ItemEntry.builder(Items.SPAWNER));

                tableBuilder.pool(pool);
            }

            // Insert spawn eggs into all chests
            if(source.isBuiltin() && key.getValue().getPath().startsWith("chests/")) {
                List<Item> spawnEggs = Registries.ITEM.stream().filter(item -> item.toString().endsWith("_spawn_egg")).toList();

                LootPool.Builder pool = LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(RandomChanceLootCondition.builder(0.1f));

                for (Item egg : spawnEggs) {
                    pool.with(ItemEntry.builder(egg).weight(1));
                }

                tableBuilder.pool(pool);
            }

            // Give the Excavator enchantment a 15% chance to spawn in ancient city chests
            if(source.isBuiltin() && key.equals(LootTables.ANCIENT_CITY_CHEST)) {
                RegistryEntry<Enchantment> excavatorEntry = registries.getEntryOrThrow(ModEnchantments.EXCAVATOR);

                LootPool.Builder pool = LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(RandomChanceLootCondition.builder(0.15f))
                        .with(ItemEntry.builder(Items.ENCHANTED_BOOK)
                                .apply(new SetEnchantmentsLootFunction.Builder()
                                        .enchantment(excavatorEntry, ConstantLootNumberProvider.create(1))));

                tableBuilder.pool(pool);
            }
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return true;
            }

            ItemStack held = player.getMainHandStack();

            boolean hasExcavator = held.getEnchantments()
                    .getEnchantments()
                    .stream()
                    .anyMatch(entry -> entry.getKey().get().getValue().equals(ModEnchantments.EXCAVATOR.getValue()));

            if (hasExcavator) {
                ExcavatorEnchantmentEffect.mine3x3(serverWorld, pos, player);
            }
            return true;
        });

        ModEnchantmentEffects.registerEnchantmentEffects();

        LOGGER.info("Quarkmod init success");
	}
}
