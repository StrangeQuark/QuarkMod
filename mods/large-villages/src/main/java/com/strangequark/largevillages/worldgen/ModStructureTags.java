package com.strangequark.largevillages.worldgen;

import com.strangequark.largevillages.LargeVillagesMod;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.gen.structure.Structure;

public final class ModStructureTags {
    public static final TagKey<Structure> LARGE_VILLAGES = of("large_villages");
    public static final TagKey<Structure> ON_LARGE_VILLAGE_MAPS = of("on_large_village_maps");

    private ModStructureTags() {
    }

    private static TagKey<Structure> of(String path) {
        return TagKey.of(RegistryKeys.STRUCTURE, LargeVillagesMod.id(path));
    }
}
