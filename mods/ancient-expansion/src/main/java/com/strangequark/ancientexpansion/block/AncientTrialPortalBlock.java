package com.strangequark.ancientexpansion.block;

import com.strangequark.ancientexpansion.trial.AncientCityTrialManager;
import com.strangequark.ancientexpansion.past.AncientPastManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

import java.util.Map;

public class AncientTrialPortalBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = Properties.HORIZONTAL_AXIS;
    private static final int WHITE_PORTAL_PARTICLE_COLOR = 0xF4F8FF;
    private static final Map<Direction.Axis, VoxelShape> SHAPES_BY_AXIS =
            VoxelShapes.createHorizontalAxisShapeMap(Block.createColumnShape(4.0D, 16.0D, 0.0D, 16.0D));
    private final DustParticleEffect portalParticle;

    public AncientTrialPortalBlock(Settings settings) {
        this(settings, WHITE_PORTAL_PARTICLE_COLOR);
    }

    public AncientTrialPortalBlock(Settings settings, int particleColor) {
        super(settings);
        this.portalParticle = new DustParticleEffect(particleColor, 1.0F);
        this.setDefaultState(this.stateManager.getDefaultState().with(AXIS, Direction.Axis.Z));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, net.minecraft.block.ShapeContext context) {
        return SHAPES_BY_AXIS.get(state.get(AXIS));
    }

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler handler) {
        if (!world.isClient && entity instanceof ServerPlayerEntity player && entity.canUsePortals(false)) {
            if (AncientCityTrialManager.hasActiveTrial(player) || !AncientPastManager.handlePortalCollision(player, pos)) {
                AncientCityTrialManager.handlePortalCollision(player, pos);
            }
        }
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            world.addParticleClient(portalParticle, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }
}
