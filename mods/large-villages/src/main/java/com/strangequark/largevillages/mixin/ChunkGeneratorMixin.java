package com.strangequark.largevillages.mixin;

import com.mojang.datafixers.util.Pair;
import com.strangequark.largevillages.worldgen.LargeVillageLocator;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Inject(method = "locateStructure", at = @At("HEAD"), cancellable = true)
    private void quarkmod$locateLargeVillage(
            ServerWorld world,
            RegistryEntryList<Structure> structures,
            BlockPos center,
            int radius,
            boolean skipReferencedStructures,
            CallbackInfoReturnable<Pair<BlockPos, RegistryEntry<Structure>>> cir
    ) {
        if (skipReferencedStructures || !LargeVillageLocator.canHandle(structures)) {
            return;
        }

        cir.setReturnValue(LargeVillageLocator.locate((ChunkGenerator) (Object) this, world, structures, center, radius));
    }
}
