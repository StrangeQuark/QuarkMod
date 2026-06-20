package com.strangequark.dreamdimension.mixin;

import com.strangequark.dreamdimension.potion.ModPotions;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.TippedArrowRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TippedArrowRecipe.class)
public abstract class TippedArrowRecipeMixin {
    @Inject(
            method = "matches(Lnet/minecraft/recipe/input/CraftingRecipeInput;Lnet/minecraft/world/World;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quarkmod$preventDreamTippedArrowMatch(
            CraftingRecipeInput input,
            World world,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (hasDreamPotionCenter(input)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "craft(Lnet/minecraft/recipe/input/CraftingRecipeInput;Lnet/minecraft/registry/RegistryWrapper$WrapperLookup;)Lnet/minecraft/item/ItemStack;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quarkmod$preventDreamTippedArrowCraft(
            CraftingRecipeInput input,
            RegistryWrapper.WrapperLookup registries,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        if (hasDreamPotionCenter(input)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    private static boolean hasDreamPotionCenter(CraftingRecipeInput input) {
        return input.getWidth() == 3
                && input.getHeight() == 3
                && ModPotions.isDreamPotionFamily(input.getStackInSlot(1, 1));
    }
}
