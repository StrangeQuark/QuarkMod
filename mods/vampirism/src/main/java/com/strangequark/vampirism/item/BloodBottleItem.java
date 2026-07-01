package com.strangequark.vampirism.item;

import com.strangequark.vampirism.vampire.BloodThirst;
import com.strangequark.vampirism.vampire.VampireData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class BloodBottleItem extends Item {
    private final int thirst;
    private final float saturationModifier;

    public BloodBottleItem(Settings settings, int thirst, float saturationModifier) {
        super(settings);
        this.thirst = thirst;
        this.saturationModifier = saturationModifier;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!VampireData.isVampire(user) || BloodThirst.isFull(user)) {
            return ActionResult.FAIL;
        }

        return super.use(world, user, hand);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient && user instanceof ServerPlayerEntity player && VampireData.isVampire(player)) {
            BloodThirst.addBlood(player, this.thirst, this.saturationModifier);
        }

        return super.finishUsing(stack, world, user);
    }
}
