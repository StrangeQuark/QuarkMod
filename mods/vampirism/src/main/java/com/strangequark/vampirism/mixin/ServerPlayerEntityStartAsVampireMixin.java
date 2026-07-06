package com.strangequark.vampirism.mixin;

import com.strangequark.vampirism.vampire.NewPlayerVampireStarter;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityStartAsVampireMixin implements NewPlayerVampireStarter {
    @Unique
    private boolean quarkmod_vampirism$shouldStartAsVampire;

    @Override
    public boolean quarkmod_vampirism$shouldStartAsVampire() {
        return this.quarkmod_vampirism$shouldStartAsVampire;
    }

    @Override
    public void quarkmod_vampirism$setShouldStartAsVampire(boolean shouldStartAsVampire) {
        this.quarkmod_vampirism$shouldStartAsVampire = shouldStartAsVampire;
    }
}
