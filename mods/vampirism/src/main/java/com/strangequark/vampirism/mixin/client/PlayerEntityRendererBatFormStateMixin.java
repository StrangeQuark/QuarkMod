package com.strangequark.vampirism.mixin.client;

import com.strangequark.vampirism.client.BatFormPlayerRenderState;
import com.strangequark.vampirism.client.FeedingPlayerRenderState;
import com.strangequark.vampirism.client.VampirePlayerRenderState;
import com.strangequark.vampirism.vampire.PlayerBatForm;
import com.strangequark.vampirism.vampire.VampireData;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.BatEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererBatFormStateMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
    private void quarkmod_vampirism$updateBatFormRenderState(
            AbstractClientPlayerEntity player,
            PlayerEntityRenderState state,
            float tickDelta,
            CallbackInfo ci
    ) {
        BatFormPlayerRenderState batFormState = (BatFormPlayerRenderState) state;
        FeedingPlayerRenderState feedingState = (FeedingPlayerRenderState) state;
        VampirePlayerRenderState vampireState = (VampirePlayerRenderState) state;
        boolean batForm = VampireData.isBatForm(player);
        batFormState.quarkmod_vampirism$setBatForm(batForm);
        vampireState.quarkmod_vampirism$setVampire(!batForm && VampireData.isVampire(player));
        feedingState.quarkmod_vampirism$setDirectFeeding(!batForm && VampireData.isDirectFeeding(player));
        feedingState.quarkmod_vampirism$setSiphoningBlood(!batForm && VampireData.isSiphoningBlood(player));

        BatEntityRenderState batState = batFormState.quarkmod_vampirism$getBatRenderState();
        batState.age = state.age;
        batState.bodyYaw = state.bodyYaw;
        batState.relativeHeadYaw = state.relativeHeadYaw;
        batState.pitch = state.pitch;
        batState.baseScale = state.baseScale;
        batState.hurt = state.hurt;
        batState.invisible = state.invisible;
        batState.invisibleToPlayer = state.invisibleToPlayer;
        batState.hasOutline = state.hasOutline;
        batState.width = PlayerBatForm.WIDTH;
        batState.height = PlayerBatForm.HEIGHT;
        batState.standingEyeHeight = PlayerBatForm.EYE_HEIGHT;
        batState.roosting = false;
        batState.flyingAnimationState.setRunning(batForm, player.age);
        batState.roostingAnimationState.setRunning(false, player.age);
    }

    @Inject(method = "shouldRenderFeatures(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)Z", at = @At("HEAD"), cancellable = true)
    private void quarkmod_vampirism$hidePlayerFeaturesInBatForm(PlayerEntityRenderState state, CallbackInfoReturnable<Boolean> cir) {
        if (((BatFormPlayerRenderState) state).quarkmod_vampirism$isBatForm()) {
            cir.setReturnValue(false);
        }
    }
}
