package com.strangequark.vampirism.mixin.client;

import com.strangequark.vampirism.vampire.VampireData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudBatFormMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void quarkmod_vampirism$hideHotbarInBatForm(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (quarkmod_vampirism$shouldHideItemHud()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
    private void quarkmod_vampirism$hideHeldItemTooltipInBatForm(DrawContext context, CallbackInfo ci) {
        if (quarkmod_vampirism$shouldHideItemHud()) {
            ci.cancel();
        }
    }

    @Unique
    private boolean quarkmod_vampirism$shouldHideItemHud() {
        return this.client.player != null && VampireData.isBatForm(this.client.player);
    }
}
