package com.strangequark.vampirism.vampire;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.level.ServerWorldProperties;

public final class VampireStartingEvents {
    private static final long MIDNIGHT_TIME = 18000L;

    private VampireStartingEvents() {
    }

    public static void register() {
        ServerWorldEvents.LOAD.register(VampireStartingEvents::startNewOverworldAtMidnight);
        ServerPlayerEvents.JOIN.register(VampireStartingEvents::startNewPlayerAsVampire);
    }

    private static void startNewOverworldAtMidnight(MinecraftServer server, ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD || !VampirismGameRules.startAsVampire(world)) {
            return;
        }

        if (world.getLevelProperties() instanceof ServerWorldProperties properties && !properties.isInitialized()) {
            properties.setTimeOfDay(MIDNIGHT_TIME);
        }
    }

    private static void startNewPlayerAsVampire(ServerPlayerEntity player) {
        if (!NewPlayerVampireStarter.shouldStartAsVampire(player)) {
            return;
        }

        VampireData.setVampire(player, true);
        NewPlayerVampireStarter.clear(player);
    }
}
