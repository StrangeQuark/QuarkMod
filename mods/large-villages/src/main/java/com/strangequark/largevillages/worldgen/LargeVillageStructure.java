package com.strangequark.largevillages.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.JigsawBlock;
import net.minecraft.structure.JigsawJunction;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructureLiquidSettings;
import net.minecraft.structure.StructurePiecesCollector;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class LargeVillageStructure extends Structure {
    private static final int ROAD_TILE_LENGTH = 8;
    private static final int ROAD_HALF_WIDTH = 2;
    private static final int MAX_ROAD_SEGMENT_LENGTH = 40;
    private static final int NORMAL_VILLAGE_RADIUS = 80;
    private static final int LARGE_VILLAGE_RADIUS = 200;
    private static final int START_TERRAIN_RADIUS = 64;
    private static final int LOCATE_TERRAIN_RADIUS = 48;
    private static final int MAX_ROAD_SEGMENTS = 100;
    private static final int LOT_CLEARANCE_FROM_TOWN_HALL = 42;
    private static final int CORE_ROAD_OFFSET = 40;
    private static final int DISTRICT_RING_SPACING = 40;
    private static final int INNER_LOT_SPACING = 13;
    private static final int OUTER_LOT_SPACING = 16;
    private static final int LOT_SETBACK_MIN = 11;
    private static final int LOT_SETBACK_RANDOM = 5;
    private static final int MAX_BUILDING_DISTANCE_FROM_ROAD = 10;
    private static final int ROAD_CONFLICT_PADDING = 2;
    private static final int MIN_ACCEPTED_BUILDINGS = 48;
    private static final int LOT_TARGET_MIN_BUFFER = 16;
    private static final int LOT_TARGET_BUFFER_DIVISOR = 4;
    private static final int VANILLA_FARM_CHANCE_PERCENT = 5;
    private static final int VANILLA_FARM_PATCH_DIVISOR = 30;

    private static final String TOWN_HALL = "quarkmod:village/large/town_hall";
    private static final String CUSTOM_FARM = "quarkmod:village/large/custom_farm";
    private static final String DESERT_CUSTOM_FARM = "quarkmod:village/large/desert_custom_farm";
    private static final String ROAD_NORTH_SOUTH = "quarkmod:village/large/road_straight";
    private static final String ROAD_EAST_WEST = "quarkmod:village/large/road_east_west";
    private static final String ROAD_CROSSING = "quarkmod:village/large/road_crossing";
    private static final String DESERT_ROAD_NORTH_SOUTH = "quarkmod:village/large/desert_road_straight";
    private static final String DESERT_ROAD_EAST_WEST = "quarkmod:village/large/desert_road_east_west";
    private static final String DESERT_ROAD_CROSSING = "quarkmod:village/large/desert_road_crossing";
    private static final String VILLAGER = "quarkmod:village/large/villager";
    private static final Identifier BUILDING_ENTRANCE = Identifier.of("minecraft", "building_entrance");

    public static final MapCodec<LargeVillageStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            configCodecBuilder(instance),
            Codec.STRING.fieldOf("village_style").forGetter(structure -> structure.villageStyle),
            Codec.intRange(1, 512).optionalFieldOf("min_radius", NORMAL_VILLAGE_RADIUS).forGetter(structure -> structure.minRadius),
            Codec.intRange(1, 512).optionalFieldOf("max_radius", LARGE_VILLAGE_RADIUS).forGetter(structure -> structure.maxRadius),
            Codec.intRange(1, 512).optionalFieldOf("min_buildings", 72).forGetter(structure -> structure.minBuildings),
            Codec.intRange(1, 512).optionalFieldOf("max_buildings", 140).forGetter(structure -> structure.maxBuildings)
    ).apply(instance, LargeVillageStructure::new));

    private final String villageStyle;
    private final int minRadius;
    private final int maxRadius;
    private final int minBuildings;
    private final int maxBuildings;

    public LargeVillageStructure(Config config, String villageStyle, int minRadius, int maxRadius, int minBuildings, int maxBuildings) {
        super(config);
        this.villageStyle = villageStyle;
        this.minRadius = Math.min(minRadius, maxRadius);
        this.maxRadius = Math.max(minRadius, maxRadius);
        this.minBuildings = Math.min(minBuildings, maxBuildings);
        this.maxBuildings = Math.max(minBuildings, maxBuildings);
    }

    @Override
    protected Optional<StructurePosition> getStructurePosition(Context context) {
        return getStructurePosition(context, new TerrainSampler(context));
    }

    Optional<StructurePosition> getStructurePosition(Context context, TerrainSampler terrain) {
        VillageStart start = getVillageStart(context, terrain);
        return start == null
                ? Optional.empty()
                : Optional.of(new StructurePosition(start.center(), collector -> createPlan(context, terrain, start.center(), true)
                .ifPresent(plan -> placePlan(collector, plan))));
    }

    boolean canGenerateAt(Context context, TerrainSampler terrain) {
        VillageStart start = getVillageStart(context, terrain);
        return start != null && createPlan(context, terrain, start.center(), false).isPresent();
    }

    private VillageStart getVillageStart(Context context, TerrainSampler terrain) {
        ChunkPos chunkPos = context.chunkPos();
        int x = chunkPos.getCenterX();
        int z = chunkPos.getCenterZ();

        int y = terrain.getPlacementY(x, z);
        BlockPos center = new BlockPos(x, y, z);

        if (!isCenterBiomeValid(context, center)) {
            return null;
        }

        if (!isLocateAreaSuitable(terrain, x, z, y)) {
            return null;
        }

        return new VillageStart(center);
    }

    @Override
    public StructureType<?> getType() {
        return ModStructureTypes.LARGE_VILLAGE;
    }

    private Optional<VillagePlan> createPlan(Context context, TerrainSampler terrain, BlockPos center, boolean includeVillagers) {
        Random random = context.random();
        StructureTemplateManager templateManager = context.structureTemplateManager();
        Map<BuildingInfoKey, BuildingPlacementInfo> buildingInfoCache = new HashMap<>();
        int radius = chooseRadius(random);
        int targetBuildings = chooseTargetBuildingCount(radius, random);
        List<PiecePlan> pieces = new ArrayList<>();
        List<BlockBox> occupied = new ArrayList<>();

        if (!isAreaSuitable(terrain, center.getX(), center.getZ(), START_TERRAIN_RADIUS, 10, 16)) {
            return Optional.empty();
        }

        if (!addCenteredPiece(terrain, templateManager, pieces, occupied, TOWN_HALL, center.getX(), center.getZ(),
                BlockRotation.NONE, StructurePool.Projection.RIGID, false, 4, 10, 6)) {
            return Optional.empty();
        }

        Optional<VillageLayout> layout = generateVillageLayout(terrain, center, radius, targetBuildings, random);
        if (layout.isEmpty()) {
            return Optional.empty();
        }

        RoadTemplates roadTemplates = roadTemplatesForStyle(villageStyle);
        RoadAnchorIndex roadAnchors = addRoadPieces(terrain, templateManager, pieces, center, layout.get().roads(), roadTemplates, random);

        int requiredBuildings = addRequiredBuildings(terrain, templateManager, buildingInfoCache, pieces, occupied, center,
                layout.get().roads(), roadAnchors, layout.get().lots(), random);
        if (requiredBuildings < requiredBuildingsForStyle(villageStyle).length) {
            return Optional.empty();
        }

        int buildings = requiredBuildings + addBuildingPieces(terrain, templateManager, buildingInfoCache, pieces, occupied, center,
                layout.get().roads(), roadAnchors, layout.get().lots(), Math.max(0, targetBuildings - requiredBuildings), random);
        if (buildings < Math.min(minBuildings, MIN_ACCEPTED_BUILDINGS)) {
            return Optional.empty();
        }

        if (includeVillagers) {
            addVillagerPieces(terrain, templateManager, pieces, center, layout.get().lots(), targetBuildings, random);
        }
        return Optional.of(new VillagePlan(pieces));
    }

    private void placePlan(StructurePiecesCollector collector, VillagePlan plan) {
        for (PiecePlan piece : plan.pieces()) {
            PoolStructurePiece poolPiece = new PoolStructurePiece(piece.templateManager(), piece.element(), piece.pos(), piece.groundLevelDelta(),
                    piece.rotation(), piece.box(), StructureLiquidSettings.APPLY_WATERLOGGING);
            piece.junctions().forEach(poolPiece::addJunction);
            collector.addPiece(poolPiece);
        }
    }

    private int chooseRadius(Random random) {
        int minSteps = Math.max(4, divideRoundUp(minRadius, ROAD_TILE_LENGTH));
        int maxSteps = Math.max(minSteps, maxRadius / ROAD_TILE_LENGTH);
        return (minSteps + random.nextInt(maxSteps - minSteps + 1)) * ROAD_TILE_LENGTH;
    }

    private int chooseTargetBuildingCount(int radius, Random random) {
        int radiusSteps = Math.max(1, radius / 24);
        int target = 58 + radiusSteps * 5 + random.nextInt(radiusSteps * 5 + 1);
        return Math.min(maxBuildings, Math.max(minBuildings, target));
    }

    private Optional<VillageLayout> generateVillageLayout(TerrainSampler terrain, BlockPos center, int radius, int targetBuildings, Random random) {
        Set<Point> nodes = new HashSet<>();
        Set<RoadSegment> segments = new HashSet<>();
        List<Lot> lots = new ArrayList<>();
        Point origin = new Point(0, 0);
        nodes.add(origin);

        int layoutRadius = chooseLayoutRadius(radius, targetBuildings);
        int targetLots = chooseTargetLotCount(targetBuildings);
        List<Integer> rings = districtRings(layoutRadius);
        if (rings.isEmpty()) {
            return Optional.empty();
        }

        int coreSegments = addDistrictRing(terrain, center, radius, random, rings.get(0), true, targetLots, nodes, segments, lots);
        if (coreSegments < 6) {
            return Optional.empty();
        }

        for (int i = 1; i < rings.size(); i++) {
            int previousRing = rings.get(i - 1);
            int ring = rings.get(i);
            addRadialConnectors(terrain, center, radius, random, previousRing, ring, targetLots, nodes, segments, lots);
            addDistrictRing(terrain, center, radius, random, ring, false, targetLots, nodes, segments, lots);
            if (lots.size() >= targetLots) {
                break;
            }
        }

        List<RoadSegment> usefulSegments = pruneRoadsWithoutLots(segments, lots);
        List<Lot> usefulLots = lotsForRoads(usefulSegments, lots);
        Set<Point> usefulNodes = nodesForSegments(usefulSegments);
        if (usefulLots.size() < MIN_ACCEPTED_BUILDINGS || usefulSegments.size() < 8) {
            return Optional.empty();
        }

        return Optional.of(new VillageLayout(new RoadNetwork(usefulNodes, new HashSet<>(usefulSegments)), usefulLots));
    }

    private int chooseLayoutRadius(int radius, int targetBuildings) {
        int desiredRadius = CORE_ROAD_OFFSET + DISTRICT_RING_SPACING + targetBuildings / 2;
        int radiusCap = Math.max(CORE_ROAD_OFFSET, Math.min(radius, desiredRadius));
        return Math.max(CORE_ROAD_OFFSET, (radiusCap / ROAD_TILE_LENGTH) * ROAD_TILE_LENGTH);
    }

    private static int chooseTargetLotCount(int targetBuildings) {
        return targetBuildings + Math.max(LOT_TARGET_MIN_BUFFER, targetBuildings / LOT_TARGET_BUFFER_DIVISOR);
    }

    private static List<Integer> districtRings(int layoutRadius) {
        List<Integer> rings = new ArrayList<>();
        for (int ring = CORE_ROAD_OFFSET; ring <= layoutRadius; ring += DISTRICT_RING_SPACING) {
            rings.add(ring);
        }
        return rings;
    }

    private int addDistrictRing(
            TerrainSampler terrain,
            BlockPos center,
            int radius,
            Random random,
            int ring,
            boolean core,
            int targetLots,
            Set<Point> nodes,
            Set<RoadSegment> segments,
            List<Lot> lots
    ) {
        int added = 0;
        Point northWest = new Point(-ring, -ring);
        Point north = new Point(0, -ring);
        Point northEast = new Point(ring, -ring);
        Point east = new Point(ring, 0);
        Point southEast = new Point(ring, ring);
        Point south = new Point(0, ring);
        Point southWest = new Point(-ring, ring);
        Point west = new Point(-ring, 0);

        List<RoadEdge> edges = new ArrayList<>(List.of(
                new RoadEdge(northWest, north),
                new RoadEdge(north, northEast),
                new RoadEdge(northEast, east),
                new RoadEdge(east, southEast),
                new RoadEdge(south, southEast),
                new RoadEdge(southWest, south),
                new RoadEdge(southWest, west),
                new RoadEdge(west, northWest)
        ));
        shuffle(edges, random);
        for (RoadEdge edge : edges) {
            added += addPlannedRoadWithLots(terrain, center, radius, random, edge.start(), edge.end(), core, targetLots, nodes, segments, lots);
        }
        return added;
    }

    private int addRadialConnectors(
            TerrainSampler terrain,
            BlockPos center,
            int radius,
            Random random,
            int innerRing,
            int outerRing,
            int targetLots,
            Set<Point> nodes,
            Set<RoadSegment> segments,
            List<Lot> lots
    ) {
        int added = 0;
        added += addPlannedRoadWithLots(terrain, center, radius, random, new Point(0, -innerRing), new Point(0, -outerRing),
                false, targetLots, nodes, segments, lots);
        added += addPlannedRoadWithLots(terrain, center, radius, random, new Point(innerRing, 0), new Point(outerRing, 0),
                false, targetLots, nodes, segments, lots);
        added += addPlannedRoadWithLots(terrain, center, radius, random, new Point(0, innerRing), new Point(0, outerRing),
                false, targetLots, nodes, segments, lots);
        added += addPlannedRoadWithLots(terrain, center, radius, random, new Point(-innerRing, 0), new Point(-outerRing, 0),
                false, targetLots, nodes, segments, lots);
        return added;
    }

    private int addPlannedRoadWithLots(
            TerrainSampler terrain,
            BlockPos center,
            int radius,
            Random random,
            Point start,
            Point end,
            boolean core,
            int targetLots,
            Set<Point> nodes,
            Set<RoadSegment> segments,
            List<Lot> lots
    ) {
        if (!addPlannedRoadSegment(terrain, center, radius + DISTRICT_RING_SPACING, start, end, nodes, segments)) {
            return 0;
        }

        RoadSegment segment = new RoadSegment(start, end);
        int[] sides = core ? new int[]{outwardSideForSegment(segment)} : shuffledSides(new int[]{-1, 1}, random);
        int spacing = core ? INNER_LOT_SPACING : OUTER_LOT_SPACING;
        addLotsForPlannedSegment(center, radius, random, segment, sides, spacing, targetLots, lots);
        return 1;
    }

    private boolean addPlannedRoadSegment(
            TerrainSampler terrain,
            BlockPos center,
            int radius,
            Point start,
            Point end,
            Set<Point> nodes,
            Set<RoadSegment> segments
    ) {
        RoadDirection direction = directionBetween(start, end);
        int length = Math.abs(end.x() - start.x()) + Math.abs(end.z() - start.z());
        return length > 0 && tryAddRoadSegment(terrain, center, radius, start, direction, length, nodes, segments);
    }

    private void addLotsForPlannedSegment(
            BlockPos center,
            int radius,
            Random random,
            RoadSegment segment,
            int[] sides,
            int spacing,
            int targetLots,
            List<Lot> lots
    ) {
        int offset = 7 + random.nextInt(5);
        while (offset < segment.length() - 4 && lots.size() < targetLots) {
            Point roadPoint = segment.pointAlong(offset);
            for (int side : shuffledSides(sides, random)) {
                if (lots.size() >= targetLots) {
                    break;
                }
                addRoadsideLot(lots, center, radius, random, segment, roadPoint, side);
            }
            offset += Math.max(ROAD_TILE_LENGTH, spacing + random.nextInt(5) - 2);
        }
    }

    private void addRoadsideLot(
            List<Lot> lots,
            BlockPos center,
            int radius,
            Random random,
            RoadSegment segment,
            Point roadPoint,
            int side
    ) {
        int setback = LOT_SETBACK_MIN + random.nextInt(LOT_SETBACK_RANDOM);
        int lateral = random.nextInt(5) - 2;
        int x;
        int z;
        if (segment.isHorizontal()) {
            x = center.getX() + roadPoint.x() + lateral;
            z = center.getZ() + roadPoint.z() + side * setback;
        } else {
            x = center.getX() + roadPoint.x() + side * setback;
            z = center.getZ() + roadPoint.z() + lateral;
        }
        addLotIfValid(lots, center, radius, x, z, segment.direction(), side, roadPoint, segment);
    }

    private static int outwardSideForSegment(RoadSegment segment) {
        Point midpoint = segment.pointAlong(segment.length() / 2);
        if (segment.isHorizontal()) {
            return midpoint.z() >= 0 ? 1 : -1;
        }
        return midpoint.x() >= 0 ? 1 : -1;
    }

    private static int[] shuffledSides(int[] sides, Random random) {
        int[] shuffled = sides.clone();
        for (int i = shuffled.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int side = shuffled[i];
            shuffled[i] = shuffled[j];
            shuffled[j] = side;
        }
        return shuffled;
    }

    private static RoadDirection directionBetween(Point start, Point end) {
        if (start.x() == end.x()) {
            return start.z() < end.z() ? RoadDirection.SOUTH : RoadDirection.NORTH;
        }
        if (start.z() == end.z()) {
            return start.x() < end.x() ? RoadDirection.EAST : RoadDirection.WEST;
        }
        throw new IllegalArgumentException("Large village roads must be axis-aligned");
    }

    private boolean tryAddRoadSegment(
            TerrainSampler terrain,
            BlockPos center,
            int radius,
            Point start,
            RoadDirection direction,
            int length,
            Set<Point> nodes,
            Set<RoadSegment> segments
    ) {
        Point end = start.offset(direction, length);
        if (!isWithinRadius(end, radius + MAX_ROAD_SEGMENT_LENGTH / 2)) {
            return false;
        }

        RoadSegment segment = new RoadSegment(start, end);
        if (segments.contains(segment)) {
            return false;
        }
        if (hasRoadConflict(center, segment, segments)) {
            return false;
        }

        BlockBox footprint = segment.footprint(center);
        if (!isFootprintSuitable(terrain, footprint, 7, 4)) {
            return false;
        }

        nodes.add(start);
        nodes.add(end);
        segments.add(segment);
        return true;
    }

    private RoadAnchorIndex addRoadPieces(
            TerrainSampler terrain,
            StructureTemplateManager templateManager,
            List<PiecePlan> pieces,
            BlockPos center,
            RoadNetwork roads,
            RoadTemplates templates,
            Random random
    ) {
        Map<RoadSegment, Map<Direction, List<RoadAnchor>>> anchors = new HashMap<>();
        for (RoadSegment segment : roads.segments()) {
            String template = segment.isHorizontal() ? templates.eastWest() : templates.northSouth();
            for (RoadTile tile : segment.tiles(center)) {
                PiecePlan roadPiece = addPieceAt(templateManager, pieces, template, tile.templatePos(terrain), BlockRotation.NONE,
                        StructurePool.Projection.TERRAIN_MATCHING, false);
                addRoadAnchorsForPiece(templateManager, anchors, segment, roadPiece);
            }
        }

        Map<Point, Integer> degrees = roads.degrees();
        for (Point node : roads.nodes()) {
            int degree = degrees.getOrDefault(node, 0);
            if (degree >= 3 || (degree == 1 && random.nextInt(100) < 35)) {
                int x = center.getX() + node.x();
                int z = center.getZ() + node.z();
                BlockBox footprint = new BlockBox(x - ROAD_HALF_WIDTH, 0, z - ROAD_HALF_WIDTH,
                        x + ROAD_HALF_WIDTH, 0, z + ROAD_HALF_WIDTH);
                if (!isFootprintSuitable(terrain, footprint, 7, 4)) {
                    continue;
                }
                BlockPos pos = new BlockPos(footprint.getMinX(), getAveragePlacementY(terrain, footprint, 6), footprint.getMinZ());
                addPieceAt(templateManager, pieces, templates.crossing(), pos, BlockRotation.NONE, StructurePool.Projection.TERRAIN_MATCHING, false);
            }
        }

        return new RoadAnchorIndex(anchors);
    }

    private void addRoadAnchorsForPiece(
            StructureTemplateManager templateManager,
            Map<RoadSegment, Map<Direction, List<RoadAnchor>>> anchors,
            RoadSegment segment,
            PiecePlan piece
    ) {
        for (StructureTemplate.JigsawBlockInfo info : piece.element().getStructureBlockInfos(templateManager, piece.pos(),
                piece.rotation(), Random.create(0L))) {
            if (BUILDING_ENTRANCE.equals(info.name())) {
                Direction facing = JigsawBlock.getFacing(info.info().state());
                RoadAnchor anchor = new RoadAnchor(piece, info, segment);
                anchors.computeIfAbsent(segment, ignored -> new EnumMap<>(Direction.class))
                        .computeIfAbsent(facing, ignored -> new ArrayList<>())
                        .add(anchor);
            }
        }
    }

    private int addBuildingPieces(
            TerrainSampler terrain,
            StructureTemplateManager templateManager,
            Map<BuildingInfoKey, BuildingPlacementInfo> buildingInfoCache,
            List<PiecePlan> pieces,
            List<BlockBox> occupied,
            BlockPos center,
            RoadNetwork roads,
            RoadAnchorIndex roadAnchors,
            List<Lot> lots,
            int targetBuildings,
            Random random
    ) {
        orderLotsForBuildingPlacement(lots, center, random);
        String[] buildings = buildingsForStyle(villageStyle);
        String[] farmBuildings = farmBuildingsForStyle(villageStyle);
        int maxVanillaFarmPatches = Math.max(1, targetBuildings / VANILLA_FARM_PATCH_DIVISOR);
        int vanillaFarmPatches = 0;
        int placed = 0;
        int attempts = 0;

        while (placed < targetBuildings && attempts < lots.size() * 5) {
            attempts++;
            Lot lot = lots.get((attempts - 1) % lots.size());
            boolean useVanillaFarm = vanillaFarmPatches < maxVanillaFarmPatches
                    && farmBuildings.length > 0
                    && random.nextInt(100) < VANILLA_FARM_CHANCE_PERCENT;
            String building = useVanillaFarm
                    ? farmBuildings[random.nextInt(farmBuildings.length)]
                    : buildings[random.nextInt(buildings.length)];
            BlockRotation rotation = chooseBuildingRotation(building, lot);
            if (addRoadsideBuildingPiece(terrain, templateManager, buildingInfoCache, pieces, occupied, center, roads, roadAnchors, building, lot,
                    rotation, StructurePool.Projection.RIGID, true, 4, 10, 3)) {
                placed++;
                if (useVanillaFarm) {
                    vanillaFarmPatches++;
                }
            }
        }

        return placed;
    }

    private static void orderLotsForBuildingPlacement(List<Lot> lots, BlockPos center, Random random) {
        shuffle(lots, random);
        lots.sort((first, second) -> Integer.compare(lotPlacementBand(center, first), lotPlacementBand(center, second)));
    }

    private static int lotPlacementBand(BlockPos center, Lot lot) {
        return distanceSquared(center, lot.x(), lot.z()) / (DISTRICT_RING_SPACING * DISTRICT_RING_SPACING);
    }

    private int addRequiredBuildings(
            TerrainSampler terrain,
            StructureTemplateManager templateManager,
            Map<BuildingInfoKey, BuildingPlacementInfo> buildingInfoCache,
            List<PiecePlan> pieces,
            List<BlockBox> occupied,
            BlockPos center,
            RoadNetwork roads,
            RoadAnchorIndex roadAnchors,
            List<Lot> lots,
            Random random
    ) {
        List<Lot> candidates = new ArrayList<>(lots);
        int placed = 0;
        for (RequiredBuilding building : requiredBuildingsForStyle(villageStyle)) {
            shuffle(candidates, random);
            if (!addRequiredBuilding(terrain, templateManager, buildingInfoCache, pieces, occupied, center, roads, roadAnchors, candidates, building)) {
                return placed;
            }
            placed++;
        }
        return placed;
    }

    private boolean addRequiredBuilding(
            TerrainSampler terrain,
            StructureTemplateManager templateManager,
            Map<BuildingInfoKey, BuildingPlacementInfo> buildingInfoCache,
            List<PiecePlan> pieces,
            List<BlockBox> occupied,
            BlockPos center,
            RoadNetwork roads,
            RoadAnchorIndex roadAnchors,
            List<Lot> lots,
            RequiredBuilding building
    ) {
        for (Lot lot : lots) {
            if (addRoadsideBuildingPiece(terrain, templateManager, buildingInfoCache, pieces, occupied, center, roads, roadAnchors, building.templateId(), lot,
                    chooseBuildingRotation(building.templateId(), lot), StructurePool.Projection.RIGID, building.legacy(), 4, 8, 5)) {
                return true;
            }
        }
        return false;
    }

    private void addVillagerPieces(
            TerrainSampler terrain,
            StructureTemplateManager templateManager,
            List<PiecePlan> pieces,
            BlockPos center,
            List<Lot> lots,
            int targetBuildings,
            Random random
    ) {
        List<Lot> candidates = new ArrayList<>(lots);
        shuffle(candidates, random);
        int count = Math.min(candidates.size(), Math.min(24, Math.max(10, targetBuildings / 5)));
        for (int i = 0; i < count; i++) {
            BlockPos pos = getVillagerSpawnPos(terrain, center, candidates.get(i), random);
            BlockBox footprint = new BlockBox(pos.getX(), 0, pos.getZ(), pos.getX(), 0, pos.getZ());
            if (isFootprintSuitable(terrain, footprint, 2, 1)) {
                addPieceAt(templateManager, pieces, VILLAGER, pos, BlockRotation.NONE, StructurePool.Projection.RIGID, false);
            }
        }
    }

    private BlockPos getVillagerSpawnPos(TerrainSampler terrain, BlockPos center, Lot lot, Random random) {
        int roadX = center.getX() + lot.roadPoint().x();
        int roadZ = center.getZ() + lot.roadPoint().z();
        int offset = ROAD_HALF_WIDTH + 2;
        int jitter = random.nextInt(5) - 2;
        int x;
        int z;
        if (lot.roadDirection().isHorizontal()) {
            x = roadX + jitter;
            z = roadZ + lot.side() * offset;
        } else {
            x = roadX + lot.side() * offset;
            z = roadZ + jitter;
        }
        return new BlockPos(x, terrain.getPlacementY(x, z), z);
    }

    private void addLotIfValid(List<Lot> lots, BlockPos center, int radius, int x, int z, RoadDirection roadDirection, int side, Point roadPoint,
            RoadSegment roadSegment) {
        if (distanceSquared(center, x, z) > (radius + 18) * (radius + 18)) {
            return;
        }
        if (isTooCloseToExistingLot(lots, x, z)) {
            return;
        }
        lots.add(new Lot(x, z, roadDirection, side, roadPoint, roadSegment));
    }

    private static boolean isTooCloseToExistingLot(List<Lot> lots, int x, int z) {
        for (Lot lot : lots) {
            int dx = lot.x() - x;
            int dz = lot.z() - z;
            if (dx * dx + dz * dz < 12 * 12) {
                return true;
            }
        }
        return false;
    }

    private static List<RoadSegment> pruneRoadsWithoutLots(Set<RoadSegment> segments, List<Lot> lots) {
        List<RoadSegment> useful = new ArrayList<>();
        for (RoadSegment segment : segments) {
            if (countLotsNearSegment(segment, lots) >= 1 || segment.a().distance() < LOT_CLEARANCE_FROM_TOWN_HALL) {
                useful.add(segment);
            }
        }
        return useful;
    }

    private static int countLotsNearSegment(RoadSegment segment, List<Lot> lots) {
        int count = 0;
        for (Lot lot : lots) {
            if (segment.containsProjection(lot.roadPoint(), 12)) {
                count++;
            }
        }
        return count;
    }

    private static Set<Point> nodesForSegments(List<RoadSegment> segments) {
        Set<Point> nodes = new HashSet<>();
        for (RoadSegment segment : segments) {
            nodes.add(segment.a());
            nodes.add(segment.b());
        }
        return nodes;
    }

    private static List<Lot> lotsForRoads(List<RoadSegment> segments, List<Lot> lots) {
        List<Lot> usefulLots = new ArrayList<>();
        for (Lot lot : lots) {
            for (RoadSegment segment : segments) {
                if (segment.containsProjection(lot.roadPoint(), 3)) {
                    usefulLots.add(lot);
                    break;
                }
            }
        }
        return usefulLots;
    }

    private static boolean hasRoadConflict(BlockPos center, RoadSegment candidate, Set<RoadSegment> existingSegments) {
        BlockBox candidateBox = candidate.footprint(center).expand(ROAD_CONFLICT_PADDING, 0, ROAD_CONFLICT_PADDING);
        for (RoadSegment existing : existingSegments) {
            BlockBox existingBox = existing.footprint(center).expand(ROAD_CONFLICT_PADDING, 0, ROAD_CONFLICT_PADDING);
            if (!candidateBox.intersectsXZ(existingBox.getMinX(), existingBox.getMinZ(), existingBox.getMaxX(), existingBox.getMaxZ())) {
                continue;
            }
            if (candidate.sharesEndpoint(existing) && !candidate.overlapsLine(existing, ROAD_TILE_LENGTH / 2)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean addRoadsideBuildingPiece(
            TerrainSampler terrain,
            StructureTemplateManager templateManager,
            Map<BuildingInfoKey, BuildingPlacementInfo> buildingInfoCache,
            List<PiecePlan> pieces,
            List<BlockBox> occupied,
            BlockPos center,
            RoadNetwork roads,
            RoadAnchorIndex roadAnchors,
            String templateId,
            Lot lot,
            BlockRotation rotation,
            StructurePool.Projection projection,
            boolean legacy,
            int terrainSpread,
            int terrainSampleStep,
            int padding
    ) {
        BuildingPlacementInfo buildingInfo = getBuildingPlacementInfo(buildingInfoCache, templateManager, templateId, projection, legacy, rotation);
        StructurePoolElement element = buildingInfo.element();
        List<StructureTemplate.JigsawBlockInfo> entrances = findBuildingEntranceJigsaws(buildingInfo.entrances(), lot);
        for (StructureTemplate.JigsawBlockInfo entrance : entrances) {
            if (addRoadsideBuildingPieceAtEntrance(terrain, templateManager, pieces, occupied, center, roads, roadAnchors, element, lot,
                    entrance, rotation, terrainSpread, terrainSampleStep, padding)) {
                return true;
            }
        }
        return false;
    }

    private boolean addRoadsideBuildingPieceAtEntrance(
            TerrainSampler terrain,
            StructureTemplateManager templateManager,
            List<PiecePlan> pieces,
            List<BlockBox> occupied,
            BlockPos center,
            RoadNetwork roads,
            RoadAnchorIndex roadAnchors,
            StructurePoolElement element,
            Lot lot,
            StructureTemplate.JigsawBlockInfo entrance,
            BlockRotation rotation,
            int terrainSpread,
            int terrainSampleStep,
            int padding
    ) {
        for (RoadAnchor roadAnchor : getRoadEntranceJigsawCandidates(lot, entrance, roadAnchors)) {
            Optional<AttachedPiece> attachedPiece = attachBuildingToRoadJigsaw(terrain, templateManager, roadAnchor, element, rotation, entrance);
            if (attachedPiece.isEmpty()) {
                continue;
            }

            PiecePlan piece = attachedPiece.get().piece();
            BlockBox box = piece.box();
            BlockBox paddedBox = box.expand(padding, 0, padding);
            if (intersectsAnyXZ(paddedBox, occupied)) {
                continue;
            }

            int distanceFromRoad = distanceFromRoads(box, center, roads);
            if (distanceFromRoad <= 0 || distanceFromRoad > MAX_BUILDING_DISTANCE_FROM_ROAD) {
                continue;
            }

            if (!isFootprintSuitable(terrain, box, terrainSpread, terrainSampleStep)) {
                continue;
            }

            roadAnchor.piece().junctions().add(attachedPiece.get().parentJunction());
            piece.junctions().add(attachedPiece.get().childJunction());
            pieces.add(piece);
            occupied.add(paddedBox);
            return true;
        }
        return false;
    }

    private BuildingPlacementInfo getBuildingPlacementInfo(
            Map<BuildingInfoKey, BuildingPlacementInfo> cache,
            StructureTemplateManager templateManager,
            String templateId,
            StructurePool.Projection projection,
            boolean legacy,
            BlockRotation rotation
    ) {
        BuildingInfoKey key = new BuildingInfoKey(templateId, projection, legacy, rotation);
        return cache.computeIfAbsent(key, ignored -> {
            StructurePoolElement element = createElement(templateId, projection, legacy);
            List<StructureTemplate.JigsawBlockInfo> entrances = new ArrayList<>();
            for (StructureTemplate.JigsawBlockInfo info : element.getStructureBlockInfos(templateManager, BlockPos.ORIGIN,
                    rotation, Random.create(0L))) {
                if (BUILDING_ENTRANCE.equals(info.name())) {
                    entrances.add(info);
                }
            }
            return new BuildingPlacementInfo(element, entrances);
        });
    }

    private static List<StructureTemplate.JigsawBlockInfo> findBuildingEntranceJigsaws(
            List<StructureTemplate.JigsawBlockInfo> buildingEntrances,
            Lot lot
    ) {
        Direction expectedFacing = roadFacingDirection(lot).toMinecraftDirection();
        List<StructureTemplate.JigsawBlockInfo> entrances = new ArrayList<>();
        for (StructureTemplate.JigsawBlockInfo info : buildingEntrances) {
            if (JigsawBlock.getFacing(info.info().state()) == expectedFacing) {
                entrances.add(info);
            }
        }
        return entrances;
    }

    private Optional<AttachedPiece> attachBuildingToRoadJigsaw(
            TerrainSampler terrain,
            StructureTemplateManager templateManager,
            RoadAnchor roadAnchor,
            StructurePoolElement element,
            BlockRotation rotation,
            StructureTemplate.JigsawBlockInfo entrance
    ) {
        StructureTemplate.JigsawBlockInfo parentJigsaw = roadAnchor.jigsaw();
        if (!JigsawBlock.attachmentMatches(parentJigsaw, entrance)) {
            return Optional.empty();
        }

        PiecePlan parent = roadAnchor.piece();
        StructureTemplate.StructureBlockInfo parentInfo = parentJigsaw.info();
        StructureTemplate.StructureBlockInfo childInfo = entrance.info();
        Direction parentFacing = JigsawBlock.getFacing(parentInfo.state());
        BlockPos parentJigsawPos = parentInfo.pos();
        BlockPos childJigsawPos = childInfo.pos();
        BlockPos connectionPos = parentJigsawPos.offset(parentFacing);
        BlockPos unadjustedPos = connectionPos.subtract(childJigsawPos);
        BlockBox unadjustedBox = element.getBoundingBox(templateManager, unadjustedPos, rotation);

        boolean parentRigid = parent.element().getProjection() == StructurePool.Projection.RIGID;
        boolean childRigid = element.getProjection() == StructurePool.Projection.RIGID;
        int parentBoxMinY = parent.box().getMinY();
        int parentJigsawDeltaY = parentJigsawPos.getY() - parentBoxMinY;
        int childJigsawY = childJigsawPos.getY();
        int deltaY = parentJigsawDeltaY - childJigsawY + parentFacing.getOffsetY();
        int targetMinY;
        if (parentRigid && childRigid) {
            targetMinY = parentBoxMinY + deltaY;
        } else {
            targetMinY = terrain.getPlacementY(parentJigsawPos.getX(), parentJigsawPos.getZ()) - childJigsawY;
        }

        int yOffset = targetMinY - unadjustedBox.getMinY();
        BlockPos pos = unadjustedPos.add(0, yOffset, 0);
        BlockBox box = unadjustedBox.offset(0, yOffset, 0);

        int childGroundLevelDelta;
        if (childRigid) {
            childGroundLevelDelta = parent.groundLevelDelta() - deltaY;
        } else {
            childGroundLevelDelta = element.getGroundLevelDelta();
        }

        PiecePlan piece = new PiecePlan(templateManager, element, pos, childGroundLevelDelta, rotation, box, new ArrayList<>());
        int junctionGroundY;
        if (parentRigid) {
            junctionGroundY = parentBoxMinY + parentJigsawDeltaY;
        } else if (childRigid) {
            junctionGroundY = targetMinY + childJigsawY;
        } else {
            junctionGroundY = terrain.getPlacementY(parentJigsawPos.getX(), parentJigsawPos.getZ()) + deltaY / 2;
        }

        JigsawJunction parentJunction = new JigsawJunction(connectionPos.getX(),
                junctionGroundY - parentJigsawDeltaY + parent.groundLevelDelta(), connectionPos.getZ(), deltaY, element.getProjection());
        JigsawJunction childJunction = new JigsawJunction(parentJigsawPos.getX(),
                junctionGroundY - childJigsawY + childGroundLevelDelta, parentJigsawPos.getZ(), -deltaY, parent.element().getProjection());
        return Optional.of(new AttachedPiece(piece, parentJunction, childJunction));
    }

    private List<RoadAnchor> getRoadEntranceJigsawCandidates(
            Lot lot,
            StructureTemplate.JigsawBlockInfo entrance,
            RoadAnchorIndex roadAnchors
    ) {
        Direction expectedParentFacing = JigsawBlock.getFacing(entrance.info().state()).getOpposite();
        List<RoadAnchor> candidates = new ArrayList<>(roadAnchors.forSegment(lot.roadSegment(), expectedParentFacing));
        candidates.sort((first, second) -> Integer.compare(
                roadAnchorDistanceSquared(lot, first.pos()),
                roadAnchorDistanceSquared(lot, second.pos())));
        return candidates;
    }

    private static int roadAnchorDistanceSquared(Lot lot, BlockPos anchor) {
        int dx = anchor.getX() - lot.x();
        int dz = anchor.getZ() - lot.z();
        return dx * dx + dz * dz;
    }

    private boolean addCenteredPiece(
            TerrainSampler terrain,
            StructureTemplateManager templateManager,
            List<PiecePlan> pieces,
            List<BlockBox> occupied,
            String templateId,
            int centerX,
            int centerZ,
            BlockRotation rotation,
            StructurePool.Projection projection,
            boolean legacy,
            int terrainSpread,
            int terrainSampleStep,
            int padding
    ) {
        StructurePoolElement element = createElement(templateId, projection, legacy);
        BlockBox originBox = element.getBoundingBox(templateManager, BlockPos.ORIGIN, rotation);
        int x = centerX - (originBox.getMinX() + originBox.getMaxX()) / 2;
        int z = centerZ - (originBox.getMinZ() + originBox.getMaxZ()) / 2;
        BlockBox horizontalBox = element.getBoundingBox(templateManager, new BlockPos(x, 0, z), rotation);
        if (!isFootprintSuitable(terrain, horizontalBox, terrainSpread, terrainSampleStep)) {
            return false;
        }

        int y = getAveragePlacementY(terrain, horizontalBox, terrainSampleStep);
        BlockPos pos = new BlockPos(x, y, z);
        BlockBox box = element.getBoundingBox(templateManager, pos, rotation);
        BlockBox paddedBox = box.expand(padding, 0, padding);
        if (intersectsAnyXZ(paddedBox, occupied)) {
            return false;
        }

        pieces.add(createPiecePlan(templateManager, element, pos, rotation, box));
        occupied.add(paddedBox);
        return true;
    }

    private PiecePlan addPieceAt(
            StructureTemplateManager templateManager,
            List<PiecePlan> pieces,
            String templateId,
            BlockPos pos,
            BlockRotation rotation,
            StructurePool.Projection projection,
            boolean legacy
    ) {
        StructurePoolElement element = createElement(templateId, projection, legacy);
        BlockBox box = element.getBoundingBox(templateManager, pos, rotation);
        PiecePlan piece = createPiecePlan(templateManager, element, pos, rotation, box);
        pieces.add(piece);
        return piece;
    }

    private static PiecePlan createPiecePlan(
            StructureTemplateManager templateManager,
            StructurePoolElement element,
            BlockPos pos,
            BlockRotation rotation,
            BlockBox box
    ) {
        return new PiecePlan(templateManager, element, pos, element.getGroundLevelDelta(), rotation, box, new ArrayList<>());
    }

    private boolean isAreaSuitable(TerrainSampler terrain, int centerX, int centerZ, int radius, int maxHeightSpread, int sampleStep) {
        BlockBox box = new BlockBox(centerX - radius, 0, centerZ - radius, centerX + radius, 0, centerZ + radius);
        return isFootprintSuitable(terrain, box, maxHeightSpread, sampleStep);
    }

    private boolean isCenterBiomeValid(Context context, BlockPos center) {
        return context.biomePredicate().test(context.chunkGenerator().getBiomeSource().getBiome(
                BiomeCoords.fromBlock(center.getX()),
                BiomeCoords.fromBlock(center.getY()),
                BiomeCoords.fromBlock(center.getZ()),
                context.noiseConfig().getMultiNoiseSampler()
        ));
    }

    private boolean isLocateAreaSuitable(TerrainSampler terrain, int centerX, int centerZ, int centerY) {
        int minY = centerY;
        int maxY = centerY;

        for (RoadDirection direction : RoadDirection.values()) {
            int y = terrain.getPlacementY(centerX + direction.dx * LOCATE_TERRAIN_RADIUS, centerZ + direction.dz * LOCATE_TERRAIN_RADIUS);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            if (maxY - minY > 12) {
                return false;
            }
        }

        return true;
    }

    private boolean isFootprintSuitable(TerrainSampler terrain, BlockBox box, int maxHeightSpread, int sampleStep) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int x : sampleAxis(box.getMinX(), box.getMaxX(), sampleStep)) {
            for (int z : sampleAxis(box.getMinZ(), box.getMaxZ(), sampleStep)) {
                SurfaceSample sample = terrain.getSurfaceSample(x, z);
                int y = sample.placementY();
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                if (maxY - minY > maxHeightSpread) {
                    return false;
                }
                if (sample.hasFluidNearSurface()) {
                    return false;
                }
            }
        }

        return true;
    }

    private TerrainStats sampleTerrain(TerrainSampler terrain, BlockBox box, int sampleStep) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int totalY = 0;
        int samples = 0;
        boolean hasFluid = false;

        for (int x : sampleAxis(box.getMinX(), box.getMaxX(), sampleStep)) {
            for (int z : sampleAxis(box.getMinZ(), box.getMaxZ(), sampleStep)) {
                SurfaceSample sample = terrain.getSurfaceSample(x, z);
                int y = sample.placementY();
                if (sample.hasFluidNearSurface()) {
                    hasFluid = true;
                }

                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                totalY += y;
                samples++;
            }
        }

        return new TerrainStats(minY, maxY, totalY / Math.max(1, samples), hasFluid);
    }

    private static boolean hasFluidNearSurface(VerticalBlockSample sample, int surfaceY) {
        for (int y = surfaceY + 2; y >= surfaceY - 8; y--) {
            BlockState state = sample.getState(y);
            if (!state.getFluidState().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private int getAveragePlacementY(TerrainSampler terrain, BlockBox box, int sampleStep) {
        int totalY = 0;
        int samples = 0;

        for (int x : sampleAxis(box.getMinX(), box.getMaxX(), sampleStep)) {
            for (int z : sampleAxis(box.getMinZ(), box.getMaxZ(), sampleStep)) {
                totalY += terrain.getPlacementY(x, z);
                samples++;
            }
        }

        return totalY / Math.max(1, samples);
    }

    private static List<Integer> sampleAxis(int min, int max, int step) {
        List<Integer> values = new ArrayList<>();
        for (int value = min; value <= max; value += step) {
            values.add(value);
        }
        if (values.isEmpty() || values.get(values.size() - 1) != max) {
            values.add(max);
        }
        return extremesFirst(values);
    }

    private static List<Integer> extremesFirst(List<Integer> values) {
        List<Integer> ordered = new ArrayList<>(values.size());
        addIfAbsent(ordered, values.get(values.size() / 2));
        addIfAbsent(ordered, values.get(0));
        addIfAbsent(ordered, values.get(values.size() - 1));

        for (int value : values) {
            addIfAbsent(ordered, value);
        }

        return ordered;
    }

    private static void addIfAbsent(List<Integer> values, int value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private static boolean intersectsAnyXZ(BlockBox box, List<BlockBox> occupied) {
        for (BlockBox occupiedBox : occupied) {
            if (box.intersectsXZ(occupiedBox.getMinX(), occupiedBox.getMinZ(), occupiedBox.getMaxX(), occupiedBox.getMaxZ())) {
                return true;
            }
        }
        return false;
    }

    private static int distanceFromRoads(BlockBox box, BlockPos center, RoadNetwork roads) {
        int closest = Integer.MAX_VALUE;
        for (RoadSegment segment : roads.segments()) {
            closest = Math.min(closest, distanceBetweenXZ(box, segment.footprint(center)));
        }
        return closest;
    }

    private static int distanceBetweenXZ(BlockBox first, BlockBox second) {
        int dx = 0;
        if (first.getMaxX() < second.getMinX()) {
            dx = second.getMinX() - first.getMaxX();
        } else if (second.getMaxX() < first.getMinX()) {
            dx = first.getMinX() - second.getMaxX();
        }

        int dz = 0;
        if (first.getMaxZ() < second.getMinZ()) {
            dz = second.getMinZ() - first.getMaxZ();
        } else if (second.getMaxZ() < first.getMinZ()) {
            dz = first.getMinZ() - second.getMaxZ();
        }

        return Math.max(dx, dz);
    }

    private static StructurePoolElement createElement(String templateId, StructurePool.Projection projection, boolean legacy) {
        if (legacy) {
            return StructurePoolElement.ofLegacySingle(templateId).apply(projection);
        }
        return StructurePoolElement.ofSingle(templateId).apply(projection);
    }

    private RoadTemplates roadTemplatesForStyle(String style) {
        if ("desert".equals(style)) {
            return new RoadTemplates(DESERT_ROAD_NORTH_SOUTH, DESERT_ROAD_EAST_WEST, DESERT_ROAD_CROSSING);
        }
        return new RoadTemplates(ROAD_NORTH_SOUTH, ROAD_EAST_WEST, ROAD_CROSSING);
    }

    private static RequiredBuilding[] requiredBuildingsForStyle(String style) {
        return switch (style) {
            case "desert" -> new RequiredBuilding[]{
                    new RequiredBuilding(DESERT_CUSTOM_FARM, false),
                    new RequiredBuilding("minecraft:village/desert/houses/desert_temple_2", true),
                    new RequiredBuilding("minecraft:village/desert/houses/desert_weaponsmith_1", true)
            };
            case "savanna" -> new RequiredBuilding[]{
                    new RequiredBuilding(CUSTOM_FARM, false),
                    new RequiredBuilding("minecraft:village/savanna/houses/savanna_temple_2", true),
                    new RequiredBuilding("minecraft:village/savanna/houses/savanna_weaponsmith_1", true)
            };
            case "snowy" -> new RequiredBuilding[]{
                    new RequiredBuilding(CUSTOM_FARM, false),
                    new RequiredBuilding("minecraft:village/snowy/houses/snowy_temple_1", true),
                    new RequiredBuilding("minecraft:village/snowy/houses/snowy_weapon_smith_1", true)
            };
            case "taiga" -> new RequiredBuilding[]{
                    new RequiredBuilding(CUSTOM_FARM, false),
                    new RequiredBuilding("minecraft:village/taiga/houses/taiga_temple_1", true),
                    new RequiredBuilding("minecraft:village/taiga/houses/taiga_weaponsmith_1", true)
            };
            default -> new RequiredBuilding[]{
                    new RequiredBuilding(CUSTOM_FARM, false),
                    new RequiredBuilding("minecraft:village/plains/houses/plains_temple_4", true),
                    new RequiredBuilding("minecraft:village/plains/houses/plains_weaponsmith_1", true)
            };
        };
    }

    private static String[] buildingsForStyle(String style) {
        return switch (style) {
            case "desert" -> new String[]{
                    "minecraft:village/desert/houses/desert_small_house_1",
                    "minecraft:village/desert/houses/desert_small_house_2",
                    "minecraft:village/desert/houses/desert_small_house_3",
                    "minecraft:village/desert/houses/desert_small_house_4",
                    "minecraft:village/desert/houses/desert_small_house_5",
                    "minecraft:village/desert/houses/desert_small_house_6",
                    "minecraft:village/desert/houses/desert_small_house_7",
                    "minecraft:village/desert/houses/desert_small_house_8",
                    "minecraft:village/desert/houses/desert_medium_house_1",
                    "minecraft:village/desert/houses/desert_medium_house_2",
                    "minecraft:village/desert/houses/desert_armorer_1",
                    "minecraft:village/desert/houses/desert_library_1",
                    "minecraft:village/desert/houses/desert_tool_smith_1",
                    "minecraft:village/desert/houses/desert_butcher_shop_1",
                    "minecraft:village/desert/houses/desert_cartographer_house_1",
                    "minecraft:village/desert/houses/desert_fisher_1",
                    "minecraft:village/desert/houses/desert_fletcher_house_1",
                    "minecraft:village/desert/houses/desert_mason_1",
                    "minecraft:village/desert/houses/desert_shepherd_house_1",
                    "minecraft:village/desert/houses/desert_tannery_1"
            };
            case "savanna" -> new String[]{
                    "minecraft:village/savanna/houses/savanna_small_house_1",
                    "minecraft:village/savanna/houses/savanna_small_house_2",
                    "minecraft:village/savanna/houses/savanna_small_house_3",
                    "minecraft:village/savanna/houses/savanna_small_house_4",
                    "minecraft:village/savanna/houses/savanna_small_house_5",
                    "minecraft:village/savanna/houses/savanna_small_house_6",
                    "minecraft:village/savanna/houses/savanna_small_house_7",
                    "minecraft:village/savanna/houses/savanna_small_house_8",
                    "minecraft:village/savanna/houses/savanna_medium_house_1",
                    "minecraft:village/savanna/houses/savanna_medium_house_2",
                    "minecraft:village/savanna/houses/savanna_armorer_1",
                    "minecraft:village/savanna/houses/savanna_library_1",
                    "minecraft:village/savanna/houses/savanna_tool_smith_1",
                    "minecraft:village/savanna/houses/savanna_butchers_shop_1",
                    "minecraft:village/savanna/houses/savanna_butchers_shop_2",
                    "minecraft:village/savanna/houses/savanna_cartographer_1",
                    "minecraft:village/savanna/houses/savanna_fisher_cottage_1",
                    "minecraft:village/savanna/houses/savanna_fletcher_house_1",
                    "minecraft:village/savanna/houses/savanna_mason_1",
                    "minecraft:village/savanna/houses/savanna_shepherd_1",
                    "minecraft:village/savanna/houses/savanna_tannery_1",
                    "minecraft:village/savanna/houses/savanna_weaponsmith_2"
            };
            case "snowy" -> new String[]{
                    "minecraft:village/snowy/houses/snowy_small_house_1",
                    "minecraft:village/snowy/houses/snowy_small_house_2",
                    "minecraft:village/snowy/houses/snowy_small_house_3",
                    "minecraft:village/snowy/houses/snowy_small_house_4",
                    "minecraft:village/snowy/houses/snowy_small_house_5",
                    "minecraft:village/snowy/houses/snowy_small_house_6",
                    "minecraft:village/snowy/houses/snowy_small_house_7",
                    "minecraft:village/snowy/houses/snowy_small_house_8",
                    "minecraft:village/snowy/houses/snowy_medium_house_1",
                    "minecraft:village/snowy/houses/snowy_medium_house_2",
                    "minecraft:village/snowy/houses/snowy_medium_house_3",
                    "minecraft:village/snowy/houses/snowy_armorer_house_1",
                    "minecraft:village/snowy/houses/snowy_armorer_house_2",
                    "minecraft:village/snowy/houses/snowy_library_1",
                    "minecraft:village/snowy/houses/snowy_tool_smith_1",
                    "minecraft:village/snowy/houses/snowy_butchers_shop_1",
                    "minecraft:village/snowy/houses/snowy_butchers_shop_2",
                    "minecraft:village/snowy/houses/snowy_cartographer_house_1",
                    "minecraft:village/snowy/houses/snowy_fisher_cottage",
                    "minecraft:village/snowy/houses/snowy_fletcher_house_1",
                    "minecraft:village/snowy/houses/snowy_masons_house_1",
                    "minecraft:village/snowy/houses/snowy_masons_house_2",
                    "minecraft:village/snowy/houses/snowy_shepherds_house_1",
                    "minecraft:village/snowy/houses/snowy_tannery_1"
            };
            case "taiga" -> new String[]{
                    "minecraft:village/taiga/houses/taiga_small_house_1",
                    "minecraft:village/taiga/houses/taiga_small_house_2",
                    "minecraft:village/taiga/houses/taiga_small_house_3",
                    "minecraft:village/taiga/houses/taiga_small_house_4",
                    "minecraft:village/taiga/houses/taiga_small_house_5",
                    "minecraft:village/taiga/houses/taiga_medium_house_1",
                    "minecraft:village/taiga/houses/taiga_medium_house_2",
                    "minecraft:village/taiga/houses/taiga_medium_house_3",
                    "minecraft:village/taiga/houses/taiga_medium_house_4",
                    "minecraft:village/taiga/houses/taiga_armorer_2",
                    "minecraft:village/taiga/houses/taiga_armorer_house_1",
                    "minecraft:village/taiga/houses/taiga_library_1",
                    "minecraft:village/taiga/houses/taiga_tool_smith_1",
                    "minecraft:village/taiga/houses/taiga_butcher_shop_1",
                    "minecraft:village/taiga/houses/taiga_cartographer_house_1",
                    "minecraft:village/taiga/houses/taiga_fisher_cottage_1",
                    "minecraft:village/taiga/houses/taiga_fletcher_house_1",
                    "minecraft:village/taiga/houses/taiga_masons_house_1",
                    "minecraft:village/taiga/houses/taiga_shepherds_house_1",
                    "minecraft:village/taiga/houses/taiga_tannery_1",
                    "minecraft:village/taiga/houses/taiga_weaponsmith_2"
            };
            default -> new String[]{
                    "minecraft:village/plains/houses/plains_small_house_1",
                    "minecraft:village/plains/houses/plains_small_house_2",
                    "minecraft:village/plains/houses/plains_small_house_3",
                    "minecraft:village/plains/houses/plains_small_house_4",
                    "minecraft:village/plains/houses/plains_small_house_5",
                    "minecraft:village/plains/houses/plains_small_house_6",
                    "minecraft:village/plains/houses/plains_small_house_7",
                    "minecraft:village/plains/houses/plains_small_house_8",
                    "minecraft:village/plains/houses/plains_medium_house_1",
                    "minecraft:village/plains/houses/plains_medium_house_2",
                    "minecraft:village/plains/houses/plains_big_house_1",
                    "minecraft:village/plains/houses/plains_armorer_house_1",
                    "minecraft:village/plains/houses/plains_library_1",
                    "minecraft:village/plains/houses/plains_library_2",
                    "minecraft:village/plains/houses/plains_tool_smith_1",
                    "minecraft:village/plains/houses/plains_butcher_shop_1",
                    "minecraft:village/plains/houses/plains_butcher_shop_2",
                    "minecraft:village/plains/houses/plains_cartographer_1",
                    "minecraft:village/plains/houses/plains_fisher_cottage_1",
                    "minecraft:village/plains/houses/plains_fletcher_house_1",
                    "minecraft:village/plains/houses/plains_masons_house_1",
                    "minecraft:village/plains/houses/plains_shepherds_house_1",
                    "minecraft:village/plains/houses/plains_stable_1",
                    "minecraft:village/plains/houses/plains_stable_2",
                    "minecraft:village/plains/houses/plains_tannery_1"
            };
        };
    }

    private static String[] farmBuildingsForStyle(String style) {
        return switch (style) {
            case "desert" -> new String[]{
                    "minecraft:village/desert/houses/desert_farm_1",
                    "minecraft:village/desert/houses/desert_farm_2",
                    "minecraft:village/desert/houses/desert_large_farm_1"
            };
            case "savanna" -> new String[]{
                    "minecraft:village/savanna/houses/savanna_small_farm",
                    "minecraft:village/savanna/houses/savanna_large_farm_1",
                    "minecraft:village/savanna/houses/savanna_large_farm_2"
            };
            case "snowy" -> new String[]{
                    "minecraft:village/snowy/houses/snowy_farm_1",
                    "minecraft:village/snowy/houses/snowy_farm_2"
            };
            case "taiga" -> new String[]{
                    "minecraft:village/taiga/houses/taiga_small_farm_1",
                    "minecraft:village/taiga/houses/taiga_large_farm_1",
                    "minecraft:village/taiga/houses/taiga_large_farm_2"
            };
            default -> new String[]{
                    "minecraft:village/plains/houses/plains_small_farm_1",
                    "minecraft:village/plains/houses/plains_large_farm_1"
            };
        };
    }

    private BlockRotation chooseBuildingRotation(String templateId, Lot lot) {
        return rotationBetween(frontDirectionForTemplate(templateId), roadFacingDirection(lot));
    }

    private static RoadDirection roadFacingDirection(Lot lot) {
        if (lot.roadDirection().isHorizontal()) {
            return lot.side() > 0 ? RoadDirection.NORTH : RoadDirection.SOUTH;
        }
        return lot.side() > 0 ? RoadDirection.WEST : RoadDirection.EAST;
    }

    private static BlockRotation rotationBetween(RoadDirection from, RoadDirection to) {
        int turns = (directionIndex(to) - directionIndex(from) + 4) % 4;
        return switch (turns) {
            case 1 -> BlockRotation.CLOCKWISE_90;
            case 2 -> BlockRotation.CLOCKWISE_180;
            case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    private static int directionIndex(RoadDirection direction) {
        return switch (direction) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
        };
    }

    private static RoadDirection frontDirectionForTemplate(String templateId) {
        return switch (templateId) {
            case "minecraft:village/desert/houses/desert_library_1",
                 "minecraft:village/desert/houses/desert_medium_house_2",
                 "minecraft:village/desert/houses/desert_small_house_5",
                 "minecraft:village/desert/houses/desert_small_house_8",
                 "minecraft:village/desert/houses/desert_tannery_1",
                 "minecraft:village/desert/houses/desert_weaponsmith_1",
                 "minecraft:village/savanna/houses/savanna_fisher_cottage_1",
                 "minecraft:village/savanna/houses/savanna_large_farm_2",
                 "minecraft:village/savanna/houses/savanna_library_1",
                 "minecraft:village/savanna/houses/savanna_small_farm",
                 "minecraft:village/snowy/houses/snowy_armorer_house_2",
                 "minecraft:village/snowy/houses/snowy_butchers_shop_1",
                 "minecraft:village/snowy/houses/snowy_medium_house_2",
                 "minecraft:village/snowy/houses/snowy_small_house_2",
                 "minecraft:village/taiga/houses/taiga_cartographer_house_1",
                 "minecraft:village/taiga/houses/taiga_library_1",
                 "minecraft:village/taiga/houses/taiga_medium_house_2",
                 "minecraft:village/taiga/houses/taiga_small_farm_1",
                 "minecraft:village/taiga/houses/taiga_small_house_1",
                 "minecraft:village/taiga/houses/taiga_small_house_2",
                 "minecraft:village/taiga/houses/taiga_small_house_3",
                 "minecraft:village/taiga/houses/taiga_weaponsmith_1",
                 "minecraft:village/taiga/houses/taiga_weaponsmith_2" -> RoadDirection.NORTH;
            case "minecraft:village/desert/houses/desert_shepherd_house_1",
                 "minecraft:village/desert/houses/desert_small_house_3",
                 "minecraft:village/desert/houses/desert_small_house_4",
                 "minecraft:village/savanna/houses/savanna_fletcher_house_1",
                 "minecraft:village/savanna/houses/savanna_large_farm_1",
                 "minecraft:village/savanna/houses/savanna_small_house_1",
                 "minecraft:village/savanna/houses/savanna_small_house_2",
                 "minecraft:village/savanna/houses/savanna_small_house_3",
                 "minecraft:village/savanna/houses/savanna_small_house_4",
                 "minecraft:village/savanna/houses/savanna_small_house_7",
                 "minecraft:village/snowy/houses/snowy_fisher_cottage",
                 "minecraft:village/snowy/houses/snowy_masons_house_1",
                 "minecraft:village/snowy/houses/snowy_small_house_1",
                 "minecraft:village/snowy/houses/snowy_small_house_3",
                 "minecraft:village/taiga/houses/taiga_butcher_shop_1",
                 "minecraft:village/taiga/houses/taiga_fisher_cottage_1",
                 "minecraft:village/taiga/houses/taiga_large_farm_2",
                 "minecraft:village/taiga/houses/taiga_medium_house_1",
                 "minecraft:village/taiga/houses/taiga_medium_house_4",
                 "minecraft:village/taiga/houses/taiga_small_house_4",
                 "minecraft:village/taiga/houses/taiga_temple_1",
                 "minecraft:village/taiga/houses/taiga_tool_smith_1" -> RoadDirection.SOUTH;
            case "minecraft:village/savanna/houses/savanna_small_house_5",
                 "minecraft:village/savanna/houses/savanna_tannery_1",
                 "minecraft:village/savanna/houses/savanna_weaponsmith_1",
                 "minecraft:village/snowy/houses/snowy_medium_house_1",
                 "minecraft:village/snowy/houses/snowy_shepherds_house_1" -> RoadDirection.EAST;
            case "minecraft:village/desert/houses/desert_armorer_1",
                 "minecraft:village/desert/houses/desert_butcher_shop_1",
                 "minecraft:village/desert/houses/desert_cartographer_house_1",
                 "minecraft:village/desert/houses/desert_farm_1",
                 "minecraft:village/desert/houses/desert_farm_2",
                 "minecraft:village/desert/houses/desert_fisher_1",
                 "minecraft:village/desert/houses/desert_fletcher_house_1",
                 "minecraft:village/desert/houses/desert_large_farm_1",
                 "minecraft:village/desert/houses/desert_mason_1",
                 "minecraft:village/desert/houses/desert_medium_house_1",
                 "minecraft:village/desert/houses/desert_small_house_1",
                 "minecraft:village/desert/houses/desert_small_house_2",
                 "minecraft:village/desert/houses/desert_small_house_6",
                 "minecraft:village/desert/houses/desert_small_house_7",
                 "minecraft:village/desert/houses/desert_temple_2",
                 "minecraft:village/desert/houses/desert_tool_smith_1",
                 "minecraft:village/plains/houses/plains_armorer_house_1",
                 "minecraft:village/plains/houses/plains_big_house_1",
                 "minecraft:village/plains/houses/plains_butcher_shop_1",
                 "minecraft:village/plains/houses/plains_butcher_shop_2",
                 "minecraft:village/plains/houses/plains_cartographer_1",
                 "minecraft:village/plains/houses/plains_fisher_cottage_1",
                 "minecraft:village/plains/houses/plains_fletcher_house_1",
                 "minecraft:village/plains/houses/plains_large_farm_1",
                 "minecraft:village/plains/houses/plains_library_1",
                 "minecraft:village/plains/houses/plains_library_2",
                 "minecraft:village/plains/houses/plains_masons_house_1",
                 "minecraft:village/plains/houses/plains_medium_house_1",
                 "minecraft:village/plains/houses/plains_medium_house_2",
                 "minecraft:village/plains/houses/plains_shepherds_house_1",
                 "minecraft:village/plains/houses/plains_small_farm_1",
                 "minecraft:village/plains/houses/plains_small_house_1",
                 "minecraft:village/plains/houses/plains_small_house_2",
                 "minecraft:village/plains/houses/plains_small_house_3",
                 "minecraft:village/plains/houses/plains_small_house_4",
                 "minecraft:village/plains/houses/plains_small_house_5",
                 "minecraft:village/plains/houses/plains_small_house_6",
                 "minecraft:village/plains/houses/plains_small_house_7",
                 "minecraft:village/plains/houses/plains_small_house_8",
                 "minecraft:village/plains/houses/plains_stable_1",
                 "minecraft:village/plains/houses/plains_stable_2",
                 "minecraft:village/plains/houses/plains_tannery_1",
                 "minecraft:village/plains/houses/plains_temple_4",
                 "minecraft:village/plains/houses/plains_tool_smith_1",
                 "minecraft:village/plains/houses/plains_weaponsmith_1",
                 "minecraft:village/savanna/houses/savanna_armorer_1",
                 "minecraft:village/savanna/houses/savanna_butchers_shop_1",
                 "minecraft:village/savanna/houses/savanna_butchers_shop_2",
                 "minecraft:village/savanna/houses/savanna_cartographer_1",
                 "minecraft:village/savanna/houses/savanna_mason_1",
                 "minecraft:village/savanna/houses/savanna_medium_house_1",
                 "minecraft:village/savanna/houses/savanna_medium_house_2",
                 "minecraft:village/savanna/houses/savanna_shepherd_1",
                 "minecraft:village/savanna/houses/savanna_small_house_6",
                 "minecraft:village/savanna/houses/savanna_small_house_8",
                 "minecraft:village/savanna/houses/savanna_temple_2",
                 "minecraft:village/savanna/houses/savanna_tool_smith_1",
                 "minecraft:village/savanna/houses/savanna_weaponsmith_2",
                 "minecraft:village/snowy/houses/snowy_armorer_house_1",
                 "minecraft:village/snowy/houses/snowy_butchers_shop_2",
                 "minecraft:village/snowy/houses/snowy_cartographer_house_1",
                 "minecraft:village/snowy/houses/snowy_farm_1",
                 "minecraft:village/snowy/houses/snowy_farm_2",
                 "minecraft:village/snowy/houses/snowy_fletcher_house_1",
                 "minecraft:village/snowy/houses/snowy_library_1",
                 "minecraft:village/snowy/houses/snowy_masons_house_2",
                 "minecraft:village/snowy/houses/snowy_medium_house_3",
                 "minecraft:village/snowy/houses/snowy_small_house_4",
                 "minecraft:village/snowy/houses/snowy_small_house_5",
                 "minecraft:village/snowy/houses/snowy_small_house_6",
                 "minecraft:village/snowy/houses/snowy_small_house_7",
                 "minecraft:village/snowy/houses/snowy_small_house_8",
                 "minecraft:village/snowy/houses/snowy_tannery_1",
                 "minecraft:village/snowy/houses/snowy_temple_1",
                 "minecraft:village/snowy/houses/snowy_tool_smith_1",
                 "minecraft:village/snowy/houses/snowy_weapon_smith_1",
                 "minecraft:village/taiga/houses/taiga_armorer_2",
                 "minecraft:village/taiga/houses/taiga_armorer_house_1",
                 "minecraft:village/taiga/houses/taiga_fletcher_house_1",
                 "minecraft:village/taiga/houses/taiga_large_farm_1",
                 "minecraft:village/taiga/houses/taiga_masons_house_1",
                 "minecraft:village/taiga/houses/taiga_medium_house_3",
                 "minecraft:village/taiga/houses/taiga_shepherds_house_1",
                 "minecraft:village/taiga/houses/taiga_small_house_5",
                 "minecraft:village/taiga/houses/taiga_tannery_1" -> RoadDirection.WEST;
            case CUSTOM_FARM, DESERT_CUSTOM_FARM -> RoadDirection.WEST;
            default -> throw new IllegalArgumentException("No large-village front direction mapped for " + templateId);
        };
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static int distanceSquared(BlockPos center, int x, int z) {
        int dx = x - center.getX();
        int dz = z - center.getZ();
        return dx * dx + dz * dz;
    }

    private static boolean isWithinRadius(Point point, int radius) {
        return point.distanceSquared() <= radius * radius;
    }

    private static <T> void shuffle(List<T> list, Random random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T value = list.get(i);
            list.set(i, list.get(j));
            list.set(j, value);
        }
    }

    private record VillagePlan(List<PiecePlan> pieces) {
    }

    private record VillageStart(BlockPos center) {
    }

    private record PiecePlan(
            StructureTemplateManager templateManager,
            StructurePoolElement element,
            BlockPos pos,
            int groundLevelDelta,
            BlockRotation rotation,
            BlockBox box,
            List<JigsawJunction> junctions
    ) {
    }

    private record RoadTemplates(String northSouth, String eastWest, String crossing) {
    }

    private record RoadAnchorIndex(Map<RoadSegment, Map<Direction, List<RoadAnchor>>> anchors) {
        private List<RoadAnchor> forSegment(RoadSegment segment, Direction facing) {
            Map<Direction, List<RoadAnchor>> byFacing = anchors.get(segment);
            if (byFacing == null) {
                return List.of();
            }
            return byFacing.getOrDefault(facing, List.of());
        }
    }

    private record RoadAnchor(PiecePlan piece, StructureTemplate.JigsawBlockInfo jigsaw, RoadSegment segment) {
        private BlockPos pos() {
            return jigsaw.info().pos();
        }
    }

    private record BuildingPlacementInfo(StructurePoolElement element, List<StructureTemplate.JigsawBlockInfo> entrances) {
    }

    private record BuildingInfoKey(String templateId, StructurePool.Projection projection, boolean legacy, BlockRotation rotation) {
    }

    private record AttachedPiece(PiecePlan piece, JigsawJunction parentJunction, JigsawJunction childJunction) {
    }

    private record RequiredBuilding(String templateId, boolean legacy) {
    }

    private record TerrainStats(int minY, int maxY, int averageY, boolean hasFluid) {
        private int heightSpread() {
            return maxY - minY;
        }
    }

    private record SurfaceSample(int placementY, boolean hasFluidNearSurface) {
    }

    static class TerrainSampler {
        private final Context context;
        private final Map<Long, Integer> placementYCache = new HashMap<>();
        private final Map<Long, Boolean> fluidNearSurfaceCache = new HashMap<>();
        private final Map<Long, SurfaceSample> surfaceSampleCache = new HashMap<>();

        TerrainSampler(Context context) {
            this.context = context;
        }

        private int getPlacementY(int x, int z) {
            long key = xzKey(x, z);
            SurfaceSample surfaceSample = surfaceSampleCache.get(key);
            if (surfaceSample != null) {
                return surfaceSample.placementY();
            }
            return placementYCache.computeIfAbsent(key, ignored -> context.chunkGenerator()
                    .getHeightOnGround(x, z, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig()));
        }

        private boolean hasFluidNearSurface(int x, int z, int placementY) {
            long key = xzKey(x, z);
            SurfaceSample surfaceSample = surfaceSampleCache.get(key);
            if (surfaceSample != null) {
                return surfaceSample.hasFluidNearSurface();
            }
            return fluidNearSurfaceCache.computeIfAbsent(key, ignored -> {
                VerticalBlockSample sample = context.chunkGenerator().getColumnSample(x, z, context.world(), context.noiseConfig());
                return LargeVillageStructure.hasFluidNearSurface(sample, placementY - 1);
            });
        }

        private SurfaceSample getSurfaceSample(int x, int z) {
            long key = xzKey(x, z);
            return surfaceSampleCache.computeIfAbsent(key, ignored -> {
                VerticalBlockSample sample = context.chunkGenerator().getColumnSample(x, z, context.world(), context.noiseConfig());
                int placementY = getPlacementY(sample);
                boolean hasFluidNearSurface = LargeVillageStructure.hasFluidNearSurface(sample, placementY - 1);
                placementYCache.put(key, placementY);
                fluidNearSurfaceCache.put(key, hasFluidNearSurface);
                return new SurfaceSample(placementY, hasFluidNearSurface);
            });
        }

        private int getPlacementY(VerticalBlockSample sample) {
            for (int y = context.world().getTopYInclusive(); y >= context.world().getBottomY(); y--) {
                if (Heightmap.Type.WORLD_SURFACE_WG.getBlockPredicate().test(sample.getState(y))) {
                    return y + 1;
                }
            }
            return context.world().getBottomY();
        }

        private static long xzKey(int x, int z) {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }
    }

    private record Lot(int x, int z, RoadDirection roadDirection, int side, Point roadPoint, RoadSegment roadSegment) {
    }

    private record VillageLayout(RoadNetwork roads, List<Lot> lots) {
    }

    private record RoadNetwork(Set<Point> nodes, Set<RoadSegment> segments) {
        private Map<Point, Integer> degrees() {
            Map<Point, Integer> degrees = new HashMap<>();
            for (RoadSegment segment : segments) {
                degrees.merge(segment.a(), 1, Integer::sum);
                degrees.merge(segment.b(), 1, Integer::sum);
            }
            return degrees;
        }
    }

    private record RoadEdge(Point start, Point end) {
    }

    private record Point(int x, int z) {
        private Point offset(RoadDirection direction, int length) {
            return new Point(x + direction.dx * length, z + direction.dz * length);
        }

        private int distance() {
            return (int) Math.sqrt(distanceSquared());
        }

        private int distanceSquared() {
            return x * x + z * z;
        }
    }

    private record RoadSegment(Point a, Point b) {
        private RoadSegment {
            if (compare(a, b) > 0) {
                Point previousA = a;
                a = b;
                b = previousA;
            }
        }

        private boolean isHorizontal() {
            return a.z() == b.z();
        }

        private int length() {
            return Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z());
        }

        private RoadDirection direction() {
            if (a.x() < b.x()) {
                return RoadDirection.EAST;
            }
            if (a.x() > b.x()) {
                return RoadDirection.WEST;
            }
            return a.z() < b.z() ? RoadDirection.SOUTH : RoadDirection.NORTH;
        }

        private Point pointAlong(int distance) {
            RoadDirection direction = direction();
            return new Point(a.x() + direction.dx * distance, a.z() + direction.dz * distance);
        }

        private List<RoadTile> tiles(BlockPos center) {
            List<RoadTile> tiles = new ArrayList<>();
            int length = length();
            if (isHorizontal()) {
                int z = center.getZ() + a.z() - ROAD_HALF_WIDTH;
                for (int offset = 0; offset < length; offset += ROAD_TILE_LENGTH) {
                    int x = center.getX() + a.x() + offset;
                    tiles.add(new RoadTile(new BlockBox(x, 0, z, x + ROAD_TILE_LENGTH - 1, 0, z + ROAD_HALF_WIDTH * 2)));
                }
                return tiles;
            }

            int x = center.getX() + a.x() - ROAD_HALF_WIDTH;
            for (int offset = 0; offset < length; offset += ROAD_TILE_LENGTH) {
                int z = center.getZ() + a.z() + offset;
                tiles.add(new RoadTile(new BlockBox(x, 0, z, x + ROAD_HALF_WIDTH * 2, 0, z + ROAD_TILE_LENGTH - 1)));
            }
            return tiles;
        }

        private BlockBox footprint(BlockPos center) {
            int minX = center.getX() + Math.min(a.x(), b.x());
            int maxX = center.getX() + Math.max(a.x(), b.x());
            int minZ = center.getZ() + Math.min(a.z(), b.z());
            int maxZ = center.getZ() + Math.max(a.z(), b.z());
            if (isHorizontal()) {
                return new BlockBox(minX, 0, minZ - ROAD_HALF_WIDTH, maxX, 0, maxZ + ROAD_HALF_WIDTH);
            }
            return new BlockBox(minX - ROAD_HALF_WIDTH, 0, minZ, maxX + ROAD_HALF_WIDTH, 0, maxZ);
        }

        private boolean containsProjection(Point point, int padding) {
            int minX = Math.min(a.x(), b.x()) - padding;
            int maxX = Math.max(a.x(), b.x()) + padding;
            int minZ = Math.min(a.z(), b.z()) - padding;
            int maxZ = Math.max(a.z(), b.z()) + padding;
            return point.x() >= minX && point.x() <= maxX && point.z() >= minZ && point.z() <= maxZ;
        }

        private boolean sharesEndpoint(RoadSegment other) {
            return a.equals(other.a()) || a.equals(other.b()) || b.equals(other.a()) || b.equals(other.b());
        }

        private boolean overlapsLine(RoadSegment other, int tolerance) {
            if (isHorizontal() != other.isHorizontal()) {
                return false;
            }
            if (isHorizontal()) {
                if (Math.abs(a.z() - other.a().z()) > tolerance) {
                    return false;
                }
                return rangesOverlap(Math.min(a.x(), b.x()), Math.max(a.x(), b.x()),
                        Math.min(other.a().x(), other.b().x()), Math.max(other.a().x(), other.b().x()), tolerance);
            }

            if (Math.abs(a.x() - other.a().x()) > tolerance) {
                return false;
            }
            return rangesOverlap(Math.min(a.z(), b.z()), Math.max(a.z(), b.z()),
                    Math.min(other.a().z(), other.b().z()), Math.max(other.a().z(), other.b().z()), tolerance);
        }

        private static int compare(Point first, Point second) {
            if (first.x() != second.x()) {
                return Integer.compare(first.x(), second.x());
            }
            return Integer.compare(first.z(), second.z());
        }

        private static boolean rangesOverlap(int firstMin, int firstMax, int secondMin, int secondMax, int tolerance) {
            return Math.max(firstMin, secondMin) + tolerance < Math.min(firstMax, secondMax);
        }
    }

    private record RoadTile(BlockBox footprint) {
        private BlockPos templatePos(TerrainSampler terrain) {
            int y = terrain.getPlacementY(footprint.getCenter().getX(), footprint.getCenter().getZ());
            return new BlockPos(footprint.getMinX(), y, footprint.getMinZ());
        }
    }

    private enum RoadDirection {
        NORTH(0, -1),
        SOUTH(0, 1),
        WEST(-1, 0),
        EAST(1, 0);

        private final int dx;
        private final int dz;

        RoadDirection(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        private boolean isHorizontal() {
            return this == WEST || this == EAST;
        }

        private Direction toMinecraftDirection() {
            return switch (this) {
                case NORTH -> Direction.NORTH;
                case SOUTH -> Direction.SOUTH;
                case WEST -> Direction.WEST;
                case EAST -> Direction.EAST;
            };
        }
    }
}
