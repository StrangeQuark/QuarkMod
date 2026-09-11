package com.strangequark.villagebuilder.client;

import com.strangequark.villagebuilder.network.SelectTemplatePayload;
import com.strangequark.villagebuilder.network.TemplateSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

public final class BlueprintPickerScreen extends Screen {
    private static final int PER_PAGE = 8;
    private int page;
    public BlueprintPickerScreen() { super(Text.literal("Village Blueprints")); }
    @Override protected void init() {
        List<TemplateSyncPayload.TemplateWire> templates = ClientBlueprints.templates();
        int start = page * PER_PAGE;
        for (int index = start; index < Math.min(start + PER_PAGE, templates.size()); index++) {
            TemplateSyncPayload.TemplateWire template = templates.get(index);
            int row = index - start;
            addDrawableChild(ButtonWidget.builder(Text.literal(template.id() + "  (" + template.sizeX() + "×" + template.sizeY() + "×" + template.sizeZ() + ")"), button -> select(template)).dimensions(width / 2 - 150, 42 + row * 24, 300, 20).build());
        }
        if (page > 0) addDrawableChild(ButtonWidget.builder(Text.literal("Previous"), button -> { page--; clearAndInit(); }).dimensions(width / 2 - 150, height - 42, 145, 20).build());
        if (start + PER_PAGE < templates.size()) addDrawableChild(ButtonWidget.builder(Text.literal("Next"), button -> { page++; clearAndInit(); }).dimensions(width / 2 + 5, height - 42, 145, 20).build());
    }
    private void select(TemplateSyncPayload.TemplateWire template) { ClientPlayNetworking.send(new SelectTemplatePayload(template.id())); close(); }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        renderBackground(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFF);
        if (ClientBlueprints.templates().isEmpty()) context.drawCenteredTextWithShadow(textRenderer, Text.literal("No templates loaded on this server."), width / 2, height / 2, 0xFF7777);
        super.render(context, mouseX, mouseY, deltaTicks);
    }
    @Override public boolean shouldPause() { return false; }
}
