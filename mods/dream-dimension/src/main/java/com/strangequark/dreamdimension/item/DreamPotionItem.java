package com.strangequark.dreamdimension.item;

import com.strangequark.dreamdimension.state.ModAttachments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.World;

public class DreamPotionItem extends Item {
    public DreamPotionItem(Settings settings) {
        super(settings);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        ItemStack result = super.finishUsing(stack, world, user);

        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            ModAttachments.setDreamState(player, ModAttachments.getDreamState(player).withReadyToDream(true));
            player.sendMessage(Text.translatable("message.quarkmod.dream_potion_ready"), true);
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.7F, 0.75F);
        }

        return result;
    }
}
