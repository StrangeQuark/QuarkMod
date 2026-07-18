package com.strangequark.ancientexpansion.mixin;

import com.strangequark.ancientexpansion.item.ModItems;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackAncientSwordEnchantingMixin {
    @Inject(method = "isEnchantable", at = @At("HEAD"), cancellable = true)
    private void quarkmod_ancientexpansion$rejectAncientSwordEnchantingTable(CallbackInfoReturnable<Boolean> cir) {
        if (ModItems.isAncientSword((ItemStack) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
