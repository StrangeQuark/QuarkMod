package com.strangequark.ancientexpansion.trial;

import com.strangequark.ancientexpansion.block.AncientTrialPortalBlock;
import com.strangequark.ancientexpansion.block.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureKeys;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class AncientCityTrialPortal {
    private static final int FRAME_SEARCH_CHUNK_RADIUS = 2;
    private static final int MAX_FRAME_DISTANCE = 64;
    private static final int PORTAL_CLEAR_LIMIT = 256;
    private static final BlockPos ALTAR_LOCAL_POS = new BlockPos(10, 17, 20);
    private static final int PORTAL_LOCAL_X = 13;
    private static final int PORTAL_MIN_Y = 18;
    private static final int PORTAL_MAX_Y = 23;
    private static final int PORTAL_MIN_Z = 11;
    private static final int PORTAL_MAX_Z = 30;

    private AncientCityTrialPortal() {
    }

    public static ActivationResult activateNearestFrame(ServerWorld world, BlockPos altarPos) {
        Optional<FrameTarget> target = findNearestFrame(world, altarPos);
        if (target.isEmpty()) {
            return ActivationResult.NO_FRAME;
        }

        return activateFrame(world, target.get());
    }

    public static ActivationResult activateFrameAt(ServerWorld world, BlockPos interactionPos) {
        Optional<FrameTarget> target = findNearestFrame(world, interactionPos);
        if (target.isEmpty() || !isPortalInteractionPos(target.get(), interactionPos)) {
            return ActivationResult.NO_FRAME;
        }

        return activateFrame(world, target.get());
    }

    private static ActivationResult activateFrame(ServerWorld world, FrameTarget target) {
        if (isPortalActive(world, target)) {
            return ActivationResult.ALREADY_ACTIVE;
        }

        placePastPortal(world, target.portalPositions(), target.portalAxis());
        return ActivationResult.ACTIVATED;
    }

    public static void placePortal(ServerWorld world, List<BlockPos> portalPositions, Direction.Axis portalAxis) {
        placePortal(world, portalPositions, portalAxis, ModBlocks.ANCIENT_TRIAL_PORTAL);
    }

    public static void placePastPortal(ServerWorld world, List<BlockPos> portalPositions, Direction.Axis portalAxis) {
        placePortal(world, portalPositions, portalAxis, ModBlocks.ANCIENT_PAST_PORTAL);
    }

    private static void placePortal(ServerWorld world, List<BlockPos> portalPositions, Direction.Axis portalAxis, Block portalBlock) {
        BlockState portalState = portalBlock.getDefaultState()
                .with(AncientTrialPortalBlock.AXIS, portalAxis);
        for (BlockPos portalPos : portalPositions) {
            world.setBlockState(portalPos, portalState, Block.NOTIFY_ALL);
        }
    }

    public static void clearConnectedPortal(ServerWorld world, BlockPos seed) {
        if (!isPortalBlock(world.getBlockState(seed))) {
            return;
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(seed);
        visited.add(seed);

        int cleared = 0;
        while (!queue.isEmpty() && cleared < PORTAL_CLEAR_LIMIT) {
            BlockPos pos = queue.removeFirst();
            if (!isPortalBlock(world.getBlockState(pos))) {
                continue;
            }

            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            cleared++;

            for (Direction direction : Direction.values()) {
                BlockPos next = pos.offset(direction);
                if (visited.add(next) && isPortalBlock(world.getBlockState(next))) {
                    queue.add(next);
                }
            }
        }
    }

    public static List<FrameTarget> findFrameTargets(ServerWorld world, ChunkPos chunkPos) {
        Structure ancientCity = getAncientCityStructure(world).orElse(null);
        if (ancientCity == null) {
            return List.of();
        }

        List<FrameTarget> targets = new ArrayList<>();
        for (StructureStart structureStart : world.getStructureAccessor().getStructureStarts(chunkPos, structure -> structure == ancientCity)) {
            for (StructurePiece piece : structureStart.getChildren()) {
                if (piece instanceof PoolStructurePiece poolPiece && isCityCenterPiece(poolPiece)) {
                    targets.add(toFrameTarget(structureStart, poolPiece));
                }
            }
        }
        return targets;
    }

    public static Optional<FrameTarget> findNearestFrame(ServerWorld world, BlockPos pos) {
        ChunkPos centerChunk = new ChunkPos(pos);
        List<FrameTarget> targets = new ArrayList<>();
        for (int dx = -FRAME_SEARCH_CHUNK_RADIUS; dx <= FRAME_SEARCH_CHUNK_RADIUS; dx++) {
            for (int dz = -FRAME_SEARCH_CHUNK_RADIUS; dz <= FRAME_SEARCH_CHUNK_RADIUS; dz++) {
                targets.addAll(findFrameTargets(world, new ChunkPos(centerChunk.x + dx, centerChunk.z + dz)));
            }
        }

        long maxDistanceSquared = (long) MAX_FRAME_DISTANCE * MAX_FRAME_DISTANCE;
        return targets.stream()
                .filter(target -> target.altarPos().getSquaredDistance(pos) <= maxDistanceSquared)
                .min(Comparator.comparingDouble(target -> target.altarPos().getSquaredDistance(pos)));
    }

    private static boolean isPortalActive(ServerWorld world, FrameTarget target) {
        for (BlockPos portalPos : target.portalPositions()) {
            if (isPortalBlock(world.getBlockState(portalPos))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPortalBlock(BlockState state) {
        return state.isOf(ModBlocks.ANCIENT_TRIAL_PORTAL) || state.isOf(ModBlocks.ANCIENT_PAST_PORTAL);
    }

    private static boolean isPortalInteractionPos(FrameTarget target, BlockPos pos) {
        for (BlockPos portalPos : target.portalPositions()) {
            if (portalPos.getChebyshevDistance(pos) <= 1) {
                return true;
            }
        }
        return false;
    }

    private static Optional<Structure> getAncientCityStructure(ServerWorld world) {
        Registry<Structure> registry = world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
        return registry.getOptionalValue(StructureKeys.ANCIENT_CITY);
    }

    private static boolean isCityCenterPiece(PoolStructurePiece piece) {
        return piece.getPoolElement().toString().contains("ancient_city/city_center/city_center_");
    }

    private static FrameTarget toFrameTarget(StructureStart structureStart, PoolStructurePiece piece) {
        Direction.Axis portalAxis = piece.getRotation().rotate(Direction.SOUTH).getAxis();
        Direction portalFrontDirection = piece.getRotation().rotate(Direction.WEST);
        List<BlockPos> portalPositions = new ArrayList<>();
        for (int y = PORTAL_MIN_Y; y <= PORTAL_MAX_Y; y++) {
            for (int z = PORTAL_MIN_Z; z <= PORTAL_MAX_Z; z++) {
                portalPositions.add(transform(piece, new BlockPos(PORTAL_LOCAL_X, y, z)));
            }
        }

        return new FrameTarget(
                transform(piece, ALTAR_LOCAL_POS),
                portalAxis,
                portalFrontDirection,
                List.copyOf(portalPositions),
                structureStart.getBoundingBox(),
                piece.getPos(),
                piece.getRotation(),
                structureStart
        );
    }

    private static BlockPos transform(PoolStructurePiece piece, BlockPos localPos) {
        return StructureTemplate.transformAround(localPos, BlockMirror.NONE, piece.getRotation(), BlockPos.ORIGIN).add(piece.getPos());
    }

    public enum ActivationResult {
        ACTIVATED,
        ALREADY_ACTIVE,
        NO_FRAME
    }

    public record FrameTarget(
            BlockPos altarPos,
            Direction.Axis portalAxis,
            Direction portalFrontDirection,
            List<BlockPos> portalPositions,
            BlockBox cityBoundingBox,
            BlockPos cityCenterPiecePos,
            BlockRotation cityCenterRotation,
            StructureStart structureStart
    ) {
        public BlockPos transformCityCenterLocal(BlockPos localPos) {
            return StructureTemplate.transformAround(localPos, BlockMirror.NONE, cityCenterRotation, BlockPos.ORIGIN).add(cityCenterPiecePos);
        }
    }
}
