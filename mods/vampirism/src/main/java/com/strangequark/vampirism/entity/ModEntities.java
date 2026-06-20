package com.strangequark.vampirism.entity;

import com.strangequark.vampirism.VampirismMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModEntities {
    public static final EntityType<VampireEntity> VAMPIRE = registerVampire();

    private ModEntities() {
    }

    public static void registerModEntities() {
        FabricDefaultAttributeRegistry.register(VAMPIRE, ZombieEntity.createZombieAttributes());
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
}
