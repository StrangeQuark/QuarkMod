package com.strangequark.ancientmaze.mixin;

import com.strangequark.ancientmaze.worldgen.AncientMazeStructure;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@Mixin(ChunkRegion.class)
public abstract class ChunkRegionAncientMazeBedrockMixin {
    @Shadow
    private Supplier<String> currentlyGeneratingStructureName;

    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void quarkmod_ancientmaze$protectGeneratedBedrock(
            BlockPos pos,
            BlockState state,
            int flags,
            int maxUpdateDepth,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (state.isOf(Blocks.BEDROCK) || isGeneratingAncientMaze()) {
            return;
        }

        ChunkRegion region = (ChunkRegion) (Object) this;
        if (!World.OVERWORLD.equals(region.toServerWorld().getRegistryKey())
                || !region.getBlockState(pos).isOf(Blocks.BEDROCK)
                || !AncientMazeStructure.protectsGeneratedBedrock(region.getSeed(), region.getBottomY(), pos)) {
            return;
        }

        cir.setReturnValue(false);
    }

    private boolean isGeneratingAncientMaze() {
        Supplier<String> supplier = this.currentlyGeneratingStructureName;
        return supplier != null && supplier.get().contains("quarkmod:ancient_maze");
    }
}
