package com.strangequark.vampirism.entity;

import com.strangequark.vampirism.VampirismMod;
import com.strangequark.vampirism.vampire.VampireData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.conversion.EntityConversionContext;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class VampiricBatEntity extends BatEntity {
    public static final Identifier TEXTURE = VampirismMod.id("textures/entity/vampiric_bat.png");
    public static final Identifier EYES_TEXTURE = VampirismMod.id("textures/entity/vampiric_bat_eyes.png");

    private static final String FLEE_TICKS_KEY = "FleeTicks";
    private static final String FLEE_FROM_X_KEY = "FleeFromX";
    private static final String FLEE_FROM_Y_KEY = "FleeFromY";
    private static final String FLEE_FROM_Z_KEY = "FleeFromZ";
    private static final int FLEE_DURATION_TICKS = 200;
    private static final double FLEE_PUSH = 0.16D;
    private static final double FLEE_LIFT = 0.09D;
    private static final double PURSUE_RANGE = 32.0D;
    private static final double ATTACK_TRANSFORM_DISTANCE = 4.0D;
    private static final double PURSUE_PUSH = 0.12D;
    private static final double MAX_HORIZONTAL_SPEED = 0.75D;
    private static final double MAX_UPWARD_SPEED = 0.45D;
    private static final float ATTACK_HEALTH_FRACTION = 0.5F;
    private static final int PASSIVE_HEAL_INTERVAL_TICKS = 20;
    private static final float PASSIVE_HEAL_AMOUNT = 1.0F;

    private int fleeTicks;
    private Vec3d fleeFrom = Vec3d.ZERO;

    public VampiricBatEntity(EntityType<? extends BatEntity> entityType, World world) {
        super(entityType, world);
    }

    public void startFleeingFrom(LivingEntity threat) {
        this.fleeTicks = FLEE_DURATION_TICKS;
        this.fleeFrom = threat == null ? this.getPos().subtract(this.getRotationVector()) : threat.getPos();
        this.setRoosting(false);
        this.applyFleeVelocity();
    }

    @Override
    protected void mobTick(ServerWorld world) {
        super.mobTick(world);

        if (this.fleeTicks <= 0) {
            this.passiveHeal();
            this.pursueNearbyPlayer(world);
            return;
        }

        this.fleeTicks--;
        this.setRoosting(false);
        this.applyFleeVelocity();
        this.passiveHeal();
    }

    @Override
    protected void writeCustomData(WriteView view) {
        super.writeCustomData(view);
        view.putInt(FLEE_TICKS_KEY, this.fleeTicks);
        view.putDouble(FLEE_FROM_X_KEY, this.fleeFrom.x);
        view.putDouble(FLEE_FROM_Y_KEY, this.fleeFrom.y);
        view.putDouble(FLEE_FROM_Z_KEY, this.fleeFrom.z);
    }

    @Override
    protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        this.fleeTicks = view.getInt(FLEE_TICKS_KEY, 0);
        this.fleeFrom = new Vec3d(
                view.getDouble(FLEE_FROM_X_KEY, this.getX()),
                view.getDouble(FLEE_FROM_Y_KEY, this.getY()),
                view.getDouble(FLEE_FROM_Z_KEY, this.getZ())
        );
    }

    private void applyFleeVelocity() {
        Vec3d away = this.getPos().subtract(this.fleeFrom.x, this.getY(), this.fleeFrom.z);
        if (away.horizontalLengthSquared() < 1.0E-4D) {
            away = this.getRotationVector();
        }

        away = new Vec3d(away.x, 0.0D, away.z).normalize();
        Vec3d velocity = this.getVelocity()
                .multiply(0.85D)
                .add(away.multiply(FLEE_PUSH))
                .add(0.0D, FLEE_LIFT, 0.0D);

        double horizontalSpeed = velocity.horizontalLength();
        if (horizontalSpeed > MAX_HORIZONTAL_SPEED) {
            double scale = MAX_HORIZONTAL_SPEED / horizontalSpeed;
            velocity = new Vec3d(velocity.x * scale, velocity.y, velocity.z * scale);
        }

        this.setVelocity(
                velocity.x,
                MathHelper.clamp(velocity.y, -0.2D, MAX_UPWARD_SPEED),
                velocity.z
        );
    }

    private void pursueNearbyPlayer(ServerWorld world) {
        if (this.getHealth() <= this.getMaxHealth() * ATTACK_HEALTH_FRACTION) {
            return;
        }

        ServerPlayerEntity target = this.findClosestTargetPlayer(world);
        if (target == null) {
            return;
        }

        this.setRoosting(false);
        if (this.squaredDistanceTo(target) <= ATTACK_TRANSFORM_DISTANCE * ATTACK_TRANSFORM_DISTANCE) {
            this.transformIntoVampire(target);
            return;
        }

        this.applyPursuitVelocity(target);
    }

    private ServerPlayerEntity findClosestTargetPlayer(ServerWorld world) {
        ServerPlayerEntity closest = null;
        double closestDistance = PURSUE_RANGE * PURSUE_RANGE;

        for (ServerPlayerEntity player : world.getPlayers(this::canPursuePlayer)) {
            double distance = this.squaredDistanceTo(player);
            if (distance < closestDistance) {
                closest = player;
                closestDistance = distance;
            }
        }

        return closest;
    }

    private boolean canPursuePlayer(ServerPlayerEntity player) {
        return player.isAlive()
                && !player.isCreative()
                && !player.isSpectator()
                && !VampireData.isVampire(player);
    }

    private void applyPursuitVelocity(ServerPlayerEntity target) {
        Vec3d targetPos = target.getPos().add(0.0D, target.getHeight() * 0.5D, 0.0D);
        Vec3d direction = targetPos.subtract(this.getPos());
        if (direction.lengthSquared() < 1.0E-4D) {
            return;
        }

        direction = direction.normalize();
        Vec3d velocity = this.getVelocity()
                .multiply(0.85D)
                .add(direction.multiply(PURSUE_PUSH));

        double horizontalSpeed = velocity.horizontalLength();
        if (horizontalSpeed > MAX_HORIZONTAL_SPEED) {
            double scale = MAX_HORIZONTAL_SPEED / horizontalSpeed;
            velocity = new Vec3d(velocity.x * scale, velocity.y, velocity.z * scale);
        }

        this.setVelocity(
                velocity.x,
                MathHelper.clamp(velocity.y, -0.2D, MAX_UPWARD_SPEED),
                velocity.z
        );
    }

    private void passiveHeal() {
        if (this.age % PASSIVE_HEAL_INTERVAL_TICKS == 0 && this.isAlive() && this.getHealth() < this.getMaxHealth()) {
            this.setHealth(Math.min(this.getMaxHealth(), this.getHealth() + PASSIVE_HEAL_AMOUNT));
        }
    }

    private void transformIntoVampire(ServerPlayerEntity target) {
        float remainingHealth = this.getHealth();
        EntityConversionContext context = EntityConversionContext.create(this, false, true);
        this.convertTo(ModEntities.VAMPIRE, context, SpawnReason.CONVERSION, vampire -> {
            vampire.setHealth(Math.max(1.0F, Math.min(vampire.getMaxHealth(), remainingHealth)));
            vampire.setTarget(target);
            vampire.setAttacking(true);
        });
    }
}
