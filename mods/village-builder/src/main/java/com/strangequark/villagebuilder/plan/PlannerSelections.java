package com.strangequark.villagebuilder.plan;

import com.strangequark.villagebuilder.blueprint.BlueprintTemplate;
import com.strangequark.villagebuilder.blueprint.TemplateRepository;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlannerSelections {
    private static final Map<UUID, Selection> SELECTIONS = new ConcurrentHashMap<>();
    private PlannerSelections() { }
    public static Selection cycle(ServerPlayerEntity player, boolean rotate) {
        List<BlueprintTemplate> templates = TemplateRepository.all();
        Selection selection = SELECTIONS.getOrDefault(player.getUuid(), new Selection(0, 0));
        if (templates.isEmpty()) return selection;
        selection = rotate ? new Selection(selection.index(), (selection.rotation() + 1) % 4) : new Selection((selection.index() + 1) % templates.size(), selection.rotation());
        SELECTIONS.put(player.getUuid(), selection); return selection;
    }
    public static BlueprintTemplate selected(ServerPlayerEntity player) {
        List<BlueprintTemplate> templates = TemplateRepository.all();
        if (templates.isEmpty()) return null;
        Selection selection = SELECTIONS.getOrDefault(player.getUuid(), new Selection(0, 0));
        return templates.get(Math.floorMod(selection.index(), templates.size()));
    }
    public static Selection get(ServerPlayerEntity player) { return SELECTIONS.getOrDefault(player.getUuid(), new Selection(0, 0)); }
    public static Selection select(ServerPlayerEntity player, String templateId) {
        int index = TemplateRepository.indexOf(templateId);
        if (index < 0) return null;
        Selection selection = new Selection(index, 0);
        SELECTIONS.put(player.getUuid(), selection);
        return selection;
    }
    public record Selection(int index, int rotation) { }
}
