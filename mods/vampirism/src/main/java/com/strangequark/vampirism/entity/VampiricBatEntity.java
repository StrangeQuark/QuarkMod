package com.strangequark.vampirism.entity;

import com.strangequark.vampirism.VampirismMod;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.BatEntity;
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
    private static final double MAX_HORIZONTAL_SPEED = 0.75D;
    private static final double MAX_UPWARD_SPEED = 0.45D;

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
            return;
        }

        this.fleeTicks--;
        this.setRoosting(false);
        this.applyFleeVelocity();
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
}
