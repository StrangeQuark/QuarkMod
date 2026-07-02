package com.strangequark.ancientmaze.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.SpreadType;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.Optional;

public final class AncientMazeStructure extends Structure {
    private static final int FLOOR_Y_OFFSET_FROM_WORLD_BOTTOM = 8;
    private static final int ANCHOR_SPACING = 96;
    private static final int ANCHOR_SEPARATION = 80;
    private static final int ANCHOR_SALT = 90218413;
    private static final float ANCHOR_FREQUENCY = 0.05F;
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

    public static final MapCodec<AncientMazeStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            configCodecBuilder(instance),
            Codec.intRange(5, 257).optionalFieldOf("cell_count", 129).forGetter(structure -> structure.cellCount),
            Codec.intRange(1, 16).optionalFieldOf("corridor_width", 6).forGetter(structure -> structure.corridorWidth),
            Codec.intRange(1, 16).optionalFieldOf("wall_thickness", 2).forGetter(structure -> structure.wallThickness),
            Codec.intRange(2, 64).optionalFieldOf("wall_height", 20).forGetter(structure -> structure.wallHeight),
            Codec.INT.optionalFieldOf("seed_salt", 51942877).forGetter(structure -> structure.seedSalt),
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
        int searchRadius = Math.ceilDiv(AncientMazePlan.totalWidth(cellCount, corridorWidth, wallThickness) + 96, 16);
        int minRegionX = Math.floorDiv(sliceChunk.x - searchRadius, ANCHOR_SPACING);
        int maxRegionX = Math.floorDiv(sliceChunk.x + searchRadius, ANCHOR_SPACING);
        int minRegionZ = Math.floorDiv(sliceChunk.z - searchRadius, ANCHOR_SPACING);
        int maxRegionZ = Math.floorDiv(sliceChunk.z + searchRadius, ANCHOR_SPACING);

        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                ChunkPos anchorChunk = ANCHOR_PLACEMENT.getStartChunk(context.seed(), regionX * ANCHOR_SPACING, regionZ * ANCHOR_SPACING);
                if (!passesAnchorFrequency(context.seed(), anchorChunk)) {
                    continue;
                }

                long mazeSeed = mazeSeed(context.seed(), anchorChunk, seedSalt);
                int entranceX = anchorChunk.getCenterX();
                int entranceZ = anchorChunk.getCenterZ();
                int originX = entranceX - AncientMazePiece.towerCenterOffsetX(cellCount, corridorWidth, wallThickness);
                int originZ = entranceZ - AncientMazePiece.towerCenterOffsetZ(wallThickness);
                BlockBox sliceBox = AncientMazePiece.sliceBoxForChunk(
                        originX,
                        floorY,
                        originZ,
                        cellCount,
                        corridorWidth,
                        wallThickness,
                        wallHeight,
                        context.world().getTopYInclusive(),
                        sliceChunk
                );
                if (sliceBox == null) {
                    continue;
                }

                int surfaceY = towerSurfaceY(context, floorY, entranceX, entranceZ);
                BlockPos piecePos = new BlockPos(sliceChunk.getStartX(), floorY + 1, sliceChunk.getStartZ());
                return Optional.of(new StructurePosition(piecePos, collector -> collector.addPiece(new AncientMazePiece(
                        originX,
                        floorY,
                        originZ,
                        cellCount,
                        corridorWidth,
                        wallThickness,
                        wallHeight,
                        context.world().getTopYInclusive(),
                        mazeSeed,
                        surfaceY,
                        sliceBox
                ))));
            }
        }

        return Optional.empty();
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
}
