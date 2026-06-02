package com.strangequark.grappling.item;

import com.strangequark.grappling.GrapplingMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.List;
import java.util.function.Function;

public final class ModItems {
    private static final int WOODEN_HOOK_DURABILITY = 30;
    private static final int GOLDEN_HOOK_DURABILITY = 16;
    private static final int STONE_HOOK_DURABILITY = 66;
    private static final int COPPER_HOOK_DURABILITY = 95;
    private static final int IRON_HOOK_DURABILITY = 125;
    private static final int DIAMOND_HOOK_DURABILITY = 781;
    private static final int NETHERITE_HOOK_DURABILITY = 1016;
    private static final int COPPER_HOOK_ENCHANTABILITY = 10;
    private static final int GRAPPLING_CROSSBOW_ENCHANTABILITY = 1;

    public static final GrapplingHookItem WOODEN_GRAPPLING_HOOK = registerHook("wooden_grappling_hook", WOODEN_HOOK_DURABILITY, ToolMaterial.WOOD.enchantmentValue());
    public static final GrapplingHookItem GOLDEN_GRAPPLING_HOOK = registerHook("golden_grappling_hook", GOLDEN_HOOK_DURABILITY, ToolMaterial.GOLD.enchantmentValue());
    public static final GrapplingHookItem STONE_GRAPPLING_HOOK = registerHook("stone_grappling_hook", STONE_HOOK_DURABILITY, ToolMaterial.STONE.enchantmentValue());
    public static final GrapplingHookItem COPPER_GRAPPLING_HOOK = registerHook("copper_grappling_hook", COPPER_HOOK_DURABILITY, COPPER_HOOK_ENCHANTABILITY);
    public static final GrapplingHookItem IRON_GRAPPLING_HOOK = registerHook("iron_grappling_hook", IRON_HOOK_DURABILITY, ToolMaterial.IRON.enchantmentValue());
    public static final GrapplingHookItem DIAMOND_GRAPPLING_HOOK = registerHook("diamond_grappling_hook", DIAMOND_HOOK_DURABILITY, ToolMaterial.DIAMOND.enchantmentValue());
    public static final GrapplingHookItem NETHERITE_GRAPPLING_HOOK = register(
            "netherite_grappling_hook",
            GrapplingHookItem::new,
            hookSettings(NETHERITE_HOOK_DURABILITY, ToolMaterial.NETHERITE.enchantmentValue()).fireproof().rarity(Rarity.EPIC)
    );

    public static final Item GRAPPLING_HOOK_REEL = register(
            "grappling_hook_reel",
            Item::new,
            new Item.Settings().maxCount(16)
    );

    public static final GrapplingCrossbowItem GRAPPLING_CROSSBOW = register(
            "grappling_crossbow",
            GrapplingCrossbowItem::new,
            new Item.Settings().maxCount(1).maxDamage(465).enchantable(GRAPPLING_CROSSBOW_ENCHANTABILITY)
    );

    public static final List<GrapplingHookItem> GRAPPLING_HOOKS = List.of(
            WOODEN_GRAPPLING_HOOK,
            GOLDEN_GRAPPLING_HOOK,
            STONE_GRAPPLING_HOOK,
            COPPER_GRAPPLING_HOOK,
            IRON_GRAPPLING_HOOK,
            DIAMOND_GRAPPLING_HOOK,
            NETHERITE_GRAPPLING_HOOK
    );

    private ModItems() {
    }

    public static void registerModItems() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            for (GrapplingHookItem hook : GRAPPLING_HOOKS) {
                entries.add(hook);
            }
            entries.add(GRAPPLING_HOOK_REEL);
            entries.add(GRAPPLING_CROSSBOW);
        });
    }

    public static boolean isGrapplingHook(ItemStack stack) {
        return stack.getItem() instanceof GrapplingHookItem;
    }

    private static GrapplingHookItem registerHook(String name, int durability, int enchantability) {
        return register(name, GrapplingHookItem::new, hookSettings(durability, enchantability));
    }

    private static Item.Settings hookSettings(int durability, int enchantability) {
        return new Item.Settings().maxDamage(durability).enchantable(enchantability);
    }

    private static <T extends Item> T register(String name, Function<Item.Settings, T> factory, Item.Settings settings) {
        Identifier id = Identifier.of(GrapplingMod.REGISTRY_NAMESPACE, name);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        T item = factory.apply(settings.registryKey(key));
        return Registry.register(Registries.ITEM, key, item);
    }
}
