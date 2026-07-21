package com.strangequark.ancientexpansion.past;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.strangequark.ancientexpansion.worldgen.ModStructureTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.StructureLiquidSettings;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolBasedGenerator;
import net.minecraft.structure.pool.alias.StructurePoolAliasBinding;
import net.minecraft.structure.pool.alias.StructurePoolAliasLookup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.world.gen.heightprovider.HeightProvider;
import net.minecraft.world.gen.structure.DimensionPadding;
import net.minecraft.world.gen.structure.JigsawStructure;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PastAncientCityStructure extends Structure {
    public static final MapCodec<PastAncientCityStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            configCodecBuilder(instance),
            StructurePool.REGISTRY_CODEC.fieldOf("start_pool").forGetter(structure -> structure.startPool),
            Identifier.CODEC.optionalFieldOf("start_jigsaw_name").forGetter(structure -> structure.startJigsawName),
            Codec.intRange(0, JigsawStructure.MAX_SIZE).fieldOf("size").forGetter(structure -> structure.size),
            HeightProvider.CODEC.fieldOf("start_height").forGetter(structure -> structure.startHeight),
            Codec.BOOL.fieldOf("use_expansion_hack").forGetter(structure -> structure.useExpansionHack),
            Heightmap.Type.CODEC.optionalFieldOf("project_start_to_heightmap").forGetter(structure -> structure.projectStartToHeightmap),
            Codec.intRange(1, 128).fieldOf("max_distance_from_center").forGetter(structure -> structure.maxDistanceFromCenter),
            StructurePoolAliasBinding.CODEC.listOf().optionalFieldOf("pool_aliases", List.of()).forGetter(structure -> structure.poolAliasBindings),
            DimensionPadding.CODEC.optionalFieldOf("dimension_padding", JigsawStructure.DEFAULT_DIMENSION_PADDING).forGetter(structure -> structure.dimensionPadding),
            StructureLiquidSettings.codec.optionalFieldOf("liquid_settings", JigsawStructure.DEFAULT_LIQUID_SETTINGS).forGetter(structure -> structure.liquidSettings)
    ).apply(instance, PastAncientCityStructure::new));

    private static final Set<AllowedStart> ALLOWED_STARTS = ConcurrentHashMap.newKeySet();

    private final RegistryEntry<StructurePool> startPool;
    private final Optional<Identifier> startJigsawName;
    private final int size;
    private final HeightProvider startHeight;
    private final boolean useExpansionHack;
    private final Optional<Heightmap.Type> projectStartToHeightmap;
    private final int maxDistanceFromCenter;
    private final List<StructurePoolAliasBinding> poolAliasBindings;
    private final DimensionPadding dimensionPadding;
    private final StructureLiquidSettings liquidSettings;

    public PastAncientCityStructure(
            Config config,
            RegistryEntry<StructurePool> startPool,
            Optional<Identifier> startJigsawName,
            int size,
            HeightProvider startHeight,
            boolean useExpansionHack,
            Optional<Heightmap.Type> projectStartToHeightmap,
            int maxDistanceFromCenter,
            List<StructurePoolAliasBinding> poolAliasBindings,
            DimensionPadding dimensionPadding,
            StructureLiquidSettings liquidSettings
    ) {
        super(config);
        this.startPool = startPool;
        this.startJigsawName = startJigsawName;
        this.size = size;
        this.startHeight = startHeight;
        this.useExpansionHack = useExpansionHack;
        this.projectStartToHeightmap = projectStartToHeightmap;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.poolAliasBindings = poolAliasBindings;
        this.dimensionPadding = dimensionPadding;
        this.liquidSettings = liquidSettings;
    }

    public static void allowStart(long worldSeed, ChunkPos sourceStartChunk) {
        ALLOWED_STARTS.add(new AllowedStart(worldSeed, sourceStartChunk.toLong()));
    }

    @Override
    protected Optional<StructurePosition> getStructurePosition(Context context) {
        if (!ALLOWED_STARTS.contains(new AllowedStart(context.seed(), context.chunkPos().toLong()))) {
            return Optional.empty();
        }

        int startY = startHeight.get(context.random(), new HeightContext(context.chunkGenerator(), context.world()));
        BlockPos startPos = new BlockPos(context.chunkPos().getStartX(), startY, context.chunkPos().getStartZ());
        return StructurePoolBasedGenerator.generate(
                context,
                startPool,
                startJigsawName,
                size,
                startPos,
                useExpansionHack,
                projectStartToHeightmap,
                maxDistanceFromCenter,
                StructurePoolAliasLookup.create(poolAliasBindings, startPos, context.seed()),
                dimensionPadding,
                liquidSettings
        );
    }

    @Override
    public StructureType<?> getType() {
        return ModStructureTypes.PAST_ANCIENT_CITY;
    }

    private record AllowedStart(long worldSeed, long chunkPos) {
    }
}
