package com.strangequark.vampirism.mixin.client;

import com.strangequark.vampirism.client.BatFormPlayerRenderState;
import net.minecraft.client.render.entity.state.BatEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public abstract class PlayerEntityRenderStateBatFormMixin implements BatFormPlayerRenderState {
    @Unique
    private boolean quarkmod_vampirism$batForm;

    @Unique
    private final BatEntityRenderState quarkmod_vampirism$batRenderState = new BatEntityRenderState();

    @Override
    public boolean quarkmod_vampirism$isBatForm() {
        return this.quarkmod_vampirism$batForm;
    }

    @Override
    public void quarkmod_vampirism$setBatForm(boolean batForm) {
        this.quarkmod_vampirism$batForm = batForm;
    }

    @Override
    public BatEntityRenderState quarkmod_vampirism$getBatRenderState() {
        return this.quarkmod_vampirism$batRenderState;
    }
}
