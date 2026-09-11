package com.strangequark.villagebuilder.item;

import com.strangequark.villagebuilder.blueprint.BlueprintTemplate;
import com.strangequark.villagebuilder.network.VillageBuilderNetworking;
import com.strangequark.villagebuilder.plan.PlanService;
import com.strangequark.villagebuilder.plan.PlannerSelections;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class PlannerWandItem extends Item {
    private static final double AIR_PLACEMENT_RANGE = 5.0D;

    public PlannerWandItem(Settings settings) { super(settings.maxCount(1)); }
    @Override public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            if (user.isSneaking()) {
                VillageBuilderNetworking.openPicker(player);
            } else {
                HitResult hit = player.raycast(AIR_PLACEMENT_RANGE, 1.0F, false);
                place(player, (ServerWorld) world, BlockPos.ofFloored(hit.getPos()));
            }
        }
        return ActionResult.SUCCESS;
    }
    @Override public ActionResult useOnBlock(ItemUsageContext context) {
        if (!context.getWorld().isClient && context.getPlayer() instanceof ServerPlayerEntity player && context.getWorld() instanceof ServerWorld world) {
            if (player.isSneaking()) VillageBuilderNetworking.openPicker(player);
            else place(player, world, context.getBlockPos().offset(context.getSide()));
        }
        return ActionResult.SUCCESS;
    }
    private static void place(ServerPlayerEntity player, ServerWorld world, BlockPos anchor) {
        BlueprintTemplate template = PlannerSelections.selected(player);
        if (template == null) player.sendMessage(Text.translatable("message.quarkmod.village_builder.no_templates"), true);
        else PlanService.place(player, world, template, anchor, PlannerSelections.get(player).rotation());
    }
}
