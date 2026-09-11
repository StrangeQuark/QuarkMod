package com.strangequark.villagebuilder.blueprint;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;

public record BlueprintTemplate(String id, int sizeX, int sizeY, int sizeZ, List<BlueprintBlock> blocks, Map<String, Integer> materials) {
    public BlueprintTemplate {
        blocks = List.copyOf(blocks);
        materials = Map.copyOf(materials);
    }

    public record BlueprintBlock(BlockPos pos, BlockState state) {
    }
}
