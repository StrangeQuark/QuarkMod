package com.strangequark.largevillages.worldgen;

import com.strangequark.largevillages.LargeVillagesMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.structure.StructureType;

public class ModStructureTypes {
    public static final StructureType<LargeVillageStructure> LARGE_VILLAGE = Registry.register(
            Registries.STRUCTURE_TYPE,
            Identifier.of(LargeVillagesMod.REGISTRY_NAMESPACE, "large_village"),
            () -> LargeVillageStructure.CODEC
    );

    public static void registerStructureTypes() {
        LargeVillagesMod.LOGGER.info("Registering structure types for " + LargeVillagesMod.REGISTRY_NAMESPACE);
    }
}
