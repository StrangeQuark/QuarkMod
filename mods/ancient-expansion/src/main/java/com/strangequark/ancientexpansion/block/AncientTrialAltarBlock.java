package com.strangequark.ancientexpansion.block;

import com.strangequark.ancientexpansion.item.ModItems;
import com.strangequark.ancientexpansion.trial.AncientCityTrialPortal;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class AncientTrialAltarBlock extends Block {
    public AncientTrialAltarBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!stack.isOf(ModItems.ANCIENT_TOKEN)) {
            return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION;
        }

        if (world.isClient) {
            return ActionResult.SUCCESS_SERVER;
        }

        if (!(world instanceof ServerWorld serverWorld)) {
            return ActionResult.SUCCESS_SERVER;
        }

        AncientCityTrialPortal.ActivationResult result = AncientCityTrialPortal.activateNearestFrame(serverWorld, pos);
        if (result == AncientCityTrialPortal.ActivationResult.ACTIVATED) {
            if (!player.getAbilities().creativeMode) {
                stack.decrement(1);
            }
            player.sendMessage(Text.translatable("message.quarkmod.ancient_trial_portal_activated"), true);
            serverWorld.playSound(null, pos, SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.BLOCKS, 0.75F, 0.65F);
            serverWorld.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 0.5F, 0.7F);
            return ActionResult.SUCCESS_SERVER;
        }

        if (result == AncientCityTrialPortal.ActivationResult.ALREADY_ACTIVE) {
            player.sendMessage(Text.translatable("message.quarkmod.ancient_trial_portal_already_active"), true);
        } else {
            player.sendMessage(Text.translatable("message.quarkmod.ancient_trial_frame_missing"), true);
        }
        return ActionResult.SUCCESS_SERVER;
    }
}
