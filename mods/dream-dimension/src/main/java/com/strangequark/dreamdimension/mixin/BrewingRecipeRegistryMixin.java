package com.strangequark.dreamdimension.mixin;

import com.strangequark.dreamdimension.potion.ModPotions;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.BrewingRecipeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BrewingRecipeRegistry.class)
public abstract class BrewingRecipeRegistryMixin {
    @Inject(method = "hasItemRecipe", at = @At("HEAD"), cancellable = true)
    private void quarkmod$preventDreamPotionItemConversion(
            ItemStack input,
            ItemStack ingredient,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (isBlockedDreamPotionConversion(input, ingredient)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "craft", at = @At("HEAD"), cancellable = true)
    private void quarkmod$keepDreamPotionUnconverted(
            ItemStack ingredient,
            ItemStack input,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        if (isBlockedDreamPotionConversion(input, ingredient)) {
            cir.setReturnValue(input);
        }
    }

    @Inject(method = "craft", at = @At("RETURN"), cancellable = true)
    private void quarkmod$colorDreamPotionResult(
            ItemStack ingredient,
            ItemStack input,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        ItemStack result = cir.getReturnValue();
        if (!result.isEmpty()) {
            ModPotions.applyDreamPotionColor(result);
            cir.setReturnValue(result);
        }
    }

    private static boolean isBlockedDreamPotionConversion(ItemStack input, ItemStack ingredient) {
        return ModPotions.isDreamPotionFamily(input)
                && (ingredient.isOf(Items.GUNPOWDER) || ingredient.isOf(Items.DRAGON_BREATH));
    }
}
