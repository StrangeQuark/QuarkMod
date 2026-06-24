package com.strangequark.vampirism.vampire;

import com.strangequark.vampirism.block.ModBlocks;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

public final class CoffinSleepHandler {
    private static final long DAY_LENGTH = 24_000L;
    private static final long NIGHT_START = 13_000L;

    private CoffinSleepHandler() {
    }

    public static void register() {
        EntitySleepEvents.ALLOW_SLEEP_TIME.register((player, sleepingPos, vanillaResult) -> {
            if (player.getWorld() instanceof ServerWorld world
                    && world.getBlockState(sleepingPos).isOf(ModBlocks.COFFIN)
                    && VampireData.isVampire(player)
                    && isDaySleepTime(world)) {
                return ActionResult.SUCCESS;
            }

            return ActionResult.PASS;
        });
    }

    public static void setSpawnPoint(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        player.setSpawnPoint(new ServerPlayerEntity.Respawn(world.getRegistryKey(), pos, player.getYaw(), false), true);
    }

    public static boolean isDaySleepTime(ServerWorld world) {
        return Math.floorMod(world.getTimeOfDay(), DAY_LENGTH) < NIGHT_START;
    }

    public static boolean shouldSuppressDayWake(PlayerEntity player) {
        return player instanceof ServerPlayerEntity serverPlayer
                && isDaySleepTime(serverPlayer.getWorld())
                && isCoffinSleeper(serverPlayer);
    }

    public static boolean isCoffinSleeper(PlayerEntity player) {
        return player.isSleeping()
                && player.getSleepingPosition()
                .map(pos -> player.getWorld().getBlockState(pos).isOf(ModBlocks.COFFIN))
                .orElse(false);
    }

    public static long nextNightTime(long timeOfDay) {
        long dayStart = timeOfDay - Math.floorMod(timeOfDay, DAY_LENGTH);
        long nightTime = dayStart + NIGHT_START;
        return nightTime > timeOfDay ? nightTime : nightTime + DAY_LENGTH;
    }
}
