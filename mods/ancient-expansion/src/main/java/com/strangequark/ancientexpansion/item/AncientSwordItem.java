package com.strangequark.ancientexpansion.item;

import com.strangequark.ancientexpansion.enchantment.AncientEnchantmentLogic;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class AncientSwordItem extends Item {
    public AncientSwordItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world instanceof ServerWorld serverWorld && user instanceof ServerPlayerEntity serverPlayer) {
            if (AncientEnchantmentLogic.releaseWarboundPulse(serverWorld, serverPlayer, stack)) {
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, @Nullable EquipmentSlot slot) {
        AncientEnchantmentLogic.syncWarboundAttributes(stack);
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return AncientEnchantmentLogic.isWarboundBarVisible(stack);
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return AncientEnchantmentLogic.warboundItemBarStep(stack);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return AncientEnchantmentLogic.warboundItemBarColor(stack);
    }

    @Override
    public void appendTooltip(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplayComponent displayComponent,
            Consumer<Text> textConsumer,
            TooltipType type
    ) {
        AncientEnchantmentLogic.appendWarboundTooltip(stack, textConsumer);
    }
}
