package com.strangequark.villagebuilder.client;

import com.strangequark.villagebuilder.network.PlanSyncPayload;
import com.strangequark.villagebuilder.network.TemplateSyncPayload;

import java.util.List;

public final class ClientBlueprints {
    private static List<TemplateSyncPayload.TemplateWire> templates = List.of();
    private static List<PlanSyncPayload.PlanWire> plans = List.of();
    private static int index;
    private static int rotation;
    private ClientBlueprints() { }
    public static void setTemplates(List<TemplateSyncPayload.TemplateWire> values) { templates = List.copyOf(values); index = Math.floorMod(index, Math.max(1, templates.size())); }
    public static void setPlans(List<PlanSyncPayload.PlanWire> values) { plans = List.copyOf(values); }
    public static void setSelection(int selectedIndex, int selectedRotation) { index = selectedIndex; rotation = selectedRotation; }
    public static TemplateSyncPayload.TemplateWire selected() { return templates.isEmpty() ? null : templates.get(Math.floorMod(index, templates.size())); }
    public static List<TemplateSyncPayload.TemplateWire> templates() { return templates; }
    public static List<PlanSyncPayload.PlanWire> plans() { return plans; }
    public static int rotation() { return rotation; }
}
