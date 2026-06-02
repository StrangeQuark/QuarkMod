package com.strangequark.excavator.enchantments;

import com.mojang.serialization.MapCodec;
import com.strangequark.excavator.ExcavatorMod;
import net.minecraft.enchantment.effect.EnchantmentEntityEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEnchantmentEffects {
    public static final MapCodec<? extends EnchantmentEntityEffect> EXCAVATOR =
            registerEntityEffect("excavator", ExcavatorEnchantmentEffect.CODEC);

    private static MapCodec<? extends EnchantmentEntityEffect> registerEntityEffect(String name, MapCodec<? extends EnchantmentEntityEffect> codec) {
        return Registry.register(Registries.ENCHANTMENT_ENTITY_EFFECT_TYPE, Identifier.of(ExcavatorMod.REGISTRY_NAMESPACE, name), codec);
    }

    public static void registerEnchantmentEffects() {
        ExcavatorMod.LOGGER.info("Registering mod enchantments for " + ExcavatorMod.REGISTRY_NAMESPACE);
    }
}
