package com.strangequark.ancientmaze.mixin;

import com.strangequark.ancientmaze.enchantment.AncientEnchantmentLogic;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.class)
public abstract class AbstractBlockWorldbreakerMixin {
    @Inject(method = "calcBlockBreakingDelta", at = @At("HEAD"), cancellable = true)
    private void quarkmod_ancientmaze$worldbreakerBreaksNormallyUnbreakableBlocks(
            BlockState state,
            PlayerEntity player,
            BlockView world,
            BlockPos pos,
            CallbackInfoReturnable<Float> cir
    ) {
        if (state.getHardness(world, pos) == -1.0F && AncientEnchantmentLogic.canWorldbreak(player, state)) {
            cir.setReturnValue(AncientEnchantmentLogic.worldbreakerMiningDelta(state, player));
        }
    }
}
