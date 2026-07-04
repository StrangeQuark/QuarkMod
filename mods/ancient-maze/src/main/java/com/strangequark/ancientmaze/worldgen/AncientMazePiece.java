package com.strangequark.ancientmaze.worldgen;

import com.strangequark.ancientmaze.AncientMazeMod;
import com.strangequark.ancientmaze.item.ModItems;
import com.strangequark.ancientmaze.village.AncientMazeVillagers;
import net.minecraft.block.Block;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.HorizontalConnectingBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.MultifaceBlock;
import net.minecraft.block.SculkShriekerBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AncientMazePiece extends StructurePiece {
    private static final int OUTER_SHELL_THICKNESS = 6;
    private static final int TOWER_WEST_SPAN = 6;
    private static final int TOWER_EAST_SPAN = 5;
    private static final int TOWER_NORTH_SPAN = 5;
    private static final int TOWER_SOUTH_SPAN = 5;
    private static final int TOWER_ABOVE_SURFACE = 22;
    private static final int APPROACH_HEADROOM = 4;
    private static final int BASEMENT_DEPTH = 5;
    private static final int PLAN_CACHE_LIMIT = 32;
    private static final int BULK_GENERATION_FLAGS = Block.FORCE_STATE_AND_SKIP_CALLBACKS_AND_DROPS;
    private static final int SCULK_FEATURE_FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
    private static final int SCULK_WALL_VEIN_CHANCE = 205;
    private static final int SCULK_SHAFT_VEIN_CHANCE = 230;
    private static final Map<PlanKey, AncientMazePlan> PLAN_CACHE = new LinkedHashMap<>(PLAN_CACHE_LIMIT, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<PlanKey, AncientMazePlan> eldest) {
            return size() > PLAN_CACHE_LIMIT;
        }
    };

    private static final RegistryKey<LootTable> CENTER_LOOT_TABLE = RegistryKey.of(
            RegistryKeys.LOOT_TABLE,
            AncientMazeMod.id("chests/ancient_maze_center")
    );

    private final int originX;
    private final int floorY;
    private final int originZ;
    private final int cellCount;
    private final int corridorWidth;
    private final int wallThickness;
    private final int wallHeight;
    private final int topY;
    private final long mazeSeed;
    private final int surfaceY;
    private final boolean markerOnly;
    private transient AncientMazePlan cachedPlan;

    public AncientMazePiece(
            int originX,
            int floorY,
            int originZ,
            int cellCount,
            int corridorWidth,
            int wallThickness,
            int wallHeight,
            int topY,
            long mazeSeed,
            int surfaceY
    ) {
        this(originX, floorY, originZ, cellCount, corridorWidth, wallThickness, wallHeight, topY, mazeSeed, surfaceY,
                createBoundingBox(originX, floorY, originZ, cellCount, corridorWidth, wallThickness, wallHeight, topY), false);
    }

    AncientMazePiece(
            int originX,
            int floorY,
            int originZ,
            int cellCount,
            int corridorWidth,
            int wallThickness,
            int wallHeight,
            int topY,
            long mazeSeed,
            int surfaceY,
            BlockBox boundingBox
    ) {
        this(originX, floorY, originZ, cellCount, corridorWidth, wallThickness, wallHeight, topY, mazeSeed, surfaceY, boundingBox, false);
    }

    private AncientMazePiece(
            int originX,
            int floorY,
            int originZ,
            int cellCount,
            int corridorWidth,
            int wallThickness,
            int wallHeight,
            int topY,
            long mazeSeed,
            int surfaceY,
            BlockBox boundingBox,
            boolean markerOnly
    ) {
        super(ModStructurePieces.ANCIENT_MAZE, 0, boundingBox);
        this.originX = originX;
        this.floorY = floorY;
        this.originZ = originZ;
        this.cellCount = AncientMazeStructure.normalizeCellCount(cellCount);
        this.corridorWidth = corridorWidth;
        this.wallThickness = wallThickness;
        this.wallHeight = wallHeight;
        this.topY = topY;
        this.mazeSeed = mazeSeed;
        this.surfaceY = surfaceY;
        this.markerOnly = markerOnly;
    }

    public AncientMazePiece(StructureContext context, NbtCompound nbt) {
        super(ModStructurePieces.ANCIENT_MAZE, nbt);
        this.originX = nbt.getInt("OriginX", 0);
        this.floorY = nbt.getInt("FloorY", 0);
        this.originZ = nbt.getInt("OriginZ", 0);
        this.cellCount = AncientMazeStructure.normalizeCellCount(nbt.getInt("CellCount", 129));
        this.corridorWidth = nbt.getInt("CorridorWidth", 6);
        this.wallThickness = nbt.getInt("WallThickness", 2);
        this.wallHeight = nbt.getInt("WallHeight", 20);
        this.topY = nbt.getInt("TopY", floorY + AncientMazePlan.totalHeight(wallHeight) - 1);
        this.mazeSeed = nbt.getLong("MazeSeed", 0L);
        this.surfaceY = nbt.getInt("SurfaceY", floorY + wallHeight + 3);
        this.markerOnly = nbt.getBoolean("MarkerOnly", false);
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putInt("OriginX", originX);
        nbt.putInt("FloorY", floorY);
        nbt.putInt("OriginZ", originZ);
        nbt.putInt("CellCount", cellCount);
        nbt.putInt("CorridorWidth", corridorWidth);
        nbt.putInt("WallThickness", wallThickness);
        nbt.putInt("WallHeight", wallHeight);
        nbt.putInt("TopY", topY);
        nbt.putLong("MazeSeed", mazeSeed);
        nbt.putInt("SurfaceY", surfaceY);
        nbt.putBoolean("MarkerOnly", markerOnly);
    }

    @Override
    public void generate(
            StructureWorldAccess world,
            StructureAccessor structureAccessor,
            ChunkGenerator chunkGenerator,
            Random random,
            BlockBox chunkBox,
            ChunkPos chunkPos,
            BlockPos pivot
    ) {
        if (markerOnly) {
            return;
        }
        AncientMazePlan plan = plan();
        generateMazeBox(world, chunkBox, plan);
        generateCenterPrizeRoom(world, chunkBox, plan);
        generateEntranceApproach(world, chunkBox, plan);
        generateTower(world, chunkBox, plan);
        placeCenterChest(world, chunkBox, plan);
        placeCenterPickaxeFrame(world, chunkBox, plan);
    }

    private void generateMazeBox(StructureWorldAccess world, BlockBox chunkBox, AncientMazePlan plan) {
        BlockBox box = mazeBox(plan);

        int minX = Math.max(box.getMinX(), chunkBox.getMinX());
        int maxX = Math.min(box.getMaxX(), chunkBox.getMaxX());
        int minY = Math.max(box.getMinY(), chunkBox.getMinY());
        int maxY = Math.min(box.getMaxY(), chunkBox.getMaxY());
        int minZ = Math.max(box.getMinZ(), chunkBox.getMinZ());
        int maxZ = Math.min(box.getMaxZ(), chunkBox.getMaxZ());
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return;
        }

        BlockState bedrock = Blocks.BEDROCK.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState sculk = Blocks.SCULK.getDefaultState();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int ceilingY = floorY + wallHeight + 1;
        int sculkY = floorY + AncientMazeStructure.SCULK_FLOOR_Y_OFFSET_FROM_FLOOR;
        int featureY = floorY + AncientMazeStructure.WALKABLE_Y_OFFSET_FROM_FLOOR;

        for (int x = minX; x <= maxX; x++) {
            int localX = x - originX;
            for (int z = minZ; z <= maxZ; z++) {
                int localZ = z - originZ;
                boolean openColumn = isMazeOpenColumn(plan, localX, localZ, corridorWidth);
                BlockState sculkFeature = openColumn ? sculkFeatureState(plan, localX, localZ) : null;
                for (int y = minY; y <= maxY; y++) {
                    boolean shell = y == floorY || y == ceilingY;
                    BlockState state;
                    int flags = BULK_GENERATION_FLAGS;
                    if (shell || !openColumn) {
                        state = bedrock;
                    } else if (y == sculkY) {
                        state = sculk;
                    } else if (y == featureY && sculkFeature != null) {
                        state = sculkFeature;
                        flags = SCULK_FEATURE_FLAGS;
                    } else if (y >= featureY) {
                        BlockState sculkVein = sculkMazeVeinState(plan, localX, localZ, y, ceilingY);
                        state = sculkVein != null ? sculkVein : air;
                    } else {
                        state = air;
                    }
                    world.setBlockState(pos.set(x, y, z), state, flags);
                }
            }
        }
    }

    private void generateCenterPrizeRoom(StructureWorldAccess world, BlockBox chunkBox, AncientMazePlan plan) {
        BlockBox roomBox = centerPrizeRoomBox(plan);
        if (!roomBox.intersects(chunkBox)) {
            return;
        }

        BlockState bedrock = Blocks.BEDROCK.getDefaultState();
        BlockState sculk = Blocks.SCULK.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int minX = Math.max(roomBox.getMinX(), chunkBox.getMinX());
        int maxX = Math.min(roomBox.getMaxX(), chunkBox.getMaxX());
        int minY = Math.max(roomBox.getMinY(), chunkBox.getMinY());
        int maxY = Math.min(roomBox.getMaxY(), chunkBox.getMaxY());
        int minZ = Math.max(roomBox.getMinZ(), chunkBox.getMinZ());
        int maxZ = Math.min(roomBox.getMaxZ(), chunkBox.getMaxZ());
        int ceilingY = floorY + wallHeight + 1;
        int sculkY = floorY + AncientMazeStructure.SCULK_FLOOR_Y_OFFSET_FROM_FLOOR;
        int carpetY = floorY + AncientMazeStructure.WALKABLE_Y_OFFSET_FROM_FLOOR;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean perimeter = x == roomBox.getMinX()
                        || x == roomBox.getMaxX()
                        || z == roomBox.getMinZ()
                        || z == roomBox.getMaxZ();
                boolean doorway = isCenterPrizeRoomDoor(plan, roomBox, x, z);
                for (int y = minY; y <= maxY; y++) {
                    boolean wall = perimeter && !doorway;
                    BlockState state;
                    int flags = BULK_GENERATION_FLAGS;
                    if (y == floorY || y == ceilingY || wall) {
                        state = bedrock;
                    } else if (y == sculkY) {
                        state = isCenterPrizeRoomFloorLight(plan, roomBox, x, z) ? Blocks.SEA_LANTERN.getDefaultState() : sculk;
                    } else if (y == carpetY && shouldPlaceCenterPrizeRoomCarpet(plan, roomBox, x, z)) {
                        state = centerPrizeRoomCarpetState(x, z);
                        flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
                    } else if (y >= carpetY) {
                        state = air;
                    } else {
                        state = air;
                    }
                    world.setBlockState(pos.set(x, y, z), state, flags);
                }
            }
        }

        placeCenterPrizeRoomLighting(world, chunkBox, roomBox, plan);
    }

    private void generateEntranceApproach(StructureWorldAccess world, BlockBox chunkBox, AncientMazePlan plan) {
        int minX = originX + entranceMinX(plan);
        int maxX = minX + corridorWidth - 1;
        int minZ = originZ + towerCenterOffsetZ(plan);
        int maxZ = originZ - OUTER_SHELL_THICKNESS - 1;
        int minY = floorY;
        int maxY = floorY + APPROACH_HEADROOM;
        BlockBox approachBox = new BlockBox(minX - 1, minY, minZ, maxX + 1, maxY, maxZ);
        if (!approachBox.intersects(chunkBox)) {
            return;
        }

        BlockState brick = Blocks.BEDROCK.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState sculk = Blocks.SCULK.getDefaultState();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int sculkY = floorY + AncientMazeStructure.SCULK_FLOOR_Y_OFFSET_FROM_FLOOR;
        for (int x = Math.max(approachBox.getMinX(), chunkBox.getMinX()); x <= Math.min(approachBox.getMaxX(), chunkBox.getMaxX()); x++) {
            for (int z = Math.max(approachBox.getMinZ(), chunkBox.getMinZ()); z <= Math.min(approachBox.getMaxZ(), chunkBox.getMaxZ()); z++) {
                for (int y = Math.max(approachBox.getMinY(), chunkBox.getMinY()); y <= Math.min(approachBox.getMaxY(), chunkBox.getMaxY()); y++) {
                    boolean floorOrCeiling = y == floorY || y == maxY;
                    boolean sideWall = x == minX - 1 || x == maxX + 1;
                    boolean passageColumn = x >= minX && x <= maxX;
                    boolean passage = passageColumn && y > floorY && y < maxY;
                    BlockState state = floorOrCeiling || sideWall ? brick : passage ? y == sculkY ? sculk : air : brick;
                    world.setBlockState(pos.set(x, y, z), state, BULK_GENERATION_FLAGS);
                }
            }
        }
    }

    private void generateTower(StructureWorldAccess world, BlockBox chunkBox, AncientMazePlan plan) {
        int centerX = originX + towerCenterOffsetX(plan);
        int centerZ = originZ + towerCenterOffsetZ(plan);
        int basementFloorY = surfaceY;
        int upperFloorY = basementFloorY + BASEMENT_DEPTH;
        int towerTopY = Math.min(topY, surfaceY + TOWER_ABOVE_SURFACE);
        BlockBox towerBox = towerBox(centerX, centerZ, floorY, towerTopY);
        if (!towerBox.intersects(chunkBox)) {
            return;
        }

        BlockState brick = Blocks.DEEPSLATE_BRICKS.getDefaultState();
        BlockState crackedBrick = Blocks.CRACKED_DEEPSLATE_BRICKS.getDefaultState();
        BlockState chiseled = Blocks.CHISELED_DEEPSLATE.getDefaultState();
        BlockState polished = Blocks.POLISHED_DEEPSLATE.getDefaultState();
        BlockState tile = Blocks.DEEPSLATE_TILES.getDefaultState();
        BlockState bedrock = Blocks.BEDROCK.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState sculk = Blocks.SCULK.getDefaultState();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        int minX = Math.max(towerBox.getMinX(), chunkBox.getMinX());
        int maxX = Math.min(towerBox.getMaxX(), chunkBox.getMaxX());
        int minY = Math.max(towerBox.getMinY(), chunkBox.getMinY());
        int maxY = Math.min(towerBox.getMaxY(), chunkBox.getMaxY());
        int minZ = Math.max(towerBox.getMinZ(), chunkBox.getMinZ());
        int maxZ = Math.min(towerBox.getMaxZ(), chunkBox.getMaxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int dx = x - centerX;
                int dz = z - centerZ;
                boolean westWall = dx == -TOWER_WEST_SPAN;
                boolean eastWall = dx == TOWER_EAST_SPAN;
                boolean northWall = dz == -TOWER_NORTH_SPAN;
                boolean southWall = dz == TOWER_SOUTH_SPAN;
                boolean wall = westWall || eastWall || northWall || southWall;
                boolean corner = (westWall || eastWall) && (northWall || southWall);
                for (int y = minY; y <= maxY; y++) {
                    int yFromSurface = y - surfaceY;
                    boolean belowBasement = y < basementFloorY;
                    boolean shaftFloor = belowBasement && y == floorY;
                    boolean lowerMazeDoor = belowBasement
                            && southWall
                            && x >= originX + entranceMinX(plan)
                            && x <= originX + entranceMaxX(plan, corridorWidth)
                            && y > floorY
                            && y < floorY + APPROACH_HEADROOM;
                    boolean basementFloor = y == basementFloorY;
                    boolean upperFloor = y == upperFloorY;
                    boolean upperFloorHatch = upperFloor && isUpperFloorHatch(dx, dz);
                    boolean roofDeck = y == towerTopY - 2;
                    boolean parapetBase = y == towerTopY - 1 && wall;
                    boolean battlement = y == towerTopY && wall && (corner || Math.floorMod(x + z, 2) == 0);
                    boolean surfaceDoor = northWall && Math.abs(dx) <= 1 && y >= surfaceY + 1 && y <= surfaceY + 3;
                    boolean window = isWindow(dx, dz, yFromSurface);
                    boolean wallBand = isWallBand(yFromSurface);

                    BlockState state = air;
                    if (belowBasement) {
                        if (!wall && y == floorY + AncientMazeStructure.SCULK_FLOOR_Y_OFFSET_FROM_FLOOR) {
                            state = sculk;
                        } else if (!wall && !shaftFloor) {
                            BlockState sculkVein = sculkShaftVeinState(dx, dz, y);
                            state = sculkVein != null ? sculkVein : air;
                        } else if (lowerMazeDoor) {
                            state = y == floorY + AncientMazeStructure.SCULK_FLOOR_Y_OFFSET_FROM_FLOOR ? sculk : air;
                        } else {
                            state = bedrock;
                        }
                    } else if (basementFloor) {
                        state = dx == 0 && dz == 0 ? air : bedrock;
                    } else if (roofDeck) {
                        state = tile;
                    } else if (parapetBase || battlement) {
                        state = tile;
                    } else if (y >= towerTopY - 1) {
                        state = air;
                    } else if (wall && !surfaceDoor) {
                        state = window ? towerWindowBarsState(dx, dz) : towerWallState(corner, wallBand, dx, dz, yFromSurface, brick, crackedBrick, chiseled, polished, tile);
                    } else if (upperFloor) {
                        state = upperFloorHatch ? air : polished;
                    }
                    world.setBlockState(pos.set(x, y, z), state, BULK_GENERATION_FLAGS);
                }
            }
        }

        placeTowerDetails(world, chunkBox, centerX, centerZ, basementFloorY, upperFloorY, towerTopY);
        spawnVillager(world, chunkBox, centerX + 2, upperFloorY + 1, centerZ);
    }

    private void placeTowerDetails(StructureWorldAccess world, BlockBox chunkBox, int centerX, int centerZ, int basementFloorY, int upperFloorY, int towerTopY) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        placeIfInChunk(world, chunkBox, pos, centerX - 4, basementFloorY + 1, centerZ - 3, Blocks.LANTERN.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX + 3, basementFloorY + 1, centerZ - 3, Blocks.LANTERN.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX - 4, basementFloorY + 1, centerZ + 3, Blocks.LANTERN.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX + 3, basementFloorY + 1, centerZ + 3, Blocks.LANTERN.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX - 5, basementFloorY + 1, centerZ + 1, Blocks.CHISELED_DEEPSLATE.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX + 4, basementFloorY + 1, centerZ + 1, Blocks.CHISELED_DEEPSLATE.getDefaultState());

        for (int y = basementFloorY + 1; y <= upperFloorY + 1; y++) {
            placeIfInChunk(world, chunkBox, pos, centerX - TOWER_WEST_SPAN + 1, y, centerZ + 3,
                    Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.EAST));
        }

        int livingY = upperFloorY + 1;
        placeIfInChunk(world, chunkBox, pos, centerX + 3, livingY, centerZ + 3,
                Blocks.RED_BED.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.FOOT));
        placeIfInChunk(world, chunkBox, pos, centerX + 3, livingY, centerZ + 2,
                Blocks.RED_BED.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.HEAD));

        placeIfInChunk(world, chunkBox, pos, centerX + 4, livingY, centerZ - 4,
                Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.WEST));
        placeIfInChunk(world, chunkBox, pos, centerX + 4, livingY, centerZ - 3, Blocks.CRAFTING_TABLE.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX + 4, livingY, centerZ - 2,
                Blocks.FURNACE.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST));
        placeIfInChunk(world, chunkBox, pos, centerX + 4, livingY, centerZ - 1,
                Blocks.TRAPPED_CHEST.getDefaultState().with(ChestBlock.FACING, Direction.WEST));

        placeIfInChunk(world, chunkBox, pos, centerX - 4, livingY, centerZ - 4, Blocks.BOOKSHELF.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX - 3, livingY, centerZ - 4, Blocks.CHISELED_BOOKSHELF.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX - 2, livingY, centerZ - 4, Blocks.BOOKSHELF.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX - 1, livingY, centerZ - 4, Blocks.CHISELED_BOOKSHELF.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX, livingY, centerZ - 4, Blocks.BOOKSHELF.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX + 1, livingY, centerZ - 4, Blocks.POTTED_WITHER_ROSE.getDefaultState());

        placeIfInChunk(world, chunkBox, pos, centerX - 4, livingY, centerZ, Blocks.ANVIL.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX - 1, livingY, centerZ, Blocks.POLISHED_DEEPSLATE.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX - 1, livingY + 1, centerZ,
                Blocks.CANDLE.getDefaultState().with(CandleBlock.CANDLES, 3).with(CandleBlock.LIT, true));
        placeIfInChunk(world, chunkBox, pos, centerX - 1, livingY, centerZ + 1,
                Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH));

        placeIfInChunk(world, chunkBox, pos, centerX, livingY, centerZ - 1, Blocks.GRAY_CARPET.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX + 1, livingY, centerZ - 1, Blocks.GRAY_CARPET.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX, livingY, centerZ, Blocks.BLACK_CARPET.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX + 1, livingY, centerZ, Blocks.GRAY_CARPET.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX, livingY, centerZ + 1, Blocks.GRAY_CARPET.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX + 1, livingY, centerZ + 1, Blocks.BLACK_CARPET.getDefaultState());

        placeIfInChunk(world, chunkBox, pos, centerX - 5, livingY, centerZ - 2, Blocks.SOUL_LANTERN.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX + 3, livingY, centerZ - 4, Blocks.LANTERN.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX - 4, livingY, centerZ + 4, Blocks.LANTERN.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX + 2, livingY, centerZ + 4, Blocks.SOUL_LANTERN.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX, towerTopY, centerZ - TOWER_NORTH_SPAN, Blocks.SOUL_LANTERN.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX, towerTopY, centerZ + TOWER_SOUTH_SPAN, Blocks.SOUL_LANTERN.getDefaultState());
    }

    private void spawnVillager(StructureWorldAccess world, BlockBox chunkBox, int x, int y, int z) {
        if (!chunkBox.contains(x, y, z)) {
            return;
        }
        VillagerEntity villager = EntityType.VILLAGER.create(world.toServerWorld(), SpawnReason.STRUCTURE);
        if (villager == null) {
            return;
        }
        villager.refreshPositionAndAngles(x + 0.5, y, z + 0.5, 180.0F, 0.0F);
        AncientMazeVillagers.configureTowerVillager(villager, world.toServerWorld());
        world.spawnEntityAndPassengers(villager);
    }

    private void placeIfInChunk(StructureWorldAccess world, BlockBox chunkBox, BlockPos.Mutable pos, int x, int y, int z, BlockState state) {
        if (chunkBox.contains(x, y, z)) {
            world.setBlockState(pos.set(x, y, z), state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        }
    }

    private BlockState sculkFeatureState(AncientMazePlan plan, int localX, int localZ) {
        if (localX < 0 || localZ < 0 || localX >= plan.totalWidth() || localZ >= plan.totalWidth()) {
            return null;
        }

        long hash = sculkFeatureHash(localX, localZ);
        int roll = (int) (hash & 1023L);
        if (roll < 12) {
            return Blocks.SCULK_SHRIEKER.getDefaultState().with(SculkShriekerBlock.CAN_SUMMON, true);
        }
        if (roll < 44) {
            return Blocks.SCULK_SENSOR.getDefaultState();
        }
        if (roll < 52) {
            return Blocks.SCULK_CATALYST.getDefaultState();
        }
        return null;
    }

    private BlockState sculkMazeVeinState(AncientMazePlan plan, int localX, int localZ, int y, int ceilingY) {
        if (localX < 0 || localZ < 0 || localX >= plan.totalWidth() || localZ >= plan.totalWidth()) {
            return null;
        }

        BlockState state = Blocks.SCULK_VEIN.getDefaultState();
        boolean hasFace = false;
        if (y == ceilingY - 1) {
            state = state.with(MultifaceBlock.getProperty(Direction.UP), true);
            hasFace = true;
        }
        for (Direction direction : Direction.Type.HORIZONTAL) {
            int neighborX = localX + direction.getOffsetX();
            int neighborZ = localZ + direction.getOffsetZ();
            if (isMazeOpenColumn(plan, neighborX, neighborZ, corridorWidth)) {
                continue;
            }
            if ((sculkVeinHash(localX, localZ, y, direction) & 255L) >= SCULK_WALL_VEIN_CHANCE) {
                continue;
            }
            state = state.with(MultifaceBlock.getProperty(direction), true);
            hasFace = true;
        }
        return hasFace ? state : null;
    }

    private BlockState sculkShaftVeinState(int dx, int dz, int y) {
        BlockState state = Blocks.SCULK_VEIN.getDefaultState();
        boolean hasFace = false;
        if (dx == -TOWER_WEST_SPAN + 1) {
            state = withShaftVeinFace(state, dx, dz, y, Direction.WEST);
            hasFace = MultifaceBlock.hasDirection(state, Direction.WEST);
        }
        if (dx == TOWER_EAST_SPAN - 1) {
            state = withShaftVeinFace(state, dx, dz, y, Direction.EAST);
            hasFace = hasFace || MultifaceBlock.hasDirection(state, Direction.EAST);
        }
        if (dz == -TOWER_NORTH_SPAN + 1) {
            state = withShaftVeinFace(state, dx, dz, y, Direction.NORTH);
            hasFace = hasFace || MultifaceBlock.hasDirection(state, Direction.NORTH);
        }
        if (dz == TOWER_SOUTH_SPAN - 1) {
            state = withShaftVeinFace(state, dx, dz, y, Direction.SOUTH);
            hasFace = hasFace || MultifaceBlock.hasDirection(state, Direction.SOUTH);
        }
        return hasFace ? state : null;
    }

    private BlockState withShaftVeinFace(BlockState state, int dx, int dz, int y, Direction direction) {
        if ((sculkVeinHash(dx, dz, y, direction) & 255L) >= SCULK_SHAFT_VEIN_CHANCE) {
            return state;
        }
        return state.with(MultifaceBlock.getProperty(direction), true);
    }

    private long sculkFeatureHash(int localX, int localZ) {
        long value = mazeSeed ^ ((long) localX * 0x9E3779B97F4A7C15L);
        value ^= (long) localZ * 0xC2B2AE3D27D4EB4FL;
        return mixSculkHash(value);
    }

    private long sculkVeinHash(int localX, int localZ, int y, Direction direction) {
        long value = mazeSeed ^ ((long) localX * 0x9E3779B97F4A7C15L);
        value ^= (long) localZ * 0xC2B2AE3D27D4EB4FL;
        value ^= (long) y * 0x165667B19E3779F9L;
        value ^= (long) direction.ordinal() * 0x85EBCA77C2B2AE63L;
        return mixSculkHash(value);
    }

    private static long mixSculkHash(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static boolean isWindow(int dx, int dz, int yFromSurface) {
        boolean basementWindow = yFromSurface >= 3 && yFromSurface <= 4;
        boolean upperWindow = yFromSurface >= 11 && yFromSurface <= 13;
        if (!basementWindow && !upperWindow) {
            return false;
        }
        return ((dz == -TOWER_NORTH_SPAN || dz == TOWER_SOUTH_SPAN) && Math.abs(dx) <= 1)
                || ((dx == -TOWER_WEST_SPAN || dx == TOWER_EAST_SPAN) && Math.abs(dz) <= 1);
    }

    private static BlockState towerWindowBarsState(int dx, int dz) {
        BlockState state = Blocks.IRON_BARS.getDefaultState();
        if (dz == -TOWER_NORTH_SPAN || dz == TOWER_SOUTH_SPAN) {
            return state
                    .with(HorizontalConnectingBlock.WEST, true)
                    .with(HorizontalConnectingBlock.EAST, true);
        }
        if (dx == -TOWER_WEST_SPAN || dx == TOWER_EAST_SPAN) {
            return state
                    .with(HorizontalConnectingBlock.NORTH, true)
                    .with(HorizontalConnectingBlock.SOUTH, true);
        }
        return state;
    }

    private static boolean isUpperFloorHatch(int dx, int dz) {
        return dx == -TOWER_WEST_SPAN + 1 && dz == 3;
    }

    private static boolean isWallBand(int yFromSurface) {
        return yFromSurface == 1 || yFromSurface == BASEMENT_DEPTH || yFromSurface == 10 || yFromSurface == 16;
    }

    private static BlockState towerWallState(
            boolean corner,
            boolean wallBand,
            int dx,
            int dz,
            int yFromSurface,
            BlockState brick,
            BlockState crackedBrick,
            BlockState chiseled,
            BlockState polished,
            BlockState tile
    ) {
        if (corner) {
            return polished;
        }
        if (wallBand) {
            return tile;
        }
        if ((Math.abs(dx) + Math.abs(dz) + yFromSurface) % 11 == 0) {
            return crackedBrick;
        }
        if (yFromSurface == 8 && (Math.abs(dx) <= 2 || Math.abs(dz) <= 2)) {
            return chiseled;
        }
        return brick;
    }

    private void placeCenterChest(StructureWorldAccess world, BlockBox chunkBox, AncientMazePlan plan) {
        BlockBox roomBox = centerPrizeRoomBox(plan);
        int centerX = originX + plan.centerBlockOffset();
        BlockPos chestPos = new BlockPos(
                centerX - 1,
                floorY + AncientMazeStructure.WALKABLE_Y_OFFSET_FROM_FLOOR,
                roomBox.getMaxZ() - 1
        );
        if (!chunkBox.contains(chestPos)) {
            return;
        }

        BlockState chestState = Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH);
        world.setBlockState(chestPos, chestState, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        BlockEntity blockEntity = world.getBlockEntity(chestPos);
        if (blockEntity instanceof LootableContainerBlockEntity lootable) {
            lootable.setLootTable(CENTER_LOOT_TABLE);
            lootable.setLootTableSeed(mazeSeed);
        }
    }

    private void placeCenterPickaxeFrame(StructureWorldAccess world, BlockBox chunkBox, AncientMazePlan plan) {
        BlockBox roomBox = centerPrizeRoomBox(plan);
        int centerX = originX + plan.centerBlockOffset();
        BlockPos framePos = new BlockPos(
                centerX + 2,
                floorY + AncientMazeStructure.WALKABLE_Y_OFFSET_FROM_FLOOR + 2,
                roomBox.getMaxZ() - 1
        );
        if (!chunkBox.contains(framePos)) {
            return;
        }

        ItemFrameEntity frame = new ItemFrameEntity(world.toServerWorld(), framePos, Direction.NORTH);
        frame.setHeldItemStack(new ItemStack(ModItems.ANCIENT_PICKAXE), false);
        world.spawnEntityAndPassengers(frame);
    }

    private void placeCenterPrizeRoomLighting(StructureWorldAccess world, BlockBox chunkBox, BlockBox roomBox, AncientMazePlan plan) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int centerX = originX + plan.centerBlockOffset();
        int centerZ = originZ + plan.centerBlockOffset();
        int y = floorY + AncientMazeStructure.WALKABLE_Y_OFFSET_FROM_FLOOR;

        placeIfInChunk(world, chunkBox, pos, roomBox.getMinX() + 3, y, roomBox.getMinZ() + 3, Blocks.SOUL_LANTERN.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, roomBox.getMaxX() - 3, y, roomBox.getMinZ() + 3, Blocks.SOUL_LANTERN.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, roomBox.getMinX() + 3, y, roomBox.getMaxZ() - 3, Blocks.SOUL_LANTERN.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, roomBox.getMaxX() - 3, y, roomBox.getMaxZ() - 3, Blocks.SOUL_LANTERN.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX - 5, y, centerZ, Blocks.LANTERN.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX + 4, y, centerZ, Blocks.LANTERN.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX, y, centerZ - 5, Blocks.SOUL_LANTERN.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX, y, centerZ + 4, Blocks.SOUL_LANTERN.getDefaultState());
    }

    private boolean shouldPlaceCenterPrizeRoomCarpet(AncientMazePlan plan, BlockBox roomBox, int x, int z) {
        if (x <= roomBox.getMinX() || x >= roomBox.getMaxX() || z <= roomBox.getMinZ() || z >= roomBox.getMaxZ()) {
            return false;
        }
        int centerX = originX + plan.centerBlockOffset();
        if (z >= roomBox.getMaxZ() - 2 && x >= centerX - 3 && x <= centerX + 3) {
            return false;
        }
        if (z <= roomBox.getMinZ() + 1 && x >= centerPrizeRoomDoorMinX(plan) && x <= centerPrizeRoomDoorMaxX(plan)) {
            return false;
        }
        return true;
    }

    private boolean isCenterPrizeRoomFloorLight(AncientMazePlan plan, BlockBox roomBox, int x, int z) {
        if (!shouldPlaceCenterPrizeRoomCarpet(plan, roomBox, x, z)) {
            return false;
        }
        int centerX = originX + plan.centerBlockOffset();
        int centerZ = originZ + plan.centerBlockOffset();
        return Math.floorMod(x - centerX, 6) == 0 && Math.floorMod(z - centerZ, 6) == 0;
    }

    private static BlockState centerPrizeRoomCarpetState(int x, int z) {
        int pattern = Math.floorMod(x * 31 + z * 17, 9);
        if (pattern == 0) {
            return Blocks.CYAN_CARPET.getDefaultState();
        }
        return (x + z) % 2 == 0 ? Blocks.BLACK_CARPET.getDefaultState() : Blocks.GRAY_CARPET.getDefaultState();
    }

    private BlockBox centerPrizeRoomBox(AncientMazePlan plan) {
        int centerX = originX + plan.centerBlockOffset();
        int centerZ = originZ + plan.centerBlockOffset();
        int negativeRadius = centerPrizeRoomNegativeRadius();
        int positiveRadius = negativeRadius - 1;
        return new BlockBox(
                centerX - negativeRadius,
                floorY,
                centerZ - negativeRadius,
                centerX + positiveRadius,
                floorY + AncientMazePlan.totalHeight(wallHeight) - 1,
                centerZ + positiveRadius
        );
    }

    private int centerPrizeRoomNegativeRadius() {
        return corridorWidth + corridorWidth / 2 + wallThickness * 2;
    }

    private boolean isCenterPrizeRoomDoor(AncientMazePlan plan, BlockBox roomBox, int x, int z) {
        return z == roomBox.getMinZ()
                && x >= centerPrizeRoomDoorMinX(plan)
                && x <= centerPrizeRoomDoorMaxX(plan);
    }

    private int centerPrizeRoomDoorMinX(AncientMazePlan plan) {
        return originX + plan.centerBlockOffset() - corridorWidth / 2;
    }

    private int centerPrizeRoomDoorMaxX(AncientMazePlan plan) {
        return centerPrizeRoomDoorMinX(plan) + corridorWidth - 1;
    }

    private AncientMazePlan plan() {
        if (cachedPlan == null) {
            PlanKey key = new PlanKey(mazeSeed, cellCount, corridorWidth, wallThickness);
            synchronized (PLAN_CACHE) {
                cachedPlan = PLAN_CACHE.computeIfAbsent(key, planKey -> AncientMazePlan.generate(
                        planKey.mazeSeed(),
                        planKey.cellCount(),
                        planKey.corridorWidth(),
                        planKey.wallThickness()
                ));
            }
        }
        return cachedPlan;
    }

    static int towerCenterOffsetX(AncientMazePlan plan) {
        return plan.entranceBlockOffsetX();
    }

    static int towerCenterOffsetZ(AncientMazePlan plan) {
        return plan.entranceBlockOffsetZ() - OUTER_SHELL_THICKNESS - TOWER_SOUTH_SPAN - 2;
    }

    static int towerCenterOffsetX(int cellCount, int corridorWidth, int wallThickness) {
        int normalizedCellCount = AncientMazeStructure.normalizeCellCount(cellCount);
        int centerCell = normalizedCellCount / 2;
        return wallThickness + centerCell * (corridorWidth + wallThickness) + corridorWidth / 2;
    }

    static int towerCenterOffsetZ(int wallThickness) {
        return wallThickness / 2 - OUTER_SHELL_THICKNESS - TOWER_SOUTH_SPAN - 2;
    }

    static AncientMazePiece marker(
            int originX,
            int floorY,
            int originZ,
            int entranceX,
            int entranceZ,
            int cellCount,
            int corridorWidth,
            int wallThickness,
            int wallHeight,
            int topY,
            long mazeSeed,
            int surfaceY
    ) {
        return new AncientMazePiece(originX, floorY, originZ, cellCount, corridorWidth, wallThickness, wallHeight, topY, mazeSeed, surfaceY,
                new BlockBox(entranceX, floorY, entranceZ, entranceX, floorY, entranceZ), true);
    }

    static BlockBox sliceBoxForChunk(
            int originX,
            int floorY,
            int originZ,
            int cellCount,
            int corridorWidth,
            int wallThickness,
            int wallHeight,
            int topY,
            ChunkPos chunkPos
    ) {
        BlockBox fullBox = createBoundingBox(originX, floorY, originZ, cellCount, corridorWidth, wallThickness, wallHeight, topY);
        int chunkMinX = chunkPos.getStartX();
        int chunkMinZ = chunkPos.getStartZ();
        int chunkMaxX = chunkMinX + 15;
        int chunkMaxZ = chunkMinZ + 15;
        if (!fullBox.intersectsXZ(chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ)) {
            return null;
        }
        return new BlockBox(
                Math.max(fullBox.getMinX(), chunkMinX),
                fullBox.getMinY(),
                Math.max(fullBox.getMinZ(), chunkMinZ),
                Math.min(fullBox.getMaxX(), chunkMaxX),
                fullBox.getMaxY(),
                Math.min(fullBox.getMaxZ(), chunkMaxZ)
        );
    }

    static boolean towerIntersectsChunk(
            int originX,
            int originZ,
            int cellCount,
            int corridorWidth,
            int wallThickness,
            ChunkPos chunkPos
    ) {
        int centerX = originX + towerCenterOffsetX(cellCount, corridorWidth, wallThickness);
        int centerZ = originZ + towerCenterOffsetZ(wallThickness);
        int chunkMinX = chunkPos.getStartX();
        int chunkMinZ = chunkPos.getStartZ();
        int chunkMaxX = chunkMinX + 15;
        int chunkMaxZ = chunkMinZ + 15;
        return chunkMaxX >= centerX - TOWER_WEST_SPAN
                && chunkMinX <= centerX + TOWER_EAST_SPAN
                && chunkMaxZ >= centerZ - TOWER_NORTH_SPAN
                && chunkMinZ <= centerZ + TOWER_SOUTH_SPAN;
    }

    static boolean isMazeOpenColumn(AncientMazePlan plan, int localX, int localZ, int corridorWidth) {
        int width = plan.totalWidth();
        if (localX >= 0 && localZ >= 0 && localX < width && localZ < width) {
            return plan.isOpenBlock(localX, localZ);
        }
        return localX >= entranceMinX(plan)
                && localX <= entranceMaxX(plan, corridorWidth)
                && localZ >= -OUTER_SHELL_THICKNESS
                && localZ < 0;
    }

    private static int entranceMinX(AncientMazePlan plan) {
        return plan.entranceBlockOffsetX() - plan.corridorWidth() / 2;
    }

    private static int entranceMaxX(AncientMazePlan plan, int corridorWidth) {
        return entranceMinX(plan) + corridorWidth - 1;
    }

    private BlockBox mazeBox(AncientMazePlan plan) {
        int width = plan.totalWidth();
        return new BlockBox(
                originX - OUTER_SHELL_THICKNESS,
                floorY,
                originZ - OUTER_SHELL_THICKNESS,
                originX + width + OUTER_SHELL_THICKNESS - 1,
                floorY + AncientMazePlan.totalHeight(wallHeight) - 1,
                originZ + width + OUTER_SHELL_THICKNESS - 1
        );
    }

    private static BlockBox towerBox(int centerX, int centerZ, int floorY, int towerTopY) {
        return new BlockBox(
                centerX - TOWER_WEST_SPAN,
                floorY,
                centerZ - TOWER_NORTH_SPAN,
                centerX + TOWER_EAST_SPAN,
                towerTopY,
                centerZ + TOWER_SOUTH_SPAN
        );
    }

    private static BlockBox createBoundingBox(
            int originX,
            int floorY,
            int originZ,
            int cellCount,
            int corridorWidth,
            int wallThickness,
            int wallHeight,
            int topY
    ) {
        int normalizedCellCount = AncientMazeStructure.normalizeCellCount(cellCount);
        int width = AncientMazePlan.totalWidth(normalizedCellCount, corridorWidth, wallThickness);
        int entranceCenterX = towerCenterOffsetX(normalizedCellCount, corridorWidth, wallThickness);
        int towerCenterZ = towerCenterOffsetZ(wallThickness);
        int minX = Math.min(originX - OUTER_SHELL_THICKNESS, originX + entranceCenterX - TOWER_WEST_SPAN);
        int minZ = Math.min(originZ - OUTER_SHELL_THICKNESS, originZ + towerCenterZ - TOWER_NORTH_SPAN);
        int maxX = Math.max(originX + width + OUTER_SHELL_THICKNESS - 1, originX + entranceCenterX + TOWER_EAST_SPAN);
        int maxZ = Math.max(originZ + width + OUTER_SHELL_THICKNESS - 1, originZ + towerCenterZ + TOWER_SOUTH_SPAN);
        return new BlockBox(minX, floorY, minZ, maxX, topY, maxZ);
    }

    static boolean footprintIntersectsChunk(
            int originX,
            int originZ,
            int cellCount,
            int corridorWidth,
            int wallThickness,
            int wallHeight,
            ChunkPos chunkPos,
            int margin
    ) {
        BlockBox box = createBoundingBox(originX, 0, originZ, cellCount, corridorWidth, wallThickness, wallHeight, 1);
        int chunkMinX = chunkPos.getStartX();
        int chunkMinZ = chunkPos.getStartZ();
        int chunkMaxX = chunkMinX + 15;
        int chunkMaxZ = chunkMinZ + 15;
        return chunkMaxX >= box.getMinX() - margin
                && chunkMinX <= box.getMaxX() + margin
                && chunkMaxZ >= box.getMinZ() - margin
                && chunkMinZ <= box.getMaxZ() + margin;
    }

    private record PlanKey(long mazeSeed, int cellCount, int corridorWidth, int wallThickness) {
    }
}
