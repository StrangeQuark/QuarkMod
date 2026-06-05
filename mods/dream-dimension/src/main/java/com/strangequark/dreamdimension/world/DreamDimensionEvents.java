package com.strangequark.dreamdimension.world;

import com.strangequark.dreamdimension.DreamDimensionMod;
import com.strangequark.dreamdimension.state.DreamReturnLocation;
import com.strangequark.dreamdimension.state.DreamState;
import com.strangequark.dreamdimension.state.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.Set;

public final class DreamDimensionEvents {
    public static final RegistryKey<World> DREAM_WORLD = RegistryKey.of(RegistryKeys.WORLD, DreamDimensionMod.id("dream"));

    private static final int DREAM_LANDING_X = 0;
    private static final int DREAM_LANDING_Y = 96;
    private static final int DREAM_LANDING_Z = 0;
    private static final int LANDING_ISLAND_RADIUS = 7;
    private static final long DREAM_SOUND_INTERVAL = 170L;
    private static final EntityAttributeModifier DREAM_GRAVITY_MODIFIER = new EntityAttributeModifier(
            DreamDimensionMod.id("dream_half_gravity"),
            -0.5D,
            EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    );

    private DreamDimensionEvents() {
    }

    public static void registerEvents() {
        UseBlockCallback.EVENT.register(DreamDimensionEvents::onUseBlock);
        EntitySleepEvents.ALLOW_SETTING_SPAWN.register((player, sleepingPos) -> !isDreamWorld(player.getWorld()));
        EntitySleepEvents.ALLOW_RESETTING_TIME.register(player -> !isDreamWorld(player.getWorld()));

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive && isDreamWorld(oldPlayer.getWorld())) {
                returnFromDream(newPlayer, ModAttachments.getDreamState(newPlayer));
            }
        });

        ServerPlayerEvents.JOIN.register(player -> {
            DreamState state = ModAttachments.getDreamState(player);
            if (isDreamWorld(player.getWorld()) && state.returnLocation().isEmpty()) {
                returnFromDream(player, state);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(DreamDimensionEvents::tickPlayers);
        ServerTickEvents.END_WORLD_TICK.register(DreamDimensionEvents::tickDreamAmbience);
    }

    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        BlockState state = world.getBlockState(hitResult.getBlockPos());
        if (!(state.getBlock() instanceof BedBlock)) {
            return ActionResult.PASS;
        }

        if (isDreamWorld(world)) {
            if (world.isClient) {
                return ActionResult.SUCCESS;
            }

            if (player instanceof ServerPlayerEntity serverPlayer) {
                returnFromDream(serverPlayer, ModAttachments.getDreamState(serverPlayer));
            }
            return ActionResult.SUCCESS;
        }

        if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }

        DreamState dreamState = ModAttachments.getDreamState(serverPlayer);
        if (!dreamState.readyToDream()) {
            return ActionResult.PASS;
        }

        enterDream(serverPlayer, dreamState);
        return ActionResult.SUCCESS;
    }

    private static void enterDream(ServerPlayerEntity player, DreamState dreamState) {
        ServerWorld dreamWorld = player.getServer().getWorld(DREAM_WORLD);
        if (dreamWorld == null) {
            player.sendMessage(Text.translatable("message.quarkmod.dream_dimension_missing"), true);
            return;
        }

        DreamReturnLocation returnLocation = DreamReturnLocation.of(
                player.getWorld().getRegistryKey(),
                player.getPos(),
                player.getYaw(),
                player.getPitch()
        );
        ModAttachments.setDreamState(player, dreamState.withReturnLocation(returnLocation));

        BlockPos landingFeet = prepareDreamLanding(dreamWorld);
        ServerPlayerEntity teleported = teleport(player, dreamWorld, landingFeet.toBottomCenterPos(), player.getYaw(), player.getPitch());
        ModAttachments.setDreamState(teleported, ModAttachments.getDreamState(teleported).withReturnLocation(returnLocation));
        applyDreamGravity(teleported);
        spawnDreamBurst(dreamWorld, teleported.getPos().add(0.0D, 1.0D, 0.0D));
        teleported.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.AMBIENT, 0.6F, 0.55F);
        teleported.sendMessage(Text.translatable("message.quarkmod.entered_dream"), true);
    }

    private static void returnFromDream(ServerPlayerEntity player, DreamState dreamState) {
        ReturnTarget target = dreamState.returnLocation()
                .flatMap(location -> resolveReturnTarget(player.getServer(), location))
                .orElseGet(() -> fallbackReturnTarget(player));

        ServerPlayerEntity teleported = teleport(player, target.world(), target.position(), target.yaw(), target.pitch());
        removeDreamGravity(teleported);
        ModAttachments.clearDreamState(teleported);
        teleported.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT, 0.55F, 1.35F);
        teleported.sendMessage(Text.translatable("message.quarkmod.left_dream"), true);
    }

    private static Optional<ReturnTarget> resolveReturnTarget(MinecraftServer server, DreamReturnLocation location) {
        ServerWorld world = server.getWorld(location.dimensionKey());
        if (world == null || isDreamWorld(world) || !world.getWorldBorder().contains(location.pos())) {
            return Optional.empty();
        }

        Vec3d position = location.pos();
        BlockPos feet = BlockPos.ofFloored(position);
        if (!isSafeStandPosition(world, feet)) {
            return Optional.empty();
        }

        return Optional.of(new ReturnTarget(world, position, location.yaw(), location.pitch()));
    }

    private static ReturnTarget fallbackReturnTarget(ServerPlayerEntity player) {
        ServerWorld overworld = player.getServer().getOverworld();
        BlockPos spawn = player.getWorldSpawnPos(overworld, overworld.getLevelProperties().getSpawnPos());
        return new ReturnTarget(overworld, spawn.toBottomCenterPos(), overworld.getLevelProperties().getSpawnAngle(), 0.0F);
    }

    private static ServerPlayerEntity teleport(ServerPlayerEntity player, ServerWorld world, Vec3d position, float yaw, float pitch) {
        player.stopRiding();
        player.dismountVehicle();

        ServerPlayerEntity teleported = player.teleportTo(new TeleportTarget(
                world,
                position,
                Vec3d.ZERO,
                yaw,
                pitch,
                Set.<PositionFlag>of(),
                TeleportTarget.NO_OP
        ));
        teleported.setVelocity(Vec3d.ZERO);
        teleported.fallDistance = 0.0D;
        teleported.extinguish();
        return teleported;
    }

    private static BlockPos prepareDreamLanding(ServerWorld world) {
        world.getChunk(DREAM_LANDING_X >> 4, DREAM_LANDING_Z >> 4);

        BlockPos feet = new BlockPos(DREAM_LANDING_X, DREAM_LANDING_Y + 1, DREAM_LANDING_Z);
        if (!isSafeStandPosition(world, feet)) {
            buildLandingIsland(world, feet.down());
        }

        return feet;
    }

    private static void buildLandingIsland(ServerWorld world, BlockPos surfaceCenter) {
        for (int dx = -LANDING_ISLAND_RADIUS; dx <= LANDING_ISLAND_RADIUS; dx++) {
            for (int dz = -LANDING_ISLAND_RADIUS; dz <= LANDING_ISLAND_RADIUS; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > LANDING_ISLAND_RADIUS + 0.25D) {
                    continue;
                }

                int edgeDrop = Math.max(0, MathHelper.floor(distance - 3.5D));
                int surfaceY = surfaceCenter.getY() - edgeDrop;
                int depth = Math.max(2, LANDING_ISLAND_RADIUS - MathHelper.floor(distance));
                BlockPos columnTop = new BlockPos(surfaceCenter.getX() + dx, surfaceY, surfaceCenter.getZ() + dz);

                for (int y = surfaceY - depth; y <= surfaceY; y++) {
                    BlockState blockState = y == surfaceY
                            ? Blocks.GRASS_BLOCK.getDefaultState()
                            : y >= surfaceY - 2 ? Blocks.DIRT.getDefaultState() : Blocks.STONE.getDefaultState();
                    world.setBlockState(new BlockPos(columnTop.getX(), y, columnTop.getZ()), blockState, Block.NOTIFY_ALL);
                }

                for (int y = surfaceY + 1; y <= surfaceY + 4; y++) {
                    world.setBlockState(new BlockPos(columnTop.getX(), y, columnTop.getZ()), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }

        placeFloorLight(world, surfaceCenter.add(3, 0, 0));
        placeFloorLight(world, surfaceCenter.add(-3, 0, 0));
        placeFloorLight(world, surfaceCenter.add(0, 0, 3));
        placeFloorLight(world, surfaceCenter.add(0, 0, -3));
    }

    private static void placeFloorLight(ServerWorld world, BlockPos pos) {
        world.setBlockState(pos, Blocks.SEA_LANTERN.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(pos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(pos.up(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
    }

    private static boolean isSafeStandPosition(ServerWorld world, BlockPos feet) {
        if (feet.getY() <= world.getBottomY() || feet.getY() + 1 >= world.getBottomY() + world.getHeight()) {
            return false;
        }

        BlockPos floor = feet.down();
        return world.getBlockState(floor).isSolidBlock(world, floor)
                && !world.getBlockState(feet).blocksMovement()
                && !world.getBlockState(feet.up()).blocksMovement();
    }

    private static void tickPlayers(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (isDreamWorld(player.getWorld())) {
                applyDreamGravity(player);
            } else {
                removeDreamGravity(player);
            }
        }
    }

    private static void tickDreamAmbience(ServerWorld world) {
        if (!isDreamWorld(world)) {
            return;
        }

        long time = world.getTime();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if ((time + player.getId() * 17L) % DREAM_SOUND_INTERVAL == 0L) {
                playDreamAmbience(world, player);
            }
        }
    }

    private static void playDreamAmbience(ServerWorld world, ServerPlayerEntity player) {
        float pitch = 0.55F + world.getRandom().nextFloat() * 0.75F;
        switch (world.getRandom().nextInt(5)) {
            case 0 -> player.playSoundToPlayer(SoundEvents.AMBIENT_CAVE.value(), SoundCategory.AMBIENT, 0.22F, pitch);
            case 1 -> player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT, 0.18F, 0.65F + pitch);
            case 2 -> player.playSoundToPlayer(SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM, SoundCategory.AMBIENT, 0.12F, 0.45F + pitch);
            case 3 -> player.playSoundToPlayer(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.AMBIENT, 0.12F, 0.7F + pitch);
            default -> player.playSoundToPlayer(SoundEvents.BLOCK_FIREFLY_BUSH_IDLE, SoundCategory.AMBIENT, 0.16F, 0.8F + pitch);
        }
    }

    private static void spawnDreamBurst(ServerWorld world, Vec3d pos) {
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y, pos.z, 80, 1.4D, 1.1D, 1.4D, 0.06D);
        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 36, 1.0D, 0.7D, 1.0D, 0.02D);
        world.spawnParticles(ParticleTypes.ENCHANT, pos.x, pos.y, pos.z, 48, 1.2D, 0.8D, 1.2D, 0.04D);
    }

    private static void applyDreamGravity(ServerPlayerEntity player) {
        EntityAttributeInstance gravity = player.getAttributeInstance(EntityAttributes.GRAVITY);
        if (gravity != null && !gravity.hasModifier(DREAM_GRAVITY_MODIFIER.id())) {
            gravity.addTemporaryModifier(DREAM_GRAVITY_MODIFIER);
        }
    }

    private static void removeDreamGravity(ServerPlayerEntity player) {
        EntityAttributeInstance gravity = player.getAttributeInstance(EntityAttributes.GRAVITY);
        if (gravity != null) {
            gravity.removeModifier(DREAM_GRAVITY_MODIFIER.id());
        }
    }

    private static boolean isDreamWorld(World world) {
        return world.getRegistryKey().equals(DREAM_WORLD);
    }

    private record ReturnTarget(ServerWorld world, Vec3d position, float yaw, float pitch) {
    }

}
