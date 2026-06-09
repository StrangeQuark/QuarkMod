package com.strangequark.dreamdimension.item;

import com.strangequark.dreamdimension.DreamDimensionMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.item.equipment.EquipmentAssetKeys;
import net.minecraft.item.equipment.EquipmentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.Map;

public final class ModItems {
    public static final TagKey<Block> INCORRECT_FOR_ETHEREAL_TOOL = blockTag("incorrect_for_ethereal_tool");
    public static final TagKey<Item> ETHEREAL_TOOL_MATERIALS = itemTag("ethereal_tool_materials");
    public static final TagKey<Item> ETHEREAL_WEAPONS_AND_TOOLS = itemTag("ethereal_weapons_and_tools");
    public static final TagKey<Item> ETHEREAL_ARMOR = itemTag("ethereal_armor");

    public static final ToolMaterial ETHEREAL_TOOL_MATERIAL = new ToolMaterial(
            INCORRECT_FOR_ETHEREAL_TOOL,
            1561,
            8.0F,
            3.0F,
            10,
            ETHEREAL_TOOL_MATERIALS
    );

    public static final int ETHEREAL_HELMET_ARMOR = 3;
    public static final int ETHEREAL_CHESTPLATE_ARMOR = 8;
    public static final int ETHEREAL_LEGGINGS_ARMOR = 6;
    public static final int ETHEREAL_BOOTS_ARMOR = 3;
    public static final float ETHEREAL_ARMOR_TOUGHNESS = 2.0F;

    private static final RegistryKey<EquipmentAsset> ETHEREAL_EQUIPMENT_ASSET = RegistryKey.of(
            EquipmentAssetKeys.REGISTRY_KEY,
            DreamDimensionMod.id("ethereal")
    );
    private static final ArmorMaterial ETHEREAL_ARMOR_MATERIAL = new ArmorMaterial(
            33,
            Map.of(
                    EquipmentType.HELMET, ETHEREAL_HELMET_ARMOR,
                    EquipmentType.CHESTPLATE, ETHEREAL_CHESTPLATE_ARMOR,
                    EquipmentType.LEGGINGS, ETHEREAL_LEGGINGS_ARMOR,
                    EquipmentType.BOOTS, ETHEREAL_BOOTS_ARMOR,
                    EquipmentType.BODY, 11
            ),
            10,
            SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND,
            ETHEREAL_ARMOR_TOUGHNESS,
            0.0F,
            ETHEREAL_TOOL_MATERIALS,
            ETHEREAL_EQUIPMENT_ASSET
    );

    public static final Item ETHEREAL_SHARD = register("ethereal_shard", EtherealItem::new, new Item.Settings());
    public static final Item ETHEREAL_SWORD = register("ethereal_sword", EtherealItem::new, new Item.Settings().sword(ETHEREAL_TOOL_MATERIAL, 3.0F, -2.4F));
    public static final Item ETHEREAL_BOW = register("ethereal_bow", EtherealBowItem::new, new Item.Settings().maxDamage(384).enchantable(1).repairable(ETHEREAL_TOOL_MATERIALS));
    public static final Item ETHEREAL_ARROW = register("ethereal_arrow", EtherealArrowItem::new, new Item.Settings());
    public static final Item ETHEREAL_SHOVEL = register("ethereal_shovel", EtherealItem::new, new Item.Settings().shovel(ETHEREAL_TOOL_MATERIAL, 1.5F, -3.0F));
    public static final Item ETHEREAL_PICKAXE = register("ethereal_pickaxe", EtherealItem::new, new Item.Settings().pickaxe(ETHEREAL_TOOL_MATERIAL, 1.0F, -2.8F));
    public static final Item ETHEREAL_AXE = register("ethereal_axe", settings -> new EtherealAxeItem(ETHEREAL_TOOL_MATERIAL, 5.0F, -3.0F, settings));
    public static final Item ETHEREAL_HOE = register("ethereal_hoe", EtherealItem::new, new Item.Settings().hoe(ETHEREAL_TOOL_MATERIAL, -3.0F, 0.0F));
    public static final Item ETHEREAL_HELMET = register("ethereal_helmet", EtherealItem::new, new Item.Settings().armor(ETHEREAL_ARMOR_MATERIAL, EquipmentType.HELMET));
    public static final Item ETHEREAL_CHESTPLATE = register("ethereal_chestplate", EtherealItem::new, new Item.Settings().armor(ETHEREAL_ARMOR_MATERIAL, EquipmentType.CHESTPLATE));
    public static final Item ETHEREAL_LEGGINGS = register("ethereal_leggings", EtherealItem::new, new Item.Settings().armor(ETHEREAL_ARMOR_MATERIAL, EquipmentType.LEGGINGS));
    public static final Item ETHEREAL_BOOTS = register("ethereal_boots", EtherealItem::new, new Item.Settings().armor(ETHEREAL_ARMOR_MATERIAL, EquipmentType.BOOTS));

    private ModItems() {
    }

    public static void registerModItems() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.add(ETHEREAL_SHARD));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(ETHEREAL_SWORD);
            entries.add(ETHEREAL_BOW);
            entries.add(ETHEREAL_ARROW);
            entries.add(ETHEREAL_AXE);
            entries.add(ETHEREAL_HELMET);
            entries.add(ETHEREAL_CHESTPLATE);
            entries.add(ETHEREAL_LEGGINGS);
            entries.add(ETHEREAL_BOOTS);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(ETHEREAL_SHOVEL);
            entries.add(ETHEREAL_PICKAXE);
            entries.add(ETHEREAL_AXE);
            entries.add(ETHEREAL_HOE);
        });
    }

    public static boolean isEtherealWeaponOrTool(ItemStack stack) {
        return stack != null && stack.isIn(ETHEREAL_WEAPONS_AND_TOOLS);
    }

    public static boolean isEtherealPickaxe(ItemStack stack) {
        return stack != null && stack.isOf(ETHEREAL_PICKAXE);
    }

    public static boolean isEtherealAxe(ItemStack stack) {
        return stack != null && stack.isOf(ETHEREAL_AXE);
    }

    public static boolean isEtherealArmor(ItemStack stack) {
        return stack != null && stack.isIn(ETHEREAL_ARMOR);
    }

    public static int getEtherealArmorValue(EquipmentSlot slot, ItemStack stack) {
        if (!isEtherealArmor(stack)) {
            return 0;
        }

        return switch (slot) {
            case HEAD -> ETHEREAL_HELMET_ARMOR;
            case CHEST -> ETHEREAL_CHESTPLATE_ARMOR;
            case LEGS -> ETHEREAL_LEGGINGS_ARMOR;
            case FEET -> ETHEREAL_BOOTS_ARMOR;
            default -> 0;
        };
    }

    public static float getEtherealArmorToughness(EquipmentSlot slot, ItemStack stack) {
        return getEtherealArmorValue(slot, stack) > 0 ? ETHEREAL_ARMOR_TOUGHNESS : 0.0F;
    }

    private static Item register(String name, Item.Settings settings) {
        return register(name, Item::new, settings);
    }

    private static Item register(String name, java.util.function.Function<Item.Settings, Item> factory) {
        return register(name, factory, new Item.Settings());
    }

    private static Item register(String name, java.util.function.Function<Item.Settings, Item> factory, Item.Settings settings) {
        Identifier id = DreamDimensionMod.id(name);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        return Registry.register(Registries.ITEM, key, factory.apply(settings.registryKey(key)));
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.of(RegistryKeys.ITEM, DreamDimensionMod.id(path));
    }

    private static TagKey<Block> blockTag(String path) {
        return TagKey.of(RegistryKeys.BLOCK, DreamDimensionMod.id(path));
    }
}
