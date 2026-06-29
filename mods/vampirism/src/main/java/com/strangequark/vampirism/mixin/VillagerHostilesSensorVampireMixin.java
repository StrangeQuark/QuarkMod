package com.strangequark.vampirism.mixin;

import com.strangequark.vampirism.vampire.VampireData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.sensor.VillagerHostilesSensor;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerHostilesSensor.class)
public abstract class VillagerHostilesSensorVampireMixin {
    private static final double VAMPIRE_PLAYER_DANGER_RANGE = 8.0D;

    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private void quarkmod_vampirism$treatVampirePlayersAsHostile(
            ServerWorld world,
            LivingEntity entity,
            LivingEntity target,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (target instanceof PlayerEntity player
                && VampireData.isVampire(player)
                && target.squaredDistanceTo(entity) <= VAMPIRE_PLAYER_DANGER_RANGE * VAMPIRE_PLAYER_DANGER_RANGE) {
            cir.setReturnValue(true);
        }
    }
}
