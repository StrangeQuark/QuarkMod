package com.strangequark.vampirism.item;

import com.strangequark.vampirism.VampirismMod;
import com.strangequark.vampirism.entity.ModEntities;
import com.strangequark.vampirism.vampire.BloodType;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.item.consume.UseAction;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public final class ModItems {
    public static final Item ANIMAL_BLOOD_BOTTLE = register(
            "animal_blood_bottle",
            settings -> new BloodBottleItem(settings, BloodType.ANIMAL.bottleThirst(), BloodType.ANIMAL.saturationModifier()),
            bloodBottleSettings()
    );
    public static final Item HUMANOID_BLOOD_BOTTLE = register(
            "humanoid_blood_bottle",
            settings -> new BloodBottleItem(settings, BloodType.HUMANOID.bottleThirst(), BloodType.HUMANOID.saturationModifier()),
            bloodBottleSettings()
    );
    public static final Item ILLAGER_BLOOD_BOTTLE = register(
            "illager_blood_bottle",
            settings -> new BloodBottleItem(settings, BloodType.ILLAGER.bottleThirst(), BloodType.ILLAGER.saturationModifier()),
            bloodBottleSettings()
    );
    public static final Item WITCH_BLOOD_BOTTLE = register(
            "witch_blood_bottle",
            settings -> new BloodBottleItem(settings, BloodType.WITCH.bottleThirst(), BloodType.WITCH.saturationModifier()),
            bloodBottleSettings()
    );
    public static final Item BLOOD_SIPHON = register(
            "blood_siphon",
            BloodSiphonItem::new,
            new Item.Settings()
                    .maxCount(1)
                    .component(DataComponentTypes.CUSTOM_DATA, BloodSiphonItem.createSiphonData())
    );
    public static final Item VAMPIRIC_BLOOD = register(
            "vampiric_blood",
            VampiricBloodItem::new,
            new Item.Settings()
                    .maxCount(16)
                    .useRemainder(Items.GLASS_BOTTLE)
                    .food(
                            new FoodComponent(0, 0.0F, true),
                            ConsumableComponent.builder()
                                    .consumeSeconds(1.6F)
                                    .useAction(UseAction.DRINK)
                                    .sound(SoundEvents.ITEM_HONEY_BOTTLE_DRINK)
                                    .consumeParticles(false)
                                    .build()
                    )
    );

    public static final Item VAMPIRE_SPAWN_EGG = register(
            "vampire_spawn_egg",
            settings -> new SpawnEggItem(ModEntities.VAMPIRE, settings),
            new Item.Settings()
    );

    private ModItems() {
    }

    public static void registerModItems() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> {
            entries.add(ANIMAL_BLOOD_BOTTLE);
            entries.add(HUMANOID_BLOOD_BOTTLE);
            entries.add(ILLAGER_BLOOD_BOTTLE);
            entries.add(WITCH_BLOOD_BOTTLE);
            entries.add(VAMPIRIC_BLOOD);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(BLOOD_SIPHON));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(VAMPIRE_SPAWN_EGG));
    }

    public static Item bloodBottleFor(BloodType bloodType) {
        return switch (bloodType) {
            case ANIMAL -> ANIMAL_BLOOD_BOTTLE;
            case HUMANOID -> HUMANOID_BLOOD_BOTTLE;
            case ILLAGER -> ILLAGER_BLOOD_BOTTLE;
            case WITCH -> WITCH_BLOOD_BOTTLE;
        };
    }

    private static Item.Settings bloodBottleSettings() {
        return new Item.Settings()
                .maxCount(16)
                .useRemainder(Items.GLASS_BOTTLE)
                .component(
                        DataComponentTypes.CONSUMABLE,
                        ConsumableComponent.builder()
                                .consumeSeconds(1.6F)
                                .useAction(UseAction.DRINK)
                                .sound(SoundEvents.ITEM_HONEY_BOTTLE_DRINK)
                                .consumeParticles(false)
                                .build()
                );
    }

    private static <T extends Item> T register(String name, Function<Item.Settings, T> factory, Item.Settings settings) {
        Identifier id = Identifier.of(VampirismMod.REGISTRY_NAMESPACE, name);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        T item = factory.apply(settings.registryKey(key));
        return Registry.register(Registries.ITEM, key, item);
    }
}
