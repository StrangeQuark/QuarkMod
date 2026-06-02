package com.strangequark.grappling.entity;

import com.strangequark.grappling.GrapplingMod;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class ModEntities {
    public static final EntityType<GrapplingHookEntity> GRAPPLING_HOOK = registerGrapplingHook();

    private ModEntities() {
    }

    public static void registerModEntities() {
    }

    private static EntityType<GrapplingHookEntity> registerGrapplingHook() {
        Identifier id = Identifier.of(GrapplingMod.REGISTRY_NAMESPACE, "grappling_hook");
        RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, id);
        EntityType<GrapplingHookEntity> type = EntityType.Builder
                .<GrapplingHookEntity>create(GrapplingHookEntity::new, SpawnGroup.MISC)
                .dimensions(0.25F, 0.25F)
                .maxTrackingRange(8)
                .trackingTickInterval(2)
                .build(key);
        return Registry.register(Registries.ENTITY_TYPE, key, type);
    }
}
