package com.strangequark.vampirism.vampire;

import net.minecraft.server.network.ServerPlayerEntity;

public interface NewPlayerVampireStarter {
    boolean quarkmod_vampirism$shouldStartAsVampire();

    void quarkmod_vampirism$setShouldStartAsVampire(boolean shouldStartAsVampire);

    static boolean shouldStartAsVampire(ServerPlayerEntity player) {
        return player instanceof NewPlayerVampireStarter starter && starter.quarkmod_vampirism$shouldStartAsVampire();
    }

    static void clear(ServerPlayerEntity player) {
        if (player instanceof NewPlayerVampireStarter starter) {
            starter.quarkmod_vampirism$setShouldStartAsVampire(false);
        }
    }
}
