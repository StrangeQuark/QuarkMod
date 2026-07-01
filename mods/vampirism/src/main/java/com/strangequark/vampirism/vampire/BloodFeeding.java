package com.strangequark.vampirism.vampire;

import com.strangequark.vampirism.item.BloodSiphonItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BloodFeeding {
    private static final int MAX_FEED_TICKS = 40;
    private static final double MAX_FEED_DISTANCE_SQUARED = 9.0D;
    private static final Map<UUID, FeedingSession> FEEDING_SESSIONS = new HashMap<>();

    private BloodFeeding() {
    }

    public static void register() {
        UseItemCallback.EVENT.register(BloodFeeding::preventVampireFoodUse);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> handleEntityUse(player, world, hand, entity));
        ServerTickEvents.END_WORLD_TICK.register(BloodFeeding::tickWorld);
    }

    private static ActionResult preventVampireFoodUse(PlayerEntity player, World world, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (VampireData.isVampire(player) && stack.contains(DataComponentTypes.FOOD)) {
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    private static ActionResult handleEntityUse(PlayerEntity player, World world, Hand hand, Entity entity) {
        if (!(entity instanceof LivingEntity target) || player.isSpectator()) {
            return ActionResult.PASS;
        }

        ItemStack stack = player.getStackInHand(hand);
        if (BloodSiphonItem.isSiphon(stack)) {
            if (world.isClient) {
                return canSiphonTarget(player, target) ? ActionResult.SUCCESS : ActionResult.PASS;
            }

            return player instanceof ServerPlayerEntity serverPlayer
                    ? BloodSiphonItem.trySiphon(serverPlayer, target)
                    : ActionResult.PASS;
        }

        if (stack.isEmpty() && canDirectFeedTarget(player, target)) {
            if (world.isClient) {
                return ActionResult.SUCCESS;
            }

            if (player instanceof ServerPlayerEntity serverPlayer) {
                startFeeding(serverPlayer, target);
                return ActionResult.SUCCESS_SERVER;
            }
        }

        if (shouldBlockVampireMerchantInteraction(player, target)) {
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    private static void startFeeding(ServerPlayerEntity player, LivingEntity target) {
        FEEDING_SESSIONS.put(player.getUuid(), new FeedingSession(target.getUuid(), MAX_FEED_TICKS));
        player.getWorld().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.ITEM_HONEY_BOTTLE_DRINK,
                SoundCategory.PLAYERS,
                0.55F,
                0.75F
        );
    }

    private static void tickWorld(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (VampireData.isVampire(player)) {
                BloodThirst.tickVampire(player);
            }

            FeedingSession session = FEEDING_SESSIONS.get(player.getUuid());
            if (session != null && !tickFeedingSession(world, player, session)) {
                FEEDING_SESSIONS.remove(player.getUuid());
            }
        }
    }

    private static boolean tickFeedingSession(ServerWorld world, ServerPlayerEntity player, FeedingSession session) {
        if (session.ticksLeft() <= 0 || !VampireData.isVampire(player) || !player.isAlive() || player.isSpectator()) {
            return false;
        }

        Entity entity = world.getEntity(session.targetUuid());
        if (!(entity instanceof LivingEntity target) || !canDirectFeed(player, target)) {
            return false;
        }

        if (player.squaredDistanceTo(target) > MAX_FEED_DISTANCE_SQUARED || BloodThirst.isFull(player)) {
            return false;
        }

        BloodType bloodType = BloodType.fromEntity(target);
        int targetWholeHealth = (int) target.getHealth();
        if (targetWholeHealth <= 0) {
            return false;
        }

        int amount = Math.min(bloodType.directDrinkPerTick(), BloodThirst.MAX_BLOOD - player.getHungerManager().getFoodLevel());
        amount = Math.min(amount, targetWholeHealth);
        if (amount <= 0) {
            return false;
        }

        target.timeUntilRegen = 0;
        target.hurtTime = 0;
        boolean damaged = target instanceof ServerPlayerEntity targetPlayer
                ? targetPlayer.damage(world, world.getDamageSources().playerAttack(player), amount)
                : target.damage(world, world.getDamageSources().generic(), amount);
        if (!damaged) {
            return false;
        }

        BloodThirst.addBlood(player, amount, bloodType.saturationModifier());
        BloodDrainReactions.reactToDrain(player, target);
        session.decrementTicksLeft();
        if (player.age % 5 == 0) {
            world.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.ITEM_HONEY_BOTTLE_DRINK,
                    SoundCategory.PLAYERS,
                    0.35F,
                    0.8F
            );
        }

        return session.ticksLeft() > 0 && target.isAlive() && !BloodThirst.isFull(player);
    }

    private static boolean canDirectFeed(PlayerEntity player, LivingEntity target) {
        return player instanceof ServerPlayerEntity
                && VampireData.isVampire(player)
                && !player.isCreative()
                && !player.isSpectator()
                && player.isAlive()
                && canDirectFeedTarget(player, target);
    }

    public static boolean canSiphon(PlayerEntity player, LivingEntity target) {
        return player instanceof ServerPlayerEntity
                && !player.isSpectator()
                && canSiphonTarget(player, target);
    }

    private static boolean canSiphonTarget(PlayerEntity player, LivingEntity target) {
        return !player.isSpectator()
                && !(target instanceof PlayerEntity)
                && canFeedFromTarget(player, target);
    }

    private static boolean canDirectFeedTarget(PlayerEntity player, LivingEntity target) {
        return VampireData.isVampire(player)
                && !player.isCreative()
                && !player.isSpectator()
                && player.isAlive()
                && !BloodThirst.isFull(player)
                && canFeedFromTarget(player, target);
    }

    private static boolean shouldBlockVampireMerchantInteraction(PlayerEntity player, LivingEntity target) {
        return VampireData.isVampire(player) && target instanceof MerchantEntity;
    }

    private static boolean canFeedFromTarget(PlayerEntity player, LivingEntity target) {
        return target != player
                && target.isAlive()
                && BloodType.fromEntity(target) != null;
    }

    private static final class FeedingSession {
        private final UUID targetUuid;
        private int ticksLeft;

        private FeedingSession(UUID targetUuid, int ticksLeft) {
            this.targetUuid = targetUuid;
            this.ticksLeft = ticksLeft;
        }

        private UUID targetUuid() {
            return this.targetUuid;
        }

        private int ticksLeft() {
            return this.ticksLeft;
        }

        private void decrementTicksLeft() {
            this.ticksLeft--;
        }
    }
}
