package com.strangequark.vampirism.vampire;

import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;

public final class BloodThirst {
    public static final int MAX_BLOOD = 20;

    private BloodThirst() {
    }

    public static boolean isFull(PlayerEntity player) {
        return player.getHungerManager().getFoodLevel() >= MAX_BLOOD;
    }

    public static int addBlood(PlayerEntity player, int amount) {
        return addBlood(player, amount, 0.0F);
    }

    public static int addBlood(PlayerEntity player, int amount, float saturationModifier) {
        if (amount <= 0) {
            return 0;
        }

        HungerManager hungerManager = player.getHungerManager();
        int currentBlood = hungerManager.getFoodLevel();
        int gained = Math.min(amount, MAX_BLOOD - currentBlood);
        if (gained <= 0) {
            return 0;
        }

        hungerManager.add(amount, saturationModifier);
        return gained;
    }

    public static void initializeVampire(PlayerEntity player) {
        HungerManager hungerManager = player.getHungerManager();
        if (hungerManager.getFoodLevel() < MAX_BLOOD) {
            hungerManager.setFoodLevel(MAX_BLOOD);
        }
    }

    public static void tickVampire(PlayerEntity player) {
    }
}
