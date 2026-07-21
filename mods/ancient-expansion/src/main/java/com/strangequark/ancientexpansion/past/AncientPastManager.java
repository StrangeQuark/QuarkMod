package com.strangequark.ancientexpansion.past;

import com.strangequark.ancientexpansion.AncientExpansionMod;
import com.strangequark.ancientexpansion.block.ModBlocks;
import com.strangequark.ancientexpansion.past.AncientPastState.PastCityInstance;
import com.strangequark.ancientexpansion.trial.AncientCityBuildHelper;
import com.strangequark.ancientexpansion.trial.AncientCityTrialManager;
import com.strangequark.ancientexpansion.trial.AncientCityTrialPortal;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class AncientPastManager {
    public static final RegistryKey<World> ANCIENT_PAST_WORLD = RegistryKey.of(RegistryKeys.WORLD, AncientExpansionMod.id("ancient_past"));

    private static final int PAST_CITY_LAYOUT_VERSION = 6;
    private static final int VANILLA_ANCIENT_CITY_START_HEIGHT = -27;
    private static final int PAST_ANCIENT_CITY_START_HEIGHT = 72;
    private static final int PAST_CITY_Y_OFFSET = PAST_ANCIENT_CITY_START_HEIGHT - VANILLA_ANCIENT_CITY_START_HEIGHT;
    private static final int PAST_CITY_BOOTSTRAP_HORIZONTAL_RADIUS = 48;
    private static final int PAST_CITY_BOOTSTRAP_DOWN = 24;
    private static final int PAST_CITY_BOOTSTRAP_UP = 48;
    private static final int TRIAL_PORTAL_WIDTH = 3;
    private static final int TRIAL_PORTAL_HEIGHT = 4;
    private static final int REDSTONE_ROOM_MIN_X = 1;
    private static final int REDSTONE_ROOM_MAX_X = 15;
    private static final int REDSTONE_ROOM_MIN_Y = 2;
    private static final int REDSTONE_ROOM_MAX_Y = 11;
    private static final int REDSTONE_ROOM_MIN_Z = 1;
    private static final int REDSTONE_ROOM_MAX_Z = 32;
    private static final int REDSTONE_ROOM_PORTAL_Z = 6;
    private static final int REDSTONE_ROOM_PORTAL_CENTER_X = 8;
    private static final int REDSTONE_ROOM_PORTAL_BASE_Y = 4;
    private static final int VILLAGER_MIN = 40;
    private static final int VILLAGER_RANDOM_BOUND = 21;
    private static final int VILLAGER_ATTEMPTS = 9000;
    private static final int VILLAGER_MIN_DISTANCE_SQUARED = 4 * 4;
    private static final int SOURCE_FRAME_RELOAD_PADDING = 16;
    private static final int PAST_CHUNK_CLEANUPS_PER_TICK = 4;
    private static final Set<Long> PENDING_PRISTINE_CHUNKS = new HashSet<>();

    private AncientPastManager() {
    }

    public static void register() {
        PastAncientCityPristineProcessor.register();
        ServerChunkEvents.CHUNK_GENERATE.register(AncientPastManager::queuePristineCleanup);
        ServerChunkEvents.CHUNK_LOAD.register(AncientPastManager::queuePristineCleanup);
        ServerTickEvents.END_WORLD_TICK.register(AncientPastManager::cleanQueuedPastChunks);
    }

    public static boolean handlePortalCollision(ServerPlayerEntity player, BlockPos portalPos) {
        if (player.isSpectator() || !AncientCityTrialPortal.isPortalBlock(player.getWorld().getBlockState(portalPos))) {
            return false;
        }

        ServerWorld world = player.getWorld();
        if (isAncientPastWorld(world)) {
            handlePastPortalCollision(player, portalPos);
            return true;
        }
        if (world.getRegistryKey().equals(AncientCityTrialManager.ANCIENT_TRIAL_WORLD)) {
            return false;
        }

        handleSourcePortalCollision(player, portalPos);
        return true;
    }

    public static boolean isAncientPastWorld(World world) {
        return world.getRegistryKey().equals(ANCIENT_PAST_WORLD);
    }

    private static void handleSourcePortalCollision(ServerPlayerEntity player, BlockPos portalPos) {
        ServerWorld sourceWorld = player.getWorld();
        Optional<AncientCityTrialPortal.FrameTarget> sourceFrame = AncientCityTrialPortal.findNearestFrame(sourceWorld, portalPos);
        if (sourceFrame.isEmpty()) {
            return;
        }

        ServerWorld pastWorld = player.getServer().getWorld(ANCIENT_PAST_WORLD);
        if (pastWorld == null) {
            player.sendMessage(Text.translatable("message.quarkmod.ancient_past_dimension_missing"), true);
            return;
        }

        AncientPastState state = AncientPastState.get(player.getServer());
        Identifier sourceWorldId = sourceWorld.getRegistryKey().getValue();
        PastCityInstance city = state.get(sourceWorldId, sourceFrame.get().altarPos())
                .filter(AncientPastManager::isCurrentLayout)
                .orElseGet(() -> createCityInstance(sourceWorldId, sourceFrame.get()));
        city = ensurePastCityPrepared(state, pastWorld, sourceFrame.get(), city);
        ensurePastPortals(pastWorld, city);

        ServerPlayerEntity teleported = AncientCityBuildHelper.teleport(player, pastWorld, city.pastSpawn().toBottomCenterPos(), player.getYaw(), player.getPitch());
        teleported.playSoundToPlayer(SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.65F, 0.85F);
    }

    private static void handlePastPortalCollision(ServerPlayerEntity player, BlockPos portalPos) {
        AncientPastState state = AncientPastState.get(player.getServer());
        Optional<PastCityInstance> mainPortalCity = state.findByMainPortal(portalPos);
        if (mainPortalCity.isPresent()) {
            returnToSourceCity(player, mainPortalCity.get());
            return;
        }

        Optional<PastCityInstance> trialPortalCity = state.findByTrialPortal(portalPos);
        if (trialPortalCity.isPresent()) {
            enterTrial(player, trialPortalCity.get());
        }
    }

    private static void returnToSourceCity(ServerPlayerEntity player, PastCityInstance city) {
        ServerWorld sourceWorld = resolveSourceWorld(player.getServer(), city).orElse(null);
        if (sourceWorld == null) {
            player.sendMessage(Text.translatable("message.quarkmod.ancient_past_source_missing"), true);
            return;
        }

        AncientCityBuildHelper.loadChunks(sourceWorld, AncientCityBuildHelper.boxAround(city.sourceReturnSpawn(), 2, 2, 3));
        ServerPlayerEntity teleported = AncientCityBuildHelper.teleport(player, sourceWorld, city.sourceReturnSpawn().toBottomCenterPos(), player.getYaw(), player.getPitch());
        teleported.playSoundToPlayer(SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.65F, 1.0F);
    }

    private static void enterTrial(ServerPlayerEntity player, PastCityInstance city) {
        ServerWorld sourceWorld = resolveSourceWorld(player.getServer(), city).orElse(null);
        if (sourceWorld == null) {
            player.sendMessage(Text.translatable("message.quarkmod.ancient_past_source_missing"), true);
            return;
        }

        AncientCityBuildHelper.loadChunks(sourceWorld, AncientCityBuildHelper.expandBox(city.sourceCityBox(), SOURCE_FRAME_RELOAD_PADDING, 8, SOURCE_FRAME_RELOAD_PADDING));
        Optional<AncientCityTrialPortal.FrameTarget> sourceFrame = AncientCityTrialPortal.findNearestFrame(sourceWorld, city.sourceAltarPos());
        if (sourceFrame.isEmpty()) {
            player.sendMessage(Text.translatable("message.quarkmod.ancient_trial_frame_missing"), true);
            return;
        }

        AncientCityTrialManager.startTrial(
                player,
                sourceWorld,
                sourceFrame.get(),
                player.getWorld(),
                city.trialReturnSpawn()
        );
    }

    private static Optional<ServerWorld> resolveSourceWorld(MinecraftServer server, PastCityInstance city) {
        RegistryKey<World> sourceKey = RegistryKey.of(RegistryKeys.WORLD, city.sourceWorld());
        return Optional.ofNullable(server.getWorld(sourceKey));
    }

    private static boolean isCurrentLayout(PastCityInstance city) {
        return city.layoutVersion() == PAST_CITY_LAYOUT_VERSION;
    }

    private static PastCityInstance createCityInstance(Identifier sourceWorldId, AncientCityTrialPortal.FrameTarget sourceFrame) {
        int offsetY = PAST_CITY_Y_OFFSET;
        BlockPos pastAltarPos = shiftToPast(sourceFrame.altarPos(), offsetY);
        BlockBox pastCityBox = AncientCityBuildHelper.offsetBox(sourceFrame.cityBoundingBox(), 0, offsetY, 0);
        TrialPortalRoomPlan trialRoom = createTrialPortalRoomPlan(sourceFrame, offsetY);

        return new PastCityInstance(
                sourceWorldId,
                sourceFrame.altarPos(),
                sourceFrame.cityBoundingBox(),
                sourceFrame.altarPos().up(2),
                sourceFrame.portalAxis(),
                sourceFrame.portalFrontDirection(),
                shiftToPast(sourceFrame.portalPositions(), offsetY),
                trialRoom.portalAxis(),
                trialRoom.portalPositions(),
                pastAltarPos.up(2),
                trialRoom.returnSpawn(),
                pastCityBox,
                PAST_CITY_LAYOUT_VERSION,
                false
        );
    }

    private static BlockPos shiftToPast(BlockPos pos, int offsetY) {
        return pos.add(0, offsetY, 0);
    }

    private static List<BlockPos> shiftToPast(List<BlockPos> positions, int offsetY) {
        List<BlockPos> shifted = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            shifted.add(shiftToPast(pos, offsetY));
        }
        return List.copyOf(shifted);
    }

    private static PastCityInstance ensurePastCityPrepared(
            AncientPastState state,
            ServerWorld pastWorld,
            AncientCityTrialPortal.FrameTarget sourceFrame,
            PastCityInstance city
    ) {
        BlockBox bootstrapBox = AncientCityBuildHelper.boxAround(
                city.pastSpawn(),
                PAST_CITY_BOOTSTRAP_HORIZONTAL_RADIUS,
                PAST_CITY_BOOTSTRAP_DOWN,
                PAST_CITY_BOOTSTRAP_UP
        );
        AncientCityBuildHelper.loadChunks(pastWorld, bootstrapBox);

        if (city.generated()) {
            return city;
        }

        cleanPristineBlocks(pastWorld, bootstrapBox);
        buildRedstoneTrialPortalRoom(pastWorld, sourceFrame, city);
        ensurePastPortals(pastWorld, city);
        prepareStandingSpot(pastWorld, city.pastSpawn());
        prepareStandingSpot(pastWorld, city.trialReturnSpawn());
        spawnVillagers(pastWorld, bootstrapBox, sourceFrame.altarPos().asLong());

        return state.put(city.withGenerated(true));
    }

    private static void ensurePastPortals(ServerWorld world, PastCityInstance city) {
        loadPortalChunks(world, city.mainPortalPositions());
        loadPortalChunks(world, city.trialPortalPositions());
        AncientCityTrialPortal.placePastPortal(world, city.mainPortalPositions(), city.mainPortalAxis());
        AncientCityTrialPortal.placePortal(world, city.trialPortalPositions(), city.trialPortalAxis());
    }

    private static void loadPortalChunks(ServerWorld world, List<BlockPos> portalPositions) {
        for (BlockPos pos : portalPositions) {
            world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        }
    }

    private static void queuePristineCleanup(ServerWorld world, WorldChunk chunk) {
        if (!isAncientPastWorld(world)) {
            return;
        }

        PENDING_PRISTINE_CHUNKS.add(chunk.getPos().toLong());
    }

    private static void cleanQueuedPastChunks(ServerWorld world) {
        if (!isAncientPastWorld(world) || PENDING_PRISTINE_CHUNKS.isEmpty()) {
            return;
        }

        int processed = 0;
        Iterator<Long> chunks = PENDING_PRISTINE_CHUNKS.iterator();
        while (chunks.hasNext() && processed < PAST_CHUNK_CLEANUPS_PER_TICK) {
            long chunkPos = chunks.next();
            chunks.remove();

            WorldChunk chunk = world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(chunkPos), ChunkPos.getPackedZ(chunkPos));
            if (chunk != null) {
                cleanPastChunk(world, chunk);
                processed++;
            }
        }
    }

    private static void cleanPastChunk(ServerWorld world, WorldChunk chunk) {
        List<BlockReplacement> replacements = new ArrayList<>();
        chunk.forEachBlockMatchingPredicate(
                state -> pristineReplacement(state) != null,
                (pos, state) -> {
                    BlockState replacement = pristineReplacement(state);
                    if (replacement != null) {
                        replacements.add(new BlockReplacement(pos.toImmutable(), replacement));
                    }
                }
        );

        for (BlockReplacement replacement : replacements) {
            world.setBlockState(replacement.pos(), replacement.state(), AncientCityBuildHelper.BULK_BLOCK_FLAGS);
        }
    }

    private static void cleanPristineBlocks(ServerWorld world, BlockBox box) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = box.getMinX(); x <= box.getMaxX(); x++) {
            for (int y = Math.max(box.getMinY(), world.getBottomY()); y <= Math.min(box.getMaxY(), world.getTopYInclusive()); y++) {
                for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    BlockState replacement = pristineReplacement(state);
                    if (replacement != null) {
                        world.setBlockState(pos, replacement, AncientCityBuildHelper.BULK_BLOCK_FLAGS);
                    }
                }
            }
        }
    }

    private static BlockState pristineReplacement(BlockState state) {
        BlockState sculkReplacement = sculkReplacement(state);
        if (sculkReplacement != null) {
            return sculkReplacement;
        }
        if (state.isOf(Blocks.CRACKED_DEEPSLATE_BRICKS)) {
            return Blocks.DEEPSLATE_BRICKS.getDefaultState();
        }
        if (state.isOf(Blocks.CRACKED_DEEPSLATE_TILES)) {
            return Blocks.DEEPSLATE_TILES.getDefaultState();
        }
        return null;
    }

    private static BlockState sculkReplacement(BlockState state) {
        if (state.isOf(Blocks.SCULK)) {
            return Blocks.POLISHED_DEEPSLATE.getDefaultState();
        }
        if (state.isOf(Blocks.SCULK_VEIN)
                || state.isOf(Blocks.SCULK_CATALYST)
                || state.isOf(Blocks.SCULK_SENSOR)
                || state.isOf(Blocks.CALIBRATED_SCULK_SENSOR)
                || state.isOf(Blocks.SCULK_SHRIEKER)) {
            return Blocks.AIR.getDefaultState();
        }
        return null;
    }

    private static TrialPortalRoomPlan createTrialPortalRoomPlan(AncientCityTrialPortal.FrameTarget sourceFrame, int offsetY) {
        Direction.Axis portalAxis = sourceFrame.cityCenterRotation().rotate(Direction.EAST).getAxis();
        List<BlockPos> portalPositions = new ArrayList<>();
        for (int width = -(TRIAL_PORTAL_WIDTH / 2); width <= TRIAL_PORTAL_WIDTH / 2; width++) {
            for (int height = 0; height < TRIAL_PORTAL_HEIGHT; height++) {
                portalPositions.add(transformCityCenterLocalToPast(
                        sourceFrame,
                        new BlockPos(REDSTONE_ROOM_PORTAL_CENTER_X + width, REDSTONE_ROOM_PORTAL_BASE_Y + height, REDSTONE_ROOM_PORTAL_Z),
                        offsetY
                ));
            }
        }

        BlockPos returnSpawn = transformCityCenterLocalToPast(
                sourceFrame,
                new BlockPos(REDSTONE_ROOM_PORTAL_CENTER_X, REDSTONE_ROOM_MIN_Y + 1, REDSTONE_ROOM_PORTAL_Z + 4),
                offsetY
        );
        return new TrialPortalRoomPlan(portalAxis, List.copyOf(portalPositions), returnSpawn);
    }

    private static BlockPos transformCityCenterLocalToPast(AncientCityTrialPortal.FrameTarget sourceFrame, BlockPos localPos, int offsetY) {
        return shiftToPast(sourceFrame.transformCityCenterLocal(localPos), offsetY);
    }

    private static void buildRedstoneTrialPortalRoom(ServerWorld world, AncientCityTrialPortal.FrameTarget sourceFrame, PastCityInstance city) {
        BlockState floor = Blocks.POLISHED_DEEPSLATE.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = REDSTONE_ROOM_MIN_X + 1; x <= REDSTONE_ROOM_MAX_X - 1; x++) {
            for (int z = REDSTONE_ROOM_MIN_Z + 1; z <= REDSTONE_ROOM_MAX_Z - 1; z++) {
                BlockPos floorPos = transformCityCenterLocalToPast(sourceFrame, new BlockPos(x, REDSTONE_ROOM_MIN_Y, z), PAST_CITY_Y_OFFSET);
                world.setBlockState(pos.set(floorPos.getX(), floorPos.getY(), floorPos.getZ()), floor, AncientCityBuildHelper.BULK_BLOCK_FLAGS);
                for (int y = REDSTONE_ROOM_MIN_Y + 1; y <= REDSTONE_ROOM_MAX_Y; y++) {
                    BlockPos clearPos = transformCityCenterLocalToPast(sourceFrame, new BlockPos(x, y, z), PAST_CITY_Y_OFFSET);
                    world.setBlockState(pos.set(clearPos.getX(), clearPos.getY(), clearPos.getZ()), air, AncientCityBuildHelper.BULK_BLOCK_FLAGS);
                }
            }
        }

        buildTrialPortalFrame(world, sourceFrame);
        AncientCityTrialPortal.placePortal(world, city.trialPortalPositions(), city.trialPortalAxis());
    }

    private static void buildTrialPortalFrame(ServerWorld world, AncientCityTrialPortal.FrameTarget sourceFrame) {
        BlockState frame = Blocks.REINFORCED_DEEPSLATE.getDefaultState();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int width = -(TRIAL_PORTAL_WIDTH / 2) - 1; width <= TRIAL_PORTAL_WIDTH / 2 + 1; width++) {
            for (int height = -1; height <= TRIAL_PORTAL_HEIGHT; height++) {
                boolean frameBlock = width == -(TRIAL_PORTAL_WIDTH / 2) - 1
                        || width == TRIAL_PORTAL_WIDTH / 2 + 1
                        || height == -1
                        || height == TRIAL_PORTAL_HEIGHT;
                if (frameBlock) {
                    BlockPos framePos = transformCityCenterLocalToPast(
                            sourceFrame,
                            new BlockPos(REDSTONE_ROOM_PORTAL_CENTER_X + width, REDSTONE_ROOM_PORTAL_BASE_Y + height, REDSTONE_ROOM_PORTAL_Z),
                            PAST_CITY_Y_OFFSET
                    );
                    world.setBlockState(pos.set(framePos.getX(), framePos.getY(), framePos.getZ()), frame, Block.NOTIFY_ALL);
                }
            }
        }
    }

    private static void prepareStandingSpot(ServerWorld world, BlockPos feet) {
        AncientCityBuildHelper.loadChunks(world, AncientCityBuildHelper.boxAround(feet, 1, 2, 2));
        world.setBlockState(feet.down(), Blocks.POLISHED_DEEPSLATE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(feet, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(feet.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
    }

    private static void spawnVillagers(ServerWorld world, BlockBox cityBox, long salt) {
        Random random = Random.create(world.getSeed() ^ salt ^ 0x5A17C1A7L);
        int targetCount = VILLAGER_MIN + random.nextInt(VILLAGER_RANDOM_BOUND);
        List<BlockPos> spawnPositions = pickVillagerSpawnPositions(world, cityBox, targetCount, random);
        for (BlockPos spawnPos : spawnPositions) {
            VillagerEntity villager = EntityType.VILLAGER.spawn(
                    world,
                    spawned -> spawned.setPersistent(),
                    spawnPos,
                    SpawnReason.STRUCTURE,
                    false,
                    false
            );
            if (villager != null) {
                villager.setPersistent();
                villager.reinitializeBrain(world);
            }
        }
    }

    private static List<BlockPos> pickVillagerSpawnPositions(ServerWorld world, BlockBox cityBox, int targetCount, Random random) {
        List<BlockPos> positions = new ArrayList<>();
        Set<Long> columns = new HashSet<>();
        int minY = Math.max(world.getBottomY() + 1, cityBox.getMinY());
        int maxY = Math.min(world.getTopYInclusive() - 2, cityBox.getMaxY() + 4);

        for (int attempt = 0; attempt < VILLAGER_ATTEMPTS && positions.size() < targetCount; attempt++) {
            int x = random.nextBetween(cityBox.getMinX() + 2, cityBox.getMaxX() - 2);
            int z = random.nextBetween(cityBox.getMinZ() + 2, cityBox.getMaxZ() - 2);
            long columnKey = BlockPos.asLong(x, 0, z);
            if (!columns.add(columnKey)) {
                continue;
            }

            for (int y = minY; y <= maxY; y++) {
                BlockPos feet = new BlockPos(x, y, z);
                if (isSafeStandPosition(world, feet) && farEnoughFromOtherVillagers(feet, positions)) {
                    positions.add(feet);
                    break;
                }
            }
        }

        return positions;
    }

    private static boolean farEnoughFromOtherVillagers(BlockPos candidate, List<BlockPos> positions) {
        for (BlockPos position : positions) {
            int dx = candidate.getX() - position.getX();
            int dz = candidate.getZ() - position.getZ();
            if (dx * dx + dz * dz < VILLAGER_MIN_DISTANCE_SQUARED) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSafeStandPosition(ServerWorld world, BlockPos feet) {
        BlockPos floor = feet.down();
        return world.getBlockState(floor).isSolidBlock(world, floor)
                && world.getBlockState(feet).isAir()
                && world.getBlockState(feet.up()).isAir();
    }

    private record TrialPortalRoomPlan(
            Direction.Axis portalAxis,
            List<BlockPos> portalPositions,
            BlockPos returnSpawn
    ) {
    }

    private record BlockReplacement(BlockPos pos, BlockState state) {
    }
}
