package com.strangequark.dreamdimension.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DreamInventoryState(
        boolean activeDreamInventory,
        DreamInventorySnapshot normalInventory,
        DreamInventorySnapshot dreamInventory
) {
    public static final Codec<DreamInventoryState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("active_dream_inventory", false).forGetter(DreamInventoryState::activeDreamInventory),
            DreamInventorySnapshot.CODEC.optionalFieldOf("normal_inventory", DreamInventorySnapshot.empty()).forGetter(DreamInventoryState::normalInventory),
            DreamInventorySnapshot.CODEC.optionalFieldOf("dream_inventory", DreamInventorySnapshot.empty()).forGetter(DreamInventoryState::dreamInventory)
    ).apply(instance, DreamInventoryState::new));

    public static DreamInventoryState empty() {
        return new DreamInventoryState(false, DreamInventorySnapshot.empty(), DreamInventorySnapshot.empty());
    }

    public DreamInventoryState withActiveDreamInventory(boolean activeDreamInventory) {
        return new DreamInventoryState(activeDreamInventory, this.normalInventory, this.dreamInventory);
    }

    public DreamInventoryState withNormalInventory(DreamInventorySnapshot normalInventory) {
        return new DreamInventoryState(this.activeDreamInventory, normalInventory, this.dreamInventory);
    }

    public DreamInventoryState withDreamInventory(DreamInventorySnapshot dreamInventory) {
        return new DreamInventoryState(this.activeDreamInventory, this.normalInventory, dreamInventory);
    }
}
