package com.strangequark.vampirism.mixin;

import com.strangequark.vampirism.vampire.PlayerBatForm;
import com.strangequark.vampirism.vampire.VampireData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class GoldenAppleVampirePlayerCureMixin {
    @Inject(method = "finishUsing", at = @At("HEAD"))
    private void quarkmod_vampirism$cureVampirePlayerWithGoldenApple(
            ItemStack stack,
            World world,
            LivingEntity user,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        if (!(world instanceof ServerWorld serverWorld)
                || !stack.isOf(Items.GOLDEN_APPLE)
                || !(user instanceof ServerPlayerEntity player)
                || !VampireData.isVampire(player)
                || !player.hasStatusEffect(StatusEffects.WEAKNESS)) {
            return;
        }

        PlayerBatForm.setBatForm(player, false);
        VampireData.setVampire(player, false);
        player.removeStatusEffect(StatusEffects.WEAKNESS);
        serverWorld.syncWorldEvent(null, WorldEvents.ZOMBIE_VILLAGER_CURED, BlockPos.ofFloored(player.getPos()), 0);
    }
}
