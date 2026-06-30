package com.strangequark.vampirism.mixin.client;

import com.strangequark.vampirism.VampirismMod;
import com.strangequark.vampirism.vampire.VampireData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudBatFormMixin {
    @Unique
    private static final Identifier BLOOD_EMPTY_TEXTURE = VampirismMod.id("hud/blood_empty");
    @Unique
    private static final Identifier BLOOD_HALF_TEXTURE = VampirismMod.id("hud/blood_half");
    @Unique
    private static final Identifier BLOOD_FULL_TEXTURE = VampirismMod.id("hud/blood_full");

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    private Random random;

    @Shadow
    private int ticks;

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

    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
    private void quarkmod_vampirism$renderBloodHud(DrawContext context, PlayerEntity player, int top, int right, CallbackInfo ci) {
        if (!VampireData.isVampire(player)) {
            return;
        }

        HungerManager hungerManager = player.getHungerManager();
        int blood = hungerManager.getFoodLevel();

        for (int index = 0; index < 10; index++) {
            int y = top;
            if (hungerManager.getSaturationLevel() <= 0.0F && this.ticks % (blood * 3 + 1) == 0) {
                y = top + (this.random.nextInt(3) - 1);
            }

            int x = right - index * 8 - 9;
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BLOOD_EMPTY_TEXTURE, x, y, 9, 9);
            if (index * 2 + 1 < blood) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BLOOD_FULL_TEXTURE, x, y, 9, 9);
            }

            if (index * 2 + 1 == blood) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BLOOD_HALF_TEXTURE, x, y, 9, 9);
            }
        }

        ci.cancel();
    }

    @Unique
    private boolean quarkmod_vampirism$shouldHideItemHud() {
        return this.client.player != null && VampireData.isBatForm(this.client.player);
    }
}
