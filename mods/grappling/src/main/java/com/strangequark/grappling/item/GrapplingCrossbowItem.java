package com.strangequark.grappling.item;

import com.strangequark.grappling.entity.GrapplingHookEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class GrapplingCrossbowItem extends Item {
    private static final double CROSSBOW_RANGE = 30.0D;
    private static final float CROSSBOW_SPEED = 3.15F;

    public GrapplingCrossbowItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        ItemStack crossbowStack = player.getStackInHand(hand);
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        if (!(world instanceof ServerWorld serverWorld) || player.getItemCooldownManager().isCoolingDown(crossbowStack)) {
            return ActionResult.FAIL;
        }

        GrapplingHookEntity activeHook = GrapplingHookEntity.getActiveHook(player);
        if (activeHook != null) {
            if (activeHook.isReelCrossbowHook() && activeHook.isHooked() && !activeHook.hasReelBeenTriggered()) {
                activeHook.startReeling();
                player.getItemCooldownManager().set(crossbowStack, 5);
                return ActionResult.CONSUME;
            }

            activeHook.release(true);
            player.getItemCooldownManager().set(crossbowStack, 5);
            return ActionResult.CONSUME;
        }

        ItemStack ammoStack = findAmmo(player);
        if (ammoStack.isEmpty()) {
            return ActionResult.FAIL;
        }

        ItemStack projectileStack = ammoStack.copyWithCount(1);

        GrapplingHookEntity hook = new GrapplingHookEntity(world, player, projectileStack, crossbowStack.copyWithCount(1));
        hook.setSourceHookStack(ammoStack);
        hook.configure(GrapplingHookEntity.UseMode.REEL_CROSSBOW, CROSSBOW_RANGE, false, hand);
        hook.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, CROSSBOW_SPEED, 0.1F);

        if (serverWorld.spawnEntity(hook)) {
            GrapplingHookEntity.setActiveHook(player, hook);
            crossbowStack.damage(1, player, LivingEntity.getSlotForHand(hand));
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_CROSSBOW_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.0F);
            player.getItemCooldownManager().set(crossbowStack, 5);
            return ActionResult.CONSUME;
        }

        return ActionResult.FAIL;
    }

    private static ItemStack findAmmo(PlayerEntity player) {
        ItemStack offhandStack = player.getOffHandStack();
        if (ModItems.isGrapplingHook(offhandStack)) {
            return offhandStack;
        }

        ItemStack mainHandStack = player.getMainHandStack();
        if (ModItems.isGrapplingHook(mainHandStack)) {
            return mainHandStack;
        }

        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (ModItems.isGrapplingHook(stack)) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }
}
