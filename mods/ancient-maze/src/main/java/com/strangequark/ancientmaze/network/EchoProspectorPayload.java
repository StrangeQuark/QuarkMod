package com.strangequark.ancientmaze.network;

import com.strangequark.ancientmaze.AncientMazeMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public record EchoProspectorPayload(List<BlockPos> positions) implements CustomPayload {
    public static final CustomPayload.Id<EchoProspectorPayload> ID = new CustomPayload.Id<>(AncientMazeMod.id("echo_prospector"));
    public static final PacketCodec<RegistryByteBuf, EchoProspectorPayload> CODEC = PacketCodec.of(EchoProspectorPayload::write, EchoProspectorPayload::new);

    public EchoProspectorPayload {
        positions = List.copyOf(positions);
    }

    private EchoProspectorPayload(RegistryByteBuf buf) {
        this(buf.readList(packetByteBuf -> packetByteBuf.readBlockPos()));
    }

    private void write(RegistryByteBuf buf) {
        buf.writeCollection(positions, (packetByteBuf, pos) -> packetByteBuf.writeBlockPos(pos));
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
