package com.strangequark.ancientexpansion.worldgen;

import com.mojang.serialization.MapCodec;
import com.strangequark.ancientexpansion.AncientExpansionMod;
import com.strangequark.ancientexpansion.past.PastAncientCityStructure;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

public final class ModStructureTypes {
    public static final StructureType<AncientMazeStructure> ANCIENT_MAZE = register("ancient_maze", AncientMazeStructure.CODEC);
    public static final StructureType<PastAncientCityStructure> PAST_ANCIENT_CITY = register("past_ancient_city", PastAncientCityStructure.CODEC);

    private ModStructureTypes() {
    }

    public static void registerStructureTypes() {
    }

    private static <S extends Structure> StructureType<S> register(String name, MapCodec<S> codec) {
        return Registry.register(Registries.STRUCTURE_TYPE, AncientExpansionMod.id(name), () -> codec);
    }
}
