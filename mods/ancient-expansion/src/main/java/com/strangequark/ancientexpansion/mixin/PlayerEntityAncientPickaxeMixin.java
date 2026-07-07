package com.strangequark.ancientexpansion.mixin;

import com.strangequark.ancientexpansion.enchantment.AncientEnchantmentLogic;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityAncientPickaxeMixin {
    @Inject(method = "canHarvest", at = @At("HEAD"), cancellable = true)
    private void quarkmod_ancientexpansion$worldbreakerCanHarvest(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (AncientEnchantmentLogic.canWorldbreak(player, state)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
    private void quarkmod_ancientexpansion$relicEfficiencySpeedsRepeatedBlocks(BlockState state, CallbackInfoReturnable<Float> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        float bonus = AncientEnchantmentLogic.relicEfficiencyBonus(player, state);
        if (bonus > 0.0F) {
            cir.setReturnValue(cir.getReturnValueF() + bonus);
        }
    }
}
