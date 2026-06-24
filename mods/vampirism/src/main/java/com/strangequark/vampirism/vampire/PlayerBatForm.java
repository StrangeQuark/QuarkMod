package com.strangequark.vampirism.vampire;

import com.strangequark.vampirism.entity.VampiricBatEntity;
import com.strangequark.vampirism.network.ToggleBatFormPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;

public final class PlayerBatForm {
    public static final float WIDTH = 0.75F;
    public static final float HEIGHT = 0.75F;
    public static final float EYE_HEIGHT = 0.375F;
    public static final EntityDimensions DIMENSIONS = EntityDimensions.changing(WIDTH, HEIGHT).withEyeHeight(EYE_HEIGHT);
    public static final double FLIGHT_DAMPING = VampiricBatEntity.MOVEMENT_DAMPING;
    public static final double FLIGHT_ACCELERATION = 0.045D;
    public static final double MAX_HORIZONTAL_SPEED = 0.25D;
    public static final double SPRINT_HORIZONTAL_SPEED = 0.28D;
    public static final double MAX_DOWNWARD_SPEED = 0.18D;
    public static final double MAX_UPWARD_SPEED = 0.18D;

    private PlayerBatForm() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ToggleBatFormPayload.ID, ToggleBatFormPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ToggleBatFormPayload.ID, (payload, context) -> toggle(context.player()));
        ServerTickEvents.END_WORLD_TICK.register(PlayerBatForm::tickWorld);
        registerInteractionBlocks();
    }

    public static void toggle(ServerPlayerEntity player) {
        if (!canUseBatForm(player)) {
            setBatForm(player, false);
            return;
        }

        setBatForm(player, !VampireData.isBatForm(player));
    }

    public static boolean canUseBatForm(PlayerEntity player) {
        return player.isAlive()
                && VampireData.isVampire(player)
                && !player.isCreative()
                && !player.isSpectator();
    }

    public static void setBatForm(ServerPlayerEntity player, boolean batForm) {
        boolean currentlyBat = VampireData.isBatForm(player);
        if (currentlyBat == batForm) {
            applyServerState(player);
            return;
        }

        if (batForm) {
            PlayerAbilities abilities = player.getAbilities();
            VampireData.setAllowedFlyingBeforeBatForm(player, abilities.allowFlying);
            VampireData.setFlyingBeforeBatForm(player, abilities.flying);
            VampireData.setNoGravityBeforeBatForm(player, player.hasNoGravity());
            VampireData.setBatFormManaged(player, true);
            VampireData.setBatForm(player, true);
        } else {
            VampireData.setBatForm(player, false);
        }

        clearUpwardVelocity(player);
        applyServerState(player);
        player.calculateDimensions();
    }

    private static void tickWorld(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (VampireData.isBatForm(player) && !canUseBatForm(player)) {
                setBatForm(player, false);
                continue;
            }

            applyServerState(player);
        }
    }

    private static void applyServerState(ServerPlayerEntity player) {
        if (VampireData.isBatForm(player)) {
            if (player.isUsingItem()) {
                player.stopUsingItem();
            }
            player.clearActiveItem();
            player.setNoGravity(true);
            player.fallDistance = 0.0D;
            updateAbilities(player, true, false);
            return;
        }

        if (!VampireData.isBatFormManaged(player)) {
            return;
        }

        if (player.hasNoGravity() && !VampireData.hadNoGravityBeforeBatForm(player)) {
            player.setNoGravity(false);
        }

        boolean gameModeAllowsFlying = player.isCreative() || player.isSpectator();
        boolean allowFlying = gameModeAllowsFlying || VampireData.wasAllowedFlyingBeforeBatForm(player);
        boolean flying = allowFlying && (gameModeAllowsFlying ? player.getAbilities().flying : VampireData.wasFlyingBeforeBatForm(player));
        updateAbilities(player, allowFlying, flying);
        VampireData.setBatFormManaged(player, false);
    }

    private static void updateAbilities(ServerPlayerEntity player, boolean allowFlying, boolean flying) {
        PlayerAbilities abilities = player.getAbilities();
        if (abilities.allowFlying == allowFlying && abilities.flying == flying) {
            return;
        }

        abilities.allowFlying = allowFlying;
        abilities.flying = flying;
        player.sendAbilitiesUpdate();
    }

    private static void registerInteractionBlocks() {
        UseItemCallback.EVENT.register((player, world, hand) -> VampireData.isBatForm(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> VampireData.isBatForm(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> VampireData.isBatForm(player) ? ActionResult.FAIL : ActionResult.PASS);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> VampireData.isBatForm(player) ? ActionResult.FAIL : ActionResult.PASS);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> VampireData.isBatForm(player) ? ActionResult.FAIL : ActionResult.PASS);
    }

    private static void clearUpwardVelocity(ServerPlayerEntity player) {
        Vec3d velocity = player.getVelocity();
        if (velocity.y > 0.0D) {
            player.setVelocity(velocity.x, 0.0D, velocity.z);
        }
        player.fallDistance = 0.0D;
    }
}
