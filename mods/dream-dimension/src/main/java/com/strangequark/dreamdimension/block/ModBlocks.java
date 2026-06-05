package com.strangequark.dreamdimension.block;

import com.strangequark.dreamdimension.DreamDimensionMod;
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
import net.minecraft.util.Identifier;

public final class ModBlocks {
    public static final Block SOMNARIUM = register("somnarium", dreamWorkstationSettings());
    public static final Block LUCID_CHARTING_TABLE = register("lucid_charting_table", dreamWorkstationSettings());
    public static final Block ECHO_LECTERN = register("echo_lectern", dreamWorkstationSettings());
    public static final Block STARGAZER_TABLE = register("stargazer_table", dreamWorkstationSettings());
    public static final Block MEMORY_LOOM = register("memory_loom", dreamWorkstationSettings());
    public static final Block WAKING_ANVIL = register("waking_anvil", dreamWorkstationSettings());
    public static final Block DRIFT_COMPOSTER = register("drift_composter", dreamWorkstationSettings());

    private ModBlocks() {
    }

    public static void registerModBlocks() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(SOMNARIUM);
            entries.add(LUCID_CHARTING_TABLE);
            entries.add(ECHO_LECTERN);
            entries.add(STARGAZER_TABLE);
            entries.add(MEMORY_LOOM);
            entries.add(WAKING_ANVIL);
            entries.add(DRIFT_COMPOSTER);
        });
    }

    private static AbstractBlock.Settings dreamWorkstationSettings() {
        return AbstractBlock.Settings.copy(Blocks.AMETHYST_BLOCK)
                .strength(1.5F, 6.0F)
                .luminance(state -> 3);
    }

    private static Block register(String name, AbstractBlock.Settings settings) {
        Identifier id = DreamDimensionMod.id(name);
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
        Block block = Registry.register(Registries.BLOCK, blockKey, new Block(settings.registryKey(blockKey)));

        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);
        Registry.register(
                Registries.ITEM,
                itemKey,
                new BlockItem(block, new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey())
        );

        return block;
    }
}
