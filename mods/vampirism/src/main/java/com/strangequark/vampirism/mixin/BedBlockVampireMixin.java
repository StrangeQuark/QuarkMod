package com.strangequark.vampirism.mixin;

import com.strangequark.vampirism.block.CoffinBlock;
import com.strangequark.vampirism.vampire.VampireData;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BedBlock.class)
public abstract class BedBlockVampireMixin {
    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void quarkmod_vampirism$disallowVampireBedUse(
            BlockState state,
            World world,
            BlockPos pos,
            PlayerEntity player,
            BlockHitResult hit,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        if (state.getBlock() instanceof CoffinBlock || !VampireData.isVampire(player)) {
            return;
        }

        if (!world.isClient) {
            player.sendMessage(Text.translatable("block.quarkmod.bed.vampire_forbidden"), true);
        }
        cir.setReturnValue(ActionResult.SUCCESS_SERVER);
    }
}
