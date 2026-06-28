package com.strangequark.vampirism.block;

import com.strangequark.vampirism.VampirismMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.BedItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

public final class ModBlocks {
    public static final Map<DyeColor, Block> COFFINS = registerCoffins();

    private ModBlocks() {
    }

    public static void registerModBlocks() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> COFFINS.values().forEach(entries::add));
    }

    public static boolean isCoffin(BlockState state) {
        return state.getBlock() instanceof CoffinBlock;
    }

    private static AbstractBlock.Settings coffinSettings() {
        return AbstractBlock.Settings.copy(Blocks.BLACK_BED)
                .strength(1.2F)
                .nonOpaque();
    }

    private static Map<DyeColor, Block> registerCoffins() {
        EnumMap<DyeColor, Block> coffins = new EnumMap<>(DyeColor.class);
        for (DyeColor color : DyeColor.values()) {
            coffins.put(color, register(color.asString() + "_coffin", settings -> new CoffinBlock(color, settings), coffinSettings()));
        }

        return Collections.unmodifiableMap(coffins);
    }

    private static Block register(String name, Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings) {
        Identifier id = VampirismMod.id(name);
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
        Block block = Registry.register(Registries.BLOCK, blockKey, factory.apply(settings.registryKey(blockKey)));

        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);
        Item.Settings itemSettings = new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey();
        Registry.register(Registries.ITEM, itemKey, new BedItem(block, itemSettings));

        return block;
    }
}
