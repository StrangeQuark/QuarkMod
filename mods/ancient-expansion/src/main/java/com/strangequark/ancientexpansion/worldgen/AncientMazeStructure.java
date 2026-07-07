package com.strangequark.ancientexpansion.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.SpreadType;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AncientMazeStructure extends Structure {
    static final int FLOOR_Y_OFFSET_FROM_WORLD_BOTTOM = 8;
    static final int DEFAULT_CELL_COUNT = 129;
    static final int DEFAULT_CORRIDOR_WIDTH = 6;
    static final int DEFAULT_WALL_THICKNESS = 2;
    static final int DEFAULT_WALL_HEIGHT = 20;
    static final int DEFAULT_SEED_SALT = 51942877;
    static final int SCULK_FLOOR_Y_OFFSET_FROM_FLOOR = 1;
    static final int WALKABLE_Y_OFFSET_FROM_FLOOR = 2;
    static final int ANCHOR_SPACING = 96;
    static final int ANCHOR_SEPARATION = 80;
    static final int ANCHOR_SALT = 90218413;
    static final float ANCHOR_FREQUENCY = 0.05F;
    private static final int OTHER_STRUCTURE_PROTECTION_MARGIN = 192;
    private static final int PLAN_CACHE_LIMIT = 64;
    private static final int LOCATED_MAZE_CACHE_LIMIT = 8192;
    private static final int PROTECTION_CACHE_LIMIT = 8192;
    private static final RandomSpreadStructurePlacement ANCHOR_PLACEMENT = new RandomSpreadStructurePlacement(
            Vec3i.ZERO,
            StructurePlacement.FrequencyReductionMethod.DEFAULT,
            ANCHOR_FREQUENCY,
            ANCHOR_SALT,
            Optional.empty(),
            ANCHOR_SPACING,
            ANCHOR_SEPARATION,
            SpreadType.TRIANGULAR
    );
    private static final ConcurrentMap<PlanCacheKey, AncientMazePlan> PLAN_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<LocatedMazeCacheKey, Optional<LocatedMaze>> LOCATED_MAZE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<ProtectionCacheKey, Boolean> PROTECTION_CACHE = new ConcurrentHashMap<>();

    public static final MapCodec<AncientMazeStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            configCodecBuilder(instance),
            Codec.intRange(5, 257).optionalFieldOf("cell_count", DEFAULT_CELL_COUNT).forGetter(structure -> structure.cellCount),
            Codec.intRange(1, 16).optionalFieldOf("corridor_width", DEFAULT_CORRIDOR_WIDTH).forGetter(structure -> structure.corridorWidth),
            Codec.intRange(1, 16).optionalFieldOf("wall_thickness", DEFAULT_WALL_THICKNESS).forGetter(structure -> structure.wallThickness),
            Codec.intRange(2, 64).optionalFieldOf("wall_height", DEFAULT_WALL_HEIGHT).forGetter(structure -> structure.wallHeight),
            Codec.INT.optionalFieldOf("seed_salt", DEFAULT_SEED_SALT).forGetter(structure -> structure.seedSalt),
            Codec.BOOL.optionalFieldOf("slice", false).forGetter(structure -> structure.slice)
    ).apply(instance, AncientMazeStructure::new));

    private final int cellCount;
    private final int corridorWidth;
    private final int wallThickness;
    private final int wallHeight;
    private final int seedSalt;
    private final boolean slice;

    public AncientMazeStructure(Config config, int cellCount, int corridorWidth, int wallThickness, int wallHeight, int seedSalt, boolean slice) {
        super(config);
        this.cellCount = normalizeCellCount(cellCount);
        this.corridorWidth = corridorWidth;
        this.wallThickness = wallThickness;
        this.wallHeight = wallHeight;
        this.seedSalt = seedSalt;
        this.slice = slice;
    }

    @Override
    protected Optional<StructurePosition> getStructurePosition(Context context) {
        int floorY = context.world().getBottomY() + FLOOR_Y_OFFSET_FROM_WORLD_BOTTOM;
        int totalHeight = AncientMazePlan.totalHeight(wallHeight);
        if (floorY + totalHeight - 1 > context.world().getTopYInclusive()) {
            return Optional.empty();
        }

        if (slice) {
            return getSliceStructurePosition(context, floorY);
        }

        ChunkPos chunkPos = context.chunkPos();
        int entranceX = chunkPos.getCenterX();
        int entranceZ = chunkPos.getCenterZ();
        long mazeSeed = mazeSeed(context.seed(), chunkPos, seedSalt);

        AncientMazePlan plan = AncientMazePlan.generate(mazeSeed, cellCount, corridorWidth, wallThickness);
        if (!plan.hasValidEntranceToCenterPath()) {
            return Optional.empty();
        }

        int originX = entranceX - AncientMazePiece.towerCenterOffsetX(plan);
        int originZ = entranceZ - AncientMazePiece.towerCenterOffsetZ(plan);
        int surfaceY = towerSurfaceY(context, floorY, entranceX, entranceZ);
        BlockPos locatePos = new BlockPos(entranceX, floorY + 1, entranceZ);

        return Optional.of(new StructurePosition(locatePos, collector -> collector.addPiece(AncientMazePiece.marker(
                originX,
                floorY,
                originZ,
                entranceX,
                entranceZ,
                cellCount,
                corridorWidth,
                wallThickness,
                wallHeight,
                context.world().getTopYInclusive(),
                mazeSeed,
                surfaceY
        ))));
    }

    private Optional<StructurePosition> getSliceStructurePosition(Context context, int floorY) {
        ChunkPos sliceChunk = context.chunkPos();
        Optional<LocatedMaze> locatedMaze = locateMazeForChunk(
                context.seed(),
                floorY,
                sliceChunk,
                cellCount,
                corridorWidth,
                wallThickness,
                wallHeight,
                seedSalt
        );
        if (locatedMaze.isEmpty()) {
            return Optional.empty();
        }

        LocatedMaze maze = locatedMaze.get();
        BlockBox sliceBox = AncientMazePiece.sliceBoxForChunk(
                maze.originX(),
                floorY,
                maze.originZ(),
                cellCount,
                corridorWidth,
                wallThickness,
                wallHeight,
                context.world().getTopYInclusive(),
                sliceChunk
        );
        if (sliceBox == null) {
            return Optional.empty();
        }

        int computedSurfaceY = floorY + wallHeight + 3;
        if (AncientMazePiece.towerIntersectsChunk(maze.originX(), maze.originZ(), cellCount, corridorWidth, wallThickness, sliceChunk)) {
            computedSurfaceY = towerSurfaceY(context, floorY, maze.entranceX(), maze.entranceZ());
        }
        int surfaceY = computedSurfaceY;
        BlockPos piecePos = new BlockPos(sliceChunk.getStartX(), floorY + 1, sliceChunk.getStartZ());
        return Optional.of(new StructurePosition(piecePos, collector -> collector.addPiece(new AncientMazePiece(
                maze.originX(),
                floorY,
                maze.originZ(),
                cellCount,
                corridorWidth,
                wallThickness,
                wallHeight,
                context.world().getTopYInclusive(),
                maze.mazeSeed(),
                surfaceY,
                sliceBox
        ))));
    }

    private int towerSurfaceY(Context context, int floorY, int x, int z) {
        int terrainY = context.chunkGenerator().getHeightOnGround(
                x,
                z,
                Heightmap.Type.WORLD_SURFACE_WG,
                context.world(),
                context.noiseConfig()
        );
        return Math.max(floorY + wallHeight + 3, terrainY);
    }

    public static boolean blocksOtherStructureStart(long worldSeed, ChunkPos chunkPos) {
        ProtectionCacheKey key = new ProtectionCacheKey(worldSeed, chunkPos.x, chunkPos.z);
        Boolean cached = PROTECTION_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        boolean blocks = blocksOtherStructureStartUncached(worldSeed, chunkPos);
        clearCacheIfNeeded(PROTECTION_CACHE, PROTECTION_CACHE_LIMIT);
        Boolean previous = PROTECTION_CACHE.putIfAbsent(key, blocks);
        return previous != null ? previous : blocks;
    }

    private static boolean blocksOtherStructureStartUncached(long worldSeed, ChunkPos chunkPos) {
        int footprintWidth = AncientMazePlan.totalWidth(DEFAULT_CELL_COUNT, DEFAULT_CORRIDOR_WIDTH, DEFAULT_WALL_THICKNESS);
        int searchRadius = Math.ceilDiv(footprintWidth + OTHER_STRUCTURE_PROTECTION_MARGIN * 2 + 256, 16);
        int minRegionX = Math.floorDiv(chunkPos.x - searchRadius, ANCHOR_SPACING);
        int maxRegionX = Math.floorDiv(chunkPos.x + searchRadius, ANCHOR_SPACING);
        int minRegionZ = Math.floorDiv(chunkPos.z - searchRadius, ANCHOR_SPACING);
        int maxRegionZ = Math.floorDiv(chunkPos.z + searchRadius, ANCHOR_SPACING);

        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                ChunkPos anchorChunk = ANCHOR_PLACEMENT.getStartChunk(worldSeed, regionX * ANCHOR_SPACING, regionZ * ANCHOR_SPACING);
                if (!passesAnchorFrequency(worldSeed, anchorChunk)) {
                    continue;
                }

                int entranceX = anchorChunk.getCenterX();
                int entranceZ = anchorChunk.getCenterZ();
                int originX = entranceX - AncientMazePiece.towerCenterOffsetX(DEFAULT_CELL_COUNT, DEFAULT_CORRIDOR_WIDTH, DEFAULT_WALL_THICKNESS);
                int originZ = entranceZ - AncientMazePiece.towerCenterOffsetZ(DEFAULT_WALL_THICKNESS);
                if (AncientMazePiece.footprintIntersectsChunk(
                        originX,
                        originZ,
                        DEFAULT_CELL_COUNT,
                        DEFAULT_CORRIDOR_WIDTH,
                        DEFAULT_WALL_THICKNESS,
                        DEFAULT_WALL_HEIGHT,
                        chunkPos,
                        OTHER_STRUCTURE_PROTECTION_MARGIN
                )) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean protectsGeneratedBedrock(long worldSeed, int worldBottomY, BlockPos pos) {
        int floorY = worldBottomY + FLOOR_Y_OFFSET_FROM_WORLD_BOTTOM;
        if (pos.getY() < floorY) {
            return false;
        }

        Optional<LocatedMaze> locatedMaze = locateMazeForChunk(
                worldSeed,
                floorY,
                new ChunkPos(pos),
                DEFAULT_CELL_COUNT,
                DEFAULT_CORRIDOR_WIDTH,
                DEFAULT_WALL_THICKNESS,
                DEFAULT_WALL_HEIGHT,
                DEFAULT_SEED_SALT
        );
        return locatedMaze.isPresent() && locatedMaze.get().containsFootprintColumn(pos.getX(), pos.getZ());
    }

    static Optional<LocatedMaze> locateMazeInteriorAt(long worldSeed, int worldBottomY, BlockPos pos) {
        int floorY = worldBottomY + FLOOR_Y_OFFSET_FROM_WORLD_BOTTOM;
        if (pos.getY() < floorY + WALKABLE_Y_OFFSET_FROM_FLOOR || pos.getY() > floorY + DEFAULT_WALL_HEIGHT) {
            return Optional.empty();
        }

        Optional<LocatedMaze> locatedMaze = locateMazeForChunk(
                worldSeed,
                floorY,
                new ChunkPos(pos),
                DEFAULT_CELL_COUNT,
                DEFAULT_CORRIDOR_WIDTH,
                DEFAULT_WALL_THICKNESS,
                DEFAULT_WALL_HEIGHT,
                DEFAULT_SEED_SALT
        );
        if (locatedMaze.isPresent() && locatedMaze.get().containsInterior(pos)) {
            return locatedMaze;
        }

        return Optional.empty();
    }

    public static Optional<BlockPos> pickNaturalSpawnCandidate(ServerWorld world, WorldChunk chunk) {
        if (!World.OVERWORLD.equals(world.getRegistryKey())) {
            return Optional.empty();
        }

        Optional<LocatedMaze> locatedMaze = locateMazeForChunk(
                world.getSeed(),
                world.getBottomY() + FLOOR_Y_OFFSET_FROM_WORLD_BOTTOM,
                chunk.getPos(),
                DEFAULT_CELL_COUNT,
                DEFAULT_CORRIDOR_WIDTH,
                DEFAULT_WALL_THICKNESS,
                DEFAULT_WALL_HEIGHT,
                DEFAULT_SEED_SALT
        );
        if (locatedMaze.isEmpty()) {
            return Optional.empty();
        }

        LocatedMaze maze = locatedMaze.get();
        ChunkPos chunkPos = chunk.getPos();
        int spawnY = world.getBottomY() + FLOOR_Y_OFFSET_FROM_WORLD_BOTTOM + WALKABLE_Y_OFFSET_FROM_FLOOR;
        int startIndex = world.random.nextInt(256);
        for (int attempt = 0; attempt < 256; attempt++) {
            int index = (startIndex + attempt) & 255;
            int x = chunkPos.getStartX() + (index & 15);
            int z = chunkPos.getStartZ() + (index >> 4);
            BlockPos spawnPos = new BlockPos(x, spawnY, z);
            if (maze.isInteriorColumn(x, z)
                    && world.getBlockState(spawnPos).isAir()
                    && world.getBlockState(spawnPos.up()).isAir()) {
                return Optional.of(spawnPos);
            }
        }

        return Optional.empty();
    }

    private static Optional<LocatedMaze> locateMazeForChunk(
            long worldSeed,
            int floorY,
            ChunkPos chunkPos,
            int cellCount,
            int corridorWidth,
            int wallThickness,
            int wallHeight,
            int seedSalt
    ) {
        LocatedMazeCacheKey key = new LocatedMazeCacheKey(
                worldSeed,
                floorY,
                cellCount,
                corridorWidth,
                wallThickness,
                wallHeight,
                seedSalt,
                chunkPos.x,
                chunkPos.z
        );
        Optional<LocatedMaze> cached = LOCATED_MAZE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        Optional<LocatedMaze> located = locateMazeForChunkUncached(
                worldSeed,
                floorY,
                chunkPos,
                cellCount,
                corridorWidth,
                wallThickness,
                wallHeight,
                seedSalt
        );
        clearCacheIfNeeded(LOCATED_MAZE_CACHE, LOCATED_MAZE_CACHE_LIMIT);
        Optional<LocatedMaze> previous = LOCATED_MAZE_CACHE.putIfAbsent(key, located);
        return previous != null ? previous : located;
    }

    private static Optional<LocatedMaze> locateMazeForChunkUncached(
            long worldSeed,
            int floorY,
            ChunkPos chunkPos,
            int cellCount,
            int corridorWidth,
            int wallThickness,
            int wallHeight,
            int seedSalt
    ) {
        int footprintWidth = AncientMazePlan.totalWidth(cellCount, corridorWidth, wallThickness);
        int searchRadius = Math.ceilDiv(footprintWidth + 256, 16);
        int minRegionX = Math.floorDiv(chunkPos.x - searchRadius, ANCHOR_SPACING);
        int maxRegionX = Math.floorDiv(chunkPos.x + searchRadius, ANCHOR_SPACING);
        int minRegionZ = Math.floorDiv(chunkPos.z - searchRadius, ANCHOR_SPACING);
        int maxRegionZ = Math.floorDiv(chunkPos.z + searchRadius, ANCHOR_SPACING);

        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                ChunkPos anchorChunk = ANCHOR_PLACEMENT.getStartChunk(worldSeed, regionX * ANCHOR_SPACING, regionZ * ANCHOR_SPACING);
                if (!passesAnchorFrequency(worldSeed, anchorChunk)) {
                    continue;
                }

                int entranceX = anchorChunk.getCenterX();
                int entranceZ = anchorChunk.getCenterZ();
                int originX = entranceX - AncientMazePiece.towerCenterOffsetX(cellCount, corridorWidth, wallThickness);
                int originZ = entranceZ - AncientMazePiece.towerCenterOffsetZ(wallThickness);
                if (AncientMazePiece.footprintIntersectsChunk(
                        originX,
                        originZ,
                        cellCount,
                        corridorWidth,
                        wallThickness,
                        wallHeight,
                        chunkPos,
                        0
                )) {
                    return Optional.of(new LocatedMaze(
                            originX,
                            floorY,
                            originZ,
                            anchorChunk,
                            mazeSeed(worldSeed, anchorChunk, seedSalt),
                            cellCount,
                            corridorWidth,
                            wallThickness
                    ));
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public StructureType<?> getType() {
        return ModStructureTypes.ANCIENT_MAZE;
    }

    static int normalizeCellCount(int cellCount) {
        int normalized = Math.max(5, cellCount);
        return normalized % 2 == 0 ? normalized + 1 : normalized;
    }

    private static long mazeSeed(long worldSeed, ChunkPos chunkPos, int seedSalt) {
        long seed = worldSeed ^ 0x6A09E667F3BCC909L;
        seed ^= (long) chunkPos.x * 0x9E3779B97F4A7C15L;
        seed ^= (long) chunkPos.z * 0xC2B2AE3D27D4EB4FL;
        seed ^= (long) seedSalt * 0x165667B19E3779F9L;
        return mix(seed);
    }

    private static boolean passesAnchorFrequency(long seed, ChunkPos chunkPos) {
        ChunkRandom chunkRandom = new ChunkRandom(new CheckedRandom(0L));
        chunkRandom.setRegionSeed(seed, ANCHOR_SALT, chunkPos.x, chunkPos.z);
        return chunkRandom.nextFloat() < ANCHOR_FREQUENCY;
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static AncientMazePlan cachedPlan(long mazeSeed, int cellCount, int corridorWidth, int wallThickness) {
        PlanCacheKey key = new PlanCacheKey(mazeSeed, cellCount, corridorWidth, wallThickness);
        clearCacheIfNeeded(PLAN_CACHE, PLAN_CACHE_LIMIT);
        return PLAN_CACHE.computeIfAbsent(key, planKey -> AncientMazePlan.generate(
                planKey.mazeSeed(),
                planKey.cellCount(),
                planKey.corridorWidth(),
                planKey.wallThickness()
        ));
    }

    private static void clearCacheIfNeeded(ConcurrentMap<?, ?> cache, int limit) {
        if (cache.size() > limit) {
            cache.clear();
        }
    }

    static final class LocatedMaze {
        private final int originX;
        private final int floorY;
        private final int originZ;
        private final ChunkPos anchorChunk;
        private final long mazeSeed;
        private final int cellCount;
        private final int corridorWidth;
        private final int wallThickness;

        private LocatedMaze(
                int originX,
                int floorY,
                int originZ,
                ChunkPos anchorChunk,
                long mazeSeed,
                int cellCount,
                int corridorWidth,
                int wallThickness
        ) {
            this.originX = originX;
            this.floorY = floorY;
            this.originZ = originZ;
            this.anchorChunk = anchorChunk;
            this.mazeSeed = mazeSeed;
            this.cellCount = cellCount;
            this.corridorWidth = corridorWidth;
            this.wallThickness = wallThickness;
        }

        int originX() {
            return originX;
        }

        int floorY() {
            return floorY;
        }

        int originZ() {
            return originZ;
        }

        int entranceX() {
            return anchorChunk.getCenterX();
        }

        int entranceZ() {
            return anchorChunk.getCenterZ();
        }

        long mazeSeed() {
            return mazeSeed;
        }

        boolean containsInterior(BlockPos pos) {
            if (pos.getY() < floorY + WALKABLE_Y_OFFSET_FROM_FLOOR || pos.getY() > floorY + DEFAULT_WALL_HEIGHT) {
                return false;
            }
            return isInteriorColumn(pos.getX(), pos.getZ());
        }

        boolean isInteriorColumn(int x, int z) {
            int localX = x - originX;
            int localZ = z - originZ;
            AncientMazePlan plan = plan();
            return localX >= 0
                    && localZ >= 0
                    && localX < plan.totalWidth()
                    && localZ < plan.totalWidth()
                    && plan.isOpenBlock(localX, localZ);
        }

        boolean containsFootprintColumn(int x, int z) {
            return AncientMazePiece.footprintContainsColumn(originX, originZ, cellCount, corridorWidth, wallThickness, x, z);
        }

        private AncientMazePlan plan() {
            return cachedPlan(mazeSeed, cellCount, corridorWidth, wallThickness);
        }
    }

    private record PlanCacheKey(long mazeSeed, int cellCount, int corridorWidth, int wallThickness) {
    }

    private record LocatedMazeCacheKey(
            long worldSeed,
            int floorY,
            int cellCount,
            int corridorWidth,
            int wallThickness,
            int wallHeight,
            int seedSalt,
            int chunkX,
            int chunkZ
    ) {
    }

    private record ProtectionCacheKey(long worldSeed, int chunkX, int chunkZ) {
    }
}
