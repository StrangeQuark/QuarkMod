package com.strangequark.dreamdimension.loot;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.entry.LeafEntry;
import net.minecraft.loot.function.SetComponentsLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.text.MutableText;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class DreamLootTables {
    private static final Identifier ENCHANTING_FONT = Identifier.ofVanilla("alt");
    private static final String BOOK_TITLE = "Alchemist notes";
    private static final String BOOK_AUTHOR = "Unknown";
    private static final Recipe DREAM_RECIPE = new Recipe(
            "???",
            List.of("Awkward", "Chorus fruit", "Ghast tear", "Phantom membrane")
    );
    private static final List<List<Recipe>> RECIPE_VARIANTS = List.of(
            List.of(
                    new Recipe("Swiftness", List.of("Awkward", "Sugar")),
                    new Recipe("Regeneration", List.of("Awkward", "Ghast tear")),
                    new Recipe("Night vision", List.of("Awkward", "Golden carrot"))
            ),
            List.of(
                    new Recipe("Fire resistance", List.of("Awkward", "Magma cream")),
                    new Recipe("Water breathing", List.of("Awkward", "Pufferfish")),
                    new Recipe("Strength", List.of("Awkward", "Blaze powder"))
            ),
            List.of(
                    new Recipe("Healing", List.of("Awkward", "Glistering melon")),
                    new Recipe("Leaping", List.of("Awkward", "Rabbit foot")),
                    new Recipe("Slow falling", List.of("Awkward", "Phantom membrane"))
            ),
            List.of(
                    new Recipe("Invisibility", List.of("Night vision", "Fermented spider eye")),
                    new Recipe("Slowness", List.of("Swiftness", "Fermented spider eye")),
                    new Recipe("Harming", List.of("Healing", "Fermented spider eye"))
            )
    );

    private DreamLootTables() {
    }

    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (source.isBuiltin() && key.equals(LootTables.STRONGHOLD_LIBRARY_CHEST)) {
                tableBuilder.pool(alchemistNotesPool());
            }
        });
    }

    private static LootPool.Builder alchemistNotesPool() {
        LootPool.Builder pool = LootPool.builder()
                .rolls(ConstantLootNumberProvider.create(1));

        for (List<Recipe> recipes : RECIPE_VARIANTS) {
            pool.with(alchemistNotesEntry(recipes));
        }

        return pool;
    }

    private static LeafEntry.Builder<?> alchemistNotesEntry(List<Recipe> recipes) {
        return ItemEntry.builder(Items.WRITTEN_BOOK)
                .apply(SetComponentsLootFunction.builder(
                        DataComponentTypes.CUSTOM_NAME,
                        enchantingText(BOOK_TITLE)
                ))
                .apply(SetComponentsLootFunction.builder(
                        DataComponentTypes.WRITTEN_BOOK_CONTENT,
                        bookContent(recipes)
                ));
    }

    private static WrittenBookContentComponent bookContent(List<Recipe> recipes) {
        List<RawFilteredPair<Text>> pages = new ArrayList<>();

        for (Recipe recipe : recipes) {
            pages.add(RawFilteredPair.of(recipePage(recipe)));
        }

        pages.add(RawFilteredPair.of(recipePage(DREAM_RECIPE)));

        return new WrittenBookContentComponent(
                RawFilteredPair.of(BOOK_TITLE),
                BOOK_AUTHOR,
                0,
                List.copyOf(pages),
                true
        );
    }

    private static Text recipePage(Recipe recipe) {
        MutableText page = Text.empty();
        if (recipe.name().equals(DREAM_RECIPE.name())) {
            page.append(Text.literal(recipe.name()));
        } else {
            page.append(enchantingText(recipe.name()));
        }

        page.append("\n\n");
        appendRecipeChain(page, recipe.steps());
        return page;
    }

    private static void appendRecipeChain(MutableText page, List<String> steps) {
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) {
                page.append(" -> ");
            }
            page.append(enchantingText(steps.get(i)));
        }
    }

    private static MutableText enchantingText(String text) {
        return Text.literal(text).styled(style -> style.withFont(ENCHANTING_FONT));
    }

    private record Recipe(String name, List<String> steps) {
    }
}
