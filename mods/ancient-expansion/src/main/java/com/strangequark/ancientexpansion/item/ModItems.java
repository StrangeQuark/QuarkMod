package com.strangequark.ancientexpansion.item;

import com.strangequark.ancientexpansion.AncientExpansionMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.Unit;

public final class ModItems {
    public static final TagKey<Item> ANCIENT_PICKAXE_ENCHANTABLE = itemTag("enchantable/ancient_pickaxe");

    private static final ToolMaterial ANCIENT_PICKAXE_MATERIAL = new ToolMaterial(
            net.minecraft.registry.tag.BlockTags.INCORRECT_FOR_IRON_TOOL,
            250,
            ToolMaterial.IRON.speed(),
            ToolMaterial.IRON.attackDamageBonus(),
            8,
            ItemTags.IRON_TOOL_MATERIALS
    );

    public static final Item ANCIENT_RELIC = register("ancient_relic", Item::new,
            new Item.Settings().maxCount(1).rarity(Rarity.RARE));

    public static final Item ANCIENT_PICKAXE = register("ancient_pickaxe", AncientPickaxeItem::new,
            new Item.Settings()
                    .pickaxe(ANCIENT_PICKAXE_MATERIAL, 1.0F, -2.8F)
                    .component(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE)
                    .fireproof()
                    .rarity(Rarity.EPIC));

    private ModItems() {
    }

    public static void registerModItems() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.add(ANCIENT_RELIC));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(ANCIENT_PICKAXE));
    }

    public static boolean isAncientPickaxe(ItemStack stack) {
        return stack != null && stack.isOf(ANCIENT_PICKAXE);
    }

    private static Item register(String name, java.util.function.Function<Item.Settings, Item> factory, Item.Settings settings) {
        Identifier id = AncientExpansionMod.id(name);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        return Registry.register(Registries.ITEM, key, factory.apply(settings.registryKey(key)));
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.of(RegistryKeys.ITEM, AncientExpansionMod.id(path));
    }
}
