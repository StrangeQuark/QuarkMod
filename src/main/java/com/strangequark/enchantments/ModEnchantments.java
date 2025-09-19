package com.strangequark.enchantments;

import com.strangequark.QuarkMod;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.registry.Registerable;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;

public class ModEnchantments {
    public static final RegistryKey<Enchantment> EXCAVATOR = RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of(QuarkMod.MOD_ID, "excavator"));
    public static RegistryEntry<Enchantment> EXCAVATOR_ENTRY;

    public static void bootstrap(Registerable<Enchantment> registerable) {
        RegistryEntryLookup<Enchantment> enchantments = registerable.getRegistryLookup(RegistryKeys.ENCHANTMENT);
        RegistryEntryLookup<Item> items = registerable.getRegistryLookup(RegistryKeys.ITEM);

        register(registerable, EXCAVATOR, Enchantment.builder(Enchantment.definition(
                items.getOrThrow(ItemTags.PICKAXES),
                5,
                1,
                Enchantment.leveledCost(5, 7),
                Enchantment.leveledCost(25, 9),
                2,
                AttributeModifierSlot.MAINHAND))
                .exclusiveSet(enchantments.getOrThrow(EnchantmentTags.MINING_EXCLUSIVE_SET))
        );

        EXCAVATOR_ENTRY = registerable.getRegistryLookup(RegistryKeys.ENCHANTMENT).getOrThrow(EXCAVATOR);
    }

    public static void register(Registerable<Enchantment> registry, RegistryKey<Enchantment> key, Enchantment.Builder builder) {
        registry.register(key, builder.build(key.getValue()));
    }
}
