package com.strangequark.ancientmaze.enchantment;

import com.strangequark.ancientmaze.item.ModItems;
import com.strangequark.ancientmaze.network.EchoProspectorPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AncientEnchantmentLogic {
    private static final int ECHO_HORIZONTAL_RADIUS = 20;
    private static final int ECHO_VERTICAL_RADIUS = 20;
    private static final int ECHO_COOLDOWN_TICKS = 20 * 30;
    private static final int ECHO_MARKER_LIFETIME_TICKS = 20 * 12;
    private static final int ECHO_MARKER_LIMIT = 256;
    private static final int END_PORTAL_CLEANUP_RADIUS = 5;
    private static final int END_PORTAL_CLEANUP_LIMIT = 64;
    private static final String ECHO_MARKER_TAG = "quarkmod_ancient_maze_echo_marker";
    private static final float RELIC_EFFICIENCY_SPEED_PER_BLOCK = 0.75F;
    private static final int RELIC_EFFICIENCY_MAX_STREAK = 4096;
    private static final Set<RegistryKey<Enchantment>> ANCIENT_PICKAXE_ONLY_ENCHANTMENTS = Set.of(
            ModEnchantments.RELIC_EFFICIENCY,
            ModEnchantments.ECHO_PROSPECTOR,
            ModEnchantments.WORLDBREAKER
    );
    private static final Set<Block> WORLDBREAKER_EXCLUDED_BLOCKS = Set.of(
            Blocks.AIR,
            Blocks.CAVE_AIR,
            Blocks.VOID_AIR,
            Blocks.WATER,
            Blocks.LAVA,
            Blocks.BUBBLE_COLUMN,
            Blocks.FIRE,
            Blocks.SOUL_FIRE,
            Blocks.PISTON_HEAD,
            Blocks.MOVING_PISTON,
            Blocks.NETHER_PORTAL,
            Blocks.END_PORTAL,
            Blocks.END_GATEWAY,
            Blocks.BARRIER,
            Blocks.LIGHT,
            Blocks.STRUCTURE_VOID,
            Blocks.COMMAND_BLOCK,
            Blocks.REPEATING_COMMAND_BLOCK,
            Blocks.CHAIN_COMMAND_BLOCK,
            Blocks.STRUCTURE_BLOCK,
            Blocks.JIGSAW,
            Blocks.TEST_BLOCK,
            Blocks.TEST_INSTANCE_BLOCK
    );
    private static final Set<Block> EXPLICIT_VALUABLE_BLOCKS = Set.of(
            Blocks.ANCIENT_DEBRIS,
            Blocks.RAW_COPPER_BLOCK,
            Blocks.RAW_IRON_BLOCK,
            Blocks.RAW_GOLD_BLOCK,
            Blocks.COPPER_BLOCK,
            Blocks.IRON_BLOCK,
            Blocks.GOLD_BLOCK,
            Blocks.DIAMOND_BLOCK,
            Blocks.EMERALD_BLOCK,
            Blocks.LAPIS_BLOCK,
            Blocks.REDSTONE_BLOCK,
            Blocks.NETHERITE_BLOCK,
            Blocks.AMETHYST_BLOCK,
            Blocks.BUDDING_AMETHYST,
            Blocks.SPAWNER,
            Blocks.TRIAL_SPAWNER,
            Blocks.VAULT
    );
    private static final List<BlockPos> ECHO_SCAN_OFFSETS = createEchoScanOffsets();
    private static final ConcurrentMap<UUID, MiningStreak> RELIC_EFFICIENCY_STREAKS = new ConcurrentHashMap<>();

    private AncientEnchantmentLogic() {
    }

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register(AncientEnchantmentLogic::afterBlockBreak);
        ServerTickEvents.END_WORLD_TICK.register(AncientEnchantmentLogic::clearExpiredEchoMarkers);
    }

    public static boolean hasEnchantment(ItemStack stack, RegistryKey<Enchantment> enchantment) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (isAncientPickaxeOnlyEnchantment(enchantment) && !ModItems.isAncientPickaxe(stack)) {
            return false;
        }

        return EnchantmentHelper.getEnchantments(stack)
                .getEnchantments()
                .stream()
                .map(RegistryEntry::getKey)
                .flatMap(Optional::stream)
                .anyMatch(enchantment::equals);
    }

    public static boolean hasInvalidAncientPickaxeOnlyEnchantments(ItemStack stack) {
        if (stack == null
                || stack.isEmpty()
                || ModItems.isAncientPickaxe(stack)
                || stack.isOf(net.minecraft.item.Items.ENCHANTED_BOOK)) {
            return false;
        }

        return EnchantmentHelper.getEnchantments(stack)
                .getEnchantments()
                .stream()
                .anyMatch(AncientEnchantmentLogic::isAncientPickaxeOnlyEnchantment);
    }

    public static boolean isAncientPickaxeOnlyEnchantment(RegistryKey<Enchantment> enchantment) {
        return ANCIENT_PICKAXE_ONLY_ENCHANTMENTS.contains(enchantment);
    }

    private static boolean isAncientPickaxeOnlyEnchantment(RegistryEntry<Enchantment> enchantment) {
        return enchantment.getKey()
                .filter(ANCIENT_PICKAXE_ONLY_ENCHANTMENTS::contains)
                .isPresent();
    }

    public static ItemStack enchantedBook(ServerWorld world, RegistryKey<Enchantment> enchantment) {
        RegistryEntry.Reference<Enchantment> entry = world.getRegistryManager().getEntryOrThrow(enchantment);
        ItemStack stack = new ItemStack(net.minecraft.item.Items.ENCHANTED_BOOK);
        stack.addEnchantment(entry, 1);
        return stack;
    }

    public static boolean canWorldbreak(PlayerEntity player, BlockState state) {
        return player != null
                && state != null
                && ModItems.isAncientPickaxe(player.getMainHandStack())
                && hasEnchantment(player.getMainHandStack(), ModEnchantments.WORLDBREAKER)
                && !WORLDBREAKER_EXCLUDED_BLOCKS.contains(state.getBlock());
    }

    public static float worldbreakerMiningDelta(BlockState state, PlayerEntity player) {
        float hardness = worldbreakerHardness(state);
        return player.getBlockBreakingSpeed(state) / hardness / 30.0F;
    }

    public static float relicEfficiencyBonus(PlayerEntity player, BlockState state) {
        ItemStack stack = player.getMainHandStack();
        if (!ModItems.isAncientPickaxe(stack) || !hasEnchantment(stack, ModEnchantments.RELIC_EFFICIENCY)) {
            return 0.0F;
        }

        MiningStreak streak = RELIC_EFFICIENCY_STREAKS.get(player.getUuid());
        Identifier blockId = Registries.BLOCK.getId(state.getBlock());
        if (streak == null || !streak.blockId().equals(blockId)) {
            return 0.0F;
        }

        int cappedStreak = Math.min(streak.count(), RELIC_EFFICIENCY_MAX_STREAK);
        return cappedStreak * RELIC_EFFICIENCY_SPEED_PER_BLOCK;
    }

    public static boolean emitEchoProspectorPulse(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
        if (!ModItems.isAncientPickaxe(stack) || !hasEnchantment(stack, ModEnchantments.ECHO_PROSPECTOR)) {
            return false;
        }
        if (player.getItemCooldownManager().isCoolingDown(stack)) {
            return true;
        }

        List<BlockPos> markers = new ArrayList<>(ECHO_MARKER_LIMIT);
        BlockPos center = player.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (BlockPos offset : ECHO_SCAN_OFFSETS) {
            if (markers.size() >= ECHO_MARKER_LIMIT) {
                break;
            }

            pos.set(center.getX() + offset.getX(), center.getY() + offset.getY(), center.getZ() + offset.getZ());
            if (pos.getY() < world.getBottomY() || pos.getY() > world.getTopYInclusive() || !world.isChunkLoaded(pos)) {
                continue;
            }

            BlockState state = world.getBlockState(pos);
            if (isValuableBlock(state)) {
                markers.add(pos.toImmutable());
            }
        }

        if (ServerPlayNetworking.canSend(player, EchoProspectorPayload.ID)) {
            ServerPlayNetworking.send(player, new EchoProspectorPayload(markers));
        }
        player.playSoundToPlayer(SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.PLAYERS, 0.7F, 1.35F);
        player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.35F, 0.65F);
        player.getItemCooldownManager().set(stack, ECHO_COOLDOWN_TICKS);
        return true;
    }

    private static List<BlockPos> createEchoScanOffsets() {
        List<BlockPos> offsets = new ArrayList<>();
        long horizontalRadiusSquared = (long) ECHO_HORIZONTAL_RADIUS * ECHO_HORIZONTAL_RADIUS;
        long verticalRadiusSquared = (long) ECHO_VERTICAL_RADIUS * ECHO_VERTICAL_RADIUS;
        long ellipsoidLimit = horizontalRadiusSquared * verticalRadiusSquared;

        for (int y = -ECHO_VERTICAL_RADIUS; y <= ECHO_VERTICAL_RADIUS; y++) {
            for (int x = -ECHO_HORIZONTAL_RADIUS; x <= ECHO_HORIZONTAL_RADIUS; x++) {
                for (int z = -ECHO_HORIZONTAL_RADIUS; z <= ECHO_HORIZONTAL_RADIUS; z++) {
                    long horizontalDistanceSquared = (long) x * x + (long) z * z;
                    long verticalDistanceSquared = (long) y * y;
                    if (horizontalDistanceSquared * verticalRadiusSquared + verticalDistanceSquared * horizontalRadiusSquared <= ellipsoidLimit) {
                        offsets.add(new BlockPos(x, y, z));
                    }
                }
            }
        }

        offsets.sort(Comparator
                .comparingInt(AncientEnchantmentLogic::echoOffsetDistanceSquared)
                .thenComparingInt(offset -> offset.getY() < 0 ? 1 : 0));
        return List.copyOf(offsets);
    }

    private static int echoOffsetDistanceSquared(BlockPos offset) {
        return offset.getX() * offset.getX() + offset.getY() * offset.getY() + offset.getZ() * offset.getZ();
    }

    public static boolean isRelicTradeItem(ItemStack stack) {
        return stack.isOf(ModItems.ANCIENT_RELIC);
    }

    private static void afterBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }

        updateRelicEfficiencyStreak(player, state);
        removeEndPortalWhenFrameBreaks(serverWorld, pos, state);
        dropWorldbreakerBlock(serverWorld, player, pos, state, blockEntity);
    }

    private static void updateRelicEfficiencyStreak(PlayerEntity player, BlockState state) {
        ItemStack stack = player.getMainHandStack();
        if (!ModItems.isAncientPickaxe(stack) || !hasEnchantment(stack, ModEnchantments.RELIC_EFFICIENCY)) {
            RELIC_EFFICIENCY_STREAKS.remove(player.getUuid());
            return;
        }

        Identifier blockId = Registries.BLOCK.getId(state.getBlock());
        RELIC_EFFICIENCY_STREAKS.compute(player.getUuid(), (uuid, streak) -> {
            if (streak != null && streak.blockId().equals(blockId)) {
                return new MiningStreak(blockId, streak.count() + 1);
            }
            return new MiningStreak(blockId, 1);
        });
    }

    private static void dropWorldbreakerBlock(
            ServerWorld world,
            PlayerEntity player,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity
    ) {
        ItemStack stack = player.getMainHandStack();
        if (!ModItems.isAncientPickaxe(stack)
                || !hasEnchantment(stack, ModEnchantments.WORLDBREAKER)
                || !canWorldbreak(player, state)
                || player.shouldSkipBlockDrops()) {
            return;
        }

        if (state.isOf(Blocks.SPAWNER)) {
            dropSpawnerSpawnEgg(world, pos, blockEntity);
        }

        List<ItemStack> vanillaDrops = Block.getDroppedStacks(state, world, pos, blockEntity, player, stack);
        if (!vanillaDrops.isEmpty()) {
            return;
        }

        Item item = state.getBlock().asItem();
        if (item != net.minecraft.item.Items.AIR) {
            Block.dropStack(world, pos, new ItemStack(item));
        }
    }

    private static void dropSpawnerSpawnEgg(ServerWorld world, BlockPos pos, @Nullable BlockEntity blockEntity) {
        if (!(blockEntity instanceof MobSpawnerBlockEntity spawnerBlockEntity)) {
            return;
        }

        Entity renderedEntity = spawnerBlockEntity.getLogic().getRenderedEntity(world, pos);
        if (renderedEntity == null) {
            return;
        }

        SpawnEggItem spawnEgg = SpawnEggItem.forEntity(renderedEntity.getType());
        if (spawnEgg != null) {
            Block.dropStack(world, pos, new ItemStack(spawnEgg));
        }
    }

    private static void removeEndPortalWhenFrameBreaks(ServerWorld world, BlockPos framePos, BlockState brokenState) {
        if (!brokenState.isOf(Blocks.END_PORTAL_FRAME)) {
            return;
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        BlockPos.Mutable scanPos = new BlockPos.Mutable();
        for (int y = framePos.getY() - 1; y <= framePos.getY() + 1; y++) {
            for (int x = framePos.getX() - END_PORTAL_CLEANUP_RADIUS; x <= framePos.getX() + END_PORTAL_CLEANUP_RADIUS; x++) {
                for (int z = framePos.getZ() - END_PORTAL_CLEANUP_RADIUS; z <= framePos.getZ() + END_PORTAL_CLEANUP_RADIUS; z++) {
                    scanPos.set(x, y, z);
                    if (world.getBlockState(scanPos).isOf(Blocks.END_PORTAL)) {
                        BlockPos seed = scanPos.toImmutable();
                        queue.add(seed);
                        visited.add(seed);
                    }
                }
            }
        }

        int removed = 0;
        while (!queue.isEmpty() && removed < END_PORTAL_CLEANUP_LIMIT) {
            BlockPos portalPos = queue.removeFirst();
            if (!world.getBlockState(portalPos).isOf(Blocks.END_PORTAL)) {
                continue;
            }

            world.setBlockState(portalPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            removed++;

            for (Direction direction : Direction.Type.HORIZONTAL) {
                BlockPos neighbor = portalPos.offset(direction);
                if (visited.add(neighbor) && isWithinEndPortalCleanupRange(framePos, neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
    }

    private static boolean isWithinEndPortalCleanupRange(BlockPos framePos, BlockPos portalPos) {
        return Math.abs(portalPos.getX() - framePos.getX()) <= END_PORTAL_CLEANUP_RADIUS
                && Math.abs(portalPos.getY() - framePos.getY()) <= 1
                && Math.abs(portalPos.getZ() - framePos.getZ()) <= END_PORTAL_CLEANUP_RADIUS;
    }

    private static boolean isValuableBlock(BlockState state) {
        Block block = state.getBlock();
        if (EXPLICIT_VALUABLE_BLOCKS.contains(block)
                || state.isIn(BlockTags.COAL_ORES)
                || state.isIn(BlockTags.COPPER_ORES)
                || state.isIn(BlockTags.IRON_ORES)
                || state.isIn(BlockTags.GOLD_ORES)
                || state.isIn(BlockTags.REDSTONE_ORES)
                || state.isIn(BlockTags.LAPIS_ORES)
                || state.isIn(BlockTags.DIAMOND_ORES)
                || state.isIn(BlockTags.EMERALD_ORES)) {
            return true;
        }

        Identifier id = Registries.BLOCK.getId(block);
        String path = id.getPath();
        return path.endsWith("_ore") || path.endsWith("ore") || path.contains("_ore_");
    }

    private static void clearExpiredEchoMarkers(ServerWorld world) {
        if (world.getTime() % 20L != 0L) {
            return;
        }

        Set<Entity> expired = new HashSet<>();
        for (Entity entity : world.iterateEntities()) {
            if (entity.getCommandTags().contains(ECHO_MARKER_TAG) && entity.age > ECHO_MARKER_LIFETIME_TICKS) {
                expired.add(entity);
            }
        }
        expired.forEach(Entity::discard);
    }

    private static float worldbreakerHardness(BlockState state) {
        if (state.isOf(Blocks.BEDROCK)) {
            return 75.0F;
        }
        if (state.isOf(Blocks.END_PORTAL_FRAME)) {
            return 15.0F;
        }
        return 25.0F;
    }

    private record MiningStreak(Identifier blockId, int count) {
    }
}
