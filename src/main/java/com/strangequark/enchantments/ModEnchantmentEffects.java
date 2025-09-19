package com.strangequark.enchantments;

import com.mojang.serialization.MapCodec;
import com.strangequark.QuarkMod;
import net.minecraft.enchantment.effect.EnchantmentEntityEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEnchantmentEffects {
    public static final MapCodec<? extends EnchantmentEntityEffect> EXCAVATOR =
            registerEntityEffect("excavator", ExcavatorEnchantmentEffect.CODEC);

    private static MapCodec<? extends EnchantmentEntityEffect> registerEntityEffect(String name, MapCodec<? extends EnchantmentEntityEffect> codec) {
        return Registry.register(Registries.ENCHANTMENT_ENTITY_EFFECT_TYPE, Identifier.of(QuarkMod.MOD_ID, name), codec);
    }

    public static void registerEnchantmentEffects() {
        QuarkMod.LOGGER.info("Registering mod enchantments for " + QuarkMod.MOD_ID);
    }
}
