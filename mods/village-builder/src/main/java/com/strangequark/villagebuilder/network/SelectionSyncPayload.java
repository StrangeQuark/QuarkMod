package com.strangequark.villagebuilder.network;

import com.strangequark.villagebuilder.VillageBuilderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record SelectionSyncPayload(int index, int rotation) implements CustomPayload {
    public static final Id<SelectionSyncPayload> ID = new Id<>(VillageBuilderMod.id("selection_sync"));
    public static final PacketCodec<RegistryByteBuf, SelectionSyncPayload> CODEC = PacketCodec.of(SelectionSyncPayload::write, SelectionSyncPayload::new);
    private SelectionSyncPayload(RegistryByteBuf buf) { this(buf.readVarInt(), buf.readVarInt()); }
    private void write(RegistryByteBuf buf) { buf.writeVarInt(index); buf.writeVarInt(rotation); }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
