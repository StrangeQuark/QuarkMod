package com.strangequark.item;

import com.strangequark.QuarkMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.List;
import java.util.function.Function;

public final class ModItems {
    public static final GrapplingHookItem WOODEN_GRAPPLING_HOOK = registerHook("wooden_grappling_hook");
    public static final GrapplingHookItem GOLDEN_GRAPPLING_HOOK = registerHook("golden_grappling_hook");
    public static final GrapplingHookItem STONE_GRAPPLING_HOOK = registerHook("stone_grappling_hook");
    public static final GrapplingHookItem COPPER_GRAPPLING_HOOK = registerHook("copper_grappling_hook");
    public static final GrapplingHookItem IRON_GRAPPLING_HOOK = registerHook("iron_grappling_hook");
    public static final GrapplingHookItem DIAMOND_GRAPPLING_HOOK = registerHook("diamond_grappling_hook");
    public static final GrapplingHookItem NETHERITE_GRAPPLING_HOOK = register(
            "netherite_grappling_hook",
            GrapplingHookItem::new,
            new Item.Settings().fireproof().rarity(Rarity.EPIC)
    );

    public static final Item GRAPPLING_HOOK_REEL = register(
            "grappling_hook_reel",
            Item::new,
            new Item.Settings().maxCount(16)
    );

    public static final GrapplingCrossbowItem GRAPPLING_CROSSBOW = register(
            "grappling_crossbow",
            GrapplingCrossbowItem::new,
            new Item.Settings().maxCount(1).maxDamage(465)
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

    private static GrapplingHookItem registerHook(String name) {
        return register(name, GrapplingHookItem::new, new Item.Settings().maxCount(16));
    }

    private static <T extends Item> T register(String name, Function<Item.Settings, T> factory, Item.Settings settings) {
        Identifier id = Identifier.of(QuarkMod.MOD_ID, name);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        T item = factory.apply(settings.registryKey(key));
        return Registry.register(Registries.ITEM, key, item);
    }
}
