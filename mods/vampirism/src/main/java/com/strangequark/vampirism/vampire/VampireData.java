package com.strangequark.vampirism.vampire;

import com.mojang.serialization.Codec;
import com.strangequark.vampirism.VampirismMod;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.codec.PacketCodecs;

public final class VampireData {
    private static final AttachmentType<Boolean> VAMPIRE = AttachmentRegistry.create(
            VampirismMod.id("vampire"),
            builder -> builder.initializer(() -> false)
                    .persistent(Codec.BOOL)
                    .copyOnDeath()
                    .syncWith(PacketCodecs.BOOLEAN, AttachmentSyncPredicate.targetOnly())
    );
    private static final AttachmentType<Boolean> BAT_FORM = AttachmentRegistry.create(
            VampirismMod.id("bat_form"),
            builder -> builder.initializer(() -> false)
                    .syncWith(PacketCodecs.BOOLEAN, AttachmentSyncPredicate.all())
    );
    private static final AttachmentType<Boolean> DIRECT_FEEDING = AttachmentRegistry.create(
            VampirismMod.id("direct_feeding"),
            builder -> builder.initializer(() -> false)
                    .syncWith(PacketCodecs.BOOLEAN, AttachmentSyncPredicate.all())
    );
    private static final AttachmentType<Boolean> SIPHONING_BLOOD = AttachmentRegistry.create(
            VampirismMod.id("siphoning_blood"),
            builder -> builder.initializer(() -> false)
                    .syncWith(PacketCodecs.BOOLEAN, AttachmentSyncPredicate.all())
    );
    private static final AttachmentType<Boolean> BAT_FORM_MANAGED = AttachmentRegistry.create(
            VampirismMod.id("bat_form_managed"),
            builder -> builder.initializer(() -> false)
    );
    private static final AttachmentType<Boolean> PRE_BAT_ALLOW_FLYING = AttachmentRegistry.create(
            VampirismMod.id("pre_bat_allow_flying"),
            builder -> builder.initializer(() -> false)
    );
    private static final AttachmentType<Boolean> PRE_BAT_FLYING = AttachmentRegistry.create(
            VampirismMod.id("pre_bat_flying"),
            builder -> builder.initializer(() -> false)
    );
    private static final AttachmentType<Boolean> PRE_BAT_NO_GRAVITY = AttachmentRegistry.create(
            VampirismMod.id("pre_bat_no_gravity"),
            builder -> builder.initializer(() -> false)
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
        if (vampire) {
            BloodThirst.initializeVampire(player);
        } else {
            setDirectFeeding(player, false);
        }
    }

    public static boolean isBatForm(PlayerEntity player) {
        return Boolean.TRUE.equals(((AttachmentTarget) player).getAttachedOrElse(BAT_FORM, false));
    }

    public static void setBatForm(PlayerEntity player, boolean batForm) {
        ((AttachmentTarget) player).setAttached(BAT_FORM, batForm);
        if (batForm) {
            setDirectFeeding(player, false);
            setSiphoningBlood(player, false);
        }
    }

    public static boolean isDirectFeeding(PlayerEntity player) {
        return Boolean.TRUE.equals(((AttachmentTarget) player).getAttachedOrElse(DIRECT_FEEDING, false));
    }

    public static void setDirectFeeding(PlayerEntity player, boolean directFeeding) {
        ((AttachmentTarget) player).setAttached(DIRECT_FEEDING, directFeeding);
    }

    public static boolean isSiphoningBlood(PlayerEntity player) {
        return Boolean.TRUE.equals(((AttachmentTarget) player).getAttachedOrElse(SIPHONING_BLOOD, false));
    }

    public static void setSiphoningBlood(PlayerEntity player, boolean siphoningBlood) {
        ((AttachmentTarget) player).setAttached(SIPHONING_BLOOD, siphoningBlood);
    }

    public static boolean isBatFormManaged(PlayerEntity player) {
        return Boolean.TRUE.equals(((AttachmentTarget) player).getAttachedOrElse(BAT_FORM_MANAGED, false));
    }

    public static void setBatFormManaged(PlayerEntity player, boolean managed) {
        ((AttachmentTarget) player).setAttached(BAT_FORM_MANAGED, managed);
    }

    public static boolean wasAllowedFlyingBeforeBatForm(PlayerEntity player) {
        return Boolean.TRUE.equals(((AttachmentTarget) player).getAttachedOrElse(PRE_BAT_ALLOW_FLYING, false));
    }

    public static void setAllowedFlyingBeforeBatForm(PlayerEntity player, boolean allowFlying) {
        ((AttachmentTarget) player).setAttached(PRE_BAT_ALLOW_FLYING, allowFlying);
    }

    public static boolean wasFlyingBeforeBatForm(PlayerEntity player) {
        return Boolean.TRUE.equals(((AttachmentTarget) player).getAttachedOrElse(PRE_BAT_FLYING, false));
    }

    public static void setFlyingBeforeBatForm(PlayerEntity player, boolean flying) {
        ((AttachmentTarget) player).setAttached(PRE_BAT_FLYING, flying);
    }

    public static boolean hadNoGravityBeforeBatForm(PlayerEntity player) {
        return Boolean.TRUE.equals(((AttachmentTarget) player).getAttachedOrElse(PRE_BAT_NO_GRAVITY, false));
    }

    public static void setNoGravityBeforeBatForm(PlayerEntity player, boolean noGravity) {
        ((AttachmentTarget) player).setAttached(PRE_BAT_NO_GRAVITY, noGravity);
    }
}
