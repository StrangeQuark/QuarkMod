package com.strangequark.villagebuilder.network;

import com.strangequark.villagebuilder.VillageBuilderMod;
import com.strangequark.villagebuilder.plan.VillagePlanState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record PlanSyncPayload(List<PlanWire> plans) implements CustomPayload {
    public static final Id<PlanSyncPayload> ID = new Id<>(VillageBuilderMod.id("plan_sync"));
    public static final PacketCodec<RegistryByteBuf, PlanSyncPayload> CODEC = PacketCodec.of(PlanSyncPayload::write, PlanSyncPayload::new);
    public PlanSyncPayload { plans = List.copyOf(plans); }
    public static PlanSyncPayload from(List<VillagePlanState.PlanRecord> plans) { return new PlanSyncPayload(plans.stream().map(PlanWire::from).toList()); }
    private PlanSyncPayload(RegistryByteBuf buf) { this(readPlans(buf)); }
    private void write(RegistryByteBuf buf) { buf.writeVarInt(plans.size()); for (PlanWire plan : plans) plan.write(buf); }
    private static List<PlanWire> readPlans(RegistryByteBuf buf) { int count = buf.readVarInt(); if (count < 0 || count > 10_000) throw new IllegalArgumentException("invalid plan count"); List<PlanWire> plans = new ArrayList<>(count); for (int i = 0; i < count; i++) plans.add(new PlanWire(buf)); return plans; }
    @Override public Id<? extends CustomPayload> getId() { return ID; }

    public record PlanWire(UUID id, UUID owner, String templateId, Identifier world, BlockPos anchor, int rotation, int sizeX, int sizeY, int sizeZ) {
        static PlanWire from(VillagePlanState.PlanRecord plan) { return new PlanWire(plan.id(), plan.owner(), plan.templateId(), plan.world(), plan.anchor(), plan.rotation(), plan.sizeX(), plan.sizeY(), plan.sizeZ()); }
        PlanWire(RegistryByteBuf buf) { this(buf.readUuid(), buf.readUuid(), buf.readString(256), buf.readIdentifier(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()); }
        void write(RegistryByteBuf buf) { buf.writeUuid(id); buf.writeUuid(owner); buf.writeString(templateId, 256); buf.writeIdentifier(world); buf.writeBlockPos(anchor); buf.writeVarInt(rotation); buf.writeVarInt(sizeX); buf.writeVarInt(sizeY); buf.writeVarInt(sizeZ); }
    }
}
