package com.strangequark.ancientexpansion.trial;

import com.strangequark.ancientexpansion.AncientExpansionMod;
import com.strangequark.ancientexpansion.block.ModBlocks;
import com.strangequark.ancientexpansion.enchantment.AncientEnchantmentLogic;
import com.strangequark.ancientexpansion.enchantment.ModEnchantments;
import com.strangequark.ancientexpansion.item.ModItems;
import com.strangequark.ancientexpansion.mixin.MobEntityGoalSelectorAccessor;
import com.strangequark.ancientexpansion.mixin.PhantomEntityAccessor;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.EntityLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.mob.ZoglinEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePiecesList;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class AncientCityTrialManager {
    public static final RegistryKey<World> ANCIENT_TRIAL_WORLD = RegistryKey.of(RegistryKeys.WORLD, AncientExpansionMod.id("ancient_trial"));
    private static final int MAX_WAVES = 15;
    private static final int WAVE_SETTLE_TICKS = 20;
    private static final int NEXT_WAVE_DELAY_TICKS = 40;
    private static final int INSTANCE_SPACING = 8192;
    private static final int INSTANCE_COLUMNS = 128;
    private static final int INSTANCE_ORIGIN_X = 0;
    private static final int INSTANCE_ORIGIN_Z = 0;
    private static final int TRIAL_ALTAR_Y = 96;
    private static final int ISLAND_MARGIN = 6;
    private static final int PLATFORM_THICKNESS = 2;
    private static final int PLATFORM_Y_RAISE = 13;
    private static final int CLEAR_VERTICAL_PADDING = 24;
    private static final int SPAWN_ATTEMPTS = 96;
    private static final int TRIAL_STATUS_TICKS = 20;
    private static final int TRIAL_GHAST_APPROACH_GOAL_PRIORITY = 4;
    private static final int TRIAL_PHANTOM_APPROACH_GOAL_PRIORITY = 0;
    private static final int TRIAL_PHANTOM_CIRCLING_HEIGHT = 20;
    private static final long TRIAL_BRAIN_TARGET_MEMORY_TICKS = 200L;
    private static final float TRIAL_WARDEN_WALK_SPEED = 1.0F;
    private static final float TRIAL_ZOGLIN_WALK_SPEED = 1.0F;
    private static final double TRIAL_GHAST_APPROACH_HEIGHT = 6.0D;
    private static final double TRIAL_MOB_MIN_FOLLOW_RANGE = 256.0D;
    private static final double TRIAL_MOB_FOLLOW_RANGE_PADDING = 32.0D;
    private static final int BULK_BLOCK_FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
    private static final List<WaveSpawn> WAVE_ONE = List.of(
            new WaveSpawn(EntityType.ZOMBIE, 10, false)
    );
    private static final List<WaveSpawn> WAVE_TWO = List.of(
            new WaveSpawn(EntityType.SKELETON, 10, false)
    );
    private static final List<WaveSpawn> WAVE_THREE = List.of(
            new WaveSpawn(EntityType.CREEPER, 10, false)
    );
    private static final List<WaveSpawn> WAVE_FOUR = List.of(
            new WaveSpawn(EntityType.ZOMBIE, 10, false),
            new WaveSpawn(EntityType.SKELETON, 10, false),
            new WaveSpawn(EntityType.CREEPER, 10, false)
    );
    private static final List<WaveSpawn> WAVE_FIVE = List.of(
            new WaveSpawn(EntityType.CAVE_SPIDER, 20, false)
    );
    private static final List<WaveSpawn> WAVE_SIX = List.of(
            new WaveSpawn(EntityType.ENDERMAN, 10, false)
    );
    private static final List<WaveSpawn> WAVE_SEVEN = List.of(
            new WaveSpawn(EntityType.ZOMBIE, 10, false),
            new WaveSpawn(EntityType.SKELETON, 10, false),
            new WaveSpawn(EntityType.CREEPER, 10, false),
            new WaveSpawn(EntityType.ENDERMAN, 10, false),
            new WaveSpawn(EntityType.CAVE_SPIDER, 10, false),
            new WaveSpawn(EntityType.PHANTOM, 10, true),
            new WaveSpawn(EntityType.WITCH, 10, false)
    );
    private static final List<WaveSpawn> WAVE_EIGHT = List.of(
            new WaveSpawn(EntityType.EVOKER, 10, false),
            new WaveSpawn(EntityType.ILLUSIONER, 10, false),
            new WaveSpawn(EntityType.VINDICATOR, 10, false),
            new WaveSpawn(EntityType.PILLAGER, 20, false),
            new WaveSpawn(EntityType.RAVAGER, 5, false)
    );
    private static final List<WaveSpawn> WAVE_NINE = List.of(
            new WaveSpawn(EntityType.BLAZE, 10, true),
            new WaveSpawn(EntityType.GHAST, 10, true),
            new WaveSpawn(EntityType.HOGLIN, 10, false),
            new WaveSpawn(EntityType.MAGMA_CUBE, 10, false),
            new WaveSpawn(EntityType.PIGLIN, 10, false),
            new WaveSpawn(EntityType.PIGLIN_BRUTE, 10, false),
            new WaveSpawn(EntityType.WITHER_SKELETON, 10, false),
            new WaveSpawn(EntityType.ZOGLIN, 10, false),
            new WaveSpawn(EntityType.ZOMBIFIED_PIGLIN, 10, false)
    );
    private static final List<WaveSpawn> WAVE_TEN = List.of(
            new WaveSpawn(EntityType.BREEZE, 5, false)
    );
    private static final List<WaveSpawn> WAVE_ELEVEN = List.of(
            new WaveSpawn(EntityType.WARDEN, 1, false)
    );
    private static final List<WaveSpawn> WAVE_TWELVE = List.of(
            new WaveSpawn(EntityType.WITHER, 1, true)
    );
    private static final List<WaveSpawn> WAVE_THIRTEEN = concat(WAVE_SEVEN, WAVE_EIGHT, WAVE_NINE);
    private static final List<WaveSpawn> WAVE_FOURTEEN = concat(WAVE_ELEVEN, WAVE_TWELVE, WAVE_TEN);
    private static final List<WaveSpawn> WAVE_FIFTEEN = concat(WAVE_THIRTEEN, WAVE_FOURTEEN);
    private static final Map<UUID, TrialInstance> ACTIVE_TRIALS = new HashMap<>();
    private static final AtomicInteger NEXT_INSTANCE_INDEX = new AtomicInteger();

    private AncientCityTrialManager() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AncientCityTrialManager::tickServer);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                abandonTrial(player.getUuid());
            }
        });
    }

    public static void handlePortalCollision(ServerPlayerEntity player, BlockPos portalPos) {
        if (player.isSpectator()) {
            return;
        }

        TrialInstance activeTrial = ACTIVE_TRIALS.get(player.getUuid());
        if (activeTrial != null) {
            handleActiveTrialPortal(player, portalPos, activeTrial);
            return;
        }

        enterTrialPortal(player, portalPos);
    }

    public static boolean hasActiveTrial(ServerPlayerEntity player) {
        return ACTIVE_TRIALS.containsKey(player.getUuid());
    }

    public static boolean startTrial(
            ServerPlayerEntity player,
            ServerWorld sourceWorld,
            AncientCityTrialPortal.FrameTarget sourceFrame,
            ServerWorld returnWorld,
            BlockPos returnSpawn
    ) {
        return startTrial(player, sourceWorld, sourceFrame, returnWorld, returnSpawn, null);
    }

    private static void enterTrialPortal(ServerPlayerEntity player, BlockPos portalPos) {
        ServerWorld sourceWorld = player.getWorld();
        if (!AncientCityTrialPortal.isPortalBlock(sourceWorld.getBlockState(portalPos))) {
            return;
        }

        Optional<AncientCityTrialPortal.FrameTarget> frameTarget = AncientCityTrialPortal.findNearestFrame(sourceWorld, portalPos);
        if (frameTarget.isEmpty()) {
            return;
        }

        ServerWorld trialWorld = player.getServer().getWorld(ANCIENT_TRIAL_WORLD);
        if (trialWorld == null) {
            player.sendMessage(Text.translatable("message.quarkmod.ancient_trial_dimension_missing"), true);
            return;
        }

        startTrial(player, sourceWorld, frameTarget.get(), sourceWorld, frameTarget.get().altarPos().up(2), portalPos);
    }

    private static boolean startTrial(
            ServerPlayerEntity player,
            ServerWorld sourceWorld,
            AncientCityTrialPortal.FrameTarget sourceFrame,
            ServerWorld returnWorld,
            BlockPos returnSpawn,
            @Nullable BlockPos sourcePortalToClear
    ) {
        if (ACTIVE_TRIALS.containsKey(player.getUuid())) {
            return false;
        }

        ServerWorld trialWorld = player.getServer().getWorld(ANCIENT_TRIAL_WORLD);
        if (trialWorld == null) {
            player.sendMessage(Text.translatable("message.quarkmod.ancient_trial_dimension_missing"), true);
            return false;
        }

        TrialInstance trial = createTrial(sourceWorld, trialWorld, returnWorld, returnSpawn, player, sourceFrame);
        ACTIVE_TRIALS.put(player.getUuid(), trial);
        if (sourcePortalToClear != null) {
            AncientCityTrialPortal.clearConnectedPortal(sourceWorld, sourcePortalToClear);
        }

        buildTrialCity(trialWorld, sourceFrame, trial);
        teleport(player, trialWorld, trial.trialSpawn.toBottomCenterPos(), player.getYaw(), player.getPitch());
        spawnNextWave(trial, player);
        player.sendMessage(Text.translatable("message.quarkmod.ancient_trial_started"), true);
        player.playSoundToPlayer(SoundEvents.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, SoundCategory.AMBIENT, 0.65F, 0.7F);
        return true;
    }

    private static void handleActiveTrialPortal(ServerPlayerEntity player, BlockPos portalPos, TrialInstance trial) {
        if (player.getWorld() == trial.world && trial.completed && trial.exitPortalPositions.contains(portalPos)) {
            AncientCityTrialPortal.clearConnectedPortal(trial.world, portalPos);
            ServerPlayerEntity teleported = teleport(player, trial.returnWorld, trial.returnSpawn.toBottomCenterPos(), player.getYaw(), player.getPitch());
            teleported.playSoundToPlayer(SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.65F, 1.0F);
            if (teleported.getWorld() == trial.returnWorld) {
                endTrial(player.getUuid(), trial);
            }
            return;
        }

        keepPlayerInTrial(player, trial);
    }

    private static void tickServer(MinecraftServer server) {
        for (Map.Entry<UUID, TrialInstance> entry : new HashMap<>(ACTIVE_TRIALS).entrySet()) {
            UUID playerUuid = entry.getKey();
            TrialInstance trial = entry.getValue();
            if (ACTIVE_TRIALS.get(playerUuid) != trial) {
                continue;
            }

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
            if (player == null) {
                continue;
            }
            if (!player.isAlive()) {
                endTrial(playerUuid, trial);
                continue;
            }

            keepPlayerInTrial(player, trial);
            TrialMobStatus mobStatus = retargetTrialMobs(trial, player);
            displayTrialStatus(player, trial, mobStatus);
            if (trial.completed) {
                continue;
            }

            if (trial.waveSettleTicks > 0) {
                trial.waveSettleTicks--;
                continue;
            }

            if (mobStatus.total() > 0) {
                trial.nextWaveDelayTicks = NEXT_WAVE_DELAY_TICKS;
                continue;
            }

            if (trial.currentWave >= MAX_WAVES) {
                completeTrial(player, trial);
                continue;
            }

            if (trial.nextWaveDelayTicks > 0) {
                trial.nextWaveDelayTicks--;
                continue;
            }

            spawnNextWave(trial, player);
        }
    }

    private static TrialInstance createTrial(
            ServerWorld sourceWorld,
            ServerWorld trialWorld,
            ServerWorld returnWorld,
            BlockPos returnSpawn,
            ServerPlayerEntity player,
            AncientCityTrialPortal.FrameTarget sourceFrame
    ) {
        int index = NEXT_INSTANCE_INDEX.getAndIncrement();
        int instanceX = INSTANCE_ORIGIN_X + (index % INSTANCE_COLUMNS) * INSTANCE_SPACING;
        int instanceZ = INSTANCE_ORIGIN_Z + (index / INSTANCE_COLUMNS) * INSTANCE_SPACING;
        int altarY = MathHelper.clamp(TRIAL_ALTAR_Y, trialWorld.getBottomY() + 80, trialWorld.getTopYInclusive() - 80);
        BlockPos trialAltarBase = new BlockPos(instanceX, altarY, instanceZ);
        int offsetX = trialAltarBase.getX() - sourceFrame.altarPos().getX();
        int offsetY = trialAltarBase.getY() - sourceFrame.altarPos().getY();
        int offsetZ = trialAltarBase.getZ() - sourceFrame.altarPos().getZ();
        BlockBox cityBox = offsetBox(sourceFrame.cityBoundingBox(), offsetX, offsetY, offsetZ);
        int platformTopY = Math.max(trialWorld.getBottomY() + PLATFORM_THICKNESS, cityBox.getMinY() - 1 + PLATFORM_Y_RAISE);
        BlockBox platformBox = new BlockBox(
                cityBox.getMinX() - ISLAND_MARGIN,
                platformTopY - PLATFORM_THICKNESS + 1,
                cityBox.getMinZ() - ISLAND_MARGIN,
                cityBox.getMaxX() + ISLAND_MARGIN,
                platformTopY,
                cityBox.getMaxZ() + ISLAND_MARGIN
        );
        List<BlockPos> exitPortalPositions = sourceFrame.portalPositions().stream()
                .map(pos -> pos.add(offsetX, offsetY, offsetZ))
                .toList();
        BlockPos trialSpawn = trialAltarBase.offset(sourceFrame.portalFrontDirection(), 2).up();
        BlockPos prizeChestPos = trialAltarBase.up();

        return new TrialInstance(
                trialWorld,
                returnWorld,
                index,
                offsetX,
                offsetY,
                offsetZ,
                cityBox,
                platformBox,
                platformTopY,
                trialSpawn,
                trialAltarBase,
                prizeChestPos,
                sourceFrame.portalFrontDirection(),
                returnSpawn,
                sourceFrame.portalAxis(),
                exitPortalPositions,
                "quarkmod_ancient_trial_" + player.getUuid()
        );
    }

    private static void keepPlayerInTrial(ServerPlayerEntity player, TrialInstance trial) {
        if (player.getWorld() != trial.world) {
            teleportToTrialSpawn(player, trial);
        }
    }

    private static void teleportToTrialSpawn(ServerPlayerEntity player, TrialInstance trial) {
        teleport(player, trial.world, trial.trialSpawn.toBottomCenterPos(), player.getYaw(), player.getPitch());
    }

    private static void abandonTrial(UUID playerUuid) {
        TrialInstance trial = ACTIVE_TRIALS.get(playerUuid);
        if (trial != null) {
            endTrial(playerUuid, trial);
        }
    }

    private static void endTrial(UUID playerUuid, TrialInstance trial) {
        if (ACTIVE_TRIALS.remove(playerUuid, trial)) {
            removeTrialMobs(trial);
            clearTrialBuild(trial);
        }
    }

    private static void spawnNextWave(TrialInstance trial, ServerPlayerEntity player) {
        trial.currentWave++;
        Random random = Random.create(trial.world.getSeed() ^ ((long) trial.instanceIndex << 32) ^ trial.currentWave ^ trial.world.getTime());
        spawnWaveEntries(trial, player, getWaveSpawns(trial.currentWave), random);
        trial.waveSettleTicks = WAVE_SETTLE_TICKS;
        trial.nextWaveDelayTicks = NEXT_WAVE_DELAY_TICKS;
        player.sendMessage(Text.translatable("message.quarkmod.ancient_trial_wave", trial.currentWave, MAX_WAVES), true);
    }

    private static List<WaveSpawn> getWaveSpawns(int wave) {
        return switch (wave) {
            case 1 -> WAVE_ONE;
            case 2 -> WAVE_TWO;
            case 3 -> WAVE_THREE;
            case 4 -> WAVE_FOUR;
            case 5 -> WAVE_FIVE;
            case 6 -> WAVE_SIX;
            case 7 -> WAVE_SEVEN;
            case 8 -> WAVE_EIGHT;
            case 9 -> WAVE_NINE;
            case 10 -> WAVE_TEN;
            case 11 -> WAVE_ELEVEN;
            case 12 -> WAVE_TWELVE;
            case 13 -> WAVE_THIRTEEN;
            case 14 -> WAVE_FOURTEEN;
            case 15 -> WAVE_FIFTEEN;
            default -> List.of();
        };
    }

    @SafeVarargs
    private static List<WaveSpawn> concat(List<WaveSpawn>... waves) {
        return java.util.stream.Stream.of(waves)
                .flatMap(List::stream)
                .toList();
    }

    private static void spawnWaveEntries(TrialInstance trial, ServerPlayerEntity player, List<WaveSpawn> entries, Random random) {
        for (WaveSpawn entry : entries) {
            spawnWaveEntry(trial, player, entry, random);
        }
    }

    private static void spawnWaveEntry(TrialInstance trial, ServerPlayerEntity player, WaveSpawn entry, Random random) {
        for (int i = 0; i < entry.count(); i++) {
            BlockPos spawnPos = findRandomSpawnPos(trial, random, entry.flying());
            Entity entity = entry.entityType().spawn(
                    trial.world,
                    spawnedEntity -> configureTrialMob(spawnedEntity, trial, player),
                    spawnPos,
                    SpawnReason.EVENT,
                    false,
                    false
            );
            if (entity instanceof MobEntity mob) {
                mob.playAmbientSound();
            }
        }
    }

    private static void configureTrialMob(Entity entity, TrialInstance trial, ServerPlayerEntity player) {
        entity.addCommandTag(trial.mobTag);
        if (entity instanceof MobEntity mob) {
            mob.setPersistent();
            addTrialFlyingGoal(mob);
            retargetTrialMob(trial, player, mob);
        }
    }

    private static void addTrialFlyingGoal(MobEntity mob) {
        if (mob instanceof GhastEntity ghast) {
            ((MobEntityGoalSelectorAccessor) ghast).quarkmod_ancientexpansion$getGoalSelector()
                    .add(TRIAL_GHAST_APPROACH_GOAL_PRIORITY, new TrialGhastApproachGoal(ghast));
        } else if (mob instanceof PhantomEntity phantom) {
            ((MobEntityGoalSelectorAccessor) phantom).quarkmod_ancientexpansion$getGoalSelector()
                    .add(TRIAL_PHANTOM_APPROACH_GOAL_PRIORITY, new TrialPhantomApproachGoal(phantom));
        }
    }

    private static BlockPos findRandomSpawnPos(TrialInstance trial, Random random, boolean flying) {
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            int x = random.nextBetween(trial.platformBox.getMinX() + 1, trial.platformBox.getMaxX() - 1);
            int z = random.nextBetween(trial.platformBox.getMinZ() + 1, trial.platformBox.getMaxZ() - 1);
            if (!isInsidePlatformFootprint(trial, x, z)) {
                continue;
            }
            int y = flying ? trial.platformTopY + random.nextBetween(7, 15) : trial.platformTopY + 1;
            BlockPos pos = new BlockPos(x, y, z);
            if (isSpawnClear(trial.world, pos, flying ? 4 : 2)) {
                return pos;
            }
        }

        return flying ? trial.trialSpawn.up(10) : trial.trialSpawn;
    }

    private static boolean isSpawnClear(ServerWorld world, BlockPos pos, int height) {
        for (int i = 0; i < height; i++) {
            if (!world.getBlockState(pos.up(i)).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static TrialMobStatus retargetTrialMobs(TrialInstance trial, ServerPlayerEntity player) {
        int livingMobs = 0;
        Map<EntityType<?>, Integer> counts = createMobCountMap(trial.currentWave);
        for (Entity entity : trial.world.iterateEntities()) {
            if (entity != null && entity.isAlive() && entity.getCommandTags().contains(trial.mobTag)) {
                livingMobs++;
                counts.merge(entity.getType(), 1, Integer::sum);
                if (entity instanceof MobEntity mob) {
                    retargetTrialMob(trial, player, mob);
                }
            }
        }
        return new TrialMobStatus(livingMobs, counts);
    }

    private static void retargetTrialMob(TrialInstance trial, ServerPlayerEntity player, MobEntity mob) {
        boostTrialMobFollowRange(trial, mob);
        if (mob instanceof ZoglinEntity zoglin) {
            retargetTrialZoglin(zoglin, player);
            return;
        }
        if (mob instanceof WardenEntity warden) {
            retargetTrialWarden(warden, player);
            return;
        }

        mob.setTarget(player);
        mob.setAttacking(true);
        mob.getLookControl().lookAt(player, 90.0F, 90.0F);
    }

    private static void retargetTrialZoglin(ZoglinEntity zoglin, ServerPlayerEntity player) {
        Brain<ZoglinEntity> brain = zoglin.getBrain();
        brain.forget(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        brain.remember(MemoryModuleType.ATTACK_TARGET, player, TRIAL_BRAIN_TARGET_MEMORY_TICKS);
        brain.remember(MemoryModuleType.LOOK_TARGET, new EntityLookTarget(player, true), TRIAL_BRAIN_TARGET_MEMORY_TICKS);
        brain.remember(MemoryModuleType.WALK_TARGET, new WalkTarget(player, TRIAL_ZOGLIN_WALK_SPEED, 1), TRIAL_BRAIN_TARGET_MEMORY_TICKS);
        zoglin.setAttacking(true);
        zoglin.getLookControl().lookAt(player, 90.0F, 90.0F);
    }

    private static void retargetTrialWarden(WardenEntity warden, ServerPlayerEntity player) {
        warden.updateAttackTarget(player);
        Brain<WardenEntity> brain = warden.getBrain();
        brain.forget(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        brain.remember(MemoryModuleType.LOOK_TARGET, new EntityLookTarget(player, true), TRIAL_BRAIN_TARGET_MEMORY_TICKS);
        brain.remember(MemoryModuleType.WALK_TARGET, new WalkTarget(player, TRIAL_WARDEN_WALK_SPEED, 1), TRIAL_BRAIN_TARGET_MEMORY_TICKS);
        warden.setAttacking(true);
        warden.getLookControl().lookAt(player, 90.0F, 90.0F);
    }

    private static Map<EntityType<?>, Integer> createMobCountMap(int wave) {
        Map<EntityType<?>, Integer> counts = new LinkedHashMap<>();
        for (WaveSpawn spawn : getWaveSpawns(wave)) {
            counts.putIfAbsent(spawn.entityType(), 0);
        }
        return counts;
    }

    private static void displayTrialStatus(ServerPlayerEntity player, TrialInstance trial, TrialMobStatus status) {
        if (trial.completed || trial.world.getTime() % TRIAL_STATUS_TICKS != 0L) {
            return;
        }

        MutableText text = Text.literal("Wave " + trial.currentWave + "/" + MAX_WAVES + " | " + status.total() + " left");
        if (status.total() > 0) {
            text.append(Text.literal(": "));
            boolean first = true;
            for (Map.Entry<EntityType<?>, Integer> entry : status.counts().entrySet()) {
                int count = entry.getValue();
                if (count <= 0) {
                    continue;
                }
                if (!first) {
                    text.append(Text.literal(", "));
                }
                text.append(entry.getKey().getName()).append(Text.literal(" x" + count));
                first = false;
            }
        }

        player.sendMessage(text, true);
    }

    private static void boostTrialMobFollowRange(TrialInstance trial, MobEntity mob) {
        EntityAttributeInstance followRange = mob.getAttributeInstance(EntityAttributes.FOLLOW_RANGE);
        double trialFollowRange = getTrialMobFollowRange(trial);
        if (followRange != null && followRange.getBaseValue() < trialFollowRange) {
            followRange.setBaseValue(trialFollowRange);
        }
    }

    private static double getTrialMobFollowRange(TrialInstance trial) {
        double width = trial.platformBox.getMaxX() - trial.platformBox.getMinX() + 1.0D;
        double depth = trial.platformBox.getMaxZ() - trial.platformBox.getMinZ() + 1.0D;
        double height = Math.max(1.0D, trial.cityBox.getMaxY() - trial.platformTopY + 1.0D);
        double arenaDiagonal = Math.sqrt(width * width + depth * depth + height * height);
        return Math.max(TRIAL_MOB_MIN_FOLLOW_RANGE, arenaDiagonal + TRIAL_MOB_FOLLOW_RANGE_PADDING);
    }

    private static void completeTrial(ServerPlayerEntity player, TrialInstance trial) {
        trial.completed = true;
        removeTrialMobs(trial);
        spawnPrizeChest(trial);
        AncientCityTrialPortal.placePortal(trial.world, trial.exitPortalPositions, trial.exitPortalAxis);
        player.sendMessage(Text.translatable("message.quarkmod.ancient_trial_complete"), true);
        player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.75F, 1.0F);
    }

    private static void removeTrialMobs(TrialInstance trial) {
        for (Entity entity : trial.world.iterateEntities()) {
            if (entity != null && entity.getCommandTags().contains(trial.mobTag)) {
                entity.discard();
            }
        }
    }

    private static void buildTrialCity(ServerWorld world, AncientCityTrialPortal.FrameTarget sourceFrame, TrialInstance trial) {
        BlockBox clearBox = getTrialBuildBox(world, trial);
        loadChunks(world, clearBox);
        clearBox(world, clearBox);
        buildPlatform(world, trial);
        generateShiftedAncientCity(world, sourceFrame, trial);
        prepareStandingSpot(world, trial.trialSpawn);
    }

    private static void clearTrialBuild(TrialInstance trial) {
        BlockBox clearBox = getTrialBuildBox(trial.world, trial);
        loadChunks(trial.world, clearBox);
        clearBox(trial.world, clearBox);
    }

    private static BlockBox getTrialBuildBox(ServerWorld world, TrialInstance trial) {
        return clampBoxVertically(
                expandBox(trial.cityBox, ISLAND_MARGIN, CLEAR_VERTICAL_PADDING, ISLAND_MARGIN),
                world.getBottomY(),
                world.getTopYInclusive()
        );
    }

    private static void clearBox(ServerWorld world, BlockBox box) {
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

    private static void buildPlatform(ServerWorld world, TrialInstance trial) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = trial.platformBox.getMinX(); x <= trial.platformBox.getMaxX(); x++) {
            for (int z = trial.platformBox.getMinZ(); z <= trial.platformBox.getMaxZ(); z++) {
                if (!isInsidePlatformFootprint(trial, x, z)) {
                    continue;
                }
                int noise = Math.floorMod(hash(x, z, trial.instanceIndex), 7);
                for (int y = trial.platformBox.getMinY(); y <= trial.platformBox.getMaxY(); y++) {
                    world.setBlockState(pos.set(x, y, z), platformState(y == trial.platformTopY, noise), BULK_BLOCK_FLAGS);
                }
            }
        }
    }

    private static boolean isInsidePlatformFootprint(TrialInstance trial, int x, int z) {
        double centerX = (trial.platformBox.getMinX() + trial.platformBox.getMaxX()) * 0.5D;
        double centerZ = (trial.platformBox.getMinZ() + trial.platformBox.getMaxZ()) * 0.5D;
        double radiusX = (trial.platformBox.getMaxX() - trial.platformBox.getMinX() + 1) * 0.5D;
        double radiusZ = (trial.platformBox.getMaxZ() - trial.platformBox.getMinZ() + 1) * 0.5D;
        double normalizedX = (x + 0.5D - centerX) / radiusX;
        double normalizedZ = (z + 0.5D - centerZ) / radiusZ;
        return normalizedX * normalizedX + normalizedZ * normalizedZ <= 1.0D;
    }

    private static BlockState platformState(boolean top, int noise) {
        if (top && noise == 0) {
            return Blocks.SCULK.getDefaultState();
        }
        if (top && noise == 1) {
            return Blocks.TUFF.getDefaultState();
        }
        return Blocks.DEEPSLATE.getDefaultState();
    }

    private static int hash(int x, int z, int salt) {
        int value = x * 73428767 ^ z * 912931 ^ salt * 42349;
        value ^= value >>> 13;
        value *= 1274126177;
        return value ^ value >>> 16;
    }

    private static void generateShiftedAncientCity(ServerWorld world, AncientCityTrialPortal.FrameTarget sourceFrame, TrialInstance trial) {
        StructureContext context = StructureContext.from(world);
        NbtList piecesNbt = new NbtList();
        for (StructurePiece piece : sourceFrame.structureStart().getChildren()) {
            piecesNbt.add(piece.toNbt(context));
        }

        StructurePiecesList pieces = StructurePiecesList.fromNbt(piecesNbt, context);
        ChunkGenerator chunkGenerator = world.getChunkManager().getChunkGenerator();
        BlockBox generationBox = expandBox(trial.cityBox, 8, 8, 8);
        Random random = Random.create(world.getSeed() ^ trial.instanceIndex);
        for (StructurePiece piece : pieces.pieces()) {
            piece.translate(trial.offsetX, trial.offsetY, trial.offsetZ);
            piece.generate(world, world.getStructureAccessor(), chunkGenerator, random, generationBox, new ChunkPos(piece.getCenter()), BlockPos.ORIGIN);
        }
    }

    private static void spawnPrizeChest(TrialInstance trial) {
        BlockPos chestPos = trial.prizeChestPos;
        prepareChestSpot(trial.world, chestPos);
        trial.world.setBlockState(chestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, trial.prizeChestFacing), Block.NOTIFY_ALL);
        BlockEntity blockEntity = trial.world.getBlockEntity(chestPos);
        if (blockEntity instanceof ChestBlockEntity chest) {
            chest.setStack(11, AncientEnchantmentLogic.enchantedBook(trial.world, ModEnchantments.WARBOUND));
            chest.setStack(13, new ItemStack(ModItems.ANCIENT_SWORD));
            chest.setStack(15, AncientEnchantmentLogic.enchantedBook(trial.world, ModEnchantments.SOUL_SIPHON));
            chest.setStack(22, AncientEnchantmentLogic.enchantedBook(trial.world, ModEnchantments.TROPHY_RITE));
            chest.markDirty();
        }
    }

    private static void prepareStandingSpot(ServerWorld world, BlockPos pos) {
        loadChunks(world, boxAround(pos, 1, 1, 2));
        world.setBlockState(pos.down(), Blocks.POLISHED_DEEPSLATE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(pos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
    }

    private static void prepareChestSpot(ServerWorld world, BlockPos chestPos) {
        loadChunks(world, boxAround(chestPos, 1, 1, 2));
        world.setBlockState(chestPos.down(), Blocks.POLISHED_DEEPSLATE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(chestPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(chestPos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
    }

    private static BlockBox boxAround(BlockPos center, int horizontalRadius, int down, int up) {
        return new BlockBox(
                center.getX() - horizontalRadius,
                center.getY() - down,
                center.getZ() - horizontalRadius,
                center.getX() + horizontalRadius,
                center.getY() + up,
                center.getZ() + horizontalRadius
        );
    }

    private static BlockBox offsetBox(BlockBox box, int x, int y, int z) {
        return new BlockBox(
                box.getMinX() + x,
                box.getMinY() + y,
                box.getMinZ() + z,
                box.getMaxX() + x,
                box.getMaxY() + y,
                box.getMaxZ() + z
        );
    }

    private static BlockBox expandBox(BlockBox box, int x, int y, int z) {
        return new BlockBox(
                box.getMinX() - x,
                box.getMinY() - y,
                box.getMinZ() - z,
                box.getMaxX() + x,
                box.getMaxY() + y,
                box.getMaxZ() + z
        );
    }

    private static BlockBox clampBoxVertically(BlockBox box, int minY, int maxY) {
        return new BlockBox(
                box.getMinX(),
                Math.max(box.getMinY(), minY),
                box.getMinZ(),
                box.getMaxX(),
                Math.min(box.getMaxY(), maxY),
                box.getMaxZ()
        );
    }

    private static void loadChunks(ServerWorld world, BlockBox box) {
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

    private static ServerPlayerEntity teleport(ServerPlayerEntity player, ServerWorld world, Vec3d position, float yaw, float pitch) {
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

    private static final class TrialInstance {
        private final ServerWorld world;
        private final ServerWorld returnWorld;
        private final int instanceIndex;
        private final int offsetX;
        private final int offsetY;
        private final int offsetZ;
        private final BlockBox cityBox;
        private final BlockBox platformBox;
        private final int platformTopY;
        private final BlockPos trialSpawn;
        private final BlockPos trialAltarBase;
        private final BlockPos prizeChestPos;
        private final Direction prizeChestFacing;
        private final BlockPos returnSpawn;
        private final Direction.Axis exitPortalAxis;
        private final List<BlockPos> exitPortalPositions;
        private final String mobTag;
        private int currentWave;
        private int waveSettleTicks;
        private int nextWaveDelayTicks;
        private boolean completed;

        private TrialInstance(
                ServerWorld world,
                ServerWorld returnWorld,
                int instanceIndex,
                int offsetX,
                int offsetY,
                int offsetZ,
                BlockBox cityBox,
                BlockBox platformBox,
                int platformTopY,
                BlockPos trialSpawn,
                BlockPos trialAltarBase,
                BlockPos prizeChestPos,
                Direction prizeChestFacing,
                BlockPos returnSpawn,
                Direction.Axis exitPortalAxis,
                List<BlockPos> exitPortalPositions,
                String mobTag
        ) {
            this.world = world;
            this.returnWorld = returnWorld;
            this.instanceIndex = instanceIndex;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.cityBox = cityBox;
            this.platformBox = platformBox;
            this.platformTopY = platformTopY;
            this.trialSpawn = trialSpawn;
            this.trialAltarBase = trialAltarBase;
            this.prizeChestPos = prizeChestPos;
            this.prizeChestFacing = prizeChestFacing;
            this.returnSpawn = returnSpawn;
            this.exitPortalAxis = exitPortalAxis;
            this.exitPortalPositions = exitPortalPositions;
            this.mobTag = mobTag;
        }
    }

    private record TrialMobStatus(int total, Map<EntityType<?>, Integer> counts) {
    }

    private static final class TrialGhastApproachGoal extends Goal {
        private final GhastEntity ghast;

        private TrialGhastApproachGoal(GhastEntity ghast) {
            this.ghast = ghast;
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            return hasUsableTarget(ghast);
        }

        @Override
        public boolean shouldContinue() {
            return hasUsableTarget(ghast);
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = ghast.getTarget();
            if (!isUsableTarget(target)) {
                return;
            }

            ghast.getMoveControl().moveTo(
                    target.getX(),
                    target.getEyeY() + TRIAL_GHAST_APPROACH_HEIGHT,
                    target.getZ(),
                    1.0D
            );
            ghast.getLookControl().lookAt(target, 90.0F, 90.0F);
        }
    }

    private static final class TrialPhantomApproachGoal extends Goal {
        private final PhantomEntity phantom;

        private TrialPhantomApproachGoal(PhantomEntity phantom) {
            this.phantom = phantom;
        }

        @Override
        public boolean canStart() {
            return hasUsableTarget(phantom);
        }

        @Override
        public boolean shouldContinue() {
            return hasUsableTarget(phantom);
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = phantom.getTarget();
            if (!isUsableTarget(target)) {
                return;
            }

            PhantomEntityAccessor accessor = (PhantomEntityAccessor) phantom;
            accessor.quarkmod_ancientexpansion$setCirclingCenter(target.getBlockPos().up(TRIAL_PHANTOM_CIRCLING_HEIGHT));
            accessor.quarkmod_ancientexpansion$setTargetPosition(new Vec3d(target.getX(), target.getBodyY(0.5D), target.getZ()));
            phantom.getLookControl().lookAt(target, 90.0F, 90.0F);
        }
    }

    private static boolean hasUsableTarget(MobEntity mob) {
        return isUsableTarget(mob.getTarget());
    }

    private static boolean isUsableTarget(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved();
    }

    private record WaveSpawn(EntityType<? extends Entity> entityType, int count, boolean flying) {
    }
}
