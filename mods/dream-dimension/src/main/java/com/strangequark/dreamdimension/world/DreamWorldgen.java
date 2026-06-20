package com.strangequark.dreamdimension.world;

import com.strangequark.dreamdimension.DreamDimensionMod;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

public final class DreamWorldgen {
    private static final RegistryKey<PlacedFeature> ETHEREAL_ORE = placedFeatureKey("ethereal_ore");
    private static final RegistryKey<PlacedFeature> ETHEREAL_TREE = placedFeatureKey("ethereal_tree");

    private DreamWorldgen() {
    }

    public static void register() {
        BiomeModifications.addFeature(DreamWorldgen::isDreamBiome, GenerationStep.Feature.UNDERGROUND_ORES, ETHEREAL_ORE);
        BiomeModifications.addFeature(DreamWorldgen::isDreamBiome, GenerationStep.Feature.VEGETAL_DECORATION, ETHEREAL_TREE);
    }

    private static boolean isDreamBiome(BiomeSelectionContext context) {
        return context.getBiomeKey().getValue().getNamespace().equals(DreamDimensionMod.REGISTRY_NAMESPACE)
                && context.getBiomeKey().getValue().getPath().startsWith("dream/");
    }

    private static RegistryKey<PlacedFeature> placedFeatureKey(String path) {
        return RegistryKey.of(RegistryKeys.PLACED_FEATURE, DreamDimensionMod.id(path));
    }
}
