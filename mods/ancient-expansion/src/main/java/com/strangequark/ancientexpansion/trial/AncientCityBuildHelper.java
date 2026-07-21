package com.strangequark.ancientexpansion.trial;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePiecesList;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.gen.chunk.ChunkGenerator;

import java.util.Set;

public final class AncientCityBuildHelper {
    public static final int BULK_BLOCK_FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;

    private AncientCityBuildHelper() {
    }

    public static void generateShiftedAncientCity(
            ServerWorld world,
            AncientCityTrialPortal.FrameTarget sourceFrame,
            int offsetX,
            int offsetY,
            int offsetZ,
            BlockBox generationBox,
            long randomSeed
    ) {
        StructureContext context = StructureContext.from(world);
        NbtList piecesNbt = new NbtList();
        for (StructurePiece piece : sourceFrame.structureStart().getChildren()) {
            piecesNbt.add(piece.toNbt(context));
        }

        StructurePiecesList pieces = StructurePiecesList.fromNbt(piecesNbt, context);
        ChunkGenerator chunkGenerator = world.getChunkManager().getChunkGenerator();
        Random random = Random.create(randomSeed);
        for (StructurePiece piece : pieces.pieces()) {
            BlockBox shiftedBox = offsetBox(piece.getBoundingBox(), offsetX, offsetY, offsetZ);
            if (!shiftedBox.intersects(generationBox)) {
                continue;
            }
            piece.translate(offsetX, offsetY, offsetZ);
            piece.generate(world, world.getStructureAccessor(), chunkGenerator, random, generationBox, new ChunkPos(piece.getCenter()), BlockPos.ORIGIN);
        }
    }

    public static void clearBox(ServerWorld world, BlockBox box) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockState air = Blocks.AIR.getDefaultState();
        for (int x = box.getMinX(); x <= box.getMaxX(); x++) {
            for (int y = box.getMinY(); y <= box.getMaxY(); y++) {
                for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
                    world.setBlockState(pos.set(x, y, z), air, BULK_BLOCK_FLAGS);
                }
            }
        }
    }

    public static void loadChunks(ServerWorld world, BlockBox box) {
        int minChunkX = ChunkSectionPos.getSectionCoord(box.getMinX());
        int maxChunkX = ChunkSectionPos.getSectionCoord(box.getMaxX());
        int minChunkZ = ChunkSectionPos.getSectionCoord(box.getMinZ());
        int maxChunkZ = ChunkSectionPos.getSectionCoord(box.getMaxZ());
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                world.getChunk(chunkX, chunkZ);
            }
        }
    }

    public static BlockBox boxAround(BlockPos center, int horizontalRadius, int down, int up) {
        return new BlockBox(
                center.getX() - horizontalRadius,
                center.getY() - down,
                center.getZ() - horizontalRadius,
                center.getX() + horizontalRadius,
                center.getY() + up,
                center.getZ() + horizontalRadius
        );
    }

    public static BlockBox offsetBox(BlockBox box, int x, int y, int z) {
        return new BlockBox(
                box.getMinX() + x,
                box.getMinY() + y,
                box.getMinZ() + z,
                box.getMaxX() + x,
                box.getMaxY() + y,
                box.getMaxZ() + z
        );
    }

    public static BlockBox expandBox(BlockBox box, int x, int y, int z) {
        return new BlockBox(
                box.getMinX() - x,
                box.getMinY() - y,
                box.getMinZ() - z,
                box.getMaxX() + x,
                box.getMaxY() + y,
                box.getMaxZ() + z
        );
    }

    public static BlockBox clampBoxVertically(BlockBox box, int minY, int maxY) {
        return new BlockBox(
                box.getMinX(),
                Math.max(box.getMinY(), minY),
                box.getMinZ(),
                box.getMaxX(),
                Math.min(box.getMaxY(), maxY),
                box.getMaxZ()
        );
    }

    public static ServerPlayerEntity teleport(ServerPlayerEntity player, ServerWorld world, Vec3d position, float yaw, float pitch) {
        player.stopRiding();
        player.dismountVehicle();
        player.closeHandledScreen();

        ServerPlayerEntity teleported = player.teleportTo(new TeleportTarget(
                world,
                position,
                Vec3d.ZERO,
                yaw,
                pitch,
                Set.<PositionFlag>of(),
                TeleportTarget.NO_OP
        ));
        if (teleported == null) {
            return player;
        }
        teleported.setVelocity(Vec3d.ZERO);
        teleported.fallDistance = 0.0F;
        teleported.extinguish();
        return teleported;
    }
}
