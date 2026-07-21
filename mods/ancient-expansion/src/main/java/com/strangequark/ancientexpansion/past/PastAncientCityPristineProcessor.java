package com.strangequark.ancientexpansion.past;

import com.mojang.serialization.MapCodec;
import com.strangequark.ancientexpansion.AncientExpansionMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.processor.StructureProcessor;
import net.minecraft.structure.processor.StructureProcessorType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

public final class PastAncientCityPristineProcessor extends StructureProcessor {
    private static final String VANILLA_ANCIENT_CITY_POOL_PREFIX = "minecraft:ancient_city";
    private static final String PAST_ANCIENT_CITY_POOL_PREFIX = "quarkmod:past_ancient_city";

    public static final MapCodec<PastAncientCityPristineProcessor> CODEC = MapCodec.unit(PastAncientCityPristineProcessor::new);
    public static final StructureProcessorType<PastAncientCityPristineProcessor> TYPE = StructureProcessorType.register(
            AncientExpansionMod.id("past_ancient_city_pristine").toString(),
            CODEC
    );

    public static void register() {
    }

    @Override
    public StructureTemplate.StructureBlockInfo process(
            WorldView world,
            BlockPos pos,
            BlockPos pivot,
            StructureTemplate.StructureBlockInfo originalBlockInfo,
            StructureTemplate.StructureBlockInfo currentBlockInfo,
            StructurePlacementData data
    ) {
        BlockState state = pristineState(currentBlockInfo.state());
        NbtCompound nbt = rewriteJigsawPool(currentBlockInfo);
        if (state == currentBlockInfo.state() && nbt == currentBlockInfo.nbt()) {
            return currentBlockInfo;
        }

        return new StructureTemplate.StructureBlockInfo(currentBlockInfo.pos(), state, nbt);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return TYPE;
    }

    private static NbtCompound rewriteJigsawPool(StructureTemplate.StructureBlockInfo blockInfo) {
        if (!blockInfo.state().isOf(Blocks.JIGSAW) || blockInfo.nbt() == null) {
            return blockInfo.nbt();
        }

        String pool = blockInfo.nbt().getString("pool").orElse("");
        if (!pool.startsWith(VANILLA_ANCIENT_CITY_POOL_PREFIX)) {
            return blockInfo.nbt();
        }

        NbtCompound copy = blockInfo.nbt().copy();
        copy.putString("pool", PAST_ANCIENT_CITY_POOL_PREFIX + pool.substring(VANILLA_ANCIENT_CITY_POOL_PREFIX.length()));
        return copy;
    }

    private static BlockState pristineState(BlockState state) {
        if (state.isOf(Blocks.SCULK)) {
            return Blocks.POLISHED_DEEPSLATE.getDefaultState();
        }
        if (state.isOf(Blocks.SCULK_VEIN)
                || state.isOf(Blocks.SCULK_CATALYST)
                || state.isOf(Blocks.SCULK_SENSOR)
                || state.isOf(Blocks.CALIBRATED_SCULK_SENSOR)
                || state.isOf(Blocks.SCULK_SHRIEKER)) {
            return Blocks.AIR.getDefaultState();
        }
        if (state.isOf(Blocks.CRACKED_DEEPSLATE_BRICKS)) {
            return Blocks.DEEPSLATE_BRICKS.getDefaultState();
        }
        if (state.isOf(Blocks.CRACKED_DEEPSLATE_TILES)) {
            return Blocks.DEEPSLATE_TILES.getDefaultState();
        }
        return state;
    }
}
