package com.strangequark.vampirism.mixin;

import com.strangequark.vampirism.vampire.NewPlayerVampireStarter;
import com.strangequark.vampirism.vampire.VampirismGameRules;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.util.ErrorReporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerStartAsVampireMixin {
    @Inject(method = "loadPlayerData", at = @At("RETURN"))
    private void quarkmod_vampirism$markNewPlayersForVampireStart(
            ServerPlayerEntity player,
            ErrorReporter reporter,
            CallbackInfoReturnable<Optional<ReadView>> cir
    ) {
        if (player instanceof NewPlayerVampireStarter starter) {
            starter.quarkmod_vampirism$setShouldStartAsVampire(
                    cir.getReturnValue().isEmpty() && VampirismGameRules.startAsVampire(player.getWorld())
            );
        }
    }
}
