package com.strangequark.dreamdimension.item;

import com.strangequark.dreamdimension.ethereal.EtherealEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Position;
import net.minecraft.world.World;

public class EtherealArrowItem extends ArrowItem {
    public EtherealArrowItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public PersistentProjectileEntity createArrow(World world, ItemStack stack, LivingEntity shooter, ItemStack weaponStack) {
        PersistentProjectileEntity projectile = super.createArrow(world, stack, shooter, weaponStack);
        EtherealEvents.markEtherealProjectile(projectile);
        return projectile;
    }

    @Override
    public ProjectileEntity createEntity(World world, Position pos, ItemStack stack, Direction direction) {
        ProjectileEntity projectile = super.createEntity(world, pos, stack, direction);
        EtherealEvents.markEtherealProjectile(projectile);
        return projectile;
    }
}
