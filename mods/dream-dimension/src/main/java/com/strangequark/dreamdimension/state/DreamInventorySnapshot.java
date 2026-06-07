package com.strangequark.dreamdimension.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

public record DreamInventorySnapshot(int selectedSlot, List<SlotStack> stacks) {
    public static final Codec<DreamInventorySnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("selected_slot", 0).forGetter(DreamInventorySnapshot::selectedSlot),
            SlotStack.CODEC.listOf().optionalFieldOf("stacks", List.of()).forGetter(DreamInventorySnapshot::stacks)
    ).apply(instance, DreamInventorySnapshot::new));

    public DreamInventorySnapshot {
        selectedSlot = PlayerInventory.isValidHotbarIndex(selectedSlot) ? selectedSlot : 0;
        stacks = stacks.stream()
                .filter(stack -> stack.slot() >= 0 && !stack.stack().isEmpty())
                .map(stack -> new SlotStack(stack.slot(), stack.stack()))
                .toList();
    }

    public static DreamInventorySnapshot empty() {
        return new DreamInventorySnapshot(0, List.of());
    }

    public static DreamInventorySnapshot capture(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        List<SlotStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty()) {
                stacks.add(new SlotStack(slot, stack));
            }
        }

        return new DreamInventorySnapshot(inventory.getSelectedSlot(), stacks);
    }

    public void restore(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setSelectedSlot(this.selectedSlot);

        for (SlotStack stack : this.stacks) {
            if (stack.slot() < inventory.size()) {
                inventory.setStack(stack.slot(), stack.stack().copy());
            }
        }

        inventory.markDirty();
    }

    public record SlotStack(int slot, ItemStack stack) {
        public static final Codec<SlotStack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("slot").forGetter(SlotStack::slot),
                ItemStack.OPTIONAL_CODEC.fieldOf("stack").forGetter(SlotStack::stack)
        ).apply(instance, SlotStack::new));

        public SlotStack {
            stack = stack.copy();
        }
    }
}
