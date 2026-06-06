package com.strangequark.dreamdimension.potion;

import com.strangequark.dreamdimension.DreamDimensionMod;
import com.strangequark.dreamdimension.effect.ModStatusEffects;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.List;
import java.util.Optional;

public final class ModPotions {
    public static final int STRANGE_POTION_COLOR = 0x210033;
    public static final int RESTLESS_POTION_COLOR = 0x7442A8;

    public static final RegistryEntry<Potion> STRANGE_POTION = register(
            "strange_potion",
            "quarkmod.strange_potion"
    );
    public static final RegistryEntry<Potion> RESTLESS_POTION = register(
            "restless_potion",
            "quarkmod.restless_potion"
    );
    public static final RegistryEntry<Potion> DREAM_POTION = register(
            "dream_potion",
            "quarkmod.dream_potion",
            new StatusEffectInstance(ModStatusEffects.DREAM_READINESS, 20 * 60, 0, false, true, true)
    );

    private ModPotions() {
    }

    public static void registerPotions() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries ->
                entries.add(createStack(Items.POTION, DREAM_POTION))
        );
        ItemGroupEvents.MODIFY_ENTRIES_ALL.register((group, entries) -> {
            normalizeDreamPotionCreativeEntries(entries.getDisplayStacks());
            normalizeDreamPotionCreativeEntries(entries.getSearchTabStacks());
        });
    }

    public static void registerBrewingRecipes() {
        FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> {
            builder.registerPotionRecipe(
                    Potions.AWKWARD,
                    Ingredient.ofItems(Items.CHORUS_FRUIT),
                    STRANGE_POTION
            );
            builder.registerPotionRecipe(
                    STRANGE_POTION,
                    Ingredient.ofItems(Items.GHAST_TEAR),
                    RESTLESS_POTION
            );
            builder.registerPotionRecipe(
                    RESTLESS_POTION,
                    Ingredient.ofItems(Items.PHANTOM_MEMBRANE),
                    DREAM_POTION
            );
        });
    }

    public static boolean isDreamPotionFamily(ItemStack stack) {
        PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        return potionContents != null
                && (potionContents.matches(STRANGE_POTION)
                || potionContents.matches(RESTLESS_POTION)
                || potionContents.matches(DREAM_POTION));
    }

    public static boolean isUnsupportedDreamPotionVariant(ItemStack stack) {
        return isDreamPotionFamily(stack)
                && (stack.isOf(Items.SPLASH_POTION)
                || stack.isOf(Items.LINGERING_POTION)
                || stack.isOf(Items.TIPPED_ARROW));
    }

    public static ItemStack createStack(Item item, RegistryEntry<Potion> potion) {
        ItemStack stack = PotionContentsComponent.createStack(item, potion);
        applyDreamPotionColor(stack);
        return stack;
    }

    public static void applyDreamPotionColor(ItemStack stack) {
        PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents == null) {
            return;
        }

        Optional<Integer> color = colorFor(potionContents);
        if (color.isEmpty()) {
            return;
        }

        stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(
                potionContents.potion(),
                color,
                potionContents.customEffects(),
                potionContents.customName()
        ));
    }

    private static Optional<Integer> colorFor(PotionContentsComponent potionContents) {
        if (potionContents.matches(STRANGE_POTION)) {
            return Optional.of(STRANGE_POTION_COLOR);
        }
        if (potionContents.matches(RESTLESS_POTION)) {
            return Optional.of(RESTLESS_POTION_COLOR);
        }
        return Optional.empty();
    }

    private static void normalizeDreamPotionCreativeEntries(List<ItemStack> stacks) {
        stacks.removeIf(ModPotions::isUnsupportedDreamPotionVariant);
        for (ItemStack stack : stacks) {
            applyDreamPotionColor(stack);
        }
    }

    private static RegistryEntry<Potion> register(
            String path,
            String baseName,
            StatusEffectInstance... effects
    ) {
        return Registry.registerReference(
                Registries.POTION,
                RegistryKey.of(RegistryKeys.POTION, DreamDimensionMod.id(path)),
                new Potion(baseName, effects)
        );
    }
}
