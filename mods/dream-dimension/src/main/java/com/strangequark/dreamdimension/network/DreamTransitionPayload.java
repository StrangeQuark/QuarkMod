package com.strangequark.dreamdimension.network;

import com.strangequark.dreamdimension.DreamDimensionMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record DreamTransitionPayload(int durationTicks) implements CustomPayload {
    public static final CustomPayload.Id<DreamTransitionPayload> ID = new CustomPayload.Id<>(DreamDimensionMod.id("dream_transition"));
    public static final PacketCodec<RegistryByteBuf, DreamTransitionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT,
            DreamTransitionPayload::durationTicks,
            DreamTransitionPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
