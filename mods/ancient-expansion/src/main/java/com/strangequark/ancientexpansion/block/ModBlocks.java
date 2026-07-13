package com.strangequark.ancientexpansion.block;

import com.strangequark.ancientexpansion.AncientExpansionMod;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModBlocks {
    public static final AncientFireBlock ANCIENT_FIRE = registerWithoutItem(
            "ancient_fire",
            AncientFireBlock::new,
            AbstractBlock.Settings.copy(Blocks.FIRE)
                    .luminance(state -> 15)
                    .dropsNothing()
                    .pistonBehavior(PistonBehavior.DESTROY)
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
    }

    private static <T extends Block> T registerWithoutItem(String name, java.util.function.Function<AbstractBlock.Settings, T> factory, AbstractBlock.Settings settings) {
        Identifier id = AncientExpansionMod.id(name);
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
        return Registry.register(Registries.BLOCK, blockKey, factory.apply(settings.registryKey(blockKey)));
    }
}
