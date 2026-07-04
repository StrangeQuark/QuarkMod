package com.strangequark.ancientmaze.mixin;

import com.strangequark.ancientmaze.village.AncientMazeVillagers;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.VillagerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityAncientMazeTradeMixin {
    @Inject(method = "setVillagerData", at = @At("HEAD"), cancellable = true)
    private void quarkmod_ancientmaze$keepAncientKeeperProfession(VillagerData villagerData, CallbackInfo ci) {
        VillagerEntity villager = (VillagerEntity) (Object) this;
        if (AncientMazeVillagers.shouldKeepKeeperProfession(villager, villagerData)) {
            villager.setVillagerData(AncientMazeVillagers.keeperData(villagerData));
            ci.cancel();
        }
    }

    @Inject(method = "interactMob", at = @At("HEAD"))
    private void quarkmod_ancientmaze$ensureOnlyKeeperOffersBeforeTrading(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        AncientMazeVillagers.ensureOnlyKeeperOffers((VillagerEntity) (Object) this);
    }

    @Inject(method = "fillRecipes", at = @At("HEAD"), cancellable = true)
    private void quarkmod_ancientmaze$replaceKeeperRecipes(CallbackInfo ci) {
        if (AncientMazeVillagers.replaceKeeperOffers((VillagerEntity) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "afterUsing", at = @At("TAIL"))
    private void quarkmod_ancientmaze$lockRelicChoicesAfterTrade(TradeOffer offer, CallbackInfo ci) {
        AncientMazeVillagers.lockRelicChoices((VillagerEntity) (Object) this, offer);
    }

    @Inject(method = "restock", at = @At("HEAD"), cancellable = true)
    private void quarkmod_ancientmaze$preventAncientKeeperRestock(CallbackInfo ci) {
        if (AncientMazeVillagers.shouldPreventRestock((VillagerEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
