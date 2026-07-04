package com.strangequark.ancientmaze.mixin;

import com.strangequark.ancientmaze.enchantment.AncientEnchantmentLogic;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerAncientEnchantmentsMixin {
    @Shadow
    @Final
    private Property levelCost;

    @Inject(method = "updateResult", at = @At("TAIL"))
    private void quarkmod_ancientmaze$rejectAncientEnchantmentsOnOtherTools(CallbackInfo ci) {
        AnvilScreenHandler handler = (AnvilScreenHandler) (Object) this;
        ItemStack result = handler.getSlot(AnvilScreenHandler.OUTPUT_ID).getStack();
        if (AncientEnchantmentLogic.hasInvalidAncientPickaxeOnlyEnchantments(result)) {
            handler.getSlot(AnvilScreenHandler.OUTPUT_ID).setStackNoCallbacks(ItemStack.EMPTY);
            this.levelCost.set(0);
            handler.sendContentUpdates();
        }
    }
}
