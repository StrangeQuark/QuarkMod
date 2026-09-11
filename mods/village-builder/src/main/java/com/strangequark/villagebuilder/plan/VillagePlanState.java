package com.strangequark.villagebuilder.plan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class VillagePlanState extends PersistentState {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    private static final Codec<PlanRecord> PLAN_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("id").forGetter(PlanRecord::id), UUID_CODEC.fieldOf("owner").forGetter(PlanRecord::owner),
            Codec.STRING.fieldOf("template").forGetter(PlanRecord::templateId), Identifier.CODEC.fieldOf("world").forGetter(PlanRecord::world),
            BlockPos.CODEC.fieldOf("anchor").forGetter(PlanRecord::anchor), Codec.INT.fieldOf("rotation").forGetter(PlanRecord::rotation),
            Codec.INT.fieldOf("size_x").forGetter(PlanRecord::sizeX), Codec.INT.fieldOf("size_y").forGetter(PlanRecord::sizeY), Codec.INT.fieldOf("size_z").forGetter(PlanRecord::sizeZ)
    ).apply(instance, PlanRecord::new));
    private static final PersistentStateType<VillagePlanState> TYPE = new PersistentStateType<>("quarkmod_village_plans", context -> new VillagePlanState(), context -> PLAN_CODEC.listOf().xmap(plans -> new VillagePlanState(plans), VillagePlanState::all), DataFixTypes.SAVED_DATA_MAP_DATA);
    private final List<PlanRecord> plans;
    public VillagePlanState() { this(List.of()); }
    private VillagePlanState(List<PlanRecord> plans) { this.plans = new ArrayList<>(plans); }
    public static VillagePlanState get(MinecraftServer server) { return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE); }
    public List<PlanRecord> all() { return List.copyOf(plans); }
    public void add(PlanRecord plan) { plans.add(plan); markDirty(); }
    public Optional<PlanRecord> at(Identifier world, BlockPos anchor) { return plans.stream().filter(plan -> plan.world.equals(world) && plan.anchor.equals(anchor)).findFirst(); }
    public boolean remove(UUID id) { boolean removed = plans.removeIf(plan -> plan.id.equals(id)); if (removed) markDirty(); return removed; }
    public record PlanRecord(UUID id, UUID owner, String templateId, Identifier world, BlockPos anchor, int rotation, int sizeX, int sizeY, int sizeZ) { }
}
