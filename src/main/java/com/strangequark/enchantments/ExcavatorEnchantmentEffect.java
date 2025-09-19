package com.strangequark.enchantments;

import com.mojang.serialization.MapCodec;
import net.minecraft.enchantment.EnchantmentEffectContext;
import net.minecraft.enchantment.effect.EnchantmentEntityEffect;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public record ExcavatorEnchantmentEffect() implements EnchantmentEntityEffect {
    public static final MapCodec<ExcavatorEnchantmentEffect> CODEC = MapCodec.unit(ExcavatorEnchantmentEffect::new);

    @Override
    public void apply(ServerWorld world, int level, EnchantmentEffectContext context, Entity user, Vec3d pos) {
        mine3x3(world, BlockPos.ofFloored(pos), (PlayerEntity) user);
    }

    @Override
    public MapCodec<? extends EnchantmentEntityEffect> getCodec() {
        return CODEC;
    }

    public static void mine3x3(ServerWorld world, BlockPos center, PlayerEntity player) {
        BlockHitResult blockHitResult = (BlockHitResult) player.raycast(5.0, 0.0f, false);
        Direction facing = blockHitResult.getSide();

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        switch (facing) {
            case UP, DOWN -> {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        mutablePos.set(center.getX() + dx, center.getY(), center.getZ() + dz);
                        if (!mutablePos.equals(center) && !world.isAir(mutablePos)) {
                            world.breakBlock(mutablePos, true, player);
                        }
                    }
                }
            }
            case NORTH, SOUTH -> {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        mutablePos.set(center.getX() + dx, center.getY() + dy, center.getZ());
                        if (!mutablePos.equals(center) && !world.isAir(mutablePos)) {
                            world.breakBlock(mutablePos, true, player);
                        }
                    }
                }
            }
            case EAST, WEST -> {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        mutablePos.set(center.getX(), center.getY() + dy, center.getZ() + dz);
                        if (!mutablePos.equals(center) && !world.isAir(mutablePos)) {
                            world.breakBlock(mutablePos, true, player);
                        }
                    }
                }
            }
        }
    }
}
