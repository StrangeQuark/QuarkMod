package com.strangequark.vampirism.mixin;

import com.strangequark.vampirism.vampire.PlayerBatForm;
import com.strangequark.vampirism.vampire.VampireData;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityBatFormDimensionsMixin {
    @Inject(method = "getBaseDimensions", at = @At("HEAD"), cancellable = true)
    private void quarkmod_vampirism$getBatFormDimensions(EntityPose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (VampireData.isBatForm((PlayerEntity) (Object) this)) {
            cir.setReturnValue(PlayerBatForm.DIMENSIONS);
        }
    }
}
