package com.strangequark.largevillages.mixin;

import com.strangequark.largevillages.village.ModVillagerTrades;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin extends MerchantEntity {
    protected VillagerEntityMixin(EntityType<? extends MerchantEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "fillRecipes", at = @At("TAIL"))
    private void quarkmod$addLargeVillageMapOffer(CallbackInfo ci) {
        VillagerEntity villager = (VillagerEntity) (Object) this;
        VillagerData villagerData = villager.getVillagerData();
        if (villagerData.level() != VillagerData.MAX_LEVEL || !villagerData.profession().matchesKey(VillagerProfession.CARTOGRAPHER)) {
            return;
        }

        TradeOffer offer = ModVillagerTrades.createLargeVillageMapOffer(villager, villager.getRandom());
        if (offer != null) {
            this.getOffers().add(offer);
        }
    }
}
