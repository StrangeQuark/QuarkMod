package com.strangequark.dreamdimension.item;

import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;

public class EtherealBowItem extends BowItem {
    public EtherealBowItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }
}
