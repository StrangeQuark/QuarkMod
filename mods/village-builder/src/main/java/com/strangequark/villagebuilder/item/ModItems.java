package com.strangequark.villagebuilder.item;

import com.strangequark.villagebuilder.VillageBuilderMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

public final class ModItems {
    public static final Item PLANNER_WAND = register("planner_wand");
    private ModItems() { }
    public static void register() { ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(PLANNER_WAND)); }
    private static Item register(String path) {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, VillageBuilderMod.id(path));
        return Registry.register(Registries.ITEM, key, new PlannerWandItem(new Item.Settings().registryKey(key)));
    }
}
