package com.strangequark.vampirism.entity;

import com.strangequark.vampirism.VampirismMod;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class VampireEntity extends ZombieEntity {
    public static final Identifier DEFAULT_SKIN = VampirismMod.id("textures/entity/vampire.png");
    private static final String SKIN_KEY = "Skin";
    private static final TrackedData<String> SKIN =
            DataTracker.registerData(VampireEntity.class, TrackedDataHandlerRegistry.STRING);

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
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SKIN, DEFAULT_SKIN.toString());
    }

    @Override
    protected void writeCustomData(WriteView view) {
        super.writeCustomData(view);
        view.putString(SKIN_KEY, this.getSkinTexture().toString());
    }

    @Override
    protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        Identifier skin = Identifier.tryParse(view.getString(SKIN_KEY, DEFAULT_SKIN.toString()));
        this.setSkinTexture(skin == null ? DEFAULT_SKIN : skin);
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
