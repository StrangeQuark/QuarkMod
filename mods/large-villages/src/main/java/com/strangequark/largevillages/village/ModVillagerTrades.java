package com.strangequark.largevillages.village;

import com.strangequark.largevillages.worldgen.ModStructureTags;
import net.minecraft.entity.Entity;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import org.jetbrains.annotations.Nullable;

public final class ModVillagerTrades {
    private static final TradeOffers.Factory LARGE_VILLAGE_MAP_FACTORY = new TradeOffers.SellMapFactory(
            14,
            ModStructureTags.ON_LARGE_VILLAGE_MAPS,
            "filled_map.large_village",
            MapDecorationTypes.VILLAGE_PLAINS,
            12,
            30
    );

    private ModVillagerTrades() {
    }

    @Nullable
    public static TradeOffer createLargeVillageMapOffer(Entity entity, Random random) {
        return LARGE_VILLAGE_MAP_FACTORY.create(entity, random);
    }
}
