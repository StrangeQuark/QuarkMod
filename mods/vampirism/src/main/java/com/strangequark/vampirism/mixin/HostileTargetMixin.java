package com.strangequark.vampirism.mixin;

import com.strangequark.vampirism.vampire.BloodDrainReactions;
import com.strangequark.vampirism.vampire.VampireData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(MobEntity.class)
public abstract class HostileTargetMixin {
    private static final Set<EntityType<?>> VAMPIRE_HOSTILE_EXCEPTIONS = Set.of(
            EntityType.WITHER,
            EntityType.ENDER_DRAGON,
            EntityType.WARDEN,
            EntityType.ELDER_GUARDIAN,
            EntityType.SHULKER,
            EntityType.GUARDIAN,
            EntityType.BREEZE,
            EntityType.CREAKING,
            EntityType.PILLAGER,
            EntityType.VINDICATOR,
            EntityType.EVOKER,
            EntityType.ILLUSIONER,
            EntityType.RAVAGER,
            EntityType.VEX,
            EntityType.ENDERMAN,
            EntityType.PIGLIN,
            EntityType.PIGLIN_BRUTE,
            EntityType.ZOMBIFIED_PIGLIN,
            EntityType.BLAZE,
            EntityType.WITHER_SKELETON
    );

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void quarkmod_vampirism$ignoreVampirePlayers(LivingEntity target, CallbackInfo ci) {
        if (!(target instanceof PlayerEntity player) || !VampireData.isVampire(player) || !((Object) this instanceof MobEntity mob)) {
            return;
        }

        if (player instanceof ServerPlayerEntity serverPlayer && BloodDrainReactions.isRetaliatingAgainst(mob, serverPlayer)) {
            return;
        }

        if (VAMPIRE_HOSTILE_EXCEPTIONS.contains(mob.getType())) {
            return;
        }

        if (mob instanceof HostileEntity || mob instanceof Monster) {
            ci.cancel();
        }
    }
}
