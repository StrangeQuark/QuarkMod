package com.strangequark.ancientmaze.enchantment;

import com.strangequark.ancientmaze.item.ModItems;
import com.strangequark.ancientmaze.network.EchoProspectorPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AncientEnchantmentLogic {
    private static final int ECHO_RADIUS = 50;
    private static final int ECHO_COOLDOWN_TICKS = 20 * 30;
    private static final int ECHO_MARKER_LIFETIME_TICKS = 20 * 12;
    private static final int ECHO_MARKER_LIMIT = 256;
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
        int minY = Math.max(world.getBottomY(), center.getY() - ECHO_RADIUS);
        int maxY = Math.min(world.getTopYInclusive(), center.getY() + ECHO_RADIUS);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int radiusSquared = ECHO_RADIUS * ECHO_RADIUS;
        for (int y = minY; y <= maxY && markers.size() < ECHO_MARKER_LIMIT; y++) {
            for (int x = center.getX() - ECHO_RADIUS; x <= center.getX() + ECHO_RADIUS && markers.size() < ECHO_MARKER_LIMIT; x++) {
                for (int z = center.getZ() - ECHO_RADIUS; z <= center.getZ() + ECHO_RADIUS && markers.size() < ECHO_MARKER_LIMIT; z++) {
                    if (center.getSquaredDistance(x, y, z) > radiusSquared) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (!world.isChunkLoaded(pos)) {
                        continue;
                    }

                    BlockState state = world.getBlockState(pos);
                    if (isValuableBlock(state)) {
                        markers.add(pos.toImmutable());
                    }
                }
            }
        }

        if (ServerPlayNetworking.canSend(player, EchoProspectorPayload.ID)) {
            ServerPlayNetworking.send(player, new EchoProspectorPayload(markers));
        }
        player.getItemCooldownManager().set(stack, ECHO_COOLDOWN_TICKS);
        return true;
    }

    public static boolean isRelicTradeItem(ItemStack stack) {
        return stack.isOf(ModItems.ANCIENT_RELIC);
    }

    private static void afterBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, @Nullable net.minecraft.block.entity.BlockEntity blockEntity) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }

        updateRelicEfficiencyStreak(player, state);
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
            @Nullable net.minecraft.block.entity.BlockEntity blockEntity
    ) {
        ItemStack stack = player.getMainHandStack();
        if (!ModItems.isAncientPickaxe(stack)
                || !hasEnchantment(stack, ModEnchantments.WORLDBREAKER)
                || !canWorldbreak(player, state)
                || player.shouldSkipBlockDrops()) {
            return;
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
