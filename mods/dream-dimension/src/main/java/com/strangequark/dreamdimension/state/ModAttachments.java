package com.strangequark.dreamdimension.state;

import com.strangequark.dreamdimension.DreamDimensionMod;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ModAttachments {
    public static final AttachmentType<DreamState> DREAM_STATE = AttachmentRegistry.create(
            DreamDimensionMod.id("dream_state"),
            builder -> builder
                    .persistent(DreamState.CODEC)
                    .copyOnDeath()
                    .initializer(DreamState::empty)
    );

    private ModAttachments() {
    }

    public static void registerAttachments() {
    }

    public static DreamState getDreamState(ServerPlayerEntity player) {
        return ((AttachmentTarget) player).getAttachedOrCreate(DREAM_STATE);
    }

    public static void setDreamState(ServerPlayerEntity player, DreamState state) {
        ((AttachmentTarget) player).setAttached(DREAM_STATE, state);
    }

    public static void clearDreamState(ServerPlayerEntity player) {
        ((AttachmentTarget) player).setAttached(DREAM_STATE, DreamState.empty());
    }
}
