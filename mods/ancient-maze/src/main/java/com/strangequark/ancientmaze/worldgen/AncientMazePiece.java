package com.strangequark.ancientmaze.worldgen;

import com.strangequark.ancientmaze.AncientMazeMod;
import net.minecraft.block.Block;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.SignBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.ErrorReporter;
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
        generateEntranceApproach(world, chunkBox, plan);
        generateTower(world, chunkBox, plan);
        placeCenterChest(world, chunkBox, plan);
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
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int ceilingY = floorY + wallHeight + 1;

        for (int x = minX; x <= maxX; x++) {
            int localX = x - originX;
            for (int z = minZ; z <= maxZ; z++) {
                int localZ = z - originZ;
                boolean openColumn = isMazeOpenColumn(plan, localX, localZ, corridorWidth);
                for (int y = minY; y <= maxY; y++) {
                    boolean shell = y == floorY || y == ceilingY;
                    BlockState state = shell || !openColumn ? bedrock : air;
                    world.setBlockState(pos.set(x, y, z), state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                }
            }
        }
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
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = Math.max(approachBox.getMinX(), chunkBox.getMinX()); x <= Math.min(approachBox.getMaxX(), chunkBox.getMaxX()); x++) {
            for (int z = Math.max(approachBox.getMinZ(), chunkBox.getMinZ()); z <= Math.min(approachBox.getMaxZ(), chunkBox.getMaxZ()); z++) {
                for (int y = Math.max(approachBox.getMinY(), chunkBox.getMinY()); y <= Math.min(approachBox.getMaxY(), chunkBox.getMaxY()); y++) {
                    boolean floorOrCeiling = y == floorY || y == maxY;
                    boolean sideWall = x == minX - 1 || x == maxX + 1;
                    boolean passage = x >= minX && x <= maxX && y > floorY && y < maxY;
                    BlockState state = floorOrCeiling || sideWall ? brick : passage ? air : brick;
                    world.setBlockState(pos.set(x, y, z), state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
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
                        state = lowerMazeDoor || (!shaftFloor && !wall) ? air : bedrock;
                    } else if (basementFloor) {
                        state = dx == 0 && dz == 0 ? air : bedrock;
                    } else if (roofDeck) {
                        state = tile;
                    } else if (parapetBase || battlement) {
                        state = tile;
                    } else if (y >= towerTopY - 1) {
                        state = air;
                    } else if (wall && !surfaceDoor) {
                        state = window ? Blocks.IRON_BARS.getDefaultState() : towerWallState(corner, wallBand, dx, dz, yFromSurface, brick, crackedBrick, chiseled, polished, tile);
                    } else if (upperFloor) {
                        state = upperFloorHatch ? air : polished;
                    }
                    world.setBlockState(pos.set(x, y, z), state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
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
        placeWarningSign(world, chunkBox, pos, centerX, basementFloorY + 1, centerZ + 2);

        for (int y = basementFloorY + 1; y <= upperFloorY + 1; y++) {
            placeIfInChunk(world, chunkBox, pos, centerX - TOWER_WEST_SPAN + 1, y, centerZ + 3,
                    Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.EAST));
        }

        placeIfInChunk(world, chunkBox, pos, centerX - 3, upperFloorY + 1, centerZ + 3,
                Blocks.RED_BED.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST).with(BedBlock.PART, BedPart.FOOT));
        placeIfInChunk(world, chunkBox, pos, centerX - 2, upperFloorY + 1, centerZ + 3,
                Blocks.RED_BED.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST).with(BedBlock.PART, BedPart.HEAD));
        placeIfInChunk(world, chunkBox, pos, centerX + 4, upperFloorY + 1, centerZ - 3,
                Blocks.BARREL.getDefaultState().with(BarrelBlock.FACING, Direction.UP));
        placeIfInChunk(world, chunkBox, pos, centerX + 4, upperFloorY + 1, centerZ - 2, Blocks.CRAFTING_TABLE.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX + 4, upperFloorY + 1, centerZ - 1,
                Blocks.FURNACE.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST));
        placeIfInChunk(world, chunkBox, pos, centerX - 5, upperFloorY + 1, centerZ - 3, Blocks.BOOKSHELF.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX - 5, upperFloorY + 1, centerZ - 2, Blocks.BOOKSHELF.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX - 5, upperFloorY + 1, centerZ - 1, Blocks.POTTED_POPPY.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX - 1, upperFloorY + 1, centerZ - 4, Blocks.LANTERN.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX + 2, upperFloorY + 1, centerZ - 4, Blocks.LANTERN.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX - 1, upperFloorY + 1, centerZ + 4, Blocks.COMPOSTER.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX + 1, upperFloorY + 1, centerZ + 2, Blocks.OAK_FENCE.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX + 1, upperFloorY + 2, centerZ + 2, Blocks.OAK_PRESSURE_PLATE.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX, towerTopY, centerZ - TOWER_NORTH_SPAN, Blocks.SOUL_LANTERN.getDefaultState());
        placeIfInChunk(world, chunkBox, pos, centerX, towerTopY, centerZ + TOWER_SOUTH_SPAN, Blocks.SOUL_LANTERN.getDefaultState());
    }

    private void placeWarningSign(StructureWorldAccess world, BlockBox chunkBox, BlockPos.Mutable pos, int x, int y, int z) {
        if (!chunkBox.contains(x, y, z)) {
            return;
        }
        BlockPos signPos = pos.set(x, y, z).toImmutable();
        world.setBlockState(signPos, Blocks.OAK_SIGN.getDefaultState().with(SignBlock.ROTATION, 0), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        BlockEntity blockEntity = world.getBlockEntity(signPos);
        if (blockEntity instanceof SignBlockEntity sign) {
            SignText warning = new SignText()
                    .withColor(DyeColor.RED)
                    .withGlowing(true)
                    .withMessage(1, Text.literal("WARNING"));
            NbtWriteView writeView = NbtWriteView.create(ErrorReporter.EMPTY, world.getRegistryManager());
            writeView.put("front_text", SignText.CODEC, warning);
            writeView.put("back_text", SignText.CODEC, new SignText());
            sign.readComponentlessData(NbtReadView.create(ErrorReporter.EMPTY, world.getRegistryManager(), writeView.getNbt()));
        }
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
        villager.setPersistent();
        world.spawnEntityAndPassengers(villager);
    }

    private void placeIfInChunk(StructureWorldAccess world, BlockBox chunkBox, BlockPos.Mutable pos, int x, int y, int z, BlockState state) {
        if (chunkBox.contains(x, y, z)) {
            world.setBlockState(pos.set(x, y, z), state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        }
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
        BlockPos chestPos = new BlockPos(originX + plan.centerBlockOffset(), floorY + 1, originZ + plan.centerBlockOffset());
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

    private record PlanKey(long mazeSeed, int cellCount, int corridorWidth, int wallThickness) {
    }
}
