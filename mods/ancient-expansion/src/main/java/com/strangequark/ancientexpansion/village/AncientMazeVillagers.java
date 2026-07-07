package com.strangequark.ancientexpansion.village;

import com.google.common.collect.ImmutableSet;
import com.strangequark.ancientexpansion.AncientExpansionMod;
import com.strangequark.ancientexpansion.enchantment.AncientEnchantmentLogic;
import com.strangequark.ancientexpansion.enchantment.ModEnchantments;
import com.strangequark.ancientexpansion.item.ModItems;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.poi.PointOfInterestType;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class AncientMazeVillagers {
    public static final RegistryKey<VillagerProfession> ANCIENT_KEEPER = RegistryKey.of(
            RegistryKeys.VILLAGER_PROFESSION,
            AncientExpansionMod.id("ancient_keeper")
    );

    private static final float PRICE_MULTIPLIER = 0.0F;
    private static final String KEEPER_TAG = "quarkmod_ancient_expansion_keeper";
    private static final String KEEPER_NAME = "Ancient Keeper";
    private static final List<RegistryKey<net.minecraft.enchantment.Enchantment>> KEEPER_BOOKS = List.of(
            ModEnchantments.RELIC_EFFICIENCY,
            ModEnchantments.ECHO_PROSPECTOR,
            ModEnchantments.WORLDBREAKER
    );

    private AncientMazeVillagers() {
    }

    public static void register() {
        registerProfession();
        registerTrades();
    }

    public static void configureTowerVillager(VillagerEntity villager, ServerWorld world) {
        applyKeeperIdentity(villager);
        replaceKeeperOffers(villager);
        villager.reinitializeBrain(world);
    }

    public static boolean isAncientKeeper(VillagerEntity villager) {
        return hasKeeperIdentity(villager);
    }

    public static boolean shouldKeepKeeperProfession(VillagerEntity villager, VillagerData requestedData) {
        return hasKeeperIdentity(villager) && !hasKeeperProfession(requestedData);
    }

    public static VillagerData keeperData(VillagerData data) {
        return data.withProfession(professionEntry()).withLevel(1);
    }

    private static boolean hasKeeperIdentity(VillagerEntity villager) {
        if (villager.getCommandTags().contains(KEEPER_TAG)) {
            return true;
        }
        if (villager.hasCustomName() && KEEPER_NAME.equals(villager.getCustomName().getString())) {
            return true;
        }
        return hasKeeperProfession(villager);
    }

    private static boolean hasKeeperProfession(VillagerEntity villager) {
        return hasKeeperProfession(villager.getVillagerData());
    }

    private static boolean hasKeeperProfession(VillagerData data) {
        Optional<RegistryKey<VillagerProfession>> professionKey = data.profession().getKey();
        return professionKey.isPresent() && ANCIENT_KEEPER.equals(professionKey.get());
    }

    private static void applyKeeperIdentity(VillagerEntity villager) {
        villager.addCommandTag(KEEPER_TAG);
        if (!hasKeeperProfession(villager) || villager.getVillagerData().level() != 1) {
            villager.setVillagerData(keeperData(villager.getVillagerData()));
        }
        villager.setExperience(0);
        villager.setPersistent();
        villager.setCustomName(null);
        villager.setCustomNameVisible(false);
    }

    public static void lockRelicChoices(VillagerEntity villager, TradeOffer usedOffer) {
        if (!isAncientKeeper(villager) || !isRelicChoice(usedOffer)) {
            return;
        }

        villager.getOffers().forEach(offer -> {
            if (isRelicChoice(offer)) {
                offer.disable();
            }
        });
    }

    public static boolean shouldPreventRestock(VillagerEntity villager) {
        return isAncientKeeper(villager);
    }

    public static boolean replaceKeeperOffers(VillagerEntity villager) {
        if (!isAncientKeeper(villager) || !(villager.getWorld() instanceof ServerWorld world)) {
            return false;
        }

        applyKeeperIdentity(villager);
        villager.setOffers(createKeeperOffers(world));
        return true;
    }

    public static void ensureOnlyKeeperOffers(VillagerEntity villager) {
        if (!isAncientKeeper(villager) || !(villager.getWorld() instanceof ServerWorld world)) {
            return;
        }
        if (isKeeperOfferList(villager.getOffers())) {
            return;
        }

        applyKeeperIdentity(villager);
        villager.setOffers(createKeeperOffers(world));
    }

    private static void registerProfession() {
        Predicate<RegistryEntry<PointOfInterestType>> noWorkstation = poi -> false;
        Registry.register(
                Registries.VILLAGER_PROFESSION,
                ANCIENT_KEEPER.getValue(),
                new VillagerProfession(
                        Text.translatable(Util.createTranslationKey("entity.minecraft.villager", ANCIENT_KEEPER.getValue())),
                        noWorkstation,
                        noWorkstation,
                        ImmutableSet.of(),
                        ImmutableSet.of(),
                        SoundEvents.ENTITY_VILLAGER_WORK_LIBRARIAN
                )
        );
    }

    private static void registerTrades() {
        TradeOfferHelper.registerVillagerOffers(ANCIENT_KEEPER, 1, factories -> factories.addAll(List.of(
                relicBook(ModEnchantments.RELIC_EFFICIENCY),
                relicBook(ModEnchantments.ECHO_PROSPECTOR),
                relicBook(ModEnchantments.WORLDBREAKER)
        )));
    }

    private static TradeOffers.Factory relicBook(RegistryKey<net.minecraft.enchantment.Enchantment> enchantment) {
        return (entity, random) -> {
            if (!(entity.getWorld() instanceof ServerWorld world)) {
                return null;
            }
            return relicBookOffer(world, enchantment);
        };
    }

    private static TradeOfferList createKeeperOffers(ServerWorld world) {
        TradeOfferList offers = new TradeOfferList();
        for (RegistryKey<net.minecraft.enchantment.Enchantment> enchantment : KEEPER_BOOKS) {
            offers.add(relicBookOffer(world, enchantment));
        }
        return offers;
    }

    private static TradeOffer relicBookOffer(ServerWorld world, RegistryKey<net.minecraft.enchantment.Enchantment> enchantment) {
        return new TradeOffer(
                new TradedItem(ModItems.ANCIENT_RELIC, 1),
                AncientEnchantmentLogic.enchantedBook(world, enchantment),
                1,
                0,
                PRICE_MULTIPLIER
        );
    }

    private static RegistryEntry<VillagerProfession> professionEntry() {
        return Registries.VILLAGER_PROFESSION.getEntry(Registries.VILLAGER_PROFESSION.getValueOrThrow(ANCIENT_KEEPER));
    }

    private static boolean isRelicChoice(TradeOffer offer) {
        return AncientEnchantmentLogic.isRelicTradeItem(offer.getOriginalFirstBuyItem())
                && offer.getSecondBuyItem().isEmpty();
    }

    private static boolean isKeeperOfferList(TradeOfferList offers) {
        if (offers == null || offers.size() != KEEPER_BOOKS.size()) {
            return false;
        }

        Set<RegistryKey<net.minecraft.enchantment.Enchantment>> seen = new HashSet<>();
        for (TradeOffer offer : offers) {
            if (!isRelicChoice(offer)) {
                return false;
            }

            RegistryKey<net.minecraft.enchantment.Enchantment> matched = null;
            for (RegistryKey<net.minecraft.enchantment.Enchantment> enchantment : KEEPER_BOOKS) {
                if (AncientEnchantmentLogic.hasEnchantment(offer.getSellItem(), enchantment)) {
                    if (matched != null) {
                        return false;
                    }
                    matched = enchantment;
                }
            }
            if (matched == null || !seen.add(matched)) {
                return false;
            }
        }
        return true;
    }
}
