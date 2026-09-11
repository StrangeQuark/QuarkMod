package com.strangequark.villagebuilder.network;

import com.strangequark.villagebuilder.VillageBuilderMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record SelectTemplatePayload(String templateId) implements CustomPayload {
    public static final Id<SelectTemplatePayload> ID = new Id<>(VillageBuilderMod.id("select_template"));
    public static final PacketCodec<RegistryByteBuf, SelectTemplatePayload> CODEC = PacketCodec.of(SelectTemplatePayload::write, SelectTemplatePayload::new);
    private SelectTemplatePayload(RegistryByteBuf buf) { this(buf.readString(256)); }
    private void write(RegistryByteBuf buf) { buf.writeString(templateId, 256); }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
