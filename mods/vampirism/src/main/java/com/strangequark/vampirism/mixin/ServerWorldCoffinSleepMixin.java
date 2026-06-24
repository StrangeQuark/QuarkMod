package com.strangequark.vampirism.mixin;

import com.strangequark.vampirism.vampire.CoffinSleepHandler;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ServerWorld.class)
public abstract class ServerWorldCoffinSleepMixin {
    @ModifyArg(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;setTimeOfDay(J)V"
            ),
            index = 0
    )
    private long quarkmod_vampirism$wakeCoffinSleepersAtNight(long vanillaWakeTime) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (world.getPlayers().stream().anyMatch(CoffinSleepHandler::isCoffinSleeper)) {
            return CoffinSleepHandler.nextNightTime(world.getTimeOfDay());
        }

        return vanillaWakeTime;
    }
}
