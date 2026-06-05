package com.strangequark.dreamdimension.village;

import com.google.common.collect.ImmutableSet;
import com.strangequark.dreamdimension.DreamDimensionMod;
import com.strangequark.dreamdimension.item.ModItems;
import com.strangequark.dreamdimension.world.DreamDimensionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.World;
import net.minecraft.world.poi.PointOfInterestType;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class DreamVillagers {
    public static final RegistryKey<VillagerProfession> SOMNOLOGIST = key("somnologist");
    public static final RegistryKey<VillagerProfession> LUCID_CARTOGRAPHER = key("lucid_cartographer");
    public static final RegistryKey<VillagerProfession> ECHO_SEER = key("echo_seer");
    public static final RegistryKey<VillagerProfession> STARGAZER = key("stargazer");
    public static final RegistryKey<VillagerProfession> MEMORY_WEAVER = key("memory_weaver");
    public static final RegistryKey<VillagerProfession> MENDER_OF_WAKING = key("mender_of_waking");
    public static final RegistryKey<VillagerProfession> DRIFT_GARDENER = key("drift_gardener");

    private static final int DREAM_VILLAGER_LEVEL = 2;
    private static final double TARGET_CLEAR_RADIUS = 128.0D;
    private static final float PRICE_MULTIPLIER = 0.05F;
    private static final List<RegistryKey<VillagerProfession>> DREAM_PROFESSIONS = List.of(
            SOMNOLOGIST,
            LUCID_CARTOGRAPHER,
            ECHO_SEER,
            STARGAZER,
            MEMORY_WEAVER,
            MENDER_OF_WAKING,
            DRIFT_GARDENER
    );
    private static final Set<RegistryKey<VillagerProfession>> DREAM_PROFESSION_SET = Set.copyOf(DREAM_PROFESSIONS);

    private DreamVillagers() {
    }

    public static void register() {
        DreamVillageWorkstations.register();
        registerProfessions();
        registerTrades();
        registerEvents();
    }

    private static void registerProfessions() {
        registerProfession(SOMNOLOGIST, DreamVillageWorkstations.SOMNARIUM_POI, SoundEvents.ENTITY_VILLAGER_WORK_CLERIC);
        registerProfession(LUCID_CARTOGRAPHER, DreamVillageWorkstations.LUCID_CHARTING_TABLE_POI, SoundEvents.ENTITY_VILLAGER_WORK_CARTOGRAPHER);
        registerProfession(ECHO_SEER, DreamVillageWorkstations.ECHO_LECTERN_POI, SoundEvents.ENTITY_VILLAGER_WORK_LIBRARIAN);
        registerProfession(STARGAZER, DreamVillageWorkstations.STARGAZER_TABLE_POI, SoundEvents.ENTITY_VILLAGER_WORK_LIBRARIAN);
        registerProfession(MEMORY_WEAVER, DreamVillageWorkstations.MEMORY_LOOM_POI, SoundEvents.ENTITY_VILLAGER_WORK_SHEPHERD);
        registerProfession(MENDER_OF_WAKING, DreamVillageWorkstations.WAKING_ANVIL_POI, SoundEvents.ENTITY_VILLAGER_WORK_TOOLSMITH);
        registerProfession(DRIFT_GARDENER, DreamVillageWorkstations.DRIFT_COMPOSTER_POI, SoundEvents.ENTITY_VILLAGER_WORK_FARMER);
    }

    private static void registerProfession(
            RegistryKey<VillagerProfession> key,
            RegistryKey<PointOfInterestType> workstationKey,
            SoundEvent workSound
    ) {
        Predicate<RegistryEntry<PointOfInterestType>> workstation = poi -> poi.matchesKey(workstationKey);
        Registry.register(
                Registries.VILLAGER_PROFESSION,
                key.getValue(),
                new VillagerProfession(
                        Text.translatable(Util.createTranslationKey("entity.minecraft.villager", key.getValue())),
                        workstation,
                        workstation,
                        ImmutableSet.of(),
                        ImmutableSet.of(),
                        workSound
                )
        );
    }

    private static void registerTrades() {
        trades(SOMNOLOGIST, 1,
                buy(Items.PHANTOM_MEMBRANE, 3, 1, 32, 2),
                buy(Items.AMETHYST_SHARD, 8, 1, 32, 2),
                sell(ModItems.DREAM_POTION, 1, 12, 16, 6));
        trades(SOMNOLOGIST, 2,
                sellPotion(Potions.SLOW_FALLING, 9, 24, 8),
                sell(Items.LIGHT_BLUE_BED, 1, 7, 16, 6),
                sell(Items.CLOCK, 1, 10, 12, 8));
        trades(SOMNOLOGIST, 3,
                sell(Items.GOLDEN_CARROT, 3, 5, 24, 10),
                sellPotion(Potions.LONG_SLOW_FALLING, 13, 16, 12));

        trades(LUCID_CARTOGRAPHER, 1,
                buy(Items.PAPER, 24, 1, 64, 2),
                sell(Items.MAP, 1, 5, 24, 4),
                sell(Items.COMPASS, 1, 6, 24, 4));
        trades(LUCID_CARTOGRAPHER, 2,
                buy(Items.GLASS_PANE, 12, 1, 48, 3),
                sell(Items.SPYGLASS, 1, 10, 16, 8),
                sell(Items.RECOVERY_COMPASS, 1, 18, 8, 12));
        trades(LUCID_CARTOGRAPHER, 3,
                sell(Items.LODESTONE, 1, 24, 8, 16),
                sell(Items.ENDER_PEARL, 2, 7, 24, 10));

        trades(ECHO_SEER, 1,
                buy(Items.ECHO_SHARD, 1, 3, 24, 4),
                buy(Items.SCULK, 12, 1, 48, 2),
                sell(Items.EXPERIENCE_BOTTLE, 3, 4, 32, 6));
        trades(ECHO_SEER, 2,
                sell(Items.NAME_TAG, 1, 15, 12, 10),
                sell(Items.SCULK_SENSOR, 1, 9, 16, 8),
                sell(Items.CALIBRATED_SCULK_SENSOR, 1, 14, 12, 10));
        trades(ECHO_SEER, 3,
                sell(Items.ENDER_EYE, 1, 12, 16, 12),
                sell(Items.ECHO_SHARD, 1, 9, 16, 12));

        trades(STARGAZER, 1,
                buy(Items.AMETHYST_SHARD, 10, 1, 48, 2),
                buy(Items.ENDER_PEARL, 2, 1, 32, 4),
                sell(Items.SPECTRAL_ARROW, 8, 5, 32, 4));
        trades(STARGAZER, 2,
                sell(Items.SPYGLASS, 1, 9, 16, 8),
                sellPotion(Potions.NIGHT_VISION, 8, 24, 8),
                sell(Items.AMETHYST_BLOCK, 3, 4, 32, 6));
        trades(STARGAZER, 3,
                sellPotion(Potions.LONG_NIGHT_VISION, 12, 16, 12),
                sell(Items.END_CRYSTAL, 1, 28, 6, 20));

        trades(MEMORY_WEAVER, 1,
                buy(Items.STRING, 18, 1, 48, 2),
                buy(Items.WHITE_WOOL, 12, 1, 48, 2),
                sell(Items.PURPLE_CARPET, 6, 3, 32, 4));
        trades(MEMORY_WEAVER, 2,
                sell(Items.PURPLE_BED, 1, 7, 16, 6),
                sell(Items.LOOM, 1, 6, 16, 6),
                sell(Items.BOOKSHELF, 2, 8, 24, 8));
        trades(MEMORY_WEAVER, 3,
                sell(Items.LIGHT_BLUE_BANNER, 1, 6, 24, 8),
                sell(Items.PURPLE_BANNER, 1, 6, 24, 8));

        trades(MENDER_OF_WAKING, 1,
                buy(Items.IRON_INGOT, 6, 1, 48, 3),
                buy(Items.GOLD_INGOT, 4, 1, 48, 3),
                sell(Items.GRINDSTONE, 1, 6, 16, 5));
        trades(MENDER_OF_WAKING, 2,
                sell(Items.GOLDEN_APPLE, 1, 12, 12, 10),
                sellPotion(Potions.HEALING, 9, 20, 8),
                sell(Items.SMITHING_TABLE, 1, 7, 16, 6));
        trades(MENDER_OF_WAKING, 3,
                sell(Items.ANVIL, 1, 24, 8, 16),
                sellPotion(Potions.STRONG_HEALING, 14, 16, 12));

        trades(DRIFT_GARDENER, 1,
                buy(Items.MOSS_BLOCK, 12, 1, 48, 2),
                buy(Items.GLOW_BERRIES, 10, 1, 48, 2),
                sell(Items.BONE_MEAL, 8, 3, 32, 4));
        trades(DRIFT_GARDENER, 2,
                sell(Items.AZALEA, 2, 4, 24, 6),
                sell(Items.FLOWERING_AZALEA, 2, 5, 24, 6),
                sell(Items.CHORUS_FRUIT, 6, 8, 24, 8));
        trades(DRIFT_GARDENER, 3,
                sell(Items.OAK_SAPLING, 4, 4, 32, 8),
                sell(Items.CHERRY_SAPLING, 4, 5, 32, 8));
    }

    private static void registerEvents() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (world instanceof ServerWorld serverWorld && isDreamWorld(serverWorld) && entity instanceof VillagerEntity villager) {
                makeDreamVillager(villager, serverWorld);
            }
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (isDreamVillager(entity) && isMobDamage(source)) {
                return false;
            }
            return true;
        });

        ServerTickEvents.END_WORLD_TICK.register(DreamVillagers::clearMobTargets);
    }

    private static void makeDreamVillager(VillagerEntity villager, ServerWorld world) {
        if (!isDreamProfession(villager)) {
            RegistryKey<VillagerProfession> profession = findNearbyWorkstationProfession(villager)
                    .orElseGet(() -> deterministicProfession(villager));
            villager.setVillagerData(villager.getVillagerData().withProfession(professionEntry(profession)));
        }

        VillagerData data = villager.getVillagerData();
        if (data.level() < DREAM_VILLAGER_LEVEL) {
            villager.setVillagerData(data.withLevel(DREAM_VILLAGER_LEVEL));
        }

        villager.setExperience(Math.max(villager.getExperience(), VillagerData.getLowerLevelExperience(DREAM_VILLAGER_LEVEL)));
        villager.setPersistent();
        villager.reinitializeBrain(world);
    }

    private static Optional<RegistryKey<VillagerProfession>> findNearbyWorkstationProfession(VillagerEntity villager) {
        BlockPos origin = villager.getBlockPos();
        for (BlockPos pos : BlockPos.iterateOutwards(origin, 10, 4, 10)) {
            Optional<RegistryKey<VillagerProfession>> profession = DreamVillageWorkstations.professionFor(
                    villager.getWorld().getBlockState(pos).getBlock()
            );
            if (profession.isPresent()) {
                return profession;
            }
        }
        return Optional.empty();
    }

    private static RegistryKey<VillagerProfession> deterministicProfession(VillagerEntity villager) {
        return DREAM_PROFESSIONS.get(Math.floorMod(villager.getUuid().hashCode(), DREAM_PROFESSIONS.size()));
    }

    private static RegistryEntry<VillagerProfession> professionEntry(RegistryKey<VillagerProfession> profession) {
        return Registries.VILLAGER_PROFESSION.getEntry(Registries.VILLAGER_PROFESSION.getValueOrThrow(profession));
    }

    private static boolean isDreamVillager(LivingEntity entity) {
        return entity instanceof VillagerEntity villager
                && isDreamWorld(villager.getWorld())
                && isDreamProfession(villager);
    }

    private static boolean isDreamProfession(VillagerEntity villager) {
        Optional<RegistryKey<VillagerProfession>> professionKey = villager.getVillagerData().profession().getKey();
        return professionKey.isPresent() && DREAM_PROFESSION_SET.contains(professionKey.get());
    }

    private static boolean isMobDamage(DamageSource source) {
        return source.getAttacker() instanceof MobEntity || source.getSource() instanceof MobEntity;
    }

    private static void clearMobTargets(ServerWorld world) {
        if (!isDreamWorld(world)) {
            return;
        }

        for (ServerPlayerEntity player : world.getPlayers()) {
            Box box = player.getBoundingBox().expand(TARGET_CLEAR_RADIUS);
            for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, box, mob -> isDreamVillager(mob.getTarget()))) {
                mob.setTarget(null);
            }
        }
    }

    private static boolean isDreamWorld(World world) {
        return world.getRegistryKey().equals(DreamDimensionEvents.DREAM_WORLD);
    }

    private static RegistryKey<VillagerProfession> key(String path) {
        return RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION, DreamDimensionMod.id(path));
    }

    private static void trades(RegistryKey<VillagerProfession> profession, int level, TradeOffers.Factory... offers) {
        TradeOfferHelper.registerVillagerOffers(profession, level, factories -> factories.addAll(List.of(offers)));
    }

    private static TradeOffers.Factory buy(ItemConvertible item, int count, int emeralds, int maxUses, int experience) {
        return (entity, random) -> new TradeOffer(
                new TradedItem(item, count),
                new ItemStack(Items.EMERALD, emeralds),
                maxUses,
                experience,
                PRICE_MULTIPLIER
        );
    }

    private static TradeOffers.Factory sell(ItemConvertible item, int count, int emeralds, int maxUses, int experience) {
        return sell(new ItemStack(item, count), emeralds, maxUses, experience);
    }

    private static TradeOffers.Factory sellPotion(RegistryEntry<net.minecraft.potion.Potion> potion, int emeralds, int maxUses, int experience) {
        return sell(PotionContentsComponent.createStack(Items.POTION, potion), emeralds, maxUses, experience);
    }

    private static TradeOffers.Factory sell(ItemStack result, int emeralds, int maxUses, int experience) {
        return (entity, random) -> new TradeOffer(
                new TradedItem(Items.EMERALD, emeralds),
                result.copy(),
                maxUses,
                experience,
                PRICE_MULTIPLIER
        );
    }
}
