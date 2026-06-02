package com.strangequark.excavator.enchantments;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentEffectContext;
import net.minecraft.enchantment.effect.EnchantmentEntityEffect;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

public record ExcavatorEnchantmentEffect() implements EnchantmentEntityEffect {
    public static final MapCodec<ExcavatorEnchantmentEffect> CODEC = MapCodec.unit(ExcavatorEnchantmentEffect::new);

    public static final Set<Block> INCOMPATIBLE_BLOCKS = Set.of(
            // --- Unmineable in Survival ---
            Blocks.BEDROCK, Blocks.BARRIER, Blocks.COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK,
            Blocks.REPEATING_COMMAND_BLOCK, Blocks.STRUCTURE_BLOCK, Blocks.JIGSAW,
            Blocks.END_PORTAL, Blocks.END_PORTAL_FRAME, Blocks.NETHER_PORTAL, Blocks.END_GATEWAY,
            Blocks.LIGHT, Blocks.STRUCTURE_VOID, Blocks.MOVING_PISTON, Blocks.PISTON_HEAD,
            Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.POWDER_SNOW,

            // --- Functional / Optional Hard Blocks ---
            Blocks.TRIAL_SPAWNER, Blocks.VAULT, Blocks.SPAWNER,

            // --- Very Hard Blocks ---
            Blocks.REINFORCED_DEEPSLATE, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN,
            Blocks.RESPAWN_ANCHOR, Blocks.ANCIENT_DEBRIS, Blocks.NETHERITE_BLOCK,
            Blocks.ENCHANTING_TABLE, Blocks.ENDER_CHEST
    );

    @Override
    public void apply(ServerWorld world, int level, EnchantmentEffectContext context, Entity user, Vec3d pos) {
        if (!(user instanceof PlayerEntity player)) {
            return;
        }

        mine3x3(world, BlockPos.ofFloored(pos), player);
    }

    @Override
    public MapCodec<? extends EnchantmentEntityEffect> getCodec() {
        return CODEC;
    }

    public static void mine3x3(ServerWorld world, BlockPos center, PlayerEntity player) {
        HitResult hitResult = player.raycast(5.0, 0.0f, false);
        if (!(hitResult instanceof BlockHitResult blockHitResult)) {
            return;
        }

        Direction facing = blockHitResult.getSide();

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        switch (facing) {
            case UP, DOWN -> {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0)
                            continue;

                        mutablePos.set(center.getX() + dx, center.getY(), center.getZ() + dz);
                        if (!world.isAir(mutablePos) && !INCOMPATIBLE_BLOCKS.contains(world.getBlockState(mutablePos).getBlock())) {
                            world.breakBlock(mutablePos, true, player);
                        }
                    }
                }
            }
            case NORTH, SOUTH -> {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0)
                            continue;

                        mutablePos.set(center.getX() + dx, center.getY() + dy, center.getZ());
                        if (!world.isAir(mutablePos) && !INCOMPATIBLE_BLOCKS.contains(world.getBlockState(mutablePos).getBlock())) {
                            world.breakBlock(mutablePos, true, player);
                        }
                    }
                }
            }
            case EAST, WEST -> {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dy == 0 && dz == 0)
                            continue;

                        mutablePos.set(center.getX(), center.getY() + dy, center.getZ() + dz);
                        if (!world.isAir(mutablePos) && !INCOMPATIBLE_BLOCKS.contains(world.getBlockState(mutablePos).getBlock())) {
                            world.breakBlock(mutablePos, true, player);
                        }
                    }
                }
            }
        }
    }
}
