package com.strangequark.vampirism.mixin;

import com.strangequark.vampirism.vampire.VampireData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class HostileTargetMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void quarkmod_vampirism$ignoreVampirePlayers(LivingEntity target, CallbackInfo ci) {
        if (target instanceof PlayerEntity player && VampireData.isVampire(player) && (Object) this instanceof HostileEntity) {
            ci.cancel();
        }
    }
}
