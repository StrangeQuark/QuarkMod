package com.strangequark.vampirism.entity;

import com.strangequark.vampirism.VampirismMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModEntities {
    private static final double VAMPIRE_MOVEMENT_SPEED = 0.45D;
    private static final double VAMPIRE_ATTACK_DAMAGE = 9.0D;

    public static final EntityType<VampireEntity> VAMPIRE = registerVampire();
    public static final EntityType<VampiricBatEntity> VAMPIRIC_BAT = registerVampiricBat();

    private ModEntities() {
    }

    public static void registerModEntities() {
        FabricDefaultAttributeRegistry.register(VAMPIRE, createVampireAttributes());
        FabricDefaultAttributeRegistry.register(VAMPIRIC_BAT, BatEntity.createBatAttributes());
    }

    private static DefaultAttributeContainer.Builder createVampireAttributes() {
        return ZombieEntity.createZombieAttributes()
                .add(EntityAttributes.MOVEMENT_SPEED, VAMPIRE_MOVEMENT_SPEED)
                .add(EntityAttributes.ATTACK_DAMAGE, VAMPIRE_ATTACK_DAMAGE);
    }

    private static EntityType<VampireEntity> registerVampire() {
        Identifier id = VampirismMod.id("vampire");
        RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, id);
        EntityType<VampireEntity> type = EntityType.Builder
                .create(VampireEntity::new, SpawnGroup.MONSTER)
                .dimensions(0.6F, 1.95F)
                .eyeHeight(1.74F)
                .maxTrackingRange(8)
                .build(key);
        return Registry.register(Registries.ENTITY_TYPE, key, type);
    }

    private static EntityType<VampiricBatEntity> registerVampiricBat() {
        Identifier id = VampirismMod.id("vampiric_bat");
        RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, id);
        EntityType<VampiricBatEntity> type = EntityType.Builder
                .create(VampiricBatEntity::new, SpawnGroup.MONSTER)
                .dimensions(1.0F, 1.0F)
                .eyeHeight(0.5F)
                .maxTrackingRange(8)
                .build(key);
        return Registry.register(Registries.ENTITY_TYPE, key, type);
    }
}
