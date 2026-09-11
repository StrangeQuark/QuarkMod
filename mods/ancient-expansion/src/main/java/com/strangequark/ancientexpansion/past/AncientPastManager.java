package com.strangequark.ancientexpansion.past;

import com.strangequark.ancientexpansion.AncientExpansionMod;
import com.strangequark.ancientexpansion.block.ModBlocks;
import com.strangequark.ancientexpansion.past.AncientPastState.PastCityInstance;
import com.strangequark.ancientexpansion.past.AncientPastState.PastTerrainPiece;
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
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructurePiece;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AncientPastManager {
    public static final RegistryKey<World> ANCIENT_PAST_WORLD = RegistryKey.of(RegistryKeys.WORLD, AncientExpansionMod.id("ancient_past"));

    private static final int PAST_CITY_LAYOUT_VERSION = 17;
    private static final int VANILLA_ANCIENT_CITY_START_HEIGHT = -27;
    private static final int PAST_ANCIENT_CITY_START_HEIGHT = 250;
    private static final int PAST_CITY_Y_OFFSET = PAST_ANCIENT_CITY_START_HEIGHT - VANILLA_ANCIENT_CITY_START_HEIGHT;
    private static final int PAST_CITY_BOOTSTRAP_HORIZONTAL_RADIUS = 32;
    private static final int PAST_CITY_BOOTSTRAP_DOWN = 24;
    private static final int PAST_CITY_BOOTSTRAP_UP = 48;
    private static final int TRIAL_PORTAL_WIDTH = 3;
    private static final int TRIAL_PORTAL_HEIGHT = 2;
    private static final int REDSTONE_ROOM_MIN_X = 1;
    private static final int REDSTONE_ROOM_MAX_X = 15;
    private static final int REDSTONE_ROOM_MIN_Y = 2;
    private static final int REDSTONE_ROOM_MIN_Z = 1;
    private static final int REDSTONE_ROOM_MAX_Z = 32;
    private static final int REDSTONE_ROOM_PORTAL_Z = 6;
    private static final int REDSTONE_ROOM_PORTAL_CENTER_X = 10;
    private static final int REDSTONE_ROOM_PORTAL_BASE_Y = 4;
    private static final int REDSTONE_ROOM_CARPET_X_OFFSET = 2;
    private static final int REDSTONE_TRIAL_ROOM_CLEAR_MAX_Y = 6;
    private static final int REDSTONE_ROOM_CEILING_MIN_Y = 7;
    private static final int REDSTONE_ROOM_CEILING_MAX_Y = 8;
    private static final int VILLAGER_MIN = 40;
    private static final int VILLAGER_RANDOM_BOUND = 21;
    private static final int VILLAGER_ATTEMPTS = 9000;
    private static final int VILLAGER_MIN_DISTANCE_SQUARED = 4 * 4;
    private static final int SOURCE_FRAME_RELOAD_PADDING = 16;
    private static final int PAST_CHUNK_CLEANUPS_PER_TICK = 4;
    private static final int PAST_CITY_BUILD_CHUNKS_PER_TICK = 1;
    private static final int PAST_BEDROCK_LAYER_THICKNESS = 5;
    private static final int PAST_CITY_CAVERN_HORIZONTAL_MARGIN = 28;
    private static final int PAST_CITY_CAVERN_FLOOR_DEPTH = 3;
    private static final int PAST_CITY_CAVERN_CEILING_MARGIN = 22;
    private static final int PAST_CITY_TERRAIN_ADAPTATION_RADIUS = 12;
    private static final double PAST_CITY_TERRAIN_CLEAR_THRESHOLD = -0.035;
    private static final double PAST_CITY_TERRAIN_FILL_THRESHOLD = 0.06;
    private static final Set<Long> PENDING_PRISTINE_CHUNKS = new HashSet<>();
    private static final Map<String, PendingPastCityBuild> PENDING_CITY_BUILDS = new LinkedHashMap<>();
    private static final Map<UUID, PortalSuppression> PORTAL_SUPPRESSIONS = new HashMap<>();

    private AncientPastManager() {
    }

    public static void register() {
        ServerChunkEvents.CHUNK_GENERATE.register(AncientPastManager::queuePristineCleanup);
        ServerChunkEvents.CHUNK_LOAD.register(AncientPastManager::queuePristineCleanup);
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            cleanQueuedPastChunks(world);
            buildQueuedPastCityChunks(world);
            tickPortalSuppressions(world);
        });
    }

    public static boolean handlePortalCollision(ServerPlayerEntity player, BlockPos portalPos) {
        if (player.isSpectator() || !AncientCityTrialPortal.isPortalBlock(player.getWorld().getBlockState(portalPos))) {
            return false;
        }
        if (isPortalSuppressed(player, portalPos)) {
            return true;
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
        suppressPortalUntilExit(teleported, city.mainPortalPositions());
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
        suppressPortalUntilExit(teleported, sourcePortalPositions(city));
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
        List<BlockPos> pastPortalPositions = shiftToPast(sourceFrame.portalPositions(), offsetY);
        BlockBox pastCityBox = AncientCityBuildHelper.offsetBox(sourceFrame.cityBoundingBox(), 0, offsetY, 0);
        TrialPortalRoomPlan trialRoom = createTrialPortalRoomPlan(sourceFrame, offsetY);
        List<PastTerrainPiece> pastTerrainPieces = collectPastTerrainPieces(sourceFrame, offsetY);
        List<BlockBox> pastPieceBoxes = pastTerrainPieces.stream().map(PastTerrainPiece::box).toList();

        return new PastCityInstance(
                sourceWorldId,
                sourceFrame.altarPos(),
                sourceFrame.cityBoundingBox(),
                portalLandingPos(sourceFrame.portalPositions()),
                sourceFrame.portalAxis(),
                sourceFrame.portalFrontDirection(),
                pastPortalPositions,
                trialRoom.portalAxis(),
                trialRoom.portalPositions(),
                portalLandingPos(pastPortalPositions),
                trialRoom.returnSpawn(),
                pastCityBox,
                pastPieceBoxes,
                pastTerrainPieces,
                PAST_CITY_LAYOUT_VERSION,
                false
        );
    }

    private static List<PastTerrainPiece> collectPastTerrainPieces(AncientCityTrialPortal.FrameTarget sourceFrame, int offsetY) {
        List<PastTerrainPiece> pieces = new ArrayList<>();
        for (StructurePiece piece : sourceFrame.structureStart().getChildren()) {
            int groundLevelDelta = piece instanceof PoolStructurePiece poolPiece ? poolPiece.getGroundLevelDelta() : 0;
            pieces.add(new PastTerrainPiece(AncientCityBuildHelper.offsetBox(piece.getBoundingBox(), 0, offsetY, 0), groundLevelDelta));
        }
        return List.copyOf(pieces);
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

    private static List<BlockPos> sourcePortalPositions(PastCityInstance city) {
        List<BlockPos> positions = new ArrayList<>(city.mainPortalPositions().size());
        for (BlockPos pos : city.mainPortalPositions()) {
            positions.add(pos.add(0, -PAST_CITY_Y_OFFSET, 0));
        }
        return List.copyOf(positions);
    }

    private static BlockPos portalLandingPos(List<BlockPos> portalPositions) {
        if (portalPositions.isEmpty()) {
            return BlockPos.ORIGIN;
        }

        int minY = portalPositions.stream().mapToInt(BlockPos::getY).min().orElse(portalPositions.getFirst().getY());
        double centerX = portalPositions.stream().mapToDouble(BlockPos::getX).average().orElse(portalPositions.getFirst().getX());
        double centerZ = portalPositions.stream().mapToDouble(BlockPos::getZ).average().orElse(portalPositions.getFirst().getZ());
        return portalPositions.stream()
                .filter(pos -> pos.getY() == minY)
                .min((first, second) -> Double.compare(horizontalDistanceSquared(first, centerX, centerZ), horizontalDistanceSquared(second, centerX, centerZ)))
                .orElse(portalPositions.getFirst());
    }

    private static double horizontalDistanceSquared(BlockPos pos, double x, double z) {
        double dx = pos.getX() - x;
        double dz = pos.getZ() - z;
        return dx * dx + dz * dz;
    }

    private static boolean isPortalSuppressed(ServerPlayerEntity player, BlockPos portalPos) {
        PortalSuppression suppression = PORTAL_SUPPRESSIONS.get(player.getUuid());
        if (suppression == null) {
            return false;
        }
        if (!player.getWorld().getRegistryKey().equals(suppression.world())) {
            PORTAL_SUPPRESSIONS.remove(player.getUuid());
            return false;
        }
        if (suppression.portalPositions().contains(portalPos)) {
            return true;
        }
        if (!isPlayerTouchingSuppressedPortal(player, suppression)) {
            PORTAL_SUPPRESSIONS.remove(player.getUuid());
        }
        return false;
    }

    private static void suppressPortalUntilExit(ServerPlayerEntity player, List<BlockPos> portalPositions) {
        if (!portalPositions.isEmpty()) {
            PORTAL_SUPPRESSIONS.put(player.getUuid(), new PortalSuppression(player.getWorld().getRegistryKey(), Set.copyOf(portalPositions)));
        }
    }

    private static void tickPortalSuppressions(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            PortalSuppression suppression = PORTAL_SUPPRESSIONS.get(player.getUuid());
            if (suppression != null
                    && world.getRegistryKey().equals(suppression.world())
                    && !isPlayerTouchingSuppressedPortal(player, suppression)) {
                PORTAL_SUPPRESSIONS.remove(player.getUuid());
            }
        }
    }

    private static boolean isPlayerTouchingSuppressedPortal(ServerPlayerEntity player, PortalSuppression suppression) {
        Box playerBox = player.getBoundingBox().expand(1.0E-4D);
        for (BlockPos portalPos : suppression.portalPositions()) {
            if (AncientCityTrialPortal.isPortalBlock(player.getWorld().getBlockState(portalPos)) && playerBox.intersects(new Box(portalPos))) {
                return true;
            }
        }
        return false;
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
        ChunkPos sourceStartChunk = sourceFrame.structureStart().getPos();
        PastAncientCityStructure.allowStart(pastWorld.getSeed(), sourceStartChunk);
        pastWorld.getChunk(sourceStartChunk.x, sourceStartChunk.z);
        AncientCityBuildHelper.loadChunks(pastWorld, bootstrapBox);

        if (!city.generated()) {
            cleanPristineBlocks(pastWorld, bootstrapBox);
            buildRedstoneTrialPortalRoom(pastWorld, sourceFrame, city);
            spawnVillagers(pastWorld, bootstrapBox, sourceFrame.altarPos().asLong());
            city = state.put(city.withGenerated(true));
        }

        ensurePastPortals(pastWorld, city);
        return city;
    }

    private static void buildPastCityBootstrap(
            ServerWorld world,
            AncientCityTrialPortal.FrameTarget sourceFrame,
            PastCityInstance city,
            BlockBox generationBox
    ) {
        shapePastCityCavern(world, city, generationBox);
        adaptPastCityTerrain(world, city, generationBox);
        AncientCityBuildHelper.generateShiftedAncientCity(
                world,
                sourceFrame,
                0,
                PAST_CITY_Y_OFFSET,
                0,
                generationBox,
                world.getSeed() ^ sourceFrame.altarPos().asLong()
        );
        cleanPristineBlocks(world, generationBox);
    }

    private static void queuePastCityBuild(PastCityInstance city, AncientCityTrialPortal.FrameTarget sourceFrame, BlockBox immediateBox) {
        if (PENDING_CITY_BUILDS.containsKey(city.key())) {
            return;
        }

        Deque<Long> chunks = new ArrayDeque<>();
        for (ChunkPos chunkPos : city.pastCityBox().streamChunkPos().toList()) {
            BlockBox chunkBox = chunkBlockBox(chunkPos, Integer.MIN_VALUE, Integer.MAX_VALUE);
            if (!chunkBox.intersects(immediateBox)) {
                chunks.addLast(chunkPos.toLong());
            }
        }

        if (!chunks.isEmpty()) {
            PENDING_CITY_BUILDS.put(city.key(), new PendingPastCityBuild(sourceFrame, city, chunks));
        }
    }

    private static void buildQueuedPastCityChunks(ServerWorld world) {
        if (!isAncientPastWorld(world) || PENDING_CITY_BUILDS.isEmpty()) {
            return;
        }

        int processed = 0;
        Iterator<PendingPastCityBuild> builds = PENDING_CITY_BUILDS.values().iterator();
        while (builds.hasNext() && processed < PAST_CITY_BUILD_CHUNKS_PER_TICK) {
            PendingPastCityBuild build = builds.next();
            Long chunkPos = build.chunks().pollFirst();
            if (chunkPos == null) {
                builds.remove();
                continue;
            }

            WorldChunk chunk = world.getChunk(ChunkPos.getPackedX(chunkPos), ChunkPos.getPackedZ(chunkPos));
            BlockBox chunkBox = chunkBlockBox(world, chunk);
            shapePastCityCavern(world, build.city(), chunkBox);
            adaptPastCityTerrain(world, build.city(), chunkBox);
            AncientCityBuildHelper.generateShiftedAncientCity(
                    world,
                    build.sourceFrame(),
                    0,
                    PAST_CITY_Y_OFFSET,
                    0,
                    chunkBox,
                    world.getSeed() ^ build.city().sourceAltarPos().asLong() ^ chunkPos
            );
            cleanPristineBlocks(world, chunkBox);
            enforcePastBedrockBounds(world, chunk);
            processed++;

            if (build.chunks().isEmpty()) {
                builds.remove();
            }
        }
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

        enforcePastBedrockBounds(world, chunk);
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
        enforcePastBedrockBounds(world, chunk);
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

    private static void adaptPastCityTerrain(ServerWorld world, PastCityInstance city, BlockBox limitBox) {
        List<PastTerrainPiece> terrainPieces = city.pastTerrainPieces().isEmpty()
                ? city.pastPieceBoxes().stream().map(box -> new PastTerrainPiece(box, 0)).toList()
                : city.pastTerrainPieces();
        for (PastTerrainPiece terrainPiece : terrainPieces) {
            if (!terrainPiece.box().intersects(limitBox)) {
                continue;
            }

            BlockBox adaptationBox = intersectBoxes(
                    pieceTerrainAdaptationBox(terrainPiece.box()),
                    limitBox,
                    world.getBottomY(),
                    world.getTopYInclusive()
            );
            if (adaptationBox != null) {
                adaptNaturalTerrainBlocks(world, terrainPiece, adaptationBox);
            }
        }
    }

    private static void shapePastCityCavern(ServerWorld world, PastCityInstance city, BlockBox limitBox) {
        BlockBox cavernBox = intersectBoxes(
                pastCityCavernBox(city),
                limitBox,
                world.getBottomY(),
                world.getTopYInclusive()
        );
        if (cavernBox == null) {
            return;
        }

        BlockState air = Blocks.AIR.getDefaultState();
        BlockBox cityBox = city.pastCityBox();
        List<BlockBox> pieceBoxes = city.pastPieceBoxes().isEmpty() ? List.of(cityBox) : city.pastPieceBoxes();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        long salt = city.sourceAltarPos().asLong();
        for (int x = cavernBox.getMinX(); x <= cavernBox.getMaxX(); x++) {
            for (int z = cavernBox.getMinZ(); z <= cavernBox.getMaxZ(); z++) {
                double edge = horizontalDistanceOutside(pieceBoxes, x, z) / PAST_CITY_CAVERN_HORIZONTAL_MARGIN;
                if (edge > 1.12) {
                    continue;
                }

                double columnNoise = terrainNoise(x, 0, z, salt);
                int floorY = cityBox.getMinY() - PAST_CITY_CAVERN_FLOOR_DEPTH + (int) Math.round(columnNoise * 2.0);
                int ceilingY = cityBox.getMaxY() + PAST_CITY_CAVERN_CEILING_MARGIN
                        + (int) Math.round(terrainNoise(x, 17, z, salt) * 8.0)
                        - (int) Math.round(Math.max(0.0, edge - 0.4) * 22.0);
                if (floorY > ceilingY) {
                    continue;
                }

                for (int y = Math.max(cavernBox.getMinY(), floorY); y <= Math.min(cavernBox.getMaxY(), ceilingY); y++) {
                    double verticalNoise = terrainNoise(x, y >> 3, z, salt ^ 0x6D2B79F5L);
                    double boundary = edge + verticalNoise * 0.16;
                    if (boundary <= 1.0) {
                        pos.set(x, y, z);
                        BlockState state = world.getBlockState(pos);
                        if (isNaturalTerrain(state)) {
                            world.setBlockState(pos, air, AncientCityBuildHelper.BULK_BLOCK_FLAGS);
                        }
                    }
                }
            }
        }
    }

    private static BlockBox pastCityCavernBox(PastCityInstance city) {
        BlockBox cityBox = city.pastCityBox();
        return new BlockBox(
                cityBox.getMinX() - PAST_CITY_CAVERN_HORIZONTAL_MARGIN,
                cityBox.getMinY() - PAST_CITY_CAVERN_FLOOR_DEPTH,
                cityBox.getMinZ() - PAST_CITY_CAVERN_HORIZONTAL_MARGIN,
                cityBox.getMaxX() + PAST_CITY_CAVERN_HORIZONTAL_MARGIN,
                cityBox.getMaxY() + PAST_CITY_CAVERN_CEILING_MARGIN,
                cityBox.getMaxZ() + PAST_CITY_CAVERN_HORIZONTAL_MARGIN
        );
    }

    private static double horizontalDistanceOutside(BlockBox box, int x, int z) {
        int dx = Math.max(0, Math.max(box.getMinX() - x, x - box.getMaxX()));
        int dz = Math.max(0, Math.max(box.getMinZ() - z, z - box.getMaxZ()));
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double horizontalDistanceOutside(List<BlockBox> boxes, int x, int z) {
        int bestSquaredDistance = Integer.MAX_VALUE;
        for (BlockBox box : boxes) {
            int dx = Math.max(0, Math.max(box.getMinX() - x, x - box.getMaxX()));
            int dz = Math.max(0, Math.max(box.getMinZ() - z, z - box.getMaxZ()));
            int squaredDistance = dx * dx + dz * dz;
            if (squaredDistance == 0) {
                return 0.0;
            }
            if (squaredDistance < bestSquaredDistance) {
                bestSquaredDistance = squaredDistance;
            }
        }
        return Math.sqrt(bestSquaredDistance);
    }

    private static BlockBox pieceTerrainAdaptationBox(BlockBox pieceBox) {
        return new BlockBox(
                pieceBox.getMinX() - PAST_CITY_TERRAIN_ADAPTATION_RADIUS,
                pieceBox.getMinY() - PAST_CITY_TERRAIN_ADAPTATION_RADIUS,
                pieceBox.getMinZ() - PAST_CITY_TERRAIN_ADAPTATION_RADIUS,
                pieceBox.getMaxX() + PAST_CITY_TERRAIN_ADAPTATION_RADIUS,
                pieceBox.getMaxY() + PAST_CITY_TERRAIN_ADAPTATION_RADIUS,
                pieceBox.getMaxZ() + PAST_CITY_TERRAIN_ADAPTATION_RADIUS
        );
    }

    private static void adaptNaturalTerrainBlocks(ServerWorld world, PastTerrainPiece terrainPiece, BlockBox box) {
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState solid = cityTerrainFillState(terrainPiece.box().getMinY());
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = box.getMinX(); x <= box.getMaxX(); x++) {
            for (int y = box.getMinY(); y <= box.getMaxY(); y++) {
                for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
                    double weight = sampleBeardBoxWeight(terrainPiece, x, y, z);
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (weight < PAST_CITY_TERRAIN_CLEAR_THRESHOLD && isNaturalTerrain(state)) {
                        world.setBlockState(pos, air, AncientCityBuildHelper.BULK_BLOCK_FLAGS);
                    } else if (weight > PAST_CITY_TERRAIN_FILL_THRESHOLD && canFillWithNaturalTerrain(state)) {
                        world.setBlockState(pos, solid, AncientCityBuildHelper.BULK_BLOCK_FLAGS);
                    }
                }
            }
        }
    }

    private static double sampleBeardBoxWeight(PastTerrainPiece terrainPiece, int x, int y, int z) {
        BlockBox box = terrainPiece.box();
        int xDistance = Math.max(0, Math.max(box.getMinX() - x, x - box.getMaxX()));
        int zDistance = Math.max(0, Math.max(box.getMinZ() - z, z - box.getMaxZ()));
        int groundY = box.getMinY() + terrainPiece.groundLevelDelta();
        int yDistance = y - groundY;
        return getStructureWeight(xDistance, yDistance, zDistance, yDistance) * 0.8;
    }

    private static double getStructureWeight(int x, int y, int z, int yDelta) {
        if (!isStructureWeightIndexInBounds(x + PAST_CITY_TERRAIN_ADAPTATION_RADIUS)
                || !isStructureWeightIndexInBounds(y + PAST_CITY_TERRAIN_ADAPTATION_RADIUS)
                || !isStructureWeightIndexInBounds(z + PAST_CITY_TERRAIN_ADAPTATION_RADIUS)) {
            return 0.0;
        }

        double adjustedY = yDelta + 0.5;
        double inverseMagnitude = 1.0 / Math.sqrt((x * x + adjustedY * adjustedY + z * z) / 2.0);
        double signAndDistance = -adjustedY * inverseMagnitude / 2.0;
        double vanillaTableWeight = Math.exp(-(x * x + (y + 0.5) * (y + 0.5) + z * z) / 16.0);
        return signAndDistance * vanillaTableWeight;
    }

    private static boolean isStructureWeightIndexInBounds(int index) {
        return index >= 0 && index < PAST_CITY_TERRAIN_ADAPTATION_RADIUS * 2;
    }

    private static double terrainNoise(int x, int y, int z, long salt) {
        int scale = 12;
        int gridX = Math.floorDiv(x, scale);
        int gridZ = Math.floorDiv(z, scale);
        double localX = smoothStep(Math.floorMod(x, scale) / (double) scale);
        double localZ = smoothStep(Math.floorMod(z, scale) / (double) scale);
        double northWest = rawTerrainNoise(gridX, y, gridZ, salt);
        double northEast = rawTerrainNoise(gridX + 1, y, gridZ, salt);
        double southWest = rawTerrainNoise(gridX, y, gridZ + 1, salt);
        double southEast = rawTerrainNoise(gridX + 1, y, gridZ + 1, salt);
        return lerp(lerp(northWest, northEast, localX), lerp(southWest, southEast, localX), localZ);
    }

    private static double rawTerrainNoise(int x, int y, int z, long salt) {
        long value = salt;
        value ^= x * 0x9E3779B97F4A7C15L;
        value ^= y * 0xC2B2AE3D27D4EB4FL;
        value ^= z * 0x165667B19E3779F9L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return ((value >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }

    private static double smoothStep(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double start, double end, double delta) {
        return start + (end - start) * delta;
    }

    private static void clearNaturalTerrainBlocks(ServerWorld world, BlockBox box) {
        BlockState air = Blocks.AIR.getDefaultState();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = box.getMinX(); x <= box.getMaxX(); x++) {
            for (int y = box.getMinY(); y <= box.getMaxY(); y++) {
                for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (isNaturalTerrain(state)) {
                        world.setBlockState(pos, air, AncientCityBuildHelper.BULK_BLOCK_FLAGS);
                    }
                }
            }
        }
    }

    private static boolean isNaturalTerrain(BlockState state) {
        return state.isIn(BlockTags.BASE_STONE_OVERWORLD)
                || state.isIn(BlockTags.OVERWORLD_CARVER_REPLACEABLES)
                || state.isIn(BlockTags.DIRT)
                || state.isIn(BlockTags.SAND)
                || state.isOf(Blocks.GRAVEL)
                || state.isOf(Blocks.TUFF)
                || state.isOf(Blocks.CALCITE)
                || state.isOf(Blocks.DRIPSTONE_BLOCK)
                || state.isOf(Blocks.POINTED_DRIPSTONE)
                || !state.getFluidState().isEmpty();
    }

    private static boolean canFillWithNaturalTerrain(BlockState state) {
        return state.isAir() || !state.getFluidState().isEmpty();
    }

    private static BlockState cityTerrainFillState(int y) {
        return y < 8 ? Blocks.DEEPSLATE.getDefaultState() : Blocks.STONE.getDefaultState();
    }

    private static BlockBox intersectBoxes(BlockBox first, BlockBox second, int minY, int maxY) {
        int minX = Math.max(first.getMinX(), second.getMinX());
        int minBoxY = Math.max(Math.max(first.getMinY(), second.getMinY()), minY);
        int minZ = Math.max(first.getMinZ(), second.getMinZ());
        int maxX = Math.min(first.getMaxX(), second.getMaxX());
        int maxBoxY = Math.min(Math.min(first.getMaxY(), second.getMaxY()), maxY);
        int maxZ = Math.min(first.getMaxZ(), second.getMaxZ());
        if (minX > maxX || minBoxY > maxBoxY || minZ > maxZ) {
            return null;
        }
        return new BlockBox(minX, minBoxY, minZ, maxX, maxBoxY, maxZ);
    }

    private static BlockBox chunkBlockBox(ServerWorld world, WorldChunk chunk) {
        return chunkBlockBox(chunk.getPos(), world.getBottomY(), world.getTopYInclusive());
    }

    private static BlockBox chunkBlockBox(ChunkPos chunkPos, int minY, int maxY) {
        return new BlockBox(chunkPos.getStartX(), minY, chunkPos.getStartZ(), chunkPos.getEndX(), maxY, chunkPos.getEndZ());
    }

    private static void enforcePastBedrockBounds(ServerWorld world, WorldChunk chunk) {
        BlockState bedrock = Blocks.BEDROCK.getDefaultState();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        int bottomY = world.getBottomY();
        int topY = world.getTopYInclusive();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = startX + localX;
                int z = startZ + localZ;
                for (int layer = 0; layer < PAST_BEDROCK_LAYER_THICKNESS; layer++) {
                    chunk.setBlockState(pos.set(x, bottomY + layer, z), bedrock, AncientCityBuildHelper.BULK_BLOCK_FLAGS);
                    chunk.setBlockState(pos.set(x, topY - layer, z), bedrock, AncientCityBuildHelper.BULK_BLOCK_FLAGS);
                }
            }
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
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState carpet = Blocks.RED_CARPET.getDefaultState();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        repairRedstoneRoomCeiling(world, sourceFrame);

        for (int x = REDSTONE_ROOM_MIN_X + 1; x <= REDSTONE_ROOM_MAX_X - 1; x++) {
            for (int y = REDSTONE_ROOM_MIN_Y + 1; y <= REDSTONE_TRIAL_ROOM_CLEAR_MAX_Y; y++) {
                for (int z = REDSTONE_ROOM_MIN_Z + 1; z <= REDSTONE_ROOM_MAX_Z - 1; z++) {
                    BlockPos clearPos = transformCityCenterLocalToPast(sourceFrame, new BlockPos(x, y, z), PAST_CITY_Y_OFFSET);
                    setBlockStateRemovingBlockEntity(world, pos.set(clearPos.getX(), clearPos.getY(), clearPos.getZ()), air, AncientCityBuildHelper.BULK_BLOCK_FLAGS);
                }
            }
        }

        for (int x = REDSTONE_ROOM_MIN_X + 1 + REDSTONE_ROOM_CARPET_X_OFFSET; x <= REDSTONE_ROOM_MAX_X - 1; x++) {
            for (int z = REDSTONE_ROOM_MIN_Z + 1; z <= REDSTONE_ROOM_MAX_Z - 1; z++) {
                BlockPos floorPos = transformCityCenterLocalToPast(sourceFrame, new BlockPos(x, REDSTONE_ROOM_MIN_Y, z), PAST_CITY_Y_OFFSET);
                BlockPos carpetPos = transformCityCenterLocalToPast(sourceFrame, new BlockPos(x, REDSTONE_ROOM_MIN_Y + 1, z), PAST_CITY_Y_OFFSET);
                if (world.getBlockState(floorPos).isSolidBlock(world, floorPos) && world.getBlockState(carpetPos).isAir()) {
                    setBlockStateRemovingBlockEntity(world, pos.set(carpetPos.getX(), carpetPos.getY(), carpetPos.getZ()), carpet, AncientCityBuildHelper.BULK_BLOCK_FLAGS);
                }
            }
        }

        buildTrialPortalFrame(world, sourceFrame);
        AncientCityTrialPortal.placePortal(world, city.trialPortalPositions(), city.trialPortalAxis());
    }

    private static void repairRedstoneRoomCeiling(ServerWorld world, AncientCityTrialPortal.FrameTarget sourceFrame) {
        BlockState ceiling = Blocks.DEEPSLATE_BRICKS.getDefaultState();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = REDSTONE_ROOM_MIN_X + 1; x <= REDSTONE_ROOM_MAX_X - 1; x++) {
            for (int y = REDSTONE_ROOM_CEILING_MIN_Y; y <= REDSTONE_ROOM_CEILING_MAX_Y; y++) {
                for (int z = REDSTONE_ROOM_MIN_Z + 1; z <= REDSTONE_ROOM_MAX_Z - 1; z++) {
                    BlockPos ceilingPos = transformCityCenterLocalToPast(sourceFrame, new BlockPos(x, y, z), PAST_CITY_Y_OFFSET);
                    pos.set(ceilingPos.getX(), ceilingPos.getY(), ceilingPos.getZ());
                    if (shouldRepairRedstoneRoomCeilingBlock(world.getBlockState(pos))) {
                        setBlockStateRemovingBlockEntity(world, pos, ceiling, AncientCityBuildHelper.BULK_BLOCK_FLAGS);
                    }
                }
            }
        }
    }

    private static boolean shouldRepairRedstoneRoomCeilingBlock(BlockState state) {
        return state.isAir()
                || AncientCityTrialPortal.isPortalBlock(state)
                || state.isOf(Blocks.REINFORCED_DEEPSLATE);
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
                    setBlockStateRemovingBlockEntity(world, pos.set(framePos.getX(), framePos.getY(), framePos.getZ()), frame, Block.NOTIFY_ALL);
                }
            }
        }
    }

    private static void setBlockStateRemovingBlockEntity(ServerWorld world, BlockPos pos, BlockState state, int flags) {
        world.removeBlockEntity(pos);
        world.setBlockState(pos, state, flags);
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

    private record PortalSuppression(RegistryKey<World> world, Set<BlockPos> portalPositions) {
    }

    private record PendingPastCityBuild(
            AncientCityTrialPortal.FrameTarget sourceFrame,
            PastCityInstance city,
            Deque<Long> chunks
    ) {
    }
}
