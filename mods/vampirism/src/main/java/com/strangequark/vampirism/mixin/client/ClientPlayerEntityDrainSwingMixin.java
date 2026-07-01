package com.strangequark.vampirism.mixin.client;

import com.strangequark.vampirism.vampire.BloodDrainClientState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityDrainSwingMixin {
    @Inject(method = "swingHand(Lnet/minecraft/util/Hand;)V", at = @At("HEAD"), cancellable = true)
    private void quarkmod_vampirism$suppressDrainSwing(Hand hand, CallbackInfo ci) {
        if (BloodDrainClientState.consumeSwingSuppression()) {
            ci.cancel();
        }
    }
}
