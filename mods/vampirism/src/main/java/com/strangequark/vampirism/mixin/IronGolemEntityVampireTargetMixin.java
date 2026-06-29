package com.strangequark.vampirism.mixin;

import com.strangequark.vampirism.vampire.VampireData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.passive.GolemEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IronGolemEntity.class)
public abstract class IronGolemEntityVampireTargetMixin extends GolemEntity {
    protected IronGolemEntityVampireTargetMixin(EntityType<? extends GolemEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void quarkmod_vampirism$targetVampirePlayers(CallbackInfo ci) {
        IronGolemEntity golem = (IronGolemEntity) (Object) this;
        this.targetSelector.add(
                3,
                new ActiveTargetGoal<>(
                        golem,
                        PlayerEntity.class,
                        10,
                        true,
                        false,
                        (target, world) -> target instanceof PlayerEntity player && VampireData.isVampire(player)
                )
        );
    }
}
