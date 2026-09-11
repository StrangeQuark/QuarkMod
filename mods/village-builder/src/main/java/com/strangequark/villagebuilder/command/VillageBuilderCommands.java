package com.strangequark.villagebuilder.command;

import com.mojang.brigadier.CommandDispatcher;
import com.strangequark.villagebuilder.blueprint.TemplateRepository;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class VillageBuilderCommands {
    private VillageBuilderCommands() { }
    public static void register() { CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher)); }
    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("villagebuilder").requires(source -> source.hasPermissionLevel(2)).then(CommandManager.literal("reload").executes(context -> { TemplateRepository.reload(context.getSource().getServer()); context.getSource().sendFeedback(() -> Text.literal("Village blueprints reloaded."), true); return 1; })).then(CommandManager.literal("templates").executes(context -> { context.getSource().sendFeedback(() -> Text.literal(String.join(", ", TemplateRepository.all().stream().map(template -> template.id()).toList())), false); return TemplateRepository.all().size(); })));
    }
}
