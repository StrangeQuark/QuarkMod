package com.strangequark.vampirism.item;

import com.strangequark.vampirism.vampire.VampireData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

public class VampiricBloodItem extends Item {
    public VampiricBloodItem(Settings settings) {
        super(settings);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        ItemStack result = super.finishUsing(stack, world, user);
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            VampireData.setVampire(player, true);
        }

        return result;
    }
}
