package com.strangequark.dreamdimension.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public record DreamState(Optional<DreamReturnLocation> returnLocation) {
    public static final Codec<DreamState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DreamReturnLocation.CODEC.optionalFieldOf("return_location").forGetter(DreamState::returnLocation)
    ).apply(instance, DreamState::new));

    public static DreamState empty() {
        return new DreamState(Optional.empty());
    }

    public DreamState withReturnLocation(DreamReturnLocation returnLocation) {
        return new DreamState(Optional.of(returnLocation));
    }

    public DreamState withoutReturnLocation() {
        return DreamState.empty();
    }
}
