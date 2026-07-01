package com.strangequark.vampirism.mixin.client;

import com.strangequark.vampirism.client.BatFormPlayerRenderState;
import com.strangequark.vampirism.client.FeedingPlayerRenderState;
import net.minecraft.client.render.entity.state.BatEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public abstract class PlayerEntityRenderStateBatFormMixin implements BatFormPlayerRenderState, FeedingPlayerRenderState {
    @Unique
    private boolean quarkmod_vampirism$batForm;

    @Unique
    private boolean quarkmod_vampirism$directFeeding;

    @Unique
    private boolean quarkmod_vampirism$siphoningBlood;

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

    @Override
    public boolean quarkmod_vampirism$isDirectFeeding() {
        return this.quarkmod_vampirism$directFeeding;
    }

    @Override
    public void quarkmod_vampirism$setDirectFeeding(boolean directFeeding) {
        this.quarkmod_vampirism$directFeeding = directFeeding;
    }

    @Override
    public boolean quarkmod_vampirism$isSiphoningBlood() {
        return this.quarkmod_vampirism$siphoningBlood;
    }

    @Override
    public void quarkmod_vampirism$setSiphoningBlood(boolean siphoningBlood) {
        this.quarkmod_vampirism$siphoningBlood = siphoningBlood;
    }
}
