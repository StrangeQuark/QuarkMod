package com.strangequark.largevillages.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructureLiquidSettings;
import net.minecraft.structure.StructurePiecesCollector;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class LargeVillageStructure extends Structure {
    private static final int ROAD_TILE_LENGTH = 8;
    private static final int ROAD_HALF_WIDTH = 2;
    private static final int MIN_ROAD_SEGMENT_LENGTH = 16;
    private static final int MAX_ROAD_SEGMENT_LENGTH = 40;
    private static final int MIN_PRIMARY_ROAD_SEGMENT_LENGTH = 24;
    private static final int MAX_PRIMARY_ROAD_SEGMENT_LENGTH = 48;
    private static final int NORMAL_VILLAGE_RADIUS = 80;
    private static final int LARGE_VILLAGE_RADIUS = 200;
    private static final int START_TERRAIN_RADIUS = 64;
    private static final int LOCATE_TERRAIN_RADIUS = 48;
    private static final int MAX_ROAD_SEGMENTS = 100;
    private static final int LOT_CLEARANCE_FROM_TOWN_HALL = 42;
    private static final int MAX_BUILDING_DISTANCE_FROM_ROAD = 10;
    private static final int ROAD_CONFLICT_PADDING = 2;
    private static final int MIN_ACCEPTED_BUILDINGS = 48;
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
        ChunkPos chunkPos = context.chunkPos();
        int x = chunkPos.getCenterX();
        int z = chunkPos.getCenterZ();
        int y = getPlacementY(context, x, z);
        BlockPos center = new BlockPos(x, y, z);

        if (!isLocateAreaSuitable(context, x, z)) {
            return Optional.empty();
        }

        return Optional.of(new StructurePosition(center, collector -> createPlan(context, center)
                .ifPresent(plan -> placePlan(collector, plan))));
    }

    @Override
    public StructureType<?> getType() {
        return ModStructureTypes.LARGE_VILLAGE;
    }

    private Optional<VillagePlan> createPlan(Context context, BlockPos center) {
        Random random = context.random();
        StructureTemplateManager templateManager = context.structureTemplateManager();
        int radius = chooseRadius(random);
        int targetBuildings = chooseTargetBuildingCount(radius, random);
        List<PiecePlan> pieces = new ArrayList<>();
        List<BlockBox> occupied = new ArrayList<>();

        if (!isAreaSuitable(context, center.getX(), center.getZ(), START_TERRAIN_RADIUS, 10, 16)) {
            return Optional.empty();
        }

        if (!addCenteredPiece(context, templateManager, pieces, occupied, TOWN_HALL, center.getX(), center.getZ(),
                BlockRotation.NONE, StructurePool.Projection.RIGID, false, 4, 10, 6)) {
            return Optional.empty();
        }

        Optional<VillageLayout> layout = generateVillageLayout(context, center, radius, targetBuildings, random);
        if (layout.isEmpty()) {
            return Optional.empty();
        }

        int requiredBuildings = addRequiredBuildings(context, templateManager, pieces, occupied, center, layout.get().roads(),
                layout.get().lots(), random);
        if (requiredBuildings < requiredBuildingsForStyle(villageStyle).length) {
            return Optional.empty();
        }

        int buildings = requiredBuildings + addBuildingPieces(context, templateManager, pieces, occupied, center, layout.get().roads(),
                layout.get().lots(), Math.max(0, targetBuildings - requiredBuildings), random);
        if (buildings < Math.min(minBuildings, MIN_ACCEPTED_BUILDINGS)) {
            return Optional.empty();
        }

        addVillagerPieces(context, templateManager, pieces, center, layout.get().lots(), targetBuildings, random);
        addRoadPieces(context, templateManager, pieces, center, layout.get().roads(), roadTemplatesForStyle(villageStyle), random);
        return Optional.of(new VillagePlan(pieces));
    }

    private void placePlan(StructurePiecesCollector collector, VillagePlan plan) {
        for (PiecePlan piece : plan.pieces()) {
            collector.addPiece(new PoolStructurePiece(piece.templateManager(), piece.element(), piece.pos(), piece.element().getGroundLevelDelta(),
                    piece.rotation(), piece.box(), StructureLiquidSettings.APPLY_WATERLOGGING));
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

    private Optional<VillageLayout> generateVillageLayout(Context context, BlockPos center, int radius, int targetBuildings, Random random) {
        Set<Point> nodes = new HashSet<>();
        Set<RoadSegment> segments = new HashSet<>();
        List<RoadTip> branchTips = new ArrayList<>();
        List<Lot> lots = new ArrayList<>();
        Point origin = new Point(0, 0);
        nodes.add(origin);

        List<RoadDirection> primaryDirections = shuffledDirections(random);
        int primaryCount = Math.min(primaryDirections.size(), 3 + random.nextInt(2));
        for (int i = 0; i < primaryCount; i++) {
            growLotRoad(context, center, radius, random, primaryDirections.get(i), nodes, segments, lots, branchTips);
        }

        int maxDistance = maxNodeDistance(nodes);
        if (maxDistance < minRadius / 2) {
            return Optional.empty();
        }

        int desiredLots = targetBuildings * 2;
        int desiredSegments = Math.min(MAX_ROAD_SEGMENTS, Math.max(30, targetBuildings / 2));
        int attempts = targetBuildings * 8;
        while ((lots.size() < desiredLots || segments.size() < desiredSegments)
                && attempts-- > 0 && !branchTips.isEmpty() && segments.size() < MAX_ROAD_SEGMENTS) {
            RoadTip tip = branchTips.get(random.nextInt(branchTips.size()));
            RoadDirection direction = chooseBranchDirection(tip.direction(), random);
            growLotRoad(context, center, radius, random, tip.point(), direction, false, nodes, segments, lots, branchTips);
        }

        List<RoadSegment> usefulSegments = pruneRoadsWithoutLots(segments, lots);
        List<Lot> usefulLots = lotsForRoads(usefulSegments, lots);
        Set<Point> usefulNodes = nodesForSegments(usefulSegments);
        if (usefulLots.size() < MIN_ACCEPTED_BUILDINGS || usefulSegments.size() < 8) {
            return Optional.empty();
        }

        return Optional.of(new VillageLayout(new RoadNetwork(usefulNodes, new HashSet<>(usefulSegments)), usefulLots));
    }

    private void growLotRoad(
            Context context,
            BlockPos center,
            int radius,
            Random random,
            RoadDirection initialDirection,
            Set<Point> nodes,
            Set<RoadSegment> segments,
            List<Lot> lots,
            List<RoadTip> branchTips
    ) {
        growLotRoad(context, center, radius, random, new Point(0, 0), initialDirection, true, nodes, segments, lots, branchTips);
    }

    private void growLotRoad(
            Context context,
            BlockPos center,
            int radius,
            Random random,
            Point start,
            RoadDirection initialDirection,
            boolean primary,
            Set<Point> nodes,
            Set<RoadSegment> segments,
            List<Lot> lots,
            List<RoadTip> branchTips
    ) {
        Point current = start;
        RoadDirection direction = initialDirection;
        int maxSteps = primary ? Math.max(5, radius / 28) : 2 + random.nextInt(4);

        for (int step = 0; step < maxSteps; step++) {
            if (segments.size() >= MAX_ROAD_SEGMENTS) {
                break;
            }
            if (current.distance() >= radius - 16 || (!primary && step > 1 && random.nextInt(100) < 35)) {
                break;
            }

            int length = chooseRoadSegmentLength(random, primary ? MIN_PRIMARY_ROAD_SEGMENT_LENGTH : MIN_ROAD_SEGMENT_LENGTH,
                    primary ? MAX_PRIMARY_ROAD_SEGMENT_LENGTH : MAX_ROAD_SEGMENT_LENGTH);
            RoadDirection chosen = chooseWalkerDirection(context, center, radius, random, current, direction, length, primary, nodes, segments);
            if (chosen == null) {
                break;
            }

            if (!tryAddRoadSegment(context, center, radius, current, chosen, length, nodes, segments)) {
                break;
            }
            current = current.offset(chosen, length);
            direction = chosen;
            addLotsForSegment(center, radius, random, new RoadSegment(current.offset(chosen.opposite(), length), current), lots);

            if (primary || random.nextInt(100) < 45) {
                branchTips.add(new RoadTip(current, direction));
            }
        }
    }

    private boolean tryAddRoadSegment(
            Context context,
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
        if (!isFootprintSuitable(context, footprint, 7, 4)) {
            return false;
        }

        nodes.add(start);
        nodes.add(end);
        segments.add(segment);
        return true;
    }

    private void addRoadPieces(
            Context context,
            StructureTemplateManager templateManager,
            List<PiecePlan> pieces,
            BlockPos center,
            RoadNetwork roads,
            RoadTemplates templates,
            Random random
    ) {
        for (RoadSegment segment : roads.segments()) {
            String template = segment.isHorizontal() ? templates.eastWest() : templates.northSouth();
            for (RoadTile tile : segment.tiles(center)) {
                addPieceAt(context, templateManager, pieces, template, tile.templatePos(context), BlockRotation.NONE,
                        StructurePool.Projection.TERRAIN_MATCHING, false);
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
                if (!isFootprintSuitable(context, footprint, 7, 4)) {
                    continue;
                }
                BlockPos pos = new BlockPos(footprint.getMinX(), getAveragePlacementY(context, footprint, 6), footprint.getMinZ());
                addPieceAt(context, templateManager, pieces, templates.crossing(), pos, BlockRotation.NONE, StructurePool.Projection.TERRAIN_MATCHING, false);
            }
        }
    }

    private int addBuildingPieces(
            Context context,
            StructureTemplateManager templateManager,
            List<PiecePlan> pieces,
            List<BlockBox> occupied,
            BlockPos center,
            RoadNetwork roads,
            List<Lot> lots,
            int targetBuildings,
            Random random
    ) {
        shuffle(lots, random);
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
            if (addRoadsideBuildingPiece(context, templateManager, pieces, occupied, center, roads, building, lot,
                    rotation, StructurePool.Projection.RIGID, true, 4, 10, 3)) {
                placed++;
                if (useVanillaFarm) {
                    vanillaFarmPatches++;
                }
            }
        }

        return placed;
    }

    private int addRequiredBuildings(
            Context context,
            StructureTemplateManager templateManager,
            List<PiecePlan> pieces,
            List<BlockBox> occupied,
            BlockPos center,
            RoadNetwork roads,
            List<Lot> lots,
            Random random
    ) {
        List<Lot> candidates = new ArrayList<>(lots);
        int placed = 0;
        for (RequiredBuilding building : requiredBuildingsForStyle(villageStyle)) {
            shuffle(candidates, random);
            if (!addRequiredBuilding(context, templateManager, pieces, occupied, center, roads, candidates, building)) {
                return placed;
            }
            placed++;
        }
        return placed;
    }

    private boolean addRequiredBuilding(
            Context context,
            StructureTemplateManager templateManager,
            List<PiecePlan> pieces,
            List<BlockBox> occupied,
            BlockPos center,
            RoadNetwork roads,
            List<Lot> lots,
            RequiredBuilding building
    ) {
        for (Lot lot : lots) {
            if (addRoadsideBuildingPiece(context, templateManager, pieces, occupied, center, roads, building.templateId(), lot,
                    chooseBuildingRotation(building.templateId(), lot), StructurePool.Projection.RIGID, building.legacy(), 4, 8, 5)) {
                return true;
            }
        }
        return false;
    }

    private void addVillagerPieces(
            Context context,
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
            BlockPos pos = getVillagerSpawnPos(context, center, candidates.get(i), random);
            BlockBox footprint = new BlockBox(pos.getX(), 0, pos.getZ(), pos.getX(), 0, pos.getZ());
            if (isFootprintSuitable(context, footprint, 2, 1)) {
                addPieceAt(context, templateManager, pieces, VILLAGER, pos, BlockRotation.NONE, StructurePool.Projection.RIGID, false);
            }
        }
    }

    private BlockPos getVillagerSpawnPos(Context context, BlockPos center, Lot lot, Random random) {
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
        return new BlockPos(x, getPlacementY(context, x, z), z);
    }

    private void addLotsForSegment(BlockPos center, int radius, Random random, RoadSegment segment, List<Lot> lots) {
        int count = 2 + segment.length() / 18 + random.nextInt(2);
        RoadDirection direction = segment.direction();
        for (int i = 0; i < count; i++) {
            int along = 4 + random.nextInt(Math.max(1, segment.length() - 7));
            Point roadPoint = segment.pointAlong(along);
            int side = random.nextBoolean() ? -1 : 1;
            int setback = 17 + random.nextInt(8);
            int lateral = random.nextInt(9) - 4;
            int x;
            int z;
            if (segment.isHorizontal()) {
                x = center.getX() + roadPoint.x() + lateral;
                z = center.getZ() + roadPoint.z() + side * setback;
            } else {
                x = center.getX() + roadPoint.x() + side * setback;
                z = center.getZ() + roadPoint.z() + lateral;
            }
            addLotIfValid(lots, center, radius, x, z, direction, side, roadPoint);
        }
    }

    private void addLotIfValid(List<Lot> lots, BlockPos center, int radius, int x, int z, RoadDirection roadDirection, int side, Point roadPoint) {
        if (Math.abs(x - center.getX()) < 46 && Math.abs(z - center.getZ()) < 46) {
            return;
        }
        if (distanceSquared(center, x, z) > (radius + 18) * (radius + 18)) {
            return;
        }
        if (isTooCloseToExistingLot(lots, x, z)) {
            return;
        }
        lots.add(new Lot(x, z, roadDirection, side, roadPoint));
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

    private RoadDirection chooseWalkerDirection(
            Context context,
            BlockPos center,
            int radius,
            Random random,
            Point current,
            RoadDirection direction,
            int length,
            boolean primary,
            Set<Point> nodes,
            Set<RoadSegment> segments
    ) {
        List<RoadDirection> candidates = new ArrayList<>();
        if (random.nextInt(100) < (primary ? 72 : 54)) {
            candidates.add(direction);
        }
        candidates.add(direction.perpendicular(random));
        candidates.add(direction.perpendicular(random));
        if (random.nextInt(100) < 18) {
            candidates.add(direction.opposite());
        }
        candidates.add(direction);
        candidates.add(RoadDirection.random(random));

        for (RoadDirection candidate : candidates) {
            if (canAddRoadSegment(context, center, radius, current, candidate, length, nodes, segments)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean canAddRoadSegment(
            Context context,
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
        if (segments.contains(segment) || hasRoadConflict(center, segment, segments)) {
            return false;
        }

        return isFootprintSuitable(context, segment.footprint(center), 7, 4);
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
            Context context,
            StructureTemplateManager templateManager,
            List<PiecePlan> pieces,
            List<BlockBox> occupied,
            BlockPos center,
            RoadNetwork roads,
            String templateId,
            Lot lot,
            BlockRotation rotation,
            StructurePool.Projection projection,
            boolean legacy,
            int terrainSpread,
            int terrainSampleStep,
            int padding
    ) {
        int[] roadGaps = new int[]{2, 4, 6, 8};
        for (int roadGap : roadGaps) {
            if (addRoadsideBuildingPieceAt(context, templateManager, pieces, occupied, center, roads, templateId, lot,
                    roadGap, rotation, projection, legacy, terrainSpread, terrainSampleStep, padding)) {
                return true;
            }
        }
        return false;
    }

    private boolean addRoadsideBuildingPieceAt(
            Context context,
            StructureTemplateManager templateManager,
            List<PiecePlan> pieces,
            List<BlockBox> occupied,
            BlockPos center,
            RoadNetwork roads,
            String templateId,
            Lot lot,
            int roadGap,
            BlockRotation rotation,
            StructurePool.Projection projection,
            boolean legacy,
            int terrainSpread,
            int terrainSampleStep,
            int padding
    ) {
        StructurePoolElement element = createElement(templateId, projection, legacy);
        BlockBox originBox = element.getBoundingBox(templateManager, BlockPos.ORIGIN, rotation);
        BlockPos posXZ = getRoadAnchoredBuildingPos(center, lot, originBox, roadGap);
        int x = posXZ.getX();
        int z = posXZ.getZ();
        BlockBox horizontalBox = element.getBoundingBox(templateManager, new BlockPos(x, 0, z), rotation);
        if (!isFootprintSuitable(context, horizontalBox, terrainSpread, terrainSampleStep)) {
            return false;
        }

        int y = getAveragePlacementY(context, horizontalBox, terrainSampleStep);
        BlockPos pos = new BlockPos(x, y, z);
        BlockBox box = element.getBoundingBox(templateManager, pos, rotation);
        BlockBox paddedBox = box.expand(padding, 0, padding);
        if (intersectsAnyXZ(paddedBox, occupied)) {
            return false;
        }

        int distanceFromRoad = distanceFromRoads(box, center, roads);
        if (distanceFromRoad <= 0 || distanceFromRoad > MAX_BUILDING_DISTANCE_FROM_ROAD) {
            return false;
        }

        pieces.add(new PiecePlan(templateManager, element, pos, rotation, box));
        occupied.add(paddedBox);
        return true;
    }

    private static BlockPos getRoadAnchoredBuildingPos(BlockPos center, Lot lot, BlockBox originBox, int roadGap) {
        int roadX = center.getX() + lot.roadPoint().x();
        int roadZ = center.getZ() + lot.roadPoint().z();

        if (lot.roadDirection().isHorizontal()) {
            int x = lot.x() - (originBox.getMinX() + originBox.getMaxX()) / 2;
            int z = lot.side() > 0
                    ? roadZ + ROAD_HALF_WIDTH + roadGap - originBox.getMinZ()
                    : roadZ - ROAD_HALF_WIDTH - roadGap - originBox.getMaxZ();
            return new BlockPos(x, 0, z);
        }

        int x = lot.side() > 0
                ? roadX + ROAD_HALF_WIDTH + roadGap - originBox.getMinX()
                : roadX - ROAD_HALF_WIDTH - roadGap - originBox.getMaxX();
        int z = lot.z() - (originBox.getMinZ() + originBox.getMaxZ()) / 2;
        return new BlockPos(x, 0, z);
    }

    private boolean addCenteredPiece(
            Context context,
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
        if (!isFootprintSuitable(context, horizontalBox, terrainSpread, terrainSampleStep)) {
            return false;
        }

        int y = getAveragePlacementY(context, horizontalBox, terrainSampleStep);
        BlockPos pos = new BlockPos(x, y, z);
        BlockBox box = element.getBoundingBox(templateManager, pos, rotation);
        BlockBox paddedBox = box.expand(padding, 0, padding);
        if (intersectsAnyXZ(paddedBox, occupied)) {
            return false;
        }

        pieces.add(new PiecePlan(templateManager, element, pos, rotation, box));
        occupied.add(paddedBox);
        return true;
    }

    private void addPieceAt(
            Context context,
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
        pieces.add(new PiecePlan(templateManager, element, pos, rotation, box));
    }

    private boolean isAreaSuitable(Context context, int centerX, int centerZ, int radius, int maxHeightSpread, int sampleStep) {
        BlockBox box = new BlockBox(centerX - radius, 0, centerZ - radius, centerX + radius, 0, centerZ + radius);
        return isFootprintSuitable(context, box, maxHeightSpread, sampleStep);
    }

    private boolean isLocateAreaSuitable(Context context, int centerX, int centerZ) {
        int centerY = getPlacementY(context, centerX, centerZ);
        int minY = centerY;
        int maxY = centerY;

        for (RoadDirection direction : RoadDirection.values()) {
            int y = getPlacementY(context, centerX + direction.dx * LOCATE_TERRAIN_RADIUS, centerZ + direction.dz * LOCATE_TERRAIN_RADIUS);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        return maxY - minY <= 12;
    }

    private boolean isFootprintSuitable(Context context, BlockBox box, int maxHeightSpread, int sampleStep) {
        TerrainStats stats = sampleTerrain(context, box, sampleStep);
        return !stats.hasFluid() && stats.heightSpread() <= maxHeightSpread;
    }

    private boolean isFootprintHeightSuitable(Context context, BlockBox box, int maxHeightSpread, int sampleStep) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int x : sampleAxis(box.getMinX(), box.getMaxX(), sampleStep)) {
            for (int z : sampleAxis(box.getMinZ(), box.getMaxZ(), sampleStep)) {
                int y = getPlacementY(context, x, z);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }

        return maxY - minY <= maxHeightSpread;
    }

    private TerrainStats sampleTerrain(Context context, BlockBox box, int sampleStep) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int totalY = 0;
        int samples = 0;
        boolean hasFluid = false;

        for (int x : sampleAxis(box.getMinX(), box.getMaxX(), sampleStep)) {
            for (int z : sampleAxis(box.getMinZ(), box.getMaxZ(), sampleStep)) {
                int y = getPlacementY(context, x, z);
                int surfaceY = context.chunkGenerator().getHeightInGround(x, z, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
                VerticalBlockSample sample = context.chunkGenerator().getColumnSample(x, z, context.world(), context.noiseConfig());
                if (hasFluidNearSurface(sample, surfaceY)) {
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

    private int getAveragePlacementY(Context context, BlockBox box, int sampleStep) {
        return sampleTerrain(context, box, sampleStep).averageY();
    }

    private static List<Integer> sampleAxis(int min, int max, int step) {
        List<Integer> values = new ArrayList<>();
        for (int value = min; value <= max; value += step) {
            values.add(value);
        }
        if (values.isEmpty() || values.get(values.size() - 1) != max) {
            values.add(max);
        }
        return values;
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

    private int getPlacementY(Context context, int x, int z) {
        return context.chunkGenerator().getHeightOnGround(x, z, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static int distanceSquared(BlockPos center, int x, int z) {
        int dx = x - center.getX();
        int dz = z - center.getZ();
        return dx * dx + dz * dz;
    }

    private static int maxNodeDistance(Set<Point> nodes) {
        int max = 0;
        for (Point node : nodes) {
            max = Math.max(max, node.distance());
        }
        return max;
    }

    private static boolean isWithinRadius(Point point, int radius) {
        return point.distanceSquared() <= radius * radius;
    }

    private static RoadDirection chooseBranchDirection(RoadDirection current, Random random) {
        int roll = random.nextInt(100);
        if (roll < 55) {
            return current;
        }
        if (roll < 88) {
            return current.perpendicular(random);
        }
        return RoadDirection.random(random);
    }

    private static int chooseRoadSegmentLength(Random random, int minLength, int maxLength) {
        int minSteps = divideRoundUp(minLength, ROAD_TILE_LENGTH);
        int maxSteps = Math.max(minSteps, maxLength / ROAD_TILE_LENGTH);
        return (minSteps + random.nextInt(maxSteps - minSteps + 1)) * ROAD_TILE_LENGTH;
    }

    private static List<RoadDirection> shuffledDirections(Random random) {
        List<RoadDirection> directions = new ArrayList<>(List.of(RoadDirection.values()));
        shuffle(directions, random);
        return directions;
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

    private record PiecePlan(
            StructureTemplateManager templateManager,
            StructurePoolElement element,
            BlockPos pos,
            BlockRotation rotation,
            BlockBox box
    ) {
    }

    private record RoadTemplates(String northSouth, String eastWest, String crossing) {
    }

    private record RequiredBuilding(String templateId, boolean legacy) {
    }

    private record TerrainStats(int minY, int maxY, int averageY, boolean hasFluid) {
        private int heightSpread() {
            return maxY - minY;
        }
    }

    private record Lot(int x, int z, RoadDirection roadDirection, int side, Point roadPoint) {
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

    private record RoadTip(Point point, RoadDirection direction) {
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
        private BlockPos templatePos(Context context) {
            int y = context.chunkGenerator()
                    .getHeightOnGround(footprint.getCenter().getX(), footprint.getCenter().getZ(), Heightmap.Type.WORLD_SURFACE_WG,
                            context.world(), context.noiseConfig());
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

        private RoadDirection perpendicular(Random random) {
            return switch (this) {
                case NORTH, SOUTH -> random.nextBoolean() ? WEST : EAST;
                case WEST, EAST -> random.nextBoolean() ? NORTH : SOUTH;
            };
        }

        private RoadDirection opposite() {
            return switch (this) {
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case WEST -> EAST;
                case EAST -> WEST;
            };
        }

        private static RoadDirection random(Random random) {
            return values()[random.nextInt(values().length)];
        }
    }
}
