package com.strangequark.ancientmaze.mixin;

import com.strangequark.ancientmaze.worldgen.AncientMazeStructure;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.StructureStart;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Mixin(Structure.class)
public abstract class StructureAncientMazeExclusionMixin {
    @Inject(method = "createStructureStart", at = @At("HEAD"), cancellable = true)
    private void quarkmod_ancientmaze$blockOtherStructuresInsideAncientMaze(
            RegistryEntry<Structure> structureEntry,
            RegistryKey<World> worldKey,
            DynamicRegistryManager dynamicRegistryManager,
            ChunkGenerator chunkGenerator,
            BiomeSource biomeSource,
            NoiseConfig noiseConfig,
            StructureTemplateManager structureTemplateManager,
            long seed,
            ChunkPos chunkPos,
            int references,
            HeightLimitView world,
            Predicate<RegistryEntry<Biome>> validBiome,
            CallbackInfoReturnable<StructureStart> cir
    ) {
        if ((Object) this instanceof AncientMazeStructure || !World.OVERWORLD.equals(worldKey)) {
            return;
        }

        if (AncientMazeStructure.blocksOtherStructureStart(seed, chunkPos)) {
            cir.setReturnValue(StructureStart.DEFAULT);
        }
    }
}
