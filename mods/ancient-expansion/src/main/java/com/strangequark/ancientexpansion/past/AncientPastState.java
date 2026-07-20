package com.strangequark.ancientexpansion.past;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AncientPastState extends PersistentState {
    private static final Codec<AncientPastState> CODEC = PastCityInstance.CODEC.listOf()
            .optionalFieldOf("cities", List.of())
            .xmap(AncientPastState::new, AncientPastState::cities)
            .codec();

    private static final PersistentStateType<AncientPastState> TYPE = new PersistentStateType<>(
            "quarkmod_ancient_past",
            context -> new AncientPastState(),
            context -> CODEC,
            DataFixTypes.SAVED_DATA_MAP_DATA
    );

    private final Map<String, PastCityInstance> cities = new LinkedHashMap<>();

    public AncientPastState() {
    }

    private AncientPastState(List<PastCityInstance> cities) {
        for (PastCityInstance city : cities) {
            this.cities.put(city.key(), city);
        }
    }

    public static AncientPastState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public Optional<PastCityInstance> get(Identifier sourceWorld, BlockPos sourceAltarPos) {
        return Optional.ofNullable(cities.get(key(sourceWorld, sourceAltarPos)));
    }

    public PastCityInstance put(PastCityInstance city) {
        cities.put(city.key(), city);
        markDirty();
        return city;
    }

    public Optional<PastCityInstance> findByMainPortal(BlockPos portalPos) {
        return cities.values().stream()
                .filter(city -> city.mainPortalPositions().contains(portalPos))
                .findFirst();
    }

    public Optional<PastCityInstance> findByTrialPortal(BlockPos portalPos) {
        return cities.values().stream()
                .filter(city -> city.trialPortalPositions().contains(portalPos))
                .findFirst();
    }

    private List<PastCityInstance> cities() {
        return List.copyOf(cities.values());
    }

    private static String key(Identifier sourceWorld, BlockPos sourceAltarPos) {
        return sourceWorld + "|" + sourceAltarPos.asLong();
    }

    public record PastCityInstance(
            Identifier sourceWorld,
            BlockPos sourceAltarPos,
            BlockBox sourceCityBox,
            BlockPos sourceReturnSpawn,
            Direction.Axis mainPortalAxis,
            Direction mainPortalFrontDirection,
            List<BlockPos> mainPortalPositions,
            Direction.Axis trialPortalAxis,
            List<BlockPos> trialPortalPositions,
            BlockPos pastSpawn,
            BlockPos trialReturnSpawn,
            BlockBox pastCityBox,
            int layoutVersion,
            boolean generated
    ) {
        private static final Codec<PastCityInstance> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Identifier.CODEC.fieldOf("source_world").forGetter(PastCityInstance::sourceWorld),
                BlockPos.CODEC.fieldOf("source_altar_pos").forGetter(PastCityInstance::sourceAltarPos),
                BlockBox.CODEC.fieldOf("source_city_box").forGetter(PastCityInstance::sourceCityBox),
                BlockPos.CODEC.fieldOf("source_return_spawn").forGetter(PastCityInstance::sourceReturnSpawn),
                Direction.Axis.CODEC.fieldOf("main_portal_axis").forGetter(PastCityInstance::mainPortalAxis),
                Direction.CODEC.fieldOf("main_portal_front_direction").forGetter(PastCityInstance::mainPortalFrontDirection),
                BlockPos.CODEC.listOf().fieldOf("main_portal_positions").forGetter(PastCityInstance::mainPortalPositions),
                Direction.Axis.CODEC.fieldOf("trial_portal_axis").forGetter(PastCityInstance::trialPortalAxis),
                BlockPos.CODEC.listOf().fieldOf("trial_portal_positions").forGetter(PastCityInstance::trialPortalPositions),
                BlockPos.CODEC.fieldOf("past_spawn").forGetter(PastCityInstance::pastSpawn),
                BlockPos.CODEC.fieldOf("trial_return_spawn").forGetter(PastCityInstance::trialReturnSpawn),
                BlockBox.CODEC.fieldOf("enclosure_box").forGetter(PastCityInstance::pastCityBox),
                Codec.INT.optionalFieldOf("layout_version", 0).forGetter(PastCityInstance::layoutVersion),
                Codec.BOOL.optionalFieldOf("generated", false).forGetter(PastCityInstance::generated)
        ).apply(builder, PastCityInstance::new));

        public String key() {
            return AncientPastState.key(sourceWorld, sourceAltarPos);
        }

        public PastCityInstance withGenerated(boolean generated) {
            return new PastCityInstance(
                    sourceWorld,
                    sourceAltarPos,
                    sourceCityBox,
                    sourceReturnSpawn,
                    mainPortalAxis,
                    mainPortalFrontDirection,
                    List.copyOf(mainPortalPositions),
                    trialPortalAxis,
                    List.copyOf(trialPortalPositions),
                    pastSpawn,
                    trialReturnSpawn,
                    pastCityBox,
                    layoutVersion,
                    generated
            );
        }
    }
}
