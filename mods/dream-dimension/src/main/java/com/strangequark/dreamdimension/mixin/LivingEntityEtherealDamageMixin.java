package com.strangequark.dreamdimension.mixin;

import com.strangequark.dreamdimension.ethereal.EtherealEvents;
import com.strangequark.dreamdimension.item.ModItems;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityEtherealDamageMixin {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.FEET,
            EquipmentSlot.LEGS,
            EquipmentSlot.CHEST,
            EquipmentSlot.HEAD
    };

    @Shadow
    public abstract ItemStack getEquippedStack(EquipmentSlot slot);

    @Shadow
    protected abstract void damageEquipment(DamageSource source, float amount, EquipmentSlot... slots);

    @Inject(method = "getDamageBlockedAmount", at = @At("HEAD"), cancellable = true)
    private void quarkmod$preventEtherealDamageShieldMitigation(
            ServerWorld world,
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Float> cir
    ) {
        if (EtherealEvents.isEtherealDamageSource(source)) {
            cir.setReturnValue(0.0F);
        }
    }

    @Inject(method = "applyArmorToDamage", at = @At("HEAD"), cancellable = true)
    private void quarkmod$applyOnlyEtherealArmorToEtherealDamage(
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Float> cir
    ) {
        if (!EtherealEvents.isEtherealDamageSource(source) || source.isIn(DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }

        float armor = 0.0F;
        float toughness = 0.0F;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = getEquippedStack(slot);
            int armorValue = ModItems.getEtherealArmorValue(slot, stack);
            if (armorValue > 0) {
                armor += armorValue;
                toughness += ModItems.getEtherealArmorToughness(slot, stack);
                damageEquipment(source, amount, slot);
            }
        }

        LivingEntity entity = (LivingEntity) (Object) this;
        cir.setReturnValue(DamageUtil.getDamageLeft(entity, amount, source, armor, toughness));
    }

    @Inject(method = "modifyAppliedDamage", at = @At("HEAD"), cancellable = true)
    private void quarkmod$preventNonArmorEtherealDamageMitigation(
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Float> cir
    ) {
        if (EtherealEvents.isEtherealDamageSource(source)) {
            cir.setReturnValue(amount);
        }
    }
}
