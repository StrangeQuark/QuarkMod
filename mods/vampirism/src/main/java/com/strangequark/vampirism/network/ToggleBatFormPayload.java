package com.strangequark.vampirism.network;

import com.strangequark.vampirism.VampirismMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record ToggleBatFormPayload() implements CustomPayload {
    public static final Id<ToggleBatFormPayload> ID = new Id<>(VampirismMod.id("toggle_bat_form"));
    public static final PacketCodec<RegistryByteBuf, ToggleBatFormPayload> CODEC =
            PacketCodec.unit(new ToggleBatFormPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
