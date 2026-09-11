package com.strangequark.villagebuilder.village;

import com.google.common.collect.ImmutableSet;
import com.strangequark.villagebuilder.VillageBuilderMod;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.poi.PointOfInterestType;

import java.util.List;
import java.util.function.Predicate;

public final class CityVillagers {
    public static final RegistryKey<PointOfInterestType> BUILDER_DESK_POI = poi("builder_desk");
    public static final RegistryKey<PointOfInterestType> MINER_STATION_POI = poi("miner_station");
    public static final RegistryKey<VillagerProfession> BUILDER = profession("builder");
    public static final RegistryKey<VillagerProfession> MINER = profession("miner");
    private CityVillagers() { }
    public static void register() {
        PointOfInterestHelper.register(BUILDER_DESK_POI.getValue(), 1, 1, ModBlocks.BUILDER_DESK);
        PointOfInterestHelper.register(MINER_STATION_POI.getValue(), 1, 1, ModBlocks.MINER_STATION);
        registerProfession(BUILDER, BUILDER_DESK_POI, SoundEvents.ENTITY_VILLAGER_WORK_TOOLSMITH);
        registerProfession(MINER, MINER_STATION_POI, SoundEvents.ENTITY_VILLAGER_WORK_ARMORER);
        trades(BUILDER, 1, buy(Items.OAK_PLANKS, 24, 1), sell(Items.SCAFFOLDING, 8, 1), sell(Items.BRICKS, 12, 2));
        trades(BUILDER, 2, sell(Items.GLASS_PANE, 16, 2), sell(Items.LANTERN, 2, 3));
        trades(MINER, 1, buy(Items.COAL, 16, 1), buy(Items.RAW_IRON, 8, 1), sell(Items.COBBLESTONE, 32, 1));
        trades(MINER, 2, sell(Items.DEEPSLATE, 24, 2), sell(Items.TORCH, 24, 1));
    }
    private static void registerProfession(RegistryKey<VillagerProfession> key, RegistryKey<PointOfInterestType> poi, net.minecraft.sound.SoundEvent sound) {
        Predicate<RegistryEntry<PointOfInterestType>> predicate = entry -> entry.matchesKey(poi);
        Registry.register(Registries.VILLAGER_PROFESSION, key.getValue(), new VillagerProfession(Text.translatable(Util.createTranslationKey("entity.minecraft.villager", key.getValue())), predicate, predicate, ImmutableSet.of(), ImmutableSet.of(), sound));
    }
    private static void trades(RegistryKey<VillagerProfession> profession, int level, TradeOffers.Factory... offers) { TradeOfferHelper.registerVillagerOffers(profession, level, factories -> factories.addAll(List.of(offers))); }
    private static TradeOffers.Factory buy(ItemConvertible item, int amount, int emeralds) { return (entity, random) -> new TradeOffer(new TradedItem(item, amount), new ItemStack(Items.EMERALD, emeralds), 24, 2, 0.05F); }
    private static TradeOffers.Factory sell(ItemConvertible item, int amount, int emeralds) { return (entity, random) -> new TradeOffer(new TradedItem(Items.EMERALD, emeralds), new ItemStack(item, amount), 16, 4, 0.05F); }
    private static RegistryKey<PointOfInterestType> poi(String path) { return RegistryKey.of(RegistryKeys.POINT_OF_INTEREST_TYPE, VillageBuilderMod.id(path)); }
    private static RegistryKey<VillagerProfession> profession(String path) { return RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION, VillageBuilderMod.id(path)); }
}
