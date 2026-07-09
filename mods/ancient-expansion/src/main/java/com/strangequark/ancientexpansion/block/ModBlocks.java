package com.strangequark.ancientexpansion.block;

import com.strangequark.ancientexpansion.AncientExpansionMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModBlocks {
    public static final Block ANCIENT_TRIAL_ALTAR = register(
            "ancient_trial_altar",
            AncientTrialAltarBlock::new,
            AbstractBlock.Settings.copy(Blocks.REINFORCED_DEEPSLATE)
                    .luminance(state -> 4)
                    .requiresTool()
    );

    public static final Block ANCIENT_TRIAL_PORTAL = registerWithoutItem(
            "ancient_trial_portal",
            AncientTrialPortalBlock::new,
            AbstractBlock.Settings.copy(Blocks.NETHER_PORTAL)
                    .noCollision()
                    .nonOpaque()
                    .luminance(state -> 11)
                    .dropsNothing()
                    .pistonBehavior(PistonBehavior.BLOCK)
    );

    private ModBlocks() {
    }

    public static void registerModBlocks() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(ANCIENT_TRIAL_ALTAR));
    }

    private static Block register(String name, java.util.function.Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings) {
        Block block = registerWithoutItem(name, factory, settings);

        Identifier id = AncientExpansionMod.id(name);
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);
        Item.Settings itemSettings = new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey();
        Registry.register(Registries.ITEM, itemKey, new BlockItem(block, itemSettings));

        return block;
    }

    private static Block registerWithoutItem(String name, java.util.function.Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings) {
        Identifier id = AncientExpansionMod.id(name);
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
        return Registry.register(Registries.BLOCK, blockKey, factory.apply(settings.registryKey(blockKey)));
    }
}
