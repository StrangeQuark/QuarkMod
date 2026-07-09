package com.strangequark.ancientexpansion.worldgen;

import com.strangequark.ancientexpansion.block.ModBlocks;
import com.strangequark.ancientexpansion.trial.AncientCityTrialPortal;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AncientCityTrialAltarPlacement {
    private static final int LANDING_RADIUS = 2;
    private static final int LANDING_FRONT_DEPTH = 2;
    private static final int LANDING_BACK_DEPTH = 2;
    private static final int LANDING_SUPPORT_DEPTH = 3;
    private static final int STAIR_WIDTH_RADIUS = 2;
    private static final int STAIR_STEPS = 11;
    private static final int BOTTOM_STAIR_CLEAR_STEPS = 3;
    private static final int BOTTOM_STAIR_CLEAR_SIDE_RADIUS = STAIR_WIDTH_RADIUS + 1;
    private static final int BRIDGE_FIXTURE_CLEAR_BACK_STEPS = 4;
    private static final int BRIDGE_FIXTURE_CLEAR_FRONT_EXTENSION = 4;
    private static final int BRIDGE_FIXTURE_CLEAR_SIDE_RADIUS = STAIR_WIDTH_RADIUS + 4;
    private static final int CLEAR_HEIGHT = 4;
    private static final int BLOCK_FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
    private static final Map<RegistryKey<World>, Set<Long>> PENDING_CHUNKS = new ConcurrentHashMap<>();

    private AncientCityTrialAltarPlacement() {
    }

    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register(AncientCityTrialAltarPlacement::queueTrialAltarPlacement);
        ServerChunkEvents.CHUNK_GENERATE.register(AncientCityTrialAltarPlacement::queueTrialAltarPlacement);
        ServerTickEvents.END_WORLD_TICK.register(AncientCityTrialAltarPlacement::processPendingPlacements);
    }

    private static void queueTrialAltarPlacement(ServerWorld world, WorldChunk chunk) {
        PENDING_CHUNKS.computeIfAbsent(world.getRegistryKey(), key -> ConcurrentHashMap.newKeySet()).add(chunk.getPos().toLong());
    }

    private static void processPendingPlacements(ServerWorld world) {
        Set<Long> pendingChunks = PENDING_CHUNKS.get(world.getRegistryKey());
        if (pendingChunks == null || pendingChunks.isEmpty()) {
            return;
        }

        Iterator<Long> iterator = pendingChunks.iterator();
        while (iterator.hasNext()) {
            long packedChunkPos = iterator.next();
            ChunkPos loadedChunk = new ChunkPos(packedChunkPos);
            if (!isChunkLoaded(world, loadedChunk)) {
                continue;
            }

            if (placeTrialAltar(world, loadedChunk)) {
                pendingChunks.remove(packedChunkPos);
            }
        }

        if (pendingChunks.isEmpty()) {
            PENDING_CHUNKS.remove(world.getRegistryKey(), pendingChunks);
        }
    }

    private static boolean placeTrialAltar(ServerWorld world, ChunkPos loadedChunk) {
        boolean waitingForChunks = false;
        for (AncientCityTrialPortal.FrameTarget target : AncientCityTrialPortal.findFrameTargets(world, loadedChunk)) {
            if (!new ChunkPos(target.altarPos()).equals(loadedChunk)) {
                continue;
            }

            if (!arePlacementChunksLoaded(world, target)) {
                waitingForChunks = true;
                continue;
            }

            if (!isApproachPresent(world, target)) {
                buildTrialApproach(world, target);
            }
        }
        return !waitingForChunks;
    }

    public static void buildTrialApproach(ServerWorld world, AncientCityTrialPortal.FrameTarget target) {
        buildTrialApproach(world, target.altarPos(), target.portalFrontDirection());
    }

    public static void buildTrialApproach(ServerWorld world, BlockPos altarPos, Direction front) {
        Direction climbDirection = front.getOpposite();
        Direction side = front.rotateYClockwise();
        BlockPos altarBlockPos = getAltarBlockPos(altarPos);

        buildLanding(world, altarPos, front, side);
        buildStairs(world, altarPos, front, side, climbDirection);
        world.setBlockState(altarBlockPos, ModBlocks.ANCIENT_TRIAL_ALTAR.getDefaultState(), BLOCK_FLAGS);
        clearAbove(world, altarBlockPos, CLEAR_HEIGHT);
    }

    public static BlockPos getAltarBlockPos(BlockPos altarPos) {
        return altarPos.up();
    }

    private static boolean isApproachPresent(ServerWorld world, AncientCityTrialPortal.FrameTarget target) {
        if (!world.getBlockState(getAltarBlockPos(target.altarPos())).isOf(ModBlocks.ANCIENT_TRIAL_ALTAR)) {
            return false;
        }

        BlockPos topStair = horizontalOffset(
                target.altarPos(),
                target.portalFrontDirection(),
                LANDING_FRONT_DEPTH + 1,
                target.portalFrontDirection().rotateYClockwise(),
                0
        ).down();
        BlockPos bottomStair = horizontalOffset(
                target.altarPos(),
                target.portalFrontDirection(),
                bottomStairFrontOffset(),
                target.portalFrontDirection().rotateYClockwise(),
                0
        ).add(0, -STAIR_STEPS, 0);
        return world.getBlockState(topStair).isOf(Blocks.DEEPSLATE_BRICK_STAIRS)
                && world.getBlockState(bottomStair).isOf(Blocks.DEEPSLATE_BRICK_STAIRS)
                && isBridgeFixtureObstructionClear(world, target.altarPos(), target.portalFrontDirection(), target.portalFrontDirection().rotateYClockwise());
    }

    private static void buildLanding(ServerWorld world, BlockPos altarPos, Direction front, Direction side) {
        for (int frontOffset = -LANDING_BACK_DEPTH; frontOffset <= LANDING_FRONT_DEPTH; frontOffset++) {
            for (int sideOffset = -LANDING_RADIUS; sideOffset <= LANDING_RADIUS; sideOffset++) {
                BlockPos floorPos = horizontalOffset(altarPos, front, frontOffset, side, sideOffset);
                for (int depth = LANDING_SUPPORT_DEPTH; depth > 0; depth--) {
                    world.setBlockState(floorPos.down(depth), Blocks.DEEPSLATE_BRICKS.getDefaultState(), BLOCK_FLAGS);
                }

                BlockState floorState = Math.abs(sideOffset) == LANDING_RADIUS || frontOffset == -LANDING_BACK_DEPTH || frontOffset == LANDING_FRONT_DEPTH
                        ? Blocks.DEEPSLATE_BRICKS.getDefaultState()
                        : Blocks.DEEPSLATE_TILES.getDefaultState();
                world.setBlockState(floorPos, floorState, BLOCK_FLAGS);
                clearAbove(world, floorPos, CLEAR_HEIGHT);
            }
        }
    }

    private static void buildStairs(ServerWorld world, BlockPos altarPos, Direction front, Direction side, Direction climbDirection) {
        BlockState stairState = Blocks.DEEPSLATE_BRICK_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, climbDirection);

        clearBottomStairObstructions(world, altarPos, front, side);
        clearBridgeFixtureObstruction(world, altarPos, front, side);
        for (int step = 0; step < STAIR_STEPS; step++) {
            int frontOffset = LANDING_FRONT_DEPTH + 1 + step;
            int yOffset = -1 - step;
            for (int sideOffset = -STAIR_WIDTH_RADIUS; sideOffset <= STAIR_WIDTH_RADIUS; sideOffset++) {
                BlockPos stepPos = horizontalOffset(altarPos, front, frontOffset, side, sideOffset).add(0, yOffset, 0);
                world.setBlockState(stepPos.down(), Blocks.DEEPSLATE_BRICKS.getDefaultState(), BLOCK_FLAGS);
                world.setBlockState(stepPos, stairState, BLOCK_FLAGS);
                clearAbove(world, stepPos, CLEAR_HEIGHT);
            }
        }
    }

    private static void clearBottomStairObstructions(ServerWorld world, BlockPos altarPos, Direction front, Direction side) {
        BlockState air = Blocks.AIR.getDefaultState();
        int firstStep = STAIR_STEPS - BOTTOM_STAIR_CLEAR_STEPS;
        int maxY = altarPos.getY() + CLEAR_HEIGHT;
        BlockPos.Mutable clearPos = new BlockPos.Mutable();
        for (int step = firstStep; step < STAIR_STEPS; step++) {
            int frontOffset = LANDING_FRONT_DEPTH + 1 + step;
            int minY = altarPos.getY() - 1 - step;
            for (int sideOffset = -BOTTOM_STAIR_CLEAR_SIDE_RADIUS; sideOffset <= BOTTOM_STAIR_CLEAR_SIDE_RADIUS; sideOffset++) {
                BlockPos columnPos = horizontalOffset(altarPos, front, frontOffset, side, sideOffset);
                for (int y = minY; y <= maxY; y++) {
                    world.setBlockState(clearPos.set(columnPos.getX(), y, columnPos.getZ()), air, BLOCK_FLAGS);
                }
            }
        }
    }

    private static void clearBridgeFixtureObstruction(ServerWorld world, BlockPos altarPos, Direction front, Direction side) {
        BlockState air = Blocks.AIR.getDefaultState();
        int minY = bridgeFixtureMinY(altarPos);
        int maxY = altarPos.getY() + CLEAR_HEIGHT;
        BlockPos.Mutable clearPos = new BlockPos.Mutable();
        for (int frontOffset = bridgeFixtureMinFrontOffset(); frontOffset <= bridgeFixtureMaxFrontOffset(); frontOffset++) {
            for (int sideOffset = -BRIDGE_FIXTURE_CLEAR_SIDE_RADIUS; sideOffset <= BRIDGE_FIXTURE_CLEAR_SIDE_RADIUS; sideOffset++) {
                BlockPos columnPos = horizontalOffset(altarPos, front, frontOffset, side, sideOffset);
                for (int y = minY; y <= maxY; y++) {
                    BlockState state = world.getBlockState(clearPos.set(columnPos.getX(), y, columnPos.getZ()));
                    if (isBridgeFixtureBlock(state) && !isPreservedBridgeDeckBlock(state, y == minY)) {
                        world.setBlockState(clearPos, air, BLOCK_FLAGS);
                    }
                }
            }
        }
    }

    private static boolean isBridgeFixtureObstructionClear(ServerWorld world, BlockPos altarPos, Direction front, Direction side) {
        int minY = bridgeFixtureMinY(altarPos);
        int maxY = altarPos.getY() + CLEAR_HEIGHT;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int frontOffset = bridgeFixtureMinFrontOffset(); frontOffset <= bridgeFixtureMaxFrontOffset(); frontOffset++) {
            for (int sideOffset = -BRIDGE_FIXTURE_CLEAR_SIDE_RADIUS; sideOffset <= BRIDGE_FIXTURE_CLEAR_SIDE_RADIUS; sideOffset++) {
                BlockPos columnPos = horizontalOffset(altarPos, front, frontOffset, side, sideOffset);
                for (int y = minY; y <= maxY; y++) {
                    BlockState state = world.getBlockState(pos.set(columnPos.getX(), y, columnPos.getZ()));
                    if (isBridgeFixtureBlock(state) && !isPreservedBridgeDeckBlock(state, y == minY)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean isBridgeFixtureBlock(BlockState state) {
        return state.isIn(BlockTags.PLANKS)
                || state.isIn(BlockTags.WOODEN_FENCES)
                || state.isIn(BlockTags.WOODEN_SLABS)
                || state.isIn(BlockTags.WOODEN_STAIRS)
                || state.isIn(BlockTags.LOGS)
                || state.isOf(Blocks.TORCH)
                || state.isOf(Blocks.WALL_TORCH)
                || state.isOf(Blocks.SOUL_TORCH)
                || state.isOf(Blocks.SOUL_WALL_TORCH);
    }

    private static boolean isPreservedBridgeDeckBlock(BlockState state, boolean bottomLayer) {
        return bottomLayer && (state.isIn(BlockTags.PLANKS) || state.isIn(BlockTags.WOODEN_SLABS));
    }

    private static void clearAbove(ServerWorld world, BlockPos floorPos, int height) {
        BlockState air = Blocks.AIR.getDefaultState();
        for (int y = 1; y <= height; y++) {
            world.setBlockState(floorPos.up(y), air, BLOCK_FLAGS);
        }
    }

    private static boolean arePlacementChunksLoaded(ServerWorld world, AncientCityTrialPortal.FrameTarget target) {
        Direction front = target.portalFrontDirection();
        Direction side = front.rotateYClockwise();
        BlockPos altarPos = target.altarPos();

        if (!isPositionLoaded(world, altarPos) || !isPositionLoaded(world, getAltarBlockPos(altarPos))) {
            return false;
        }

        BlockPos topStair = horizontalOffset(
                altarPos,
                front,
                LANDING_FRONT_DEPTH + 1,
                side,
                0
        ).down();
        if (!isPositionLoaded(world, topStair)) {
            return false;
        }

        for (int frontOffset = -LANDING_BACK_DEPTH; frontOffset <= LANDING_FRONT_DEPTH; frontOffset++) {
            for (int sideOffset = -LANDING_RADIUS; sideOffset <= LANDING_RADIUS; sideOffset++) {
                if (!isPositionLoaded(world, horizontalOffset(altarPos, front, frontOffset, side, sideOffset))) {
                    return false;
                }
            }
        }

        for (int step = 0; step < STAIR_STEPS; step++) {
            int frontOffset = LANDING_FRONT_DEPTH + 1 + step;
            for (int sideOffset = -STAIR_WIDTH_RADIUS; sideOffset <= STAIR_WIDTH_RADIUS; sideOffset++) {
                if (!isPositionLoaded(world, horizontalOffset(altarPos, front, frontOffset, side, sideOffset))) {
                    return false;
                }
            }
        }

        for (int step = STAIR_STEPS - BOTTOM_STAIR_CLEAR_STEPS; step < STAIR_STEPS; step++) {
            int frontOffset = LANDING_FRONT_DEPTH + 1 + step;
            for (int sideOffset = -BOTTOM_STAIR_CLEAR_SIDE_RADIUS; sideOffset <= BOTTOM_STAIR_CLEAR_SIDE_RADIUS; sideOffset++) {
                if (!isPositionLoaded(world, horizontalOffset(altarPos, front, frontOffset, side, sideOffset))) {
                    return false;
                }
            }
        }

        for (int frontOffset = bridgeFixtureMinFrontOffset(); frontOffset <= bridgeFixtureMaxFrontOffset(); frontOffset++) {
            for (int sideOffset = -BRIDGE_FIXTURE_CLEAR_SIDE_RADIUS; sideOffset <= BRIDGE_FIXTURE_CLEAR_SIDE_RADIUS; sideOffset++) {
                if (!isPositionLoaded(world, horizontalOffset(altarPos, front, frontOffset, side, sideOffset))) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean isPositionLoaded(ServerWorld world, BlockPos pos) {
        return isChunkLoaded(world, new ChunkPos(pos));
    }

    private static boolean isChunkLoaded(ServerWorld world, ChunkPos pos) {
        return world.getChunkManager().getWorldChunk(pos.x, pos.z) != null;
    }

    private static int bottomStairFrontOffset() {
        return LANDING_FRONT_DEPTH + STAIR_STEPS;
    }

    private static int bridgeFixtureMinFrontOffset() {
        return bottomStairFrontOffset() - BRIDGE_FIXTURE_CLEAR_BACK_STEPS;
    }

    private static int bridgeFixtureMaxFrontOffset() {
        return bottomStairFrontOffset() + BRIDGE_FIXTURE_CLEAR_FRONT_EXTENSION;
    }

    private static int bridgeFixtureMinY(BlockPos altarPos) {
        return altarPos.getY() - STAIR_STEPS + 1;
    }

    private static BlockPos horizontalOffset(BlockPos origin, Direction front, int frontOffset, Direction side, int sideOffset) {
        return origin.offset(front, frontOffset).offset(side, sideOffset);
    }
}
