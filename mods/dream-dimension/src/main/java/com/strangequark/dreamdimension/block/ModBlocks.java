package com.strangequark.dreamdimension.block;

import com.strangequark.dreamdimension.DreamDimensionMod;
import com.strangequark.dreamdimension.item.EtherealBlockItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.UntintedParticleLeavesBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public final class ModBlocks {
    public static final TagKey<Block> ETHEREAL_PICKAXE_MINEABLE = blockTag("ethereal_pickaxe_mineable");
    public static final TagKey<Block> ETHEREAL_AXE_MINEABLE = blockTag("ethereal_axe_mineable");

    public static final Block SOMNARIUM = register("somnarium", dreamWorkstationSettings());
    public static final Block LUCID_CHARTING_TABLE = register("lucid_charting_table", dreamWorkstationSettings());
    public static final Block ECHO_LECTERN = register("echo_lectern", dreamWorkstationSettings());
    public static final Block STARGAZER_TABLE = register("stargazer_table", dreamWorkstationSettings());
    public static final Block MEMORY_LOOM = register("memory_loom", dreamWorkstationSettings());
    public static final Block WAKING_ANVIL = register("waking_anvil", dreamWorkstationSettings());
    public static final Block DRIFT_COMPOSTER = register("drift_composter", dreamWorkstationSettings());
    public static final Block ETHEREAL_ORE = register("ethereal_ore", etherealOreSettings());
    public static final Block ETHEREAL_LOG = register("ethereal_log", settings -> new PillarBlock(settings), etherealLogSettings());
    public static final Block ETHEREAL_LEAVES = register(
            "ethereal_leaves",
            settings -> new UntintedParticleLeavesBlock(0.01F, ParticleTypes.END_ROD, settings),
            etherealLeavesSettings()
    );

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
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> {
            entries.add(ETHEREAL_ORE);
            entries.add(ETHEREAL_LOG);
            entries.add(ETHEREAL_LEAVES);
        });
    }

    private static AbstractBlock.Settings dreamWorkstationSettings() {
        return AbstractBlock.Settings.copy(Blocks.AMETHYST_BLOCK)
                .strength(1.5F, 6.0F)
                .luminance(state -> 3);
    }

    private static AbstractBlock.Settings etherealOreSettings() {
        return AbstractBlock.Settings.copy(Blocks.DIAMOND_ORE)
                .strength(4.5F, 6.0F)
                .luminance(state -> 4)
                .nonOpaque()
                .requiresTool();
    }

    private static AbstractBlock.Settings etherealLogSettings() {
        return AbstractBlock.Settings.copy(Blocks.WARPED_STEM)
                .strength(2.2F)
                .luminance(state -> 2)
                .nonOpaque();
    }

    private static AbstractBlock.Settings etherealLeavesSettings() {
        return AbstractBlock.Settings.copy(Blocks.CHERRY_LEAVES)
                .luminance(state -> 2)
                .nonOpaque()
                .ticksRandomly();
    }

    private static Block register(String name, AbstractBlock.Settings settings) {
        return register(name, Block::new, settings);
    }

    private static Block register(String name, java.util.function.Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings) {
        Identifier id = DreamDimensionMod.id(name);
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
        Block block = Registry.register(Registries.BLOCK, blockKey, factory.apply(settings.registryKey(blockKey)));

        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);
        Item.Settings itemSettings = new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey();
        Registry.register(
                Registries.ITEM,
                itemKey,
                name.startsWith("ethereal_") ? new EtherealBlockItem(block, itemSettings) : new BlockItem(block, itemSettings)
        );

        return block;
    }

    private static TagKey<Block> blockTag(String path) {
        return TagKey.of(RegistryKeys.BLOCK, DreamDimensionMod.id(path));
    }
}
