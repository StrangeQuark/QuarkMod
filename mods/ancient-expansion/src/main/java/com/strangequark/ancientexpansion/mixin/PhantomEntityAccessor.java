package com.strangequark.ancientexpansion.mixin;

import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PhantomEntity.class)
public interface PhantomEntityAccessor {
    @Accessor("targetPosition")
    void quarkmod_ancientexpansion$setTargetPosition(Vec3d targetPosition);

    @Accessor("circlingCenter")
    void quarkmod_ancientexpansion$setCirclingCenter(BlockPos circlingCenter);
}
