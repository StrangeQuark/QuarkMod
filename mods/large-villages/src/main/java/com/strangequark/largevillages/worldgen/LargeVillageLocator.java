package com.strangequark.largevillages.worldgen;

import com.mojang.datafixers.util.Pair;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureSet;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class LargeVillageLocator {
    private static final int[] FAST_BIOME_SAMPLE_Y = {64};
    private static final int RING_BATCH_SIZE = 16;

    private LargeVillageLocator() {
    }

    public static boolean canHandle(RegistryEntryList<Structure> structures) {
        boolean hasStructure = false;
        for (RegistryEntry<Structure> structure : structures) {
            hasStructure = true;
            if (!(structure.value() instanceof LargeVillageStructure)) {
                return false;
            }
        }
        return hasStructure;
    }

    @Nullable
    public static Pair<BlockPos, RegistryEntry<Structure>> locate(
            ChunkGenerator chunkGenerator,
            ServerWorld world,
            RegistryEntryList<Structure> structures,
            BlockPos center,
            int radius
    ) {
        Pair<BlockPos, RegistryEntry<Structure>> likelyResult = locate(chunkGenerator, world, structures, center, radius, true);
        return likelyResult != null ? likelyResult : locate(chunkGenerator, world, structures, center, radius, false);
    }

    @Nullable
    private static Pair<BlockPos, RegistryEntry<Structure>> locate(
            ChunkGenerator chunkGenerator,
            ServerWorld world,
            RegistryEntryList<Structure> structures,
            BlockPos center,
            int radius,
            boolean requireLikelyBiome
    ) {
        StructurePlacementCalculator calculator = world.getChunkManager().getStructurePlacementCalculator();
        List<PlacementGroup> groups = collectPlacementGroups(calculator, structures);
        if (groups == null || groups.isEmpty()) {
            return null;
        }

        int centerChunkX = ChunkSectionPos.getSectionCoord(center.getX());
        int centerChunkZ = ChunkSectionPos.getSectionCoord(center.getZ());
        long seed = calculator.getStructureSeed();
        Executor executor = Util.getMainWorkerExecutor().named("largeVillageLocate");

        Pair<BlockPos, RegistryEntry<Structure>> closestResult = null;
        double closestDistance = Double.MAX_VALUE;

        for (int batchStart = 0; batchStart <= radius; batchStart += RING_BATCH_SIZE) {
            int batchEnd = Math.min(radius, batchStart + RING_BATCH_SIZE - 1);
            List<List<RingSearch>> batch = new ArrayList<>(batchEnd - batchStart + 1);

            for (int searchRadius = batchStart; searchRadius <= batchEnd; searchRadius++) {
                List<RingSearch> ringSearches = new ArrayList<>(groups.size());
                for (PlacementGroup group : groups) {
                    ringSearches.add(startRandomSpreadRingSearch(
                            chunkGenerator,
                            world,
                            calculator,
                            group,
                            centerChunkX,
                            centerChunkZ,
                            searchRadius,
                            seed,
                            executor,
                            requireLikelyBiome
                    ));
                }
                batch.add(ringSearches);
            }

            for (int ringIndex = 0; ringIndex < batch.size(); ringIndex++) {
                boolean foundInRing = false;

                for (RingSearch search : batch.get(ringIndex)) {
                    Pair<BlockPos, RegistryEntry<Structure>> result = finishRandomSpreadRingSearch(search);
                    if (result != null) {
                        foundInRing = true;
                        double distance = center.getSquaredDistance(result.getFirst());
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closestResult = result;
                        }
                    }
                }

                if (foundInRing) {
                    cancelRemaining(batch, ringIndex + 1);
                    return closestResult;
                }
            }
        }

        return closestResult;
    }

    @Nullable
    private static List<PlacementGroup> collectPlacementGroups(StructurePlacementCalculator calculator, RegistryEntryList<Structure> structures) {
        List<RegistryEntry<Structure>> requestedStructures = new ArrayList<>();
        for (RegistryEntry<Structure> structure : structures) {
            requestedStructures.add(structure);
        }

        List<PlacementGroup> groups = new ArrayList<>();
        for (RegistryEntry<StructureSet> structureSet : calculator.getStructureSets()) {
            StructureSet set = structureSet.value();
            List<RegistryEntry<Structure>> requestedInSet = requestedInSet(requestedStructures, set);
            if (requestedInSet.isEmpty()) {
                continue;
            }
            if (!(set.placement() instanceof RandomSpreadStructurePlacement randomSpreadPlacement)) {
                return null;
            }
            for (StructureSet.WeightedEntry weightedEntry : set.structures()) {
                if (!(weightedEntry.structure().value() instanceof LargeVillageStructure)) {
                    return null;
                }
            }
            groups.add(new PlacementGroup(randomSpreadPlacement, requestedInSet, set.structures()));
        }
        return groups;
    }

    private static List<RegistryEntry<Structure>> requestedInSet(List<RegistryEntry<Structure>> requestedStructures, StructureSet set) {
        List<RegistryEntry<Structure>> requestedInSet = new ArrayList<>();
        for (RegistryEntry<Structure> requestedStructure : requestedStructures) {
            for (StructureSet.WeightedEntry weightedEntry : set.structures()) {
                if (requestedStructure.value() == weightedEntry.structure().value()) {
                    requestedInSet.add(requestedStructure);
                    break;
                }
            }
        }
        return requestedInSet;
    }

    private static RingSearch startRandomSpreadRingSearch(
            ChunkGenerator chunkGenerator,
            ServerWorld world,
            StructurePlacementCalculator calculator,
            PlacementGroup group,
            int centerChunkX,
            int centerChunkZ,
            int radius,
            long seed,
            Executor executor,
            boolean requireLikelyBiome
    ) {
        List<ChunkPos> candidates = collectRingCandidates(
                chunkGenerator,
                calculator,
                group,
                centerChunkX,
                centerChunkZ,
                radius,
                seed,
                requireLikelyBiome
        );
        if (candidates.isEmpty()) {
            return new RingSearch(List.of());
        }

        List<CompletableFuture<CandidateResult>> futures = new ArrayList<>(candidates.size());
        for (ChunkPos candidate : candidates) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> evaluateCandidate(chunkGenerator, world, calculator, group, candidate),
                    executor
            ));
        }

        return new RingSearch(futures);
    }

    @Nullable
    private static Pair<BlockPos, RegistryEntry<Structure>> finishRandomSpreadRingSearch(RingSearch search) {
        if (search.futures().isEmpty()) {
            return null;
        }

        CompletableFuture.allOf(search.futures().toArray(CompletableFuture[]::new)).join();
        for (CompletableFuture<CandidateResult> future : search.futures()) {
            CandidateResult result = future.join();
            if (result.structure() != null) {
                return Pair.of(result.locatePos(), result.structure());
            }
        }
        return null;
    }

    private static void cancelRemaining(List<List<RingSearch>> batch, int firstRingIndex) {
        for (int ringIndex = firstRingIndex; ringIndex < batch.size(); ringIndex++) {
            for (RingSearch search : batch.get(ringIndex)) {
                cancelFutures(search.futures());
            }
        }
    }

    private static void cancelFutures(List<CompletableFuture<CandidateResult>> futures) {
        for (CompletableFuture<CandidateResult> future : futures) {
            future.cancel(false);
        }
    }

    private static List<ChunkPos> collectRingCandidates(
            ChunkGenerator chunkGenerator,
            StructurePlacementCalculator calculator,
            PlacementGroup group,
            int centerChunkX,
            int centerChunkZ,
            int radius,
            long seed,
            boolean requireLikelyBiome
    ) {
        RandomSpreadStructurePlacement placement = group.placement();
        int spacing = placement.getSpacing();
        List<ChunkPos> candidates = new ArrayList<>(Math.max(1, radius * 8));

        for (int ringX = -radius; ringX <= radius; ringX++) {
            boolean onHorizontalEdge = ringX == -radius || ringX == radius;

            for (int ringZ = -radius; ringZ <= radius; ringZ++) {
                boolean onVerticalEdge = ringZ == -radius || ringZ == radius;
                if (onHorizontalEdge || onVerticalEdge) {
                    int chunkX = centerChunkX + spacing * ringX;
                    int chunkZ = centerChunkZ + spacing * ringZ;
                    ChunkPos startChunk = placement.getStartChunk(seed, chunkX, chunkZ);
                    if (placement.shouldGenerate(calculator, startChunk.x, startChunk.z)
                            && (!requireLikelyBiome || hasLikelyRequestedBiome(chunkGenerator, calculator, group, startChunk))) {
                        candidates.add(startChunk);
                    }
                }
            }
        }

        return candidates;
    }

    private static boolean hasLikelyRequestedBiome(
            ChunkGenerator chunkGenerator,
            StructurePlacementCalculator calculator,
            PlacementGroup group,
            ChunkPos chunkPos
    ) {
        int biomeX = BiomeCoords.fromBlock(chunkPos.getCenterX());
        int biomeZ = BiomeCoords.fromBlock(chunkPos.getCenterZ());

        for (int y : FAST_BIOME_SAMPLE_Y) {
            RegistryEntry<Biome> biome = chunkGenerator.getBiomeSource().getBiome(
                    biomeX,
                    BiomeCoords.fromBlock(y),
                    biomeZ,
                    calculator.getNoiseConfig().getMultiNoiseSampler()
            );
            for (RegistryEntry<Structure> requestedStructure : group.requestedStructures()) {
                if (requestedStructure.value().getValidBiomes().contains(biome)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static CandidateResult evaluateCandidate(
            ChunkGenerator chunkGenerator,
            ServerWorld world,
            StructurePlacementCalculator calculator,
            PlacementGroup group,
            ChunkPos chunkPos
    ) {
        LargeVillageStructure.TerrainSampler terrain = null;
        for (StructureSet.WeightedEntry weightedEntry : weightedOrder(group.weightedEntries(), calculator.getStructureSeed(), chunkPos)) {
            RegistryEntry<Structure> structureEntry = weightedEntry.structure();
            LargeVillageStructure structure = (LargeVillageStructure) structureEntry.value();
            Structure.Context context = createContext(chunkGenerator, world, calculator, chunkPos, structure);
            if (terrain == null) {
                terrain = new LargeVillageStructure.TerrainSampler(context);
            }

            if (structure.canGenerateAt(context, terrain)) {
                return new CandidateResult(
                        containsStructure(group.requestedStructures(), structureEntry) ? structureEntry : null,
                        group.placement().getLocatePos(chunkPos)
                );
            }
        }

        return CandidateResult.MISS;
    }

    private static Structure.Context createContext(
            ChunkGenerator chunkGenerator,
            ServerWorld world,
            StructurePlacementCalculator calculator,
            ChunkPos chunkPos,
            Structure structure
    ) {
        DynamicRegistryManager registryManager = world.getRegistryManager();
        NoiseConfig noiseConfig = calculator.getNoiseConfig();
        StructureTemplateManager templateManager = world.getStructureTemplateManager();
        return new Structure.Context(
                registryManager,
                chunkGenerator,
                chunkGenerator.getBiomeSource(),
                noiseConfig,
                templateManager,
                calculator.getStructureSeed(),
                chunkPos,
                world,
                structure.getValidBiomes()::contains
        );
    }

    private static List<StructureSet.WeightedEntry> weightedOrder(List<StructureSet.WeightedEntry> weightedEntries, long seed, ChunkPos chunkPos) {
        if (weightedEntries.size() <= 1) {
            return weightedEntries;
        }

        List<StructureSet.WeightedEntry> remaining = new ArrayList<>(weightedEntries);
        List<StructureSet.WeightedEntry> ordered = new ArrayList<>(weightedEntries.size());
        ChunkRandom random = new ChunkRandom(new CheckedRandom(0L));
        random.setCarverSeed(seed, chunkPos.x, chunkPos.z);
        int totalWeight = 0;
        for (StructureSet.WeightedEntry weightedEntry : remaining) {
            totalWeight += weightedEntry.weight();
        }

        while (!remaining.isEmpty()) {
            int chosenWeight = random.nextInt(totalWeight);
            int chosenIndex = 0;

            for (StructureSet.WeightedEntry weightedEntry : remaining) {
                chosenWeight -= weightedEntry.weight();
                if (chosenWeight < 0) {
                    break;
                }
                chosenIndex++;
            }

            StructureSet.WeightedEntry chosen = remaining.remove(chosenIndex);
            ordered.add(chosen);
            totalWeight -= chosen.weight();
        }

        return ordered;
    }

    private static boolean containsStructure(List<RegistryEntry<Structure>> structures, RegistryEntry<Structure> target) {
        for (RegistryEntry<Structure> structure : structures) {
            if (structure.value() == target.value()) {
                return true;
            }
        }
        return false;
    }

    private record PlacementGroup(
            RandomSpreadStructurePlacement placement,
            List<RegistryEntry<Structure>> requestedStructures,
            List<StructureSet.WeightedEntry> weightedEntries
    ) {
    }

    private record CandidateResult(@Nullable RegistryEntry<Structure> structure, @Nullable BlockPos locatePos) {
        private static final CandidateResult MISS = new CandidateResult(null, null);
    }

    private record RingSearch(List<CompletableFuture<CandidateResult>> futures) {
    }

}
