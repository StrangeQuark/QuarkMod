package com.strangequark.vampirism.client;

import com.strangequark.vampirism.network.ToggleBatFormPayload;
import com.strangequark.vampirism.vampire.PlayerBatForm;
import com.strangequark.vampirism.vampire.VampireData;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerBatFormClient {
    private static final int DOUBLE_JUMP_WINDOW_TICKS = 7;
    private static final int NO_RECENT_JUMP_PRESS = -1_000_000;
    private static final Map<UUID, Boolean> LAST_BAT_FORM_BY_PLAYER = new HashMap<>();
    private static int lastJumpPressTick = NO_RECENT_JUMP_PRESS;

    private PlayerBatFormClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PlayerBatFormClient::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            lastJumpPressTick = NO_RECENT_JUMP_PRESS;
            LAST_BAT_FORM_BY_PLAYER.clear();
            return;
        }

        updatePlayerDimensions(client);
        handleJumpToggle(client);
        applyLocalFlight(client.player);
    }

    private static void updatePlayerDimensions(MinecraftClient client) {
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            boolean batForm = VampireData.isBatForm(player);
            Boolean lastBatForm = LAST_BAT_FORM_BY_PLAYER.put(player.getUuid(), batForm);
            if (lastBatForm == null || lastBatForm != batForm) {
                player.calculateDimensions();
            }
        }
    }

    private static void handleJumpToggle(MinecraftClient client) {
        while (client.options.jumpKey.wasPressed()) {
            int tick = client.player.age;
            if (!canRequestToggle(client)) {
                lastJumpPressTick = NO_RECENT_JUMP_PRESS;
                continue;
            }

            if (lastJumpPressTick != NO_RECENT_JUMP_PRESS && tick - lastJumpPressTick <= DOUBLE_JUMP_WINDOW_TICKS) {
                ClientPlayNetworking.send(new ToggleBatFormPayload());
                lastJumpPressTick = NO_RECENT_JUMP_PRESS;
            } else {
                lastJumpPressTick = tick;
            }
        }
    }

    private static boolean canRequestToggle(MinecraftClient client) {
        return client.currentScreen == null
                && ClientPlayNetworking.canSend(ToggleBatFormPayload.ID)
                && VampireData.isVampire(client.player)
                && !client.player.isCreative()
                && !client.player.isSpectator();
    }

    private static void applyLocalFlight(ClientPlayerEntity player) {
        if (!VampireData.isBatForm(player)) {
            return;
        }

        player.setNoGravity(true);
        player.fallDistance = 0.0D;
        player.getAbilities().allowFlying = true;
        player.getAbilities().flying = false;
        if (player.isUsingItem()) {
            player.clearActiveItem();
        }

        PlayerInput input = player.input.playerInput;
        Vec3d direction = getFlightDirection(player, input);
        Vec3d velocity = player.getVelocity().multiply(PlayerBatForm.FLIGHT_DAMPING);
        if (direction.lengthSquared() > 1.0E-4D) {
            velocity = velocity.add(direction.normalize().multiply(PlayerBatForm.FLIGHT_ACCELERATION));
        }

        player.setVelocity(clampFlightVelocity(velocity));
    }

    private static Vec3d getFlightDirection(ClientPlayerEntity player, PlayerInput input) {
        Vec3d look = player.getRotationVector().normalize();
        Vec3d horizontalForward = getHorizontalForward(player);
        Vec3d right = new Vec3d(-horizontalForward.z, 0.0D, horizontalForward.x);
        Vec3d direction = Vec3d.ZERO;

        if (input.forward()) {
            direction = direction.add(look);
        }
        if (input.backward()) {
            direction = direction.subtract(look);
        }
        if (input.left()) {
            direction = direction.subtract(right);
        }
        if (input.right()) {
            direction = direction.add(right);
        }
        if (input.jump()) {
            direction = direction.add(0.0D, 1.0D, 0.0D);
        }
        if (input.sneak()) {
            direction = direction.add(0.0D, -1.0D, 0.0D);
        }

        return direction;
    }

    private static Vec3d getHorizontalForward(ClientPlayerEntity player) {
        float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(yaw), 0.0D, MathHelper.cos(yaw)).normalize();
    }

    private static Vec3d clampFlightVelocity(Vec3d velocity) {
        double horizontalSpeed = velocity.horizontalLength();
        if (horizontalSpeed > PlayerBatForm.MAX_HORIZONTAL_SPEED) {
            double scale = PlayerBatForm.MAX_HORIZONTAL_SPEED / horizontalSpeed;
            velocity = new Vec3d(velocity.x * scale, velocity.y, velocity.z * scale);
        }

        return new Vec3d(
                velocity.x,
                MathHelper.clamp(velocity.y, -PlayerBatForm.MAX_DOWNWARD_SPEED, PlayerBatForm.MAX_UPWARD_SPEED),
                velocity.z
        );
    }
}
