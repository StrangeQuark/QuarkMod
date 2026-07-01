package com.strangequark.vampirism.item;

import com.strangequark.vampirism.vampire.BloodFeeding;
import com.strangequark.vampirism.vampire.BloodDrainReactions;
import com.strangequark.vampirism.vampire.BloodType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public class BloodSiphonItem extends Item {
    private static final String SIPHON_KEY = "quarkmod:blood_siphon";
    private static final String SIPHON_VERSION_KEY = "quarkmod:siphon_version";

    public BloodSiphonItem(Settings settings) {
        super(settings);
    }

    @Override
    public ItemStack getDefaultStack() {
        ItemStack stack = super.getDefaultStack();
        markSiphon(stack);
        return stack;
    }

    public static NbtComponent createSiphonData() {
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(SIPHON_KEY, true);
        nbt.putInt(SIPHON_VERSION_KEY, 1);
        return NbtComponent.of(nbt);
    }

    public static void markSiphon(ItemStack stack) {
        stack.set(DataComponentTypes.CUSTOM_DATA, createSiphonData());
    }

    public static boolean isSiphon(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        return stack.isOf(ModItems.BLOOD_SIPHON)
                && customData != null
                && customData.copyNbt().getBoolean(SIPHON_KEY, false);
    }

    public static ActionResult trySiphon(ServerPlayerEntity player, LivingEntity target) {
        if (!BloodFeeding.canSiphon(player, target)) {
            return ActionResult.PASS;
        }

        BloodType bloodType = BloodType.fromEntity(target);
        if (bloodType == null || target.getHealth() < bloodType.bottleThirst()) {
            return ActionResult.FAIL;
        }

        Hand bottleHand = findBottleHand(player);
        int bottleSlot = bottleHand == null ? findBottleSlot(player) : -1;
        if (bottleHand == null && bottleSlot < 0) {
            return ActionResult.FAIL;
        }

        ServerWorld world = player.getWorld();
        target.timeUntilRegen = 0;
        target.hurtTime = 0;
        if (!target.damage(world, world.getDamageSources().generic(), bloodType.bottleThirst())) {
            return ActionResult.FAIL;
        }
        BloodDrainReactions.reactToDrain(player, target);
        BloodFeeding.startSiphonPose(player);

        ItemStack bloodBottle = new ItemStack(ModItems.bloodBottleFor(bloodType));
        if (bottleHand != null) {
            replaceBottleInHand(player, bottleHand, bloodBottle);
        } else {
            ItemStack bottleStack = player.getInventory().getStack(bottleSlot);
            bottleStack.decrement(1);
            if (bottleStack.isEmpty()) {
                player.getInventory().setStack(bottleSlot, ItemStack.EMPTY);
            }
            player.giveOrDropStack(bloodBottle);
        }

        world.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.ITEM_BOTTLE_FILL,
                SoundCategory.PLAYERS,
                1.0F,
                0.6F
        );
        return ActionResult.SUCCESS_SERVER;
    }

    private static Hand findBottleHand(ServerPlayerEntity player) {
        if (player.getOffHandStack().isOf(Items.GLASS_BOTTLE)) {
            return Hand.OFF_HAND;
        }

        ItemStack mainHandStack = player.getMainHandStack();
        if (mainHandStack.isOf(Items.GLASS_BOTTLE)) {
            return Hand.MAIN_HAND;
        }

        return null;
    }

    private static int findBottleSlot(ServerPlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().getMainStacks().size(); slot++) {
            if (player.getInventory().getStack(slot).isOf(Items.GLASS_BOTTLE)) {
                return slot;
            }
        }

        return -1;
    }

    private static void replaceBottleInHand(ServerPlayerEntity player, Hand hand, ItemStack bloodBottle) {
        ItemStack bottleStack = player.getStackInHand(hand);
        bottleStack.decrement(1);
        if (bottleStack.isEmpty()) {
            player.setStackInHand(hand, bloodBottle);
            return;
        }

        player.giveOrDropStack(bloodBottle);
    }
}
