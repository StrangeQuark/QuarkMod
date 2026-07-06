package com.strangequark.vampirism.vampire;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;

public final class VampirismGameRules {
    public static final GameRules.Key<GameRules.BooleanRule> START_AS_VAMPIRE = GameRuleRegistry.register(
            "quarkmodStartAsVampire",
            GameRules.Category.PLAYER,
            GameRuleFactory.createBooleanRule(false)
    );

    private VampirismGameRules() {
    }

    public static void register() {
    }

    public static boolean startAsVampire(ServerWorld world) {
        return world.getGameRules().getBoolean(START_AS_VAMPIRE);
    }
}
