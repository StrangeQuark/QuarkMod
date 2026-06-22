package com.strangequark.vampirism.client;

import net.minecraft.client.render.entity.state.BatEntityRenderState;

public interface BatFormPlayerRenderState {
    boolean quarkmod_vampirism$isBatForm();

    void quarkmod_vampirism$setBatForm(boolean batForm);

    BatEntityRenderState quarkmod_vampirism$getBatRenderState();
}
