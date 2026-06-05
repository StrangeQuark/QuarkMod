package com.strangequark.dreamdimension.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public record DreamReturnLocation(String dimension, double x, double y, double z, float yaw, float pitch) {
    public static final Codec<DreamReturnLocation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("dimension").forGetter(DreamReturnLocation::dimension),
            Codec.DOUBLE.fieldOf("x").forGetter(DreamReturnLocation::x),
            Codec.DOUBLE.fieldOf("y").forGetter(DreamReturnLocation::y),
            Codec.DOUBLE.fieldOf("z").forGetter(DreamReturnLocation::z),
            Codec.FLOAT.fieldOf("yaw").forGetter(DreamReturnLocation::yaw),
            Codec.FLOAT.fieldOf("pitch").forGetter(DreamReturnLocation::pitch)
    ).apply(instance, DreamReturnLocation::new));

    public static DreamReturnLocation of(RegistryKey<World> dimension, Vec3d pos, float yaw, float pitch) {
        return new DreamReturnLocation(dimension.getValue().toString(), pos.x, pos.y, pos.z, yaw, pitch);
    }

    public RegistryKey<World> dimensionKey() {
        return RegistryKey.of(RegistryKeys.WORLD, Identifier.of(this.dimension));
    }

    public Vec3d pos() {
        return new Vec3d(this.x, this.y, this.z);
    }
}
