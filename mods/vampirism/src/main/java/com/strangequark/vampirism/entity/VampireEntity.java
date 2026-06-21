package com.strangequark.vampirism.entity;

import com.strangequark.vampirism.VampirismMod;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.conversion.EntityConversionContext;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Identifier;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

public class VampireEntity extends ZombieEntity {
    public static final Identifier DEFAULT_SKIN = VampirismMod.id("textures/entity/vampire.png");
    private static final String SKIN_KEY = "Skin";
    private static final String SPAWN_AS_BAT_KEY = "SpawnAsBat";
    private static final float BAT_TRANSFORM_HEALTH_FRACTION = 0.2F;
    private static final float SPAWN_AS_BAT_CHANCE = 0.2F;
    private static final int PASSIVE_HEAL_INTERVAL_TICKS = 20;
    private static final float PASSIVE_HEAL_AMOUNT = 1.0F;
    private static final TrackedData<String> SKIN =
            DataTracker.registerData(VampireEntity.class, TrackedDataHandlerRegistry.STRING);

    private boolean spawnAsBat;

    public VampireEntity(EntityType<? extends VampireEntity> entityType, World world) {
        super(entityType, world);
    }

    public Identifier getSkinTexture() {
        Identifier skin = Identifier.tryParse(this.getDataTracker().get(SKIN));
        return skin == null ? DEFAULT_SKIN : skin;
    }

    public void setSkinTexture(Identifier skin) {
        this.getDataTracker().set(SKIN, skin.toString());
    }

    @Override
    protected boolean canConvertInWater() {
        return false;
    }

    @Override
    protected void mobTick(ServerWorld world) {
        super.mobTick(world);

        if (this.spawnAsBat) {
            this.spawnAsBat = false;
            this.transformIntoBat(null);
            return;
        }

        if (this.shouldTransformIntoBat()) {
            this.transformIntoBat(this.getFleeThreat());
            return;
        }

        this.passiveHeal();
    }

    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, EntityData entityData) {
        EntityData initializedData = super.initialize(world, difficulty, spawnReason, entityData);
        if (spawnReason != SpawnReason.CONVERSION && this.getRandom().nextFloat() < SPAWN_AS_BAT_CHANCE) {
            this.spawnAsBat = true;
        }
        return initializedData;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SKIN, DEFAULT_SKIN.toString());
    }

    @Override
    protected void writeCustomData(WriteView view) {
        super.writeCustomData(view);
        view.putString(SKIN_KEY, this.getSkinTexture().toString());
        view.putBoolean(SPAWN_AS_BAT_KEY, this.spawnAsBat);
    }

    @Override
    protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        Identifier skin = Identifier.tryParse(view.getString(SKIN_KEY, DEFAULT_SKIN.toString()));
        this.setSkinTexture(skin == null ? DEFAULT_SKIN : skin);
        this.spawnAsBat = view.getBoolean(SPAWN_AS_BAT_KEY, false);
    }

    private boolean shouldTransformIntoBat() {
        if (!this.isAlive() || this.getHealth() > this.getMaxHealth() * BAT_TRANSFORM_HEALTH_FRACTION) {
            return false;
        }

        return this.getTarget() != null || this.getAttacker() != null || this.getLastAttacker() != null;
    }

    private void passiveHeal() {
        if (this.age % PASSIVE_HEAL_INTERVAL_TICKS == 0 && this.isAlive() && this.getHealth() < this.getMaxHealth()) {
            this.setHealth(Math.min(this.getMaxHealth(), this.getHealth() + PASSIVE_HEAL_AMOUNT));
        }
    }

    private LivingEntity getFleeThreat() {
        LivingEntity threat = this.getTarget();
        if (threat == null) {
            threat = this.getAttacker();
        }
        if (threat == null) {
            threat = this.getLastAttacker();
        }
        return threat;
    }

    private void transformIntoBat(LivingEntity threat) {
        final LivingEntity fleeThreat = threat;
        float remainingHealth = this.getHealth();
        EntityConversionContext context = EntityConversionContext.create(this, false, true);
        this.convertTo(ModEntities.VAMPIRIC_BAT, context, SpawnReason.CONVERSION, bat -> {
            bat.setHealth(Math.max(1.0F, Math.min(bat.getMaxHealth(), remainingHealth)));
            bat.setRoosting(false);
            if (fleeThreat != null) {
                bat.startFleeingFrom(fleeThreat);
            }
        });
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_ZOMBIE_VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_ZOMBIE_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_ZOMBIE_VILLAGER_DEATH;
    }
}
