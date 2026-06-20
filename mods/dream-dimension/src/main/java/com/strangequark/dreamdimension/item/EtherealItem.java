package com.strangequark.dreamdimension.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class EtherealItem extends Item {
    public EtherealItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }
}
