package com.strangequark.ancientexpansion.item;

import com.strangequark.ancientexpansion.block.ModBlocks;
import com.strangequark.ancientexpansion.trial.AncientCityTrialPortal;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.CandleCakeBlock;
import net.minecraft.block.FireBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

public class AncientFlintAndSteelItem extends Item {
    public AncientFlintAndSteelItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        BlockState state = world.getBlockState(pos);

        ActionResult trialActivation = tryActivateTrialPortal(context, player, world, pos);
        if (trialActivation != null) {
            return trialActivation;
        }

        if (CampfireBlock.canBeLit(state) || CandleBlock.canBeLit(state) || CandleCakeBlock.canBeLit(state)) {
            playUseSound(world, player, pos);
            world.setBlockState(pos, state.with(Properties.LIT, true), Block.NOTIFY_ALL);
            world.emitGameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            damageStack(context, player);
            return ActionResult.SUCCESS;
        }

        BlockPos firePos = pos.offset(context.getSide());
        if (!ModBlocks.ANCIENT_FIRE.canPlaceAt(world, firePos) && !FireBlock.canPlaceAt(world, firePos, context.getHorizontalPlayerFacing())) {
            return ActionResult.FAIL;
        }

        playUseSound(world, player, firePos);
        BlockState fireState = ModBlocks.ANCIENT_FIRE.getAncientFireState(world, firePos);
        world.setBlockState(firePos, fireState, Block.NOTIFY_ALL);
        world.emitGameEvent(player, GameEvent.BLOCK_PLACE, firePos);

        ItemStack stack = context.getStack();
        if (player instanceof ServerPlayerEntity serverPlayer) {
            Criteria.PLACED_BLOCK.trigger(serverPlayer, firePos, stack);
        }
        damageStack(context, player);
        return ActionResult.SUCCESS;
    }

    private static ActionResult tryActivateTrialPortal(ItemUsageContext context, PlayerEntity player, World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return null;
        }

        AncientCityTrialPortal.ActivationResult result = AncientCityTrialPortal.activateFrameAt(serverWorld, pos);
        if (result == AncientCityTrialPortal.ActivationResult.NO_FRAME) {
            return null;
        }

        if (result == AncientCityTrialPortal.ActivationResult.ACTIVATED) {
            damageStack(context, player);
            if (player != null) {
                player.sendMessage(Text.translatable("message.quarkmod.ancient_trial_portal_activated"), true);
            }
            serverWorld.playSound(null, pos, SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.BLOCKS, 0.75F, 0.65F);
            serverWorld.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 0.5F, 0.7F);
            return ActionResult.SUCCESS_SERVER;
        }

        if (player != null) {
            player.sendMessage(Text.translatable("message.quarkmod.ancient_trial_portal_already_active"), true);
        }
        return ActionResult.SUCCESS_SERVER;
    }

    private static void playUseSound(World world, PlayerEntity player, BlockPos pos) {
        world.playSound(
                player,
                pos,
                SoundEvents.ITEM_FLINTANDSTEEL_USE,
                SoundCategory.BLOCKS,
                1.0F,
                world.getRandom().nextFloat() * 0.4F + 0.8F
        );
    }

    private static void damageStack(ItemUsageContext context, PlayerEntity player) {
        if (player != null) {
            context.getStack().damage(1, player, LivingEntity.getSlotForHand(context.getHand()));
        }
    }
}
