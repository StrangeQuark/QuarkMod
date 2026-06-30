package com.strangequark.vampirism.vampire;

import com.strangequark.vampirism.entity.VampireEntity;
import com.strangequark.vampirism.entity.VampiricBatEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.IllagerEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.EntityTypeTags;

public enum BloodType {
    ANIMAL(1, 4, 0.15F),
    HUMANOID(2, 8, 0.8F),
    ILLAGER(2, 8, 0.8F),
    WITCH(2, 8, 0.8F);

    private final int directDrinkPerTick;
    private final int bottleThirst;
    private final float saturationModifier;

    BloodType(int directDrinkPerTick, int bottleThirst, float saturationModifier) {
        this.directDrinkPerTick = directDrinkPerTick;
        this.bottleThirst = bottleThirst;
        this.saturationModifier = saturationModifier;
    }

    public int directDrinkPerTick() {
        return this.directDrinkPerTick;
    }

    public int bottleThirst() {
        return this.bottleThirst;
    }

    public float saturationModifier() {
        return this.saturationModifier;
    }

    public static BloodType fromEntity(LivingEntity entity) {
        if (entity instanceof VampireEntity || entity instanceof VampiricBatEntity) {
            return null;
        }

        if (entity instanceof PlayerEntity player && VampireData.isVampire(player)) {
            return null;
        }

        if (entity.getType().isIn(EntityTypeTags.UNDEAD)) {
            return null;
        }

        if (entity instanceof WitchEntity) {
            return WITCH;
        }

        if (entity instanceof PillagerEntity || entity instanceof PlayerEntity || entity instanceof MerchantEntity) {
            return HUMANOID;
        }

        if (entity instanceof IllagerEntity) {
            return ILLAGER;
        }

        if (entity instanceof AnimalEntity || entity instanceof SpiderEntity || entity instanceof SlimeEntity) {
            return ANIMAL;
        }

        return null;
    }
}
