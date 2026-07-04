package com.strangequark.ancientmaze.item;

import com.strangequark.ancientmaze.enchantment.AncientEnchantmentLogic;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public final class AncientPickaxeItem extends Item {
    public AncientPickaxeItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world instanceof ServerWorld serverWorld && user instanceof ServerPlayerEntity serverPlayer) {
            if (AncientEnchantmentLogic.emitEchoProspectorPulse(serverWorld, serverPlayer, stack)) {
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }
}
