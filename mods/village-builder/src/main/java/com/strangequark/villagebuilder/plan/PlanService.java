package com.strangequark.villagebuilder.plan;

import com.strangequark.villagebuilder.blueprint.BlueprintTemplate;
import com.strangequark.villagebuilder.network.VillageBuilderNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.UUID;

public final class PlanService {
    private PlanService() { }
    public static boolean place(ServerPlayerEntity player, ServerWorld world, BlueprintTemplate template, BlockPos anchor, int rotation) {
        int sizeX = rotation % 2 == 0 ? template.sizeX() : template.sizeZ();
        int sizeZ = rotation % 2 == 0 ? template.sizeZ() : template.sizeX();
        BlockPos farCorner = anchor.add(sizeX - 1, template.sizeY() - 1, sizeZ - 1);
        if (!world.isInBuildLimit(anchor) || !world.isInBuildLimit(farCorner)) return false;
        Box proposed = new Box(anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getX() + sizeX, anchor.getY() + template.sizeY(), anchor.getZ() + sizeZ);
        VillagePlanState state = VillagePlanState.get(player.getServer());
        for (VillagePlanState.PlanRecord existing : state.all()) {
            if (existing.world().equals(world.getRegistryKey().getValue()) && proposed.intersects(box(existing))) {
                player.sendMessage(Text.translatable("message.quarkmod.village_builder.overlap"), true); return false;
            }
        }
        state.add(new VillagePlanState.PlanRecord(UUID.randomUUID(), player.getUuid(), template.id(), world.getRegistryKey().getValue(), anchor.toImmutable(), rotation, template.sizeX(), template.sizeY(), template.sizeZ()));
        VillageBuilderNetworking.syncPlans(player.getServer());
        player.sendMessage(Text.translatable("message.quarkmod.village_builder.planned", template.id()), true); return true;
    }
    public static boolean remove(ServerPlayerEntity player, ServerWorld world, BlockPos anchor) {
        VillagePlanState state = VillagePlanState.get(player.getServer());
        return state.at(world.getRegistryKey().getValue(), anchor).map(plan -> {
            if (!plan.owner().equals(player.getUuid()) && !player.hasPermissionLevel(2)) { player.sendMessage(Text.translatable("message.quarkmod.village_builder.denied"), true); return false; }
            state.remove(plan.id()); VillageBuilderNetworking.syncPlans(player.getServer()); player.sendMessage(Text.translatable("message.quarkmod.village_builder.removed"), true); return true;
        }).orElse(false);
    }
    private static Box box(VillagePlanState.PlanRecord plan) { int x = plan.rotation() % 2 == 0 ? plan.sizeX() : plan.sizeZ(); int z = plan.rotation() % 2 == 0 ? plan.sizeZ() : plan.sizeX(); return new Box(plan.anchor().getX(), plan.anchor().getY(), plan.anchor().getZ(), plan.anchor().getX() + x, plan.anchor().getY() + plan.sizeY(), plan.anchor().getZ() + z); }
}
