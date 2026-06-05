package com.strangequark.dreamdimension.world.structure;

import com.mojang.serialization.MapCodec;
import com.strangequark.dreamdimension.DreamDimensionMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

public final class ModStructureTypes {
    public static final StructureType<DreamVillageStructure> DREAM_VILLAGE = register("dream_village", DreamVillageStructure.CODEC);

    private ModStructureTypes() {
    }

    public static void registerStructureTypes() {
    }

    private static <S extends Structure> StructureType<S> register(String name, MapCodec<S> codec) {
        return Registry.register(Registries.STRUCTURE_TYPE, DreamDimensionMod.id(name), () -> codec);
    }
}
