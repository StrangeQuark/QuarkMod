package com.strangequark.vampirism.vampire;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BloodDrainReactions {
    private static final int FLEE_HORIZONTAL_RANGE = 10;
    private static final int FLEE_VERTICAL_RANGE = 4;
    private static final double FLEE_SPEED = 1.45D;
    private static final double FLEE_PUSH = 0.55D;
    private static final int RETALIATION_TICKS = 200;
    private static final Map<UUID, Retaliation> RETALIATIONS = new HashMap<>();

    private BloodDrainReactions() {
    }

    public static void reactToDrain(ServerPlayerEntity drainer, LivingEntity target) {
        if (target instanceof MobEntity mob && mob instanceof Monster) {
            markRetaliating(mob, drainer);
            mob.setTarget(drainer);
            return;
        }

        if (target instanceof AnimalEntity animal) {
            fleeFrom(animal, drainer);
        }
    }

    public static boolean isRetaliatingAgainst(MobEntity mob, ServerPlayerEntity player) {
        Retaliation retaliation = RETALIATIONS.get(mob.getUuid());
        if (retaliation == null) {
            return false;
        }

        if (mob.age > retaliation.expiresAtAge()) {
            RETALIATIONS.remove(mob.getUuid());
            return false;
        }

        return retaliation.playerUuid().equals(player.getUuid());
    }

    private static void markRetaliating(MobEntity mob, ServerPlayerEntity player) {
        RETALIATIONS.put(mob.getUuid(), new Retaliation(player.getUuid(), mob.age + RETALIATION_TICKS));
    }

    private static void fleeFrom(PathAwareEntity mob, ServerPlayerEntity player) {
        Vec3d away = mob.getPos().subtract(player.getPos());
        if (away.horizontalLengthSquared() < 1.0E-4D) {
            away = mob.getRotationVector();
        }

        Vec3d horizontalAway = new Vec3d(away.x, 0.0D, away.z).normalize();
        mob.addVelocity(horizontalAway.multiply(FLEE_PUSH).add(0.0D, 0.1D, 0.0D));

        Vec3d targetPos = NoPenaltyTargeting.findFrom(mob, FLEE_HORIZONTAL_RANGE, FLEE_VERTICAL_RANGE, player.getPos());
        if (targetPos == null) {
            targetPos = mob.getPos().add(horizontalAway.multiply(FLEE_HORIZONTAL_RANGE));
        }

        mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, FLEE_SPEED);
    }

    private record Retaliation(UUID playerUuid, int expiresAtAge) {
    }
}
