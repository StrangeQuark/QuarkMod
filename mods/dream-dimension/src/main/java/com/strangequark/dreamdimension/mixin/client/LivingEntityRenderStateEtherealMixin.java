package com.strangequark.dreamdimension.mixin.client;

import com.strangequark.dreamdimension.client.EtherealRenderStateAccess;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateEtherealMixin implements EtherealRenderStateAccess {
    @Unique
    private boolean quarkmod$ethereal;

    @Override
    public void quarkmod$setEthereal(boolean ethereal) {
        this.quarkmod$ethereal = ethereal;
    }

    @Override
    public boolean quarkmod$isEthereal() {
        return this.quarkmod$ethereal;
    }
}
