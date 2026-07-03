package com.strangequark.ancientmaze.mixin;

import com.strangequark.ancientmaze.worldgen.AncientMazeStructure;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpawnHelper.class)
public abstract class SpawnHelperAncientMazeMixin {
    @Inject(method = "getRandomPosInChunkSection", at = @At("HEAD"), cancellable = true)
    private static void quarkmod_ancientmaze$pickMazeSpawnCandidate(
            World world,
            WorldChunk chunk,
            CallbackInfoReturnable<BlockPos> cir
    ) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }

        AncientMazeStructure.pickNaturalSpawnCandidate(serverWorld, chunk).ifPresent(cir::setReturnValue);
    }
}
