package com.strangequark.dreamdimension.item;

import com.strangequark.dreamdimension.DreamDimensionMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.function.Function;

public final class ModItems {
    public static final DreamPotionItem DREAM_POTION = register(
            "dream_potion",
            DreamPotionItem::new,
            new Item.Settings()
                    .maxCount(16)
                    .rarity(Rarity.RARE)
                    .useRemainder(Items.GLASS_BOTTLE)
                    .food(
                            new FoodComponent(0, 0.0F, true),
                            ConsumableComponent.builder()
                                    .consumeSeconds(1.6F)
                                    .useAction(UseAction.DRINK)
                                    .sound(SoundEvents.ENTITY_GENERIC_DRINK)
                                    .build()
                    )
    );

    private ModItems() {
    }

    public static void registerModItems() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> entries.add(DREAM_POTION));
    }

    private static <T extends Item> T register(String name, Function<Item.Settings, T> factory, Item.Settings settings) {
        Identifier id = DreamDimensionMod.id(name);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        T item = factory.apply(settings.registryKey(key));
        return Registry.register(Registries.ITEM, key, item);
    }
}
