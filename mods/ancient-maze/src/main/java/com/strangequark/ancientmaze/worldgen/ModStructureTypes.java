package com.strangequark.ancientmaze.worldgen;

import com.mojang.serialization.MapCodec;
import com.strangequark.ancientmaze.AncientMazeMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

public final class ModStructureTypes {
    public static final StructureType<AncientMazeStructure> ANCIENT_MAZE = register("ancient_maze", AncientMazeStructure.CODEC);

    private ModStructureTypes() {
    }

    public static void registerStructureTypes() {
    }

    private static <S extends Structure> StructureType<S> register(String name, MapCodec<S> codec) {
        return Registry.register(Registries.STRUCTURE_TYPE, AncientMazeMod.id(name), () -> codec);
    }
}
