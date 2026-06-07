package com.strangequark.dreamdimension.world;

import com.strangequark.dreamdimension.state.DreamInventorySnapshot;
import com.strangequark.dreamdimension.state.DreamInventoryState;
import com.strangequark.dreamdimension.state.ModAttachments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;

public final class DreamInventorySwapper {
    private DreamInventorySwapper() {
    }

    public static void activateDreamInventory(ServerPlayerEntity player) {
        DreamInventoryState state = ModAttachments.getDreamInventoryState(player);
        if (state.activeDreamInventory()) {
            return;
        }

        closeCurrentScreen(player);

        DreamInventorySnapshot normalInventory = DreamInventorySnapshot.capture(player);
        state.dreamInventory().restore(player);
        ModAttachments.setDreamInventoryState(player, state
                .withNormalInventory(normalInventory)
                .withActiveDreamInventory(true));
        syncInventory(player);
    }

    public static void deactivateDreamInventory(ServerPlayerEntity player) {
        DreamInventoryState state = ModAttachments.getDreamInventoryState(player);
        if (!state.activeDreamInventory()) {
            return;
        }

        closeCurrentScreen(player);

        DreamInventorySnapshot dreamInventory = DreamInventorySnapshot.capture(player);
        state.normalInventory().restore(player);
        ModAttachments.setDreamInventoryState(player, state
                .withDreamInventory(dreamInventory)
                .withNormalInventory(DreamInventorySnapshot.empty())
                .withActiveDreamInventory(false));
        syncInventory(player);
    }

    public static void alignWithWorld(ServerPlayerEntity player, boolean dreamWorld) {
        if (dreamWorld) {
            activateDreamInventory(player);
        } else {
            deactivateDreamInventory(player);
        }
    }

    private static void closeCurrentScreen(ServerPlayerEntity player) {
        player.closeHandledScreen();
    }

    private static void syncInventory(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            player.networkHandler.sendPacket(inventory.createSlotSetPacket(slot));
        }

        player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(inventory.getSelectedSlot()));
        syncScreen(player.playerScreenHandler);
        if (player.currentScreenHandler != player.playerScreenHandler) {
            syncScreen(player.currentScreenHandler);
        }
    }

    private static void syncScreen(ScreenHandler screenHandler) {
        screenHandler.updateToClient();
        screenHandler.sendContentUpdates();
    }
}
