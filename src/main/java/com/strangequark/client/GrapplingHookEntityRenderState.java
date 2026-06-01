package com.strangequark.client;

import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class GrapplingHookEntityRenderState extends EntityRenderState {
    public final ItemRenderState itemRenderState = new ItemRenderState();
    public float yaw;
    public float pitch;
    public boolean hooked;
    public boolean hasLine;
    public Direction anchorSide = Direction.UP;
    public Vec3d lineToOwner = Vec3d.ZERO;
}
