package com.strangequark.ancientexpansion.past;

import com.strangequark.ancientexpansion.AncientExpansionMod;
import com.strangequark.ancientexpansion.block.ModBlocks;
import com.strangequark.ancientexpansion.past.AncientPastState.PastCityInstance;
import com.strangequark.ancientexpansion.trial.AncientCityBuildHelper;
import com.strangequark.ancientexpansion.trial.AncientCityTrialManager;
import com.strangequark.ancientexpansion.trial.AncientCityTrialPortal;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class AncientPastManager {
    public static final RegistryKey<World> ANCIENT_PAST_WORLD = RegistryKey.of(RegistryKeys.WORLD, AncientExpansionMod.id("ancient_past"));

    private static final int PAST_CITY_LAYOUT_VERSION = 4;
    private static final int PAST_CITY_ALTAR_TOP_PADDING = 32;
    private static final int PAST_CITY_ROOF_CLEARANCE = 28;
    private static final int PAST_CITY_MIN_HEIGHT_ABOVE_BOTTOM = 112;
    private static final int SOURCE_COPY_HORIZONTAL_PADDING = 40;
    private static final int SOURCE_COPY_UP_PADDING = 48;
    private static final int CAVERN_HALF_WIDTH = 7;
    private static final int CAVERN_HALF_DEPTH = 7;
    private static final int CAVERN_HEIGHT = 7;
    private static final int STAIR_LENGTH = 11;
    private static final int STAIR_WIDTH = 3;
    private static final int TRIAL_PORTAL_WIDTH = 3;
    private static final int TRIAL_PORTAL_HEIGHT = 4;
    private static final int VILLAGER_MIN = 40;
    private static final int VILLAGER_RANDOM_BOUND = 21;
    private static final int VILLAGER_ATTEMPTS = 9000;
    private static final int VILLAGER_MIN_DISTANCE_SQUARED = 4 * 4;
    private static final int SOURCE_FRAME_RELOAD_PADDING = 16;

    private AncientPastManager() {
    }

    public static void register() {
    }

    public static boolean handlePortalCollision(ServerPlayerEntity player, BlockPos portalPos) {
        if (player.isSpectator() || !player.getWorld().getBlockState(portalPos).isOf(ModBlocks.ANCIENT_TRIAL_PORTAL)) {
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
                .orElseGet(() -> createCityInstance(sourceWorldId, sourceFrame.get(), pastWorld));
        city = ensurePastCityGenerated(state, sourceWorld, pastWorld, sourceFrame.get(), city);
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

    private static PastCityInstance createCityInstance(Identifier sourceWorldId, AncientCityTrialPortal.FrameTarget sourceFrame, ServerWorld pastWorld) {
        int offsetY = calculatePastCityOffsetY(pastWorld, sourceFrame);
        BlockPos pastAltarPos = shiftToPast(sourceFrame.altarPos(), offsetY);
        BlockBox pastCityBox = AncientCityBuildHelper.offsetBox(sourceFrame.cityBoundingBox(), 0, offsetY, 0);
        CavernPlan cavern = createCavernPlan(pastAltarPos, sourceFrame.portalFrontDirection());

        return new PastCityInstance(
                sourceWorldId,
                sourceFrame.altarPos(),
                sourceFrame.cityBoundingBox(),
                sourceFrame.altarPos().up(2),
                sourceFrame.portalAxis(),
                sourceFrame.portalFrontDirection(),
                shiftToPast(sourceFrame.portalPositions(), offsetY),
                cavern.portalAxis(),
                cavern.portalPositions(),
                pastAltarPos.up(2),
                cavern.returnSpawn(),
                pastCityBox,
                PAST_CITY_LAYOUT_VERSION,
                false
        );
    }

    private static int calculatePastCityOffsetY(ServerWorld pastWorld, AncientCityTrialPortal.FrameTarget sourceFrame) {
        int cityHeightAboveAltar = sourceFrame.cityBoundingBox().getMaxY() - sourceFrame.altarPos().getY();
        int highestFittingAltarY = pastWorld.getTopYInclusive() - PAST_CITY_ROOF_CLEARANCE - cityHeightAboveAltar;
        int preferredAltarY = pastWorld.getTopYInclusive() - PAST_CITY_ALTAR_TOP_PADDING;
        int lowerBoundAltarY = pastWorld.getBottomY() + PAST_CITY_MIN_HEIGHT_ABOVE_BOTTOM;
        int targetAltarY = Math.min(preferredAltarY, highestFittingAltarY);
        targetAltarY = Math.max(lowerBoundAltarY, targetAltarY);
        targetAltarY = Math.min(targetAltarY, highestFittingAltarY);
        targetAltarY = Math.max(pastWorld.getBottomY() + 32, targetAltarY);
        return targetAltarY - sourceFrame.altarPos().getY();
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

    private static PastCityInstance ensurePastCityGenerated(
            AncientPastState state,
            ServerWorld sourceWorld,
            ServerWorld pastWorld,
            AncientCityTrialPortal.FrameTarget sourceFrame,
            PastCityInstance city
    ) {
        if (city.generated()) {
            return city;
        }

        int offsetY = city.pastSpawn().getY() - sourceFrame.altarPos().up(2).getY();
        BlockBox pastCityBox = city.pastCityBox();
        BlockBox sourceCopyBox = createSourceCopyBox(sourceWorld, pastWorld, sourceFrame, offsetY);
        BlockBox pastCopyBox = AncientCityBuildHelper.offsetBox(sourceCopyBox, 0, offsetY, 0);
        AncientCityBuildHelper.loadChunks(sourceWorld, sourceCopyBox);
        AncientCityBuildHelper.loadChunks(pastWorld, pastCopyBox);
        copySourceRegion(sourceWorld, pastWorld, sourceCopyBox, offsetY);
        removeSculk(pastWorld, pastCopyBox);
        buildCityCenterCavern(pastWorld, city.pastSpawn().down(2), sourceFrame.portalFrontDirection(), city);
        ensurePastPortals(pastWorld, city);
        prepareStandingSpot(pastWorld, city.pastSpawn());
        prepareStandingSpot(pastWorld, city.trialReturnSpawn());
        spawnVillagers(pastWorld, pastCityBox, sourceFrame.altarPos().asLong());

        return state.put(city.withGenerated(true));
    }

    private static void ensurePastPortals(ServerWorld world, PastCityInstance city) {
        loadPortalChunks(world, city.mainPortalPositions());
        loadPortalChunks(world, city.trialPortalPositions());
        AncientCityTrialPortal.placePortal(world, city.mainPortalPositions(), city.mainPortalAxis());
        AncientCityTrialPortal.placePortal(world, city.trialPortalPositions(), city.trialPortalAxis());
    }

    private static void loadPortalChunks(ServerWorld world, List<BlockPos> portalPositions) {
        for (BlockPos pos : portalPositions) {
            world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        }
    }

    private static BlockBox createSourceCopyBox(
            ServerWorld sourceWorld,
            ServerWorld pastWorld,
            AncientCityTrialPortal.FrameTarget sourceFrame,
            int offsetY
    ) {
        BlockBox cityBox = sourceFrame.cityBoundingBox();
        int minY = Math.max(sourceWorld.getBottomY(), pastWorld.getBottomY() - offsetY);
        int maxY = Math.min(sourceWorld.getTopYInclusive(), pastWorld.getTopYInclusive() - offsetY);
        maxY = Math.min(maxY, cityBox.getMaxY() + SOURCE_COPY_UP_PADDING);

        return new BlockBox(
                cityBox.getMinX() - SOURCE_COPY_HORIZONTAL_PADDING,
                minY,
                cityBox.getMinZ() - SOURCE_COPY_HORIZONTAL_PADDING,
                cityBox.getMaxX() + SOURCE_COPY_HORIZONTAL_PADDING,
                maxY,
                cityBox.getMaxZ() + SOURCE_COPY_HORIZONTAL_PADDING
        );
    }

    private static void copySourceRegion(ServerWorld sourceWorld, ServerWorld pastWorld, BlockBox sourceBox, int offsetY) {
        BlockPos.Mutable sourcePos = new BlockPos.Mutable();
        BlockPos.Mutable pastPos = new BlockPos.Mutable();
        for (int x = sourceBox.getMinX(); x <= sourceBox.getMaxX(); x++) {
            for (int y = sourceBox.getMinY(); y <= sourceBox.getMaxY(); y++) {
                for (int z = sourceBox.getMinZ(); z <= sourceBox.getMaxZ(); z++) {
                    sourcePos.set(x, y, z);
                    pastWorld.setBlockState(
                            pastPos.set(x, y + offsetY, z),
                            copiedPastState(sourceWorld.getBlockState(sourcePos)),
                            AncientCityBuildHelper.BULK_BLOCK_FLAGS
                    );
                }
            }
        }
    }

    private static BlockState copiedPastState(BlockState sourceState) {
        if (sourceState.isOf(Blocks.BEDROCK)) {
            return Blocks.DEEPSLATE.getDefaultState();
        }
        return sourceState;
    }

    private static void removeSculk(ServerWorld world, BlockBox box) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = box.getMinX(); x <= box.getMaxX(); x++) {
            for (int y = Math.max(box.getMinY(), world.getBottomY()); y <= Math.min(box.getMaxY(), world.getTopYInclusive()); y++) {
                for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    BlockState replacement = sculkReplacement(state);
                    if (replacement != null) {
                        world.setBlockState(pos, replacement, AncientCityBuildHelper.BULK_BLOCK_FLAGS);
                    }
                }
            }
        }
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

    private static void buildCityCenterCavern(ServerWorld world, BlockPos pastAltarPos, Direction portalFrontDirection, PastCityInstance city) {
        CavernPlan cavern = createCavernPlan(pastAltarPos, portalFrontDirection);
        buildCavernRoom(world, cavern.center());
        buildCavernStair(world, pastAltarPos, portalFrontDirection, cavern.center());
        buildTrialPortalFrame(world, cavern);
        AncientCityTrialPortal.placePortal(world, city.trialPortalPositions(), city.trialPortalAxis());
    }

    private static CavernPlan createCavernPlan(BlockPos altarPos, Direction portalFrontDirection) {
        Direction stairDirection = portalFrontDirection.getOpposite();
        BlockPos entranceFeet = altarPos.up(2).offset(stairDirection, 2);
        BlockPos center = entranceFeet.offset(stairDirection, STAIR_LENGTH + 3).down(STAIR_LENGTH - 1);
        Direction portalFront = portalFrontDirection;
        Direction across = portalFront.rotateYClockwise();
        Direction.Axis portalAxis = across.getAxis();
        BlockPos portalBaseCenter = center.offset(portalFront, CAVERN_HALF_DEPTH - 1);
        List<BlockPos> portalPositions = new ArrayList<>();
        for (int width = -(TRIAL_PORTAL_WIDTH / 2); width <= TRIAL_PORTAL_WIDTH / 2; width++) {
            for (int height = 0; height < TRIAL_PORTAL_HEIGHT; height++) {
                portalPositions.add(portalBaseCenter.offset(across, width).up(height));
            }
        }

        return new CavernPlan(center, portalFront, across, portalAxis, List.copyOf(portalPositions), center);
    }

    private static void buildCavernRoom(ServerWorld world, BlockPos center) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockState wall = Blocks.DEEPSLATE_TILES.getDefaultState();
        BlockState floor = Blocks.POLISHED_DEEPSLATE.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        int floorY = center.getY() - 1;
        int ceilingY = floorY + CAVERN_HEIGHT;

        for (int x = center.getX() - CAVERN_HALF_WIDTH; x <= center.getX() + CAVERN_HALF_WIDTH; x++) {
            for (int y = floorY; y <= ceilingY; y++) {
                for (int z = center.getZ() - CAVERN_HALF_DEPTH; z <= center.getZ() + CAVERN_HALF_DEPTH; z++) {
                    boolean boundary = x == center.getX() - CAVERN_HALF_WIDTH
                            || x == center.getX() + CAVERN_HALF_WIDTH
                            || z == center.getZ() - CAVERN_HALF_DEPTH
                            || z == center.getZ() + CAVERN_HALF_DEPTH
                            || y == floorY
                            || y == ceilingY;
                    world.setBlockState(pos.set(x, y, z), boundary ? (y == floorY ? floor : wall) : air, AncientCityBuildHelper.BULK_BLOCK_FLAGS);
                }
            }
        }

        placeFloorLight(world, center.add(3, -1, 3));
        placeFloorLight(world, center.add(-3, -1, 3));
        placeFloorLight(world, center.add(3, -1, -3));
        placeFloorLight(world, center.add(-3, -1, -3));
    }

    private static void buildCavernStair(ServerWorld world, BlockPos altarPos, Direction portalFrontDirection, BlockPos cavernCenter) {
        Direction stairDirection = portalFrontDirection.getOpposite();
        Direction across = stairDirection.rotateYClockwise();
        BlockPos entranceFeet = altarPos.up(2).offset(stairDirection, 2);
        BlockState floor = Blocks.POLISHED_DEEPSLATE.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int step = 0; step <= STAIR_LENGTH; step++) {
            BlockPos feet = entranceFeet.offset(stairDirection, step).down(step);
            for (int width = -(STAIR_WIDTH / 2); width <= STAIR_WIDTH / 2; width++) {
                BlockPos column = feet.offset(across, width);
                world.setBlockState(pos.set(column.getX(), column.getY() - 1, column.getZ()), floor, AncientCityBuildHelper.BULK_BLOCK_FLAGS);
                for (int clear = 0; clear <= 3; clear++) {
                    world.setBlockState(pos.set(column.getX(), column.getY() + clear, column.getZ()), air, AncientCityBuildHelper.BULK_BLOCK_FLAGS);
                }
            }
        }

        BlockBox connector = AncientCityBuildHelper.boxAround(cavernCenter, 2, 0, 3);
        AncientCityBuildHelper.clearBox(world, connector);
    }

    private static void buildTrialPortalFrame(ServerWorld world, CavernPlan cavern) {
        BlockState frame = Blocks.REINFORCED_DEEPSLATE.getDefaultState();
        BlockPos portalBaseCenter = cavern.center().offset(cavern.portalFront(), CAVERN_HALF_DEPTH - 1);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int width = -(TRIAL_PORTAL_WIDTH / 2) - 1; width <= TRIAL_PORTAL_WIDTH / 2 + 1; width++) {
            for (int height = -1; height <= TRIAL_PORTAL_HEIGHT; height++) {
                boolean frameBlock = width == -(TRIAL_PORTAL_WIDTH / 2) - 1
                        || width == TRIAL_PORTAL_WIDTH / 2 + 1
                        || height == -1
                        || height == TRIAL_PORTAL_HEIGHT;
                if (frameBlock) {
                    BlockPos framePos = portalBaseCenter.offset(cavern.across(), width).up(height);
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

    private static void placeFloorLight(ServerWorld world, BlockPos floorPos) {
        world.setBlockState(floorPos, Blocks.SEA_LANTERN.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(floorPos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(floorPos.up(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
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

    private record CavernPlan(
            BlockPos center,
            Direction portalFront,
            Direction across,
            Direction.Axis portalAxis,
            List<BlockPos> portalPositions,
            BlockPos returnSpawn
    ) {
    }
}
