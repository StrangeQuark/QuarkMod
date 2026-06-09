package com.strangequark.dreamdimension.mixin;

import com.strangequark.dreamdimension.ethereal.EtherealEntityAccess;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityEtherealVariantMixin implements EtherealEntityAccess {
    @Unique
    private static final TrackedData<Boolean> QUARKMOD_ETHEREAL_VARIANT = DataTracker.registerData(
            LivingEntity.class,
            TrackedDataHandlerRegistry.BOOLEAN
    );

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void quarkmod$initEtherealVariantTracker(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(QUARKMOD_ETHEREAL_VARIANT, false);
    }

    @Override
    public void quarkmod$setEtherealVariant(boolean ethereal) {
        ((LivingEntity) (Object) this).getDataTracker().set(QUARKMOD_ETHEREAL_VARIANT, ethereal);
    }

    @Override
    public boolean quarkmod$isEtherealVariant() {
        return ((LivingEntity) (Object) this).getDataTracker().get(QUARKMOD_ETHEREAL_VARIANT);
    }
}
