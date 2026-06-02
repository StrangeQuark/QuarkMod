package com.strangequark.excavator;

import com.strangequark.excavator.enchantments.ExcavatorEnchantmentEffect;
import com.strangequark.excavator.enchantments.ModEnchantmentEffects;
import com.strangequark.excavator.enchantments.ModEnchantments;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetEnchantmentsLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExcavatorMod implements ModInitializer {
    public static final String MOD_ID = "quarkmod_excavator";
    public static final String REGISTRY_NAMESPACE = "quarkmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(REGISTRY_NAMESPACE, path);
    }

    @Override
    public void onInitialize() {
        registerLootTables();
        registerBlockBreakEvents();
        ModEnchantmentEffects.registerEnchantmentEffects();

        LOGGER.info("QuarkMod Excavator init success");
    }

    private static void registerLootTables() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (source.isBuiltin() && key.equals(LootTables.ANCIENT_CITY_CHEST)) {
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
    }

    private static void registerBlockBreakEvents() {
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
    }
}
