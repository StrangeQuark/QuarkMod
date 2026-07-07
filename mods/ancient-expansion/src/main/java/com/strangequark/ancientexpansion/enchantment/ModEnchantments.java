package com.strangequark.ancientexpansion.enchantment;

import com.strangequark.ancientexpansion.AncientExpansionMod;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

public final class ModEnchantments {
    public static final RegistryKey<Enchantment> RELIC_EFFICIENCY = key("relic_efficiency");
    public static final RegistryKey<Enchantment> ECHO_PROSPECTOR = key("echo_prospector");
    public static final RegistryKey<Enchantment> WORLDBREAKER = key("worldbreaker");

    private ModEnchantments() {
    }

    private static RegistryKey<Enchantment> key(String path) {
        return RegistryKey.of(RegistryKeys.ENCHANTMENT, AncientExpansionMod.id(path));
    }
}
