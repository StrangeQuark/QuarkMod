package com.strangequark.vampirism.block;

import com.strangequark.vampirism.vampire.CoffinSleepHandler;
import com.strangequark.vampirism.vampire.VampireData;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class CoffinBlock extends BedBlock {
    public CoffinBlock(AbstractBlock.Settings settings) {
        super(DyeColor.BLACK, settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS_SERVER;
        }

        BlockPos headPos = getHeadPos(state, pos);
        BlockState headState = world.getBlockState(headPos);
        if (!headState.isOf(this)) {
            return ActionResult.CONSUME;
        }

        if (!BedBlock.isBedWorking(world)) {
            explodeCoffin(world, headPos, headState);
            return ActionResult.SUCCESS_SERVER;
        }

        if (!VampireData.isVampire(player)) {
            player.sendMessage(Text.translatable("block.quarkmod.coffin.not_vampire"), true);
            return ActionResult.SUCCESS_SERVER;
        }

        if (!(player instanceof ServerPlayerEntity serverPlayer) || !(world instanceof ServerWorld serverWorld)) {
            return ActionResult.SUCCESS_SERVER;
        }

        if (!CoffinSleepHandler.isDaySleepTime(serverWorld)) {
            CoffinSleepHandler.setSpawnPoint(serverPlayer, serverWorld, headPos);
            player.sendMessage(Text.translatable("block.quarkmod.coffin.day_only"), true);
            return ActionResult.SUCCESS_SERVER;
        }

        return super.onUse(state, world, pos, player, hit);
    }

    private static BlockPos getHeadPos(BlockState state, BlockPos pos) {
        if (state.get(PART) == BedPart.HEAD) {
            return pos;
        }

        return pos.offset(state.get(FACING));
    }

    private void explodeCoffin(World world, BlockPos headPos, BlockState headState) {
        world.removeBlock(headPos, false);
        Direction facing = headState.get(FACING);
        BlockPos footPos = headPos.offset(facing.getOpposite());
        if (world.getBlockState(footPos).isOf(this)) {
            world.removeBlock(footPos, false);
        }

        Vec3d center = headPos.toCenterPos();
        world.createExplosion(null, world.getDamageSources().badRespawnPoint(center), null, center, 5.0F, true, World.ExplosionSourceType.BLOCK);
    }
}
