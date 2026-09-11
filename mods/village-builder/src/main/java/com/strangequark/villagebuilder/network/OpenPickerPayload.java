package com.strangequark.villagebuilder.network;

import com.strangequark.villagebuilder.VillageBuilderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record OpenPickerPayload() implements CustomPayload {
    public static final Id<OpenPickerPayload> ID = new Id<>(VillageBuilderMod.id("open_picker"));
    public static final PacketCodec<RegistryByteBuf, OpenPickerPayload> CODEC = PacketCodec.unit(new OpenPickerPayload());
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
