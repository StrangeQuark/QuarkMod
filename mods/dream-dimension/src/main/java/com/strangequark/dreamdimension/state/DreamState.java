package com.strangequark.dreamdimension.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public record DreamState(boolean readyToDream, Optional<DreamReturnLocation> returnLocation) {
    public static final Codec<DreamState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("ready_to_dream", false).forGetter(DreamState::readyToDream),
            DreamReturnLocation.CODEC.optionalFieldOf("return_location").forGetter(DreamState::returnLocation)
    ).apply(instance, DreamState::new));

    public static DreamState empty() {
        return new DreamState(false, Optional.empty());
    }

    public DreamState withReadyToDream(boolean readyToDream) {
        return new DreamState(readyToDream, this.returnLocation);
    }

    public DreamState withReturnLocation(DreamReturnLocation returnLocation) {
        return new DreamState(false, Optional.of(returnLocation));
    }

    public DreamState withoutReturnLocation() {
        return new DreamState(this.readyToDream, Optional.empty());
    }
}
