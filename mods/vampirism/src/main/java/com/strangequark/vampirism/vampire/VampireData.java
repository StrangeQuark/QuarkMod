package com.strangequark.vampirism.vampire;

import com.mojang.serialization.Codec;
import com.strangequark.vampirism.VampirismMod;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.player.PlayerEntity;

public final class VampireData {
    private static final AttachmentType<Boolean> VAMPIRE = AttachmentRegistry.create(
            VampirismMod.id("vampire"),
            builder -> builder.initializer(() -> false).persistent(Codec.BOOL).copyOnDeath()
    );

    private VampireData() {
    }

    public static void register() {
    }

    public static boolean isVampire(PlayerEntity player) {
        return Boolean.TRUE.equals(((AttachmentTarget) player).getAttachedOrElse(VAMPIRE, false));
    }

    public static void setVampire(PlayerEntity player, boolean vampire) {
        ((AttachmentTarget) player).setAttached(VAMPIRE, vampire);
    }
}
