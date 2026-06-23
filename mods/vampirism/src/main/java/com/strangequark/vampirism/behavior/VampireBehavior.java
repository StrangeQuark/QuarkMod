package com.strangequark.vampirism.behavior;

import com.strangequark.vampirism.vampire.VampireData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class VampireBehavior {
    private static final int SUN_DAMAGE_INTERVAL_TICKS = 20;
    private static final float SUN_DAMAGE = 6.0F;
    private static final double HOSTILE_PACIFY_RANGE = 64.0D;

    private VampireBehavior() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(VampireBehavior::tickWorld);
    }

    private static void tickWorld(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers(VampireData::isVampire)) {
            damageInSunlight(world, player);
            pacifyHostileTargets(world, player);
        }
    }

    private static void damageInSunlight(ServerWorld world, ServerPlayerEntity player) {
        if (player.age % SUN_DAMAGE_INTERVAL_TICKS != 0 || !shouldTakeSunDamage(world, player)) {
            return;
        }

        player.setOnFireFor(2.0F);
        player.damage(world, world.getDamageSources().onFire(), SUN_DAMAGE);
    }

    private static boolean shouldTakeSunDamage(ServerWorld world, ServerPlayerEntity player) {
        if (player.isCreative() || player.isSpectator() || player.isSubmergedInWater()) {
            return false;
        }

        if (!world.isDay() || world.isRaining() || world.isThundering()) {
            return false;
        }

        BlockPos eyePos = BlockPos.ofFloored(player.getX(), player.getEyeY(), player.getZ());
        return world.isSkyVisibleAllowingSea(eyePos);
    }

    private static void pacifyHostileTargets(ServerWorld world, ServerPlayerEntity player) {
        for (HostileEntity hostile : world.getEntitiesByClass(
                HostileEntity.class,
                player.getBoundingBox().expand(HOSTILE_PACIFY_RANGE),
                hostile -> hostile.getTarget() == player
        )) {
            hostile.setTarget(null);
        }
    }
}
