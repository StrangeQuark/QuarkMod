package com.strangequark.ancientexpansion.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MobEntity.class)
public interface MobEntityGoalSelectorAccessor {
    @Accessor("goalSelector")
    GoalSelector quarkmod_ancientexpansion$getGoalSelector();
}
