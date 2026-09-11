package com.strangequark.villagebuilder.network;

import com.strangequark.villagebuilder.VillageBuilderMod;
import com.strangequark.villagebuilder.blueprint.BlueprintTemplate;
import com.mojang.serialization.DataResult;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

public record TemplateSyncPayload(List<TemplateWire> templates) implements CustomPayload {
    public static final Id<TemplateSyncPayload> ID = new Id<>(VillageBuilderMod.id("template_sync"));
    public static final PacketCodec<RegistryByteBuf, TemplateSyncPayload> CODEC = PacketCodec.of(TemplateSyncPayload::write, TemplateSyncPayload::new);

    public TemplateSyncPayload {
        templates = List.copyOf(templates);
    }

    public static TemplateSyncPayload from(List<BlueprintTemplate> templates) {
        return new TemplateSyncPayload(templates.stream().map(TemplateWire::from).toList());
    }

    private TemplateSyncPayload(RegistryByteBuf buf) {
        this(readTemplates(buf));
    }

    private void write(RegistryByteBuf buf) {
        buf.writeVarInt(templates.size());
        for (TemplateWire template : templates) template.write(buf);
    }

    private static List<TemplateWire> readTemplates(RegistryByteBuf buf) {
        int count = checkedCount(buf.readVarInt(), 512);
        List<TemplateWire> templates = new ArrayList<>(count);
        for (int i = 0; i < count; i++) templates.add(new TemplateWire(buf));
        return templates;
    }

    private static int checkedCount(int count, int max) {
        if (count < 0 || count > max) throw new IllegalArgumentException("invalid packet count " + count);
        return count;
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    public record TemplateWire(String id, int sizeX, int sizeY, int sizeZ, List<BlockWire> blocks) {
        public TemplateWire { blocks = List.copyOf(blocks); }
        static TemplateWire from(BlueprintTemplate template) { return new TemplateWire(template.id(), template.sizeX(), template.sizeY(), template.sizeZ(), template.blocks().stream().map(BlockWire::from).toList()); }
        TemplateWire(RegistryByteBuf buf) {
            this(buf.readString(256), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), readBlocks(buf));
        }
        private static List<BlockWire> readBlocks(RegistryByteBuf buf) { int count = checkedCount(buf.readVarInt(), 8_000); List<BlockWire> blocks = new ArrayList<>(count); for (int i = 0; i < count; i++) blocks.add(new BlockWire(buf)); return blocks; }
        void write(RegistryByteBuf buf) { buf.writeString(id, 256); buf.writeVarInt(sizeX); buf.writeVarInt(sizeY); buf.writeVarInt(sizeZ); buf.writeVarInt(blocks.size()); for (BlockWire block : blocks) block.write(buf); }
    }

    public record BlockWire(long pos, NbtCompound state) {
        static BlockWire from(BlueprintTemplate.BlueprintBlock block) {
            NbtCompound nbt = (NbtCompound) BlockState.CODEC.encodeStart(NbtOps.INSTANCE, block.state()).getOrThrow(error -> new IllegalArgumentException("Cannot encode block state: " + error));
            return new BlockWire(block.pos().asLong(), nbt);
        }
        BlockWire(RegistryByteBuf buf) { this(buf.readLong(), buf.readNbt()); }
        void write(RegistryByteBuf buf) { buf.writeLong(pos); buf.writeNbt(state); }
        public BlockState blockState() { return BlockState.CODEC.parse(NbtOps.INSTANCE, state).getOrThrow(error -> new IllegalArgumentException("Cannot decode block state: " + error)); }
    }
}
