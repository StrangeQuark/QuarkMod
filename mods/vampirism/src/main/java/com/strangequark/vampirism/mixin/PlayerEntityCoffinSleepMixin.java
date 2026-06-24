package com.strangequark.vampirism.mixin;

import com.strangequark.vampirism.vampire.CoffinSleepHandler;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityCoffinSleepMixin {
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;wakeUp(ZZ)V"
            )
    )
    private void quarkmod_vampirism$keepCoffinSleepersAsleepDuringDay(PlayerEntity player, boolean skipSleepTimer, boolean updateSleepingPlayers) {
        if (CoffinSleepHandler.shouldSuppressDayWake(player)) {
            return;
        }

        player.wakeUp(skipSleepTimer, updateSleepingPlayers);
    }
}
