package com.strangequark.grappling.item;

import com.strangequark.grappling.entity.GrapplingHookEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class GrapplingHookItem extends Item {
    private static final double HAND_RANGE = 10.0D;
    private static final float HAND_SPEED = 1.7F;

    public GrapplingHookItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        if (GrapplingHookEntity.releaseActiveHook(player, true)) {
            player.getItemCooldownManager().set(stack, 5);
            return ActionResult.CONSUME;
        }

        if (!(world instanceof ServerWorld serverWorld) || player.getItemCooldownManager().isCoolingDown(stack)) {
            return ActionResult.FAIL;
        }

        GrapplingHookEntity hook = new GrapplingHookEntity(world, player, stack.copyWithCount(1), stack.copyWithCount(1));
        hook.setSourceHookStack(stack);
        hook.configure(GrapplingHookEntity.UseMode.HAND, HAND_RANGE, false, hand);
        hook.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, HAND_SPEED, 0.15F);
        if (serverWorld.spawnEntity(hook)) {
            GrapplingHookEntity.setActiveHook(player, hook);
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 0.7F, 1.2F);
            player.getItemCooldownManager().set(stack, 5);
            return ActionResult.CONSUME;
        }

        return ActionResult.FAIL;
    }
}
