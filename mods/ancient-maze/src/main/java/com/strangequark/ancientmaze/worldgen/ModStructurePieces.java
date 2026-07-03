package com.strangequark.ancientmaze.worldgen;

import com.strangequark.ancientmaze.AncientMazeMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.StructurePieceType;

public final class ModStructurePieces {
    public static final StructurePieceType ANCIENT_MAZE = Registry.register(
            Registries.STRUCTURE_PIECE,
            AncientMazeMod.id("ancient_maze"),
            AncientMazePiece::new
    );

    private ModStructurePieces() {
    }

    public static void registerStructurePieces() {
    }
}
