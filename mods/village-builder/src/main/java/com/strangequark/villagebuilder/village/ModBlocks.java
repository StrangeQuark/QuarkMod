package com.strangequark.villagebuilder.village;

import com.strangequark.villagebuilder.VillageBuilderMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

public final class ModBlocks {
    public static final Block BUILDER_DESK = register("builder_desk", Blocks.CRAFTING_TABLE);
    public static final Block MINER_STATION = register("miner_station", Blocks.DEEPSLATE);
    private ModBlocks() { }
    public static void register() { ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> { entries.add(BUILDER_DESK); entries.add(MINER_STATION); }); }
    private static Block register(String name, Block source) {
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, VillageBuilderMod.id(name));
        Block block = Registry.register(Registries.BLOCK, blockKey, new Block(AbstractBlock.Settings.copy(source).registryKey(blockKey)));
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, VillageBuilderMod.id(name));
        Registry.register(Registries.ITEM, itemKey, new BlockItem(block, new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey()));
        return block;
    }
}
