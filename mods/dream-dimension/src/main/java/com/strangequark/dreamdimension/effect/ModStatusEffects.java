package com.strangequark.dreamdimension.effect;

import com.strangequark.dreamdimension.DreamDimensionMod;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;

public final class ModStatusEffects {
    public static final RegistryEntry<StatusEffect> DREAM_READINESS = Registry.registerReference(
            Registries.STATUS_EFFECT,
            RegistryKey.of(RegistryKeys.STATUS_EFFECT, DreamDimensionMod.id("dream_readiness")),
            new DreamReadinessStatusEffect()
    );

    private ModStatusEffects() {
    }

    public static void registerStatusEffects() {
    }
}
