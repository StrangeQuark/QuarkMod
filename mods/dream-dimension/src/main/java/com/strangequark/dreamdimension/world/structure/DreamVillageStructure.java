package com.strangequark.dreamdimension.world.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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

public final class DreamVillageStructure extends Structure {
    public static final MapCodec<DreamVillageStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
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
    ).apply(instance, DreamVillageStructure::new));

    private static final int MIN_SURFACE_ABOVE_BOTTOM = 16;
    private static final int NEAR_HEIGHT_TOLERANCE = 24;
    private static final int FAR_HEIGHT_TOLERANCE = 40;
    private static final int MIN_NEAR_LAND_SAMPLES = 7;
    private static final int MIN_FAR_LAND_SAMPLES = 15;
    private static final int[] NEAR_SAMPLE_OFFSETS = {-16, 0, 16};
    private static final int[] FAR_SAMPLE_OFFSETS = {-48, -24, 0, 24, 48};

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

    public DreamVillageStructure(
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

    @Override
    protected Optional<StructurePosition> getStructurePosition(Context context) {
        ChunkPos chunkPos = context.chunkPos();
        int startY = startHeight.get(context.random(), new HeightContext(context.chunkGenerator(), context.world()));
        BlockPos startPos = new BlockPos(chunkPos.getStartX(), startY, chunkPos.getStartZ());

        if (!hasEnoughLandSupport(context, startPos.getX(), startPos.getZ())) {
            return Optional.empty();
        }

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
        return ModStructureTypes.DREAM_VILLAGE;
    }

    private static boolean hasEnoughLandSupport(Context context, int centerX, int centerZ) {
        int centerY = surfaceY(context, centerX, centerZ);
        if (!isValidSurface(context, centerY)) {
            return false;
        }

        int nearLand = countSupportedSamples(context, centerX, centerZ, centerY, NEAR_SAMPLE_OFFSETS, NEAR_HEIGHT_TOLERANCE);
        if (nearLand < MIN_NEAR_LAND_SAMPLES) {
            return false;
        }

        int farLand = countSupportedSamples(context, centerX, centerZ, centerY, FAR_SAMPLE_OFFSETS, FAR_HEIGHT_TOLERANCE);
        return farLand >= MIN_FAR_LAND_SAMPLES;
    }

    private static int countSupportedSamples(Context context, int centerX, int centerZ, int centerY, int[] offsets, int heightTolerance) {
        int landSamples = 0;
        for (int dx : offsets) {
            for (int dz : offsets) {
                int surfaceY = surfaceY(context, centerX + dx, centerZ + dz);
                if (isValidSurface(context, surfaceY) && Math.abs(surfaceY - centerY) <= heightTolerance) {
                    landSamples++;
                }
            }
        }
        return landSamples;
    }

    private static int surfaceY(Context context, int x, int z) {
        return context.chunkGenerator().getHeight(
                x,
                z,
                Heightmap.Type.WORLD_SURFACE_WG,
                context.world(),
                context.noiseConfig()
        ) - 1;
    }

    private static boolean isValidSurface(Context context, int surfaceY) {
        return surfaceY >= context.world().getBottomY() + MIN_SURFACE_ABOVE_BOTTOM
                && surfaceY <= context.world().getTopYInclusive();
    }
}
