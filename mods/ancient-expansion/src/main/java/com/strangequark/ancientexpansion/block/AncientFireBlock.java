package com.strangequark.ancientexpansion.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FireBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.TntBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;

public class AncientFireBlock extends FireBlock {
    private static final int MAX_AGE = 15;
    private static final int MIN_TICK_DELAY = 24;
    private static final int TICK_DELAY_RANDOM_BOUND = 10;
    private static final int DIRECT_SPREAD_MULTIPLIER = 1;
    private static final int INDIRECT_SPREAD_MULTIPLIER = 1;
    private static final int MAX_SPREAD_CHANCE = 70;
    private static final int BURNOUT_AGE = 5;

    public AncientFireBlock(Settings settings) {
        super(settings);
    }

    public BlockState getAncientFireState(BlockView world, BlockPos pos) {
        return getStateForPosition(world, pos);
    }

    public static boolean canPlaceAt(World world, BlockPos pos) {
        return world.getBlockState(pos).isAir()
                && ModBlocks.ANCIENT_FIRE.getAncientFireState(world, pos).canPlaceAt(world, pos);
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        world.scheduleBlockTick(pos, this, getTickDelay(random));
        if (!world.getGameRules().getBoolean(GameRules.DO_FIRE_TICK)) {
            return;
        }
        if (!world.getGameRules().getBoolean(GameRules.ALLOW_FIRE_TICKS_AWAY_FROM_PLAYER) && !world.shouldTickBlockAt(pos)) {
            return;
        }
        if (!state.canPlaceAt(world, pos)) {
            world.removeBlock(pos, false);
            return;
        }
        if (world.isRaining() && isRainingAround(world, pos) && random.nextFloat() < 0.35F) {
            world.removeBlock(pos, false);
            return;
        }

        int age = state.get(AGE);
        int nextAge = Math.min(MAX_AGE, age + random.nextInt(3));
        if (nextAge != age) {
            state = state.with(AGE, nextAge);
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
        }

        boolean hasFlammableNeighbor = hasFlammableNeighbor(world, pos);
        boolean hasFlammableBase = isFlammable(world.getBlockState(pos.down()));
        if (!hasFlammableNeighbor) {
            if (!hasSolidTop(world, pos.down()) || nextAge > BURNOUT_AGE || random.nextInt(4) == 0) {
                world.removeBlock(pos, false);
            }
            return;
        }
        if (!hasFlammableBase && nextAge >= MAX_AGE && random.nextInt(3) == 0) {
            world.removeBlock(pos, false);
            return;
        }

        for (Direction direction : Direction.values()) {
            trySpreadTo(world, pos.offset(direction), random, nextAge);
        }
        trySpreadAround(world, pos, random, nextAge);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(
            BlockState state,
            WorldView world,
            ScheduledTickView tickView,
            BlockPos pos,
            Direction direction,
            BlockPos neighborPos,
            BlockState neighborState,
            Random random
    ) {
        if (!canPlaceAt(state, world, pos)) {
            return Blocks.AIR.getDefaultState();
        }
        return getAncientFireState(world, pos).with(AGE, state.get(AGE));
    }

    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        world.scheduleBlockTick(pos, this, getTickDelay(world.random));
    }

    @Override
    protected boolean isFlammable(BlockState state) {
        return getBurnChance(state) > 0;
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return hasSolidTop(world, pos.down()) || hasFlammableNeighbor(world, pos);
    }

    @Override
    protected net.minecraft.util.shape.VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return BASE_SHAPE;
    }

    private static int getTickDelay(Random random) {
        return MIN_TICK_DELAY + random.nextInt(TICK_DELAY_RANDOM_BOUND);
    }

    private void trySpreadTo(ServerWorld world, BlockPos pos, Random random, int age) {
        BlockState state = world.getBlockState(pos);
        int spreadChance = getSpreadChance(state);
        if (spreadChance <= 0 || !passesSpreadChance(random, spreadChance, DIRECT_SPREAD_MULTIPLIER)) {
            return;
        }
        if (world.isRaining() && isRainingAround(world, pos)) {
            return;
        }

        Block block = state.getBlock();
        world.setBlockState(pos, getAncientFireState(world, pos).with(AGE, nextSpreadAge(random, age)), Block.NOTIFY_ALL);
        if (block instanceof TntBlock) {
            TntBlock.primeTnt(world, pos);
        }
    }

    private void trySpreadAround(ServerWorld world, BlockPos origin, Random random, int age) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int yOffset = -1; yOffset <= 1; yOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    if (xOffset == 0 && yOffset == 0 && zOffset == 0) {
                        continue;
                    }

                    pos.set(origin, xOffset, yOffset, zOffset);
                    if (!world.isAir(pos) || world.isRaining() && isRainingAround(world, pos)) {
                        continue;
                    }

                    int burnChance = getNeighborBurnChance(world, pos);
                    if (burnChance > 0 && passesSpreadChance(random, burnChance, INDIRECT_SPREAD_MULTIPLIER)) {
                        world.setBlockState(pos, getAncientFireState(world, pos).with(AGE, nextSpreadAge(random, age)), Block.NOTIFY_ALL);
                    }
                }
            }
        }
    }

    private static int nextSpreadAge(Random random, int age) {
        return Math.min(MAX_AGE, age + random.nextInt(5) / 4);
    }

    private static boolean passesSpreadChance(Random random, int chance, int multiplier) {
        return random.nextInt(100) < Math.min(MAX_SPREAD_CHANCE, chance * multiplier);
    }

    private boolean hasFlammableNeighbor(BlockView world, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (isFlammable(world.getBlockState(pos.offset(direction)))) {
                return true;
            }
        }
        return false;
    }

    private int getNeighborBurnChance(WorldView world, BlockPos pos) {
        int chance = 0;
        for (Direction direction : Direction.values()) {
            chance = Math.max(chance, getBurnChance(world.getBlockState(pos.offset(direction))));
        }
        return chance;
    }

    private static boolean hasSolidTop(BlockView world, BlockPos pos) {
        return world.getBlockState(pos).isSideSolidFullSquare(world, pos, Direction.UP);
    }

    private static int getBurnChance(BlockState state) {
        if (isWaterlogged(state)) {
            return 0;
        }
        if (isFastVegetation(state) || state.isIn(BlockTags.WOOL_CARPETS)) {
            return 60;
        }
        if (state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.WOOL) || state.isOf(Blocks.BOOKSHELF) || state.isIn(BlockTags.BEEHIVES)) {
            return 30;
        }
        if (state.isOf(Blocks.TNT) || state.isOf(Blocks.VINE) || state.isOf(Blocks.GLOW_LICHEN) || state.isIn(BlockTags.CAVE_VINES)) {
            return 15;
        }
        if (isWoodenBlock(state) || isSlowFuel(state)) {
            return 5;
        }
        return 0;
    }

    private static int getSpreadChance(BlockState state) {
        if (isWaterlogged(state)) {
            return 0;
        }
        if (isFastVegetation(state) || state.isOf(Blocks.TNT) || state.isOf(Blocks.VINE) || state.isOf(Blocks.GLOW_LICHEN)) {
            return 100;
        }
        if (state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.WOOL) || state.isIn(BlockTags.CAVE_VINES)) {
            return 60;
        }
        if (isWoodenBlock(state) || state.isIn(BlockTags.WOOL_CARPETS) || state.isOf(Blocks.BOOKSHELF)
                || state.isOf(Blocks.HAY_BLOCK) || state.isOf(Blocks.TARGET) || state.isIn(BlockTags.BEEHIVES)) {
            return 20;
        }
        if (isSlowFuel(state)) {
            return 5;
        }
        return 0;
    }

    private static boolean isWaterlogged(BlockState state) {
        return state.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED);
    }

    private static boolean isWoodenBlock(BlockState state) {
        return state.isIn(BlockTags.PLANKS)
                || state.isIn(BlockTags.WOODEN_STAIRS)
                || state.isIn(BlockTags.WOODEN_SLABS)
                || state.isIn(BlockTags.WOODEN_FENCES)
                || state.isIn(BlockTags.FENCE_GATES)
                || state.isIn(BlockTags.WOODEN_DOORS)
                || state.isIn(BlockTags.WOODEN_TRAPDOORS)
                || state.isIn(BlockTags.WOODEN_BUTTONS)
                || state.isIn(BlockTags.WOODEN_PRESSURE_PLATES);
    }

    private static boolean isSlowFuel(BlockState state) {
        return state.isIn(BlockTags.LOGS_THAT_BURN)
                || state.isIn(BlockTags.BAMBOO_BLOCKS)
                || state.isOf(Blocks.MANGROVE_ROOTS)
                || state.isOf(Blocks.COAL_BLOCK);
    }

    private static boolean isFastVegetation(BlockState state) {
        return state.isIn(BlockTags.FLOWERS)
                || state.isIn(BlockTags.SAPLINGS)
                || state.isOf(Blocks.SHORT_GRASS)
                || state.isOf(Blocks.TALL_GRASS)
                || state.isOf(Blocks.FERN)
                || state.isOf(Blocks.LARGE_FERN)
                || state.isOf(Blocks.DEAD_BUSH)
                || state.isOf(Blocks.BUSH)
                || state.isOf(Blocks.SHORT_DRY_GRASS)
                || state.isOf(Blocks.TALL_DRY_GRASS)
                || state.isOf(Blocks.SWEET_BERRY_BUSH)
                || state.isOf(Blocks.BAMBOO)
                || state.isOf(Blocks.SCAFFOLDING)
                || state.isOf(Blocks.CAVE_VINES)
                || state.isOf(Blocks.CAVE_VINES_PLANT)
                || state.isOf(Blocks.AZALEA)
                || state.isOf(Blocks.FLOWERING_AZALEA)
                || state.isOf(Blocks.PALE_MOSS_BLOCK)
                || state.isOf(Blocks.PALE_MOSS_CARPET)
                || state.isOf(Blocks.PALE_HANGING_MOSS)
                || state.isOf(Blocks.SPORE_BLOSSOM)
                || state.isOf(Blocks.BIG_DRIPLEAF)
                || state.isOf(Blocks.BIG_DRIPLEAF_STEM)
                || state.isOf(Blocks.SMALL_DRIPLEAF)
                || state.isOf(Blocks.HANGING_ROOTS)
                || state.isOf(Blocks.FIREFLY_BUSH);
    }
}
