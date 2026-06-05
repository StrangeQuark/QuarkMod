package com.strangequark.dreamdimension.village;

import com.strangequark.dreamdimension.DreamDimensionMod;
import com.strangequark.dreamdimension.block.ModBlocks;
import com.strangequark.dreamdimension.world.DreamDimensionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.poi.PointOfInterestType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class DreamVillageWorkstations {
    public static final RegistryKey<PointOfInterestType> SOMNARIUM_POI = poiKey("somnarium");
    public static final RegistryKey<PointOfInterestType> LUCID_CHARTING_TABLE_POI = poiKey("lucid_charting_table");
    public static final RegistryKey<PointOfInterestType> ECHO_LECTERN_POI = poiKey("echo_lectern");
    public static final RegistryKey<PointOfInterestType> STARGAZER_TABLE_POI = poiKey("stargazer_table");
    public static final RegistryKey<PointOfInterestType> MEMORY_LOOM_POI = poiKey("memory_loom");
    public static final RegistryKey<PointOfInterestType> WAKING_ANVIL_POI = poiKey("waking_anvil");
    public static final RegistryKey<PointOfInterestType> DRIFT_COMPOSTER_POI = poiKey("drift_composter");

    private static final int CHUNK_REPLACEMENTS_PER_TICK = 4;
    private static final Set<Long> PENDING_REPLACEMENT_CHUNKS = new HashSet<>();

    private DreamVillageWorkstations() {
    }

    public static void register() {
        registerPois();
        ServerChunkEvents.CHUNK_GENERATE.register(DreamVillageWorkstations::queueWorkstationReplacement);
        ServerChunkEvents.CHUNK_LOAD.register(DreamVillageWorkstations::queueWorkstationReplacement);
        ServerTickEvents.END_WORLD_TICK.register(DreamVillageWorkstations::replaceQueuedWorkstations);
    }

    public static Optional<RegistryKey<VillagerProfession>> professionFor(Block block) {
        if (block == ModBlocks.SOMNARIUM) {
            return Optional.of(DreamVillagers.SOMNOLOGIST);
        }
        if (block == ModBlocks.LUCID_CHARTING_TABLE) {
            return Optional.of(DreamVillagers.LUCID_CARTOGRAPHER);
        }
        if (block == ModBlocks.ECHO_LECTERN) {
            return Optional.of(DreamVillagers.ECHO_SEER);
        }
        if (block == ModBlocks.STARGAZER_TABLE) {
            return Optional.of(DreamVillagers.STARGAZER);
        }
        if (block == ModBlocks.MEMORY_LOOM) {
            return Optional.of(DreamVillagers.MEMORY_WEAVER);
        }
        if (block == ModBlocks.WAKING_ANVIL) {
            return Optional.of(DreamVillagers.MENDER_OF_WAKING);
        }
        if (block == ModBlocks.DRIFT_COMPOSTER) {
            return Optional.of(DreamVillagers.DRIFT_GARDENER);
        }
        return Optional.empty();
    }

    private static void registerPois() {
        PointOfInterestHelper.register(SOMNARIUM_POI.getValue(), 1, 1, ModBlocks.SOMNARIUM);
        PointOfInterestHelper.register(LUCID_CHARTING_TABLE_POI.getValue(), 1, 1, ModBlocks.LUCID_CHARTING_TABLE);
        PointOfInterestHelper.register(ECHO_LECTERN_POI.getValue(), 1, 1, ModBlocks.ECHO_LECTERN);
        PointOfInterestHelper.register(STARGAZER_TABLE_POI.getValue(), 1, 1, ModBlocks.STARGAZER_TABLE);
        PointOfInterestHelper.register(MEMORY_LOOM_POI.getValue(), 1, 1, ModBlocks.MEMORY_LOOM);
        PointOfInterestHelper.register(WAKING_ANVIL_POI.getValue(), 1, 1, ModBlocks.WAKING_ANVIL);
        PointOfInterestHelper.register(DRIFT_COMPOSTER_POI.getValue(), 1, 1, ModBlocks.DRIFT_COMPOSTER);
    }

    private static void queueWorkstationReplacement(ServerWorld world, WorldChunk chunk) {
        if (!isDreamWorld(world)) {
            return;
        }

        PENDING_REPLACEMENT_CHUNKS.add(chunk.getPos().toLong());
    }

    private static void replaceQueuedWorkstations(ServerWorld world) {
        if (!isDreamWorld(world) || PENDING_REPLACEMENT_CHUNKS.isEmpty()) {
            return;
        }

        int processed = 0;
        Iterator<Long> chunks = PENDING_REPLACEMENT_CHUNKS.iterator();
        while (chunks.hasNext() && processed < CHUNK_REPLACEMENTS_PER_TICK) {
            long chunkPos = chunks.next();
            chunks.remove();

            WorldChunk chunk = world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(chunkPos), ChunkPos.getPackedZ(chunkPos));
            if (chunk != null) {
                replaceVanillaWorkstations(world, chunk);
                processed++;
            }
        }
    }

    private static void replaceVanillaWorkstations(ServerWorld world, WorldChunk chunk) {
        List<Replacement> replacements = new ArrayList<>();
        chunk.forEachBlockMatchingPredicate(
                state -> replacementFor(state).isPresent(),
                (pos, state) -> replacementFor(state).ifPresent(replacement -> replacements.add(new Replacement(pos.toImmutable(), replacement)))
        );

        for (Replacement replacement : replacements) {
            world.setBlockState(replacement.pos(), replacement.state(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        }
    }

    private static Optional<BlockState> replacementFor(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.BREWING_STAND || block == Blocks.CAULDRON) {
            return Optional.of(ModBlocks.SOMNARIUM.getDefaultState());
        }
        if (block == Blocks.CARTOGRAPHY_TABLE) {
            return Optional.of(ModBlocks.LUCID_CHARTING_TABLE.getDefaultState());
        }
        if (block == Blocks.LECTERN) {
            return Optional.of(ModBlocks.ECHO_LECTERN.getDefaultState());
        }
        if (block == Blocks.FLETCHING_TABLE) {
            return Optional.of(ModBlocks.STARGAZER_TABLE.getDefaultState());
        }
        if (block == Blocks.LOOM) {
            return Optional.of(ModBlocks.MEMORY_LOOM.getDefaultState());
        }
        if (block == Blocks.GRINDSTONE
                || block == Blocks.SMITHING_TABLE
                || block == Blocks.BLAST_FURNACE
                || block == Blocks.STONECUTTER) {
            return Optional.of(ModBlocks.WAKING_ANVIL.getDefaultState());
        }
        if (block == Blocks.COMPOSTER || block == Blocks.BARREL || block == Blocks.SMOKER) {
            return Optional.of(ModBlocks.DRIFT_COMPOSTER.getDefaultState());
        }
        return Optional.empty();
    }

    private static boolean isDreamWorld(World world) {
        return world.getRegistryKey().equals(DreamDimensionEvents.DREAM_WORLD);
    }

    private static RegistryKey<PointOfInterestType> poiKey(String path) {
        return RegistryKey.of(RegistryKeys.POINT_OF_INTEREST_TYPE, DreamDimensionMod.id(path));
    }

    private record Replacement(BlockPos pos, BlockState state) {
    }
}
