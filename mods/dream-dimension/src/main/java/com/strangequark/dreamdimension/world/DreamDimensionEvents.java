package com.strangequark.dreamdimension.world;

import com.strangequark.dreamdimension.DreamDimensionMod;
import com.strangequark.dreamdimension.network.DreamTransitionPayload;
import com.strangequark.dreamdimension.state.DreamReturnLocation;
import com.strangequark.dreamdimension.state.DreamState;
import com.strangequark.dreamdimension.state.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
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
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.poi.PointOfInterest;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestType;
import net.minecraft.world.poi.PointOfInterestTypes;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DreamDimensionEvents {
    public static final RegistryKey<World> DREAM_WORLD = RegistryKey.of(RegistryKeys.WORLD, DreamDimensionMod.id("dream"));
    private static final TagKey<Structure> DREAM_VILLAGES = TagKey.of(RegistryKeys.STRUCTURE, DreamDimensionMod.id("dream_villages"));

    private static final int DREAM_LANDING_X = 0;
    private static final int DREAM_LANDING_Y = 96;
    private static final int DREAM_LANDING_Z = 0;
    private static final BlockPos DREAM_BED_SEARCH_ORIGIN = new BlockPos(DREAM_LANDING_X, DREAM_LANDING_Y, DREAM_LANDING_Z);
    private static final int DREAM_BED_SEARCH_RADIUS = 1024;
    private static final int DREAM_VILLAGE_LOCATE_RADIUS = 128;
    private static final int DREAM_VILLAGE_BED_SEARCH_RADIUS = 192;
    private static final int DREAM_VILLAGE_GENERATION_RADIUS = 8;
    private static final int DREAM_FALLBACK_BED_SEARCH_RADIUS = 48;
    private static final int DREAM_FALLBACK_BED_PLATFORM_RADIUS = 3;
    private static final int LANDING_ISLAND_RADIUS = 7;
    private static final int DREAM_TRANSITION_TICKS = 80;
    private static final long DREAM_SOUND_INTERVAL = 170L;
    private static final Map<UUID, PendingDreamTransition> PENDING_DREAM_TRANSITIONS = new HashMap<>();
    private static final EntityAttributeModifier DREAM_GRAVITY_MODIFIER = new EntityAttributeModifier(
            DreamDimensionMod.id("dream_half_gravity"),
            -0.5D,
            EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    );

    private DreamDimensionEvents() {
    }

    private static boolean hasPendingDreamTransition(LivingEntity entity) {
        return entity instanceof PlayerEntity player
                && !player.getWorld().isClient
                && PENDING_DREAM_TRANSITIONS.containsKey(player.getUuid());
    }

    public static void registerEvents() {
        UseBlockCallback.EVENT.register(DreamDimensionEvents::onUseBlock);
        EntitySleepEvents.ALLOW_BED.register((entity, sleepingPos, state, vanillaResult) ->
                hasPendingDreamTransition(entity) ? ActionResult.SUCCESS : ActionResult.PASS
        );
        EntitySleepEvents.ALLOW_SLEEP_TIME.register((player, sleepingPos, vanillaResult) ->
                hasPendingDreamTransition(player) ? ActionResult.SUCCESS : ActionResult.PASS
        );
        EntitySleepEvents.ALLOW_SETTING_SPAWN.register((player, sleepingPos) -> !isDreamWorld(player.getWorld()));
        EntitySleepEvents.ALLOW_RESETTING_TIME.register(player ->
                !isDreamWorld(player.getWorld()) && !hasPendingDreamTransition(player)
        );

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
                startDreamExit(serverPlayer, hitResult.getBlockPos());
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

        startDreamEntry(serverPlayer, hitResult.getBlockPos());
        return ActionResult.SUCCESS;
    }

    private static void startDreamEntry(ServerPlayerEntity player, BlockPos bedPos) {
        if (PENDING_DREAM_TRANSITIONS.containsKey(player.getUuid())) {
            return;
        }

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

        PENDING_DREAM_TRANSITIONS.put(
                player.getUuid(),
                PendingDreamTransition.enter(returnLocation, player.getYaw(), player.getPitch())
        );
        startDreamTransition(player, bedPos);
        sendDreamTransition(player);
    }

    private static void startDreamExit(ServerPlayerEntity player, BlockPos bedPos) {
        if (PENDING_DREAM_TRANSITIONS.containsKey(player.getUuid())) {
            return;
        }

        PENDING_DREAM_TRANSITIONS.put(player.getUuid(), PendingDreamTransition.exit());
        startDreamTransition(player, bedPos);
        sendDreamTransition(player);
    }

    private static void startDreamTransition(ServerPlayerEntity player, BlockPos bedPos) {
        player.stopRiding();
        player.dismountVehicle();
        player.closeHandledScreen();
        player.sleep(bedPos);
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0.0D;
    }

    private static void sendDreamTransition(ServerPlayerEntity player) {
        sendDreamTransition(player, -DREAM_TRANSITION_TICKS);
    }

    private static void sendDreamTransition(ServerPlayerEntity player, int durationTicks) {
        if (ServerPlayNetworking.canSend(player, DreamTransitionPayload.ID)) {
            ServerPlayNetworking.send(player, new DreamTransitionPayload(durationTicks));
        }
    }

    private static void finishDreamEntry(ServerPlayerEntity player, DreamReturnLocation returnLocation, DreamSpawnTarget target) {
        ServerWorld dreamWorld = player.getServer().getWorld(DREAM_WORLD);
        if (dreamWorld == null) {
            cancelDreamTransition(player);
            player.sendMessage(Text.translatable("message.quarkmod.dream_dimension_missing"), true);
            return;
        }

        DreamState dreamState = ModAttachments.getDreamState(player);
        ModAttachments.setDreamState(player, dreamState.withReturnLocation(returnLocation));
        wakeFromTransitionBed(player);

        ServerPlayerEntity teleported = teleport(player, dreamWorld, target.position(), target.yaw(), target.pitch());
        ModAttachments.setDreamState(teleported, ModAttachments.getDreamState(teleported).withReturnLocation(returnLocation));
        DreamInventorySwapper.activateDreamInventory(teleported);
        applyDreamGravity(teleported);
        spawnDreamBurst(dreamWorld, teleported.getPos().add(0.0D, 1.0D, 0.0D));
        teleported.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.AMBIENT, 0.6F, 0.55F);
        teleported.sendMessage(Text.translatable("message.quarkmod.entered_dream"), true);
        clearDreamTransition(teleported);
    }

    private static void returnFromDream(ServerPlayerEntity player, DreamState dreamState) {
        ReturnTarget target = dreamState.returnLocation()
                .flatMap(location -> resolveReturnTarget(player.getServer(), location))
                .orElseGet(() -> fallbackReturnTarget(player));

        finishReturnFromDream(player, target);
    }

    private static void finishReturnFromDream(ServerPlayerEntity player, ReturnTarget target) {
        wakeFromTransitionBed(player);
        ServerPlayerEntity teleported = teleport(player, target.world(), target.position(), target.yaw(), target.pitch());
        removeDreamGravity(teleported);
        DreamInventorySwapper.deactivateDreamInventory(teleported);
        ModAttachments.clearDreamState(teleported);
        teleported.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT, 0.55F, 1.35F);
        teleported.sendMessage(Text.translatable("message.quarkmod.left_dream"), true);
        clearDreamTransition(teleported);
    }

    private static void cancelDreamTransition(ServerPlayerEntity player) {
        wakeFromTransitionBed(player);
        clearDreamTransition(player);
    }

    private static void clearDreamTransition(ServerPlayerEntity player) {
        sendDreamTransition(player, 0);
    }

    private static void wakeFromTransitionBed(ServerPlayerEntity player) {
        if (player.isSleeping()) {
            player.wakeUp(true, false);
        }
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

    private static DreamSpawnTarget resolveDreamSpawn(ServerWorld world, float fallbackYaw, float fallbackPitch) {
        return findNearestDreamBed(world, DREAM_BED_SEARCH_ORIGIN)
                .flatMap(bed -> resolveBedSpawn(world, bed))
                .orElseGet(() -> {
                    BlockPos landingFeet = prepareDreamLanding(world);
                    return new DreamSpawnTarget(landingFeet.toBottomCenterPos(), fallbackYaw, fallbackPitch);
                });
    }

    private static Optional<BlockPos> findNearestDreamBed(ServerWorld world, BlockPos origin) {
        Optional<BlockPos> knownBed = findKnownDreamBed(world, origin, DREAM_BED_SEARCH_RADIUS);
        if (knownBed.isPresent()) {
            return knownBed;
        }

        BlockPos villagePos = world.locateStructure(DREAM_VILLAGES, origin, DREAM_VILLAGE_LOCATE_RADIUS, false);
        if (villagePos == null) {
            return createFallbackDreamBed(world, origin);
        }

        generateChunksAround(world, villagePos, DREAM_VILLAGE_GENERATION_RADIUS);
        return findKnownDreamBed(world, villagePos, DREAM_VILLAGE_BED_SEARCH_RADIUS)
                .or(() -> findKnownDreamBed(world, origin, DREAM_BED_SEARCH_RADIUS))
                .or(() -> createFallbackDreamBed(world, villagePos));
    }

    private static Optional<BlockPos> findKnownDreamBed(ServerWorld world, BlockPos origin, int radius) {
        PointOfInterestStorage pointOfInterestStorage = world.getPointOfInterestStorage();
        pointOfInterestStorage.preloadChunks(world, origin, radius);

        return pointOfInterestStorage.getInCircle(
                        DreamDimensionEvents::isHomePointOfInterest,
                        origin,
                        radius,
                        PointOfInterestStorage.OccupationStatus.ANY
                )
                .map(PointOfInterest::getPos)
                .flatMap(pos -> normalizeBedPos(world, pos).stream())
                .distinct()
                .filter(pos -> resolveBedSpawn(world, pos).isPresent())
                .min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(origin)));
    }

    private static boolean isHomePointOfInterest(RegistryEntry<PointOfInterestType> pointOfInterestType) {
        return pointOfInterestType.matchesKey(PointOfInterestTypes.HOME);
    }

    private static Optional<BlockPos> normalizeBedPos(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) {
            return Optional.empty();
        }

        Direction facing = state.get(BedBlock.FACING);
        BedPart part = state.get(BedBlock.PART);
        BlockPos head = part == BedPart.HEAD ? pos : pos.offset(facing);
        BlockPos foot = part == BedPart.FOOT ? pos : pos.offset(facing.getOpposite());

        BlockState headState = world.getBlockState(head);
        BlockState footState = world.getBlockState(foot);
        if (!(headState.getBlock() instanceof BedBlock) || !(footState.getBlock() instanceof BedBlock)) {
            return Optional.empty();
        }
        if (headState.get(BedBlock.PART) != BedPart.HEAD || footState.get(BedBlock.PART) != BedPart.FOOT) {
            return Optional.empty();
        }
        if (headState.get(BedBlock.FACING) != facing || footState.get(BedBlock.FACING) != facing) {
            return Optional.empty();
        }

        return Optional.of(head);
    }

    private static Optional<DreamSpawnTarget> resolveBedSpawn(ServerWorld world, BlockPos bedPos) {
        BlockState state = world.getBlockState(bedPos);
        if (!(state.getBlock() instanceof BedBlock)) {
            return Optional.empty();
        }

        Direction facing = state.get(BedBlock.FACING);
        float yaw = facing.getPositiveHorizontalDegrees();
        return BedBlock.findWakeUpPosition(EntityType.PLAYER, world, bedPos, facing, yaw)
                .map(position -> new DreamSpawnTarget(position, yaw, 0.0F));
    }

    private static Optional<BlockPos> createFallbackDreamBed(ServerWorld world, BlockPos origin) {
        world.getChunk(origin.getX() >> 4, origin.getZ() >> 4);

        return findFallbackBedFoot(world, origin)
                .map(foot -> {
                    Direction facing = Direction.SOUTH;
                    BlockPos head = foot.offset(facing);
                    prepareFallbackBedArea(world, foot, head);
                    world.setBlockState(
                            foot,
                            Blocks.WHITE_BED.getDefaultState()
                                    .with(BedBlock.FACING, facing)
                                    .with(BedBlock.PART, BedPart.FOOT)
                                    .with(BedBlock.OCCUPIED, false),
                            Block.NOTIFY_ALL
                    );
                    world.setBlockState(
                            head,
                            Blocks.WHITE_BED.getDefaultState()
                                    .with(BedBlock.FACING, facing)
                                    .with(BedBlock.PART, BedPart.HEAD)
                                    .with(BedBlock.OCCUPIED, false),
                            Block.NOTIFY_ALL
                    );
                    return head;
                })
                .filter(pos -> resolveBedSpawn(world, pos).isPresent());
    }

    private static Optional<BlockPos> findFallbackBedFoot(ServerWorld world, BlockPos origin) {
        for (int radius = 8; radius <= DREAM_FALLBACK_BED_SEARCH_RADIUS; radius += 4) {
            for (int dx = -radius; dx <= radius; dx += 4) {
                for (int dz = -radius; dz <= radius; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }

                    BlockPos foot = fallbackBedFootAt(world, origin.getX() + dx, origin.getZ() + dz);
                    if (canPrepareFallbackBed(world, foot, Direction.SOUTH)) {
                        return Optional.of(foot);
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static BlockPos fallbackBedFootAt(ServerWorld world, int x, int z) {
        int minY = world.getBottomY() + 2;
        int maxY = world.getBottomY() + world.getHeight() - 4;
        int bedY = MathHelper.clamp(world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), minY, maxY);
        return new BlockPos(x, bedY, z);
    }

    private static boolean canPrepareFallbackBed(ServerWorld world, BlockPos foot, Direction facing) {
        BlockPos head = foot.offset(facing);
        return world.getWorldBorder().contains(foot)
                && world.getWorldBorder().contains(head)
                && foot.getY() > world.getBottomY()
                && foot.getY() + 3 < world.getBottomY() + world.getHeight();
    }

    private static void prepareFallbackBedArea(ServerWorld world, BlockPos foot, BlockPos head) {
        int minX = Math.min(foot.getX(), head.getX()) - DREAM_FALLBACK_BED_PLATFORM_RADIUS;
        int maxX = Math.max(foot.getX(), head.getX()) + DREAM_FALLBACK_BED_PLATFORM_RADIUS;
        int minZ = Math.min(foot.getZ(), head.getZ()) - DREAM_FALLBACK_BED_PLATFORM_RADIUS;
        int maxZ = Math.max(foot.getZ(), head.getZ()) + DREAM_FALLBACK_BED_PLATFORM_RADIUS;
        int floorY = foot.getY() - 1;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.setBlockState(new BlockPos(x, floorY - 2, z), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                world.setBlockState(new BlockPos(x, floorY - 1, z), Blocks.DIRT.getDefaultState(), Block.NOTIFY_ALL);
                world.setBlockState(new BlockPos(x, floorY, z), Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_ALL);

                for (int y = foot.getY(); y <= foot.getY() + 3; y++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }
    }

    private static void generateChunksAround(ServerWorld world, BlockPos center, int chunkRadius) {
        ChunkPos centerChunk = new ChunkPos(center);
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                world.getChunk(centerChunk.x + dx, centerChunk.z + dz);
            }
        }
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
        tickDreamTransitions(server);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            boolean inDreamWorld = isDreamWorld(player.getWorld());
            DreamInventorySwapper.alignWithWorld(player, inDreamWorld);

            if (inDreamWorld) {
                applyDreamGravity(player);
            } else {
                removeDreamGravity(player);
            }
        }
    }

    private static void tickDreamTransitions(MinecraftServer server) {
        Iterator<Map.Entry<UUID, PendingDreamTransition>> iterator = PENDING_DREAM_TRANSITIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingDreamTransition> entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            PendingDreamTransition transition = entry.getValue();
            if (player == null || !transition.isStillValid(player)) {
                if (player != null) {
                    cancelDreamTransition(player);
                }
                iterator.remove();
                continue;
            }

            transition.resolveTarget(player);
            transition.ticksRemaining--;
            if (transition.ticksRemaining > 0) {
                continue;
            }

            iterator.remove();
            transition.finish(player);
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

    private enum DreamTransitionKind {
        ENTER,
        EXIT
    }

    private static final class PendingDreamTransition {
        private final DreamTransitionKind kind;
        private final DreamReturnLocation returnLocation;
        private final float fallbackYaw;
        private final float fallbackPitch;
        private int ticksRemaining = DREAM_TRANSITION_TICKS;
        private boolean targetResolved;
        private DreamSpawnTarget dreamTarget;
        private ReturnTarget returnTarget;

        private PendingDreamTransition(
                DreamTransitionKind kind,
                DreamReturnLocation returnLocation,
                float fallbackYaw,
                float fallbackPitch
        ) {
            this.kind = kind;
            this.returnLocation = returnLocation;
            this.fallbackYaw = fallbackYaw;
            this.fallbackPitch = fallbackPitch;
        }

        private static PendingDreamTransition enter(
                DreamReturnLocation returnLocation,
                float fallbackYaw,
                float fallbackPitch
        ) {
            return new PendingDreamTransition(DreamTransitionKind.ENTER, returnLocation, fallbackYaw, fallbackPitch);
        }

        private static PendingDreamTransition exit() {
            return new PendingDreamTransition(DreamTransitionKind.EXIT, null, 0.0F, 0.0F);
        }

        private boolean isStillValid(ServerPlayerEntity player) {
            if (isDreamWorld(player.getWorld()) != (kind == DreamTransitionKind.EXIT)) {
                return false;
            }
            return player.isAlive();
        }

        private void resolveTarget(ServerPlayerEntity player) {
            if (targetResolved) {
                return;
            }

            targetResolved = true;
            if (kind == DreamTransitionKind.ENTER) {
                ServerWorld dreamWorld = player.getServer().getWorld(DREAM_WORLD);
                dreamTarget = dreamWorld == null ? null : resolveDreamSpawn(dreamWorld, fallbackYaw, fallbackPitch);
            } else {
                DreamState dreamState = ModAttachments.getDreamState(player);
                returnTarget = dreamState.returnLocation()
                        .flatMap(location -> resolveReturnTarget(player.getServer(), location))
                        .orElseGet(() -> fallbackReturnTarget(player));
            }
        }

        private void finish(ServerPlayerEntity player) {
            if (kind == DreamTransitionKind.ENTER) {
                DreamSpawnTarget target = dreamTarget;
                if (target == null) {
                    ServerWorld dreamWorld = player.getServer().getWorld(DREAM_WORLD);
                    if (dreamWorld == null) {
                        cancelDreamTransition(player);
                        player.sendMessage(Text.translatable("message.quarkmod.dream_dimension_missing"), true);
                        return;
                    }
                    target = resolveDreamSpawn(dreamWorld, fallbackYaw, fallbackPitch);
                }

                finishDreamEntry(player, returnLocation, target);
            } else {
                finishReturnFromDream(player, returnTarget == null ? fallbackReturnTarget(player) : returnTarget);
            }
        }
    }

    private record DreamSpawnTarget(Vec3d position, float yaw, float pitch) {
    }

    private record ReturnTarget(ServerWorld world, Vec3d position, float yaw, float pitch) {
    }

}
