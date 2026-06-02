package com.strangequark.entity;

import com.strangequark.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GrapplingHookEntity extends PersistentProjectileEntity implements FlyingItemEntity {
    private static final Map<UUID, Integer> ACTIVE_HOOKS = new HashMap<>();
    private static final double DEFAULT_RANGE = 10.0D;
    private static final double HAND_GRAVITY = 0.15D;
    private static final double CROSSBOW_GRAVITY = 0.155D;
    private static final double ROPE_CORRECTION = 0.08D;
    private static final double MAX_ROPE_CORRECTION = 0.12D;
    private static final double REEL_PULL = 0.2D;
    private static final double MAX_REEL_SPEED = 1.2D;
    private static final double REEL_HOLD_RADIUS = 1.0D;
    private static final double MIN_REEL_HOLD_SPEED = 0.08D;
    private static final double REEL_HOLD_CORRECTION = 0.35D;
    private static final double MAX_REEL_HOLD_SPEED = 0.32D;
    private static final double REEL_SETTLE_DISTANCE = REEL_HOLD_RADIUS;
    private static final double REEL_COLLISION_SETTLE_DISTANCE = 2.25D;
    private static final int MIN_REEL_TICKS_BEFORE_COLLISION_SETTLE = 4;
    private static final TrackedData<Boolean> HOOKED =
            DataTracker.registerData(GrapplingHookEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> REELING =
            DataTracker.registerData(GrapplingHookEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Direction> ANCHOR_SIDE =
            DataTracker.registerData(GrapplingHookEntity.class, TrackedDataHandlerRegistry.FACING);
    private static final TrackedData<Integer> OWNER_ID =
            DataTracker.registerData(GrapplingHookEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<ItemStack> HOOK_STACK =
            DataTracker.registerData(GrapplingHookEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);

    private UseMode useMode = UseMode.HAND;
    private double maxRange = DEFAULT_RANGE;
    private boolean shouldReturnItem;
    private boolean reelTriggered;
    private boolean reelHolding;
    private int reelingTicks;
    private Hand controllerHand = Hand.MAIN_HAND;
    private Vec3d launchPos = Vec3d.ZERO;
    private Vec3d anchorPos = Vec3d.ZERO;
    private BlockPos anchorBlockPos = BlockPos.ORIGIN;
    private double ropeLength = DEFAULT_RANGE;

    public GrapplingHookEntity(EntityType<GrapplingHookEntity> entityType, World world) {
        super(entityType, world);
        this.pickupType = PickupPermission.DISALLOWED;
    }

    public GrapplingHookEntity(World world, LivingEntity owner, ItemStack stack, ItemStack weaponStack) {
        super(ModEntities.GRAPPLING_HOOK, owner, world, stack, weaponStack);
        this.pickupType = PickupPermission.DISALLOWED;
        this.launchPos = owner.getEyePos();
        this.getDataTracker().set(OWNER_ID, owner.getId());
        this.setHookStack(stack);
        this.setDamage(0.0D);
    }

    public static void setActiveHook(PlayerEntity player, GrapplingHookEntity hook) {
        ACTIVE_HOOKS.put(player.getUuid(), hook.getId());
    }

    public static GrapplingHookEntity getActiveHook(PlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld serverWorld)) {
            return null;
        }

        Integer hookId = ACTIVE_HOOKS.get(player.getUuid());
        if (hookId == null) {
            return null;
        }

        Entity entity = serverWorld.getEntityById(hookId);
        if (entity instanceof GrapplingHookEntity hook && !hook.isRemoved() && hook.getOwner() == player) {
            return hook;
        }

        ACTIVE_HOOKS.remove(player.getUuid());
        return null;
    }

    public static boolean releaseActiveHook(PlayerEntity player, boolean returnItem) {
        GrapplingHookEntity hook = getActiveHook(player);
        if (hook == null) {
            return false;
        }

        hook.release(returnItem);
        return true;
    }

    public void configure(UseMode useMode, double maxRange, boolean shouldReturnItem) {
        this.configure(useMode, maxRange, shouldReturnItem, Hand.MAIN_HAND);
    }

    public void configure(UseMode useMode, double maxRange, boolean shouldReturnItem, Hand controllerHand) {
        this.useMode = useMode;
        this.maxRange = maxRange;
        this.shouldReturnItem = shouldReturnItem;
        this.ropeLength = maxRange;
        this.controllerHand = controllerHand;
        this.setNoGravity(false);
    }

    public boolean isHooked() {
        return this.getDataTracker().get(HOOKED);
    }

    public boolean isReeling() {
        return this.getDataTracker().get(REELING);
    }

    public boolean hasReelBeenTriggered() {
        return this.reelTriggered;
    }

    public boolean isReelCrossbowHook() {
        return this.useMode == UseMode.REEL_CROSSBOW;
    }

    public Direction getAnchorSide() {
        return this.getDataTracker().get(ANCHOR_SIDE);
    }

    public int getHookOwnerId() {
        return this.getDataTracker().get(OWNER_ID);
    }

    public void startReeling() {
        if (this.isHooked()) {
            this.reelTriggered = true;
            this.reelHolding = false;
            this.reelingTicks = 0;
            this.getDataTracker().set(REELING, true);
            Entity owner = this.getOwner();
            if (owner != null) {
                this.getWorld().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, SoundCategory.PLAYERS, 0.9F, 1.0F);
            }
        }
    }

    public void release(boolean returnItem) {
        if (!this.getWorld().isClient && returnItem && this.shouldReturnItem) {
            this.returnHookItem();
        }
        this.discard();
    }

    @Override
    public void tick() {
        if (this.launchPos == Vec3d.ZERO) {
            this.launchPos = this.getPos();
        }

        if (!this.getWorld().isClient) {
            this.claimOrDiscardDuplicate();
            if (this.isRemoved()) {
                return;
            }

            if (!this.isControllerStillHeld()) {
                this.release(true);
                return;
            }
        }

        if (!this.getWorld().isClient && !this.isHooked() && this.launchPos.squaredDistanceTo(this.getPos()) > this.maxRange * this.maxRange) {
            this.release(true);
            return;
        }

        super.tick();

        if (!this.getWorld().isClient && this.isHooked()) {
            if (this.getWorld().getBlockState(this.anchorBlockPos).isAir()) {
                this.release(true);
                return;
            }
            this.applyTether();
        }
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        if (this.isHooked()) {
            return;
        }

        super.onBlockHit(blockHitResult);
        this.getDataTracker().set(HOOKED, true);
        this.getDataTracker().set(REELING, false);
        this.anchorPos = blockHitResult.getPos();
        this.anchorBlockPos = blockHitResult.getBlockPos();
        this.getDataTracker().set(ANCHOR_SIDE, blockHitResult.getSide());
        this.ropeLength = Math.min(this.maxRange, this.anchorPos.distanceTo(this.getOwnerPos()));
        this.setVelocity(Vec3d.ZERO);
        this.setNoGravity(true);
        this.playSound(SoundEvents.BLOCK_CHAIN_PLACE, 0.8F, 1.35F);
    }

    @Override
    protected boolean canHit(Entity entity) {
        return false;
    }

    @Override
    protected double getGravity() {
        if (this.isHooked()) {
            return 0.0D;
        }

        return this.useMode == UseMode.HAND ? HAND_GRAVITY : CROSSBOW_GRAVITY;
    }

    @Override
    protected ItemStack getDefaultItemStack() {
        return new ItemStack(ModItems.IRON_GRAPPLING_HOOK);
    }

    @Override
    public ItemStack getStack() {
        return this.getDataTracker().get(HOOK_STACK);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(HOOKED, false);
        builder.add(REELING, false);
        builder.add(ANCHOR_SIDE, Direction.UP);
        builder.add(OWNER_ID, -1);
        builder.add(HOOK_STACK, new ItemStack(ModItems.IRON_GRAPPLING_HOOK));
    }

    @Override
    protected void writeCustomData(WriteView view) {
        super.writeCustomData(view);
        view.putString("UseMode", this.useMode.name());
        view.putDouble("MaxRange", this.maxRange);
        view.putBoolean("ShouldReturnItem", this.shouldReturnItem);
        view.putBoolean("ReelTriggered", this.reelTriggered);
        view.putBoolean("ReelHolding", this.reelHolding);
        view.putInt("ReelingTicks", this.reelingTicks);
        view.putString("ControllerHand", this.controllerHand.name());
        view.putBoolean("Hooked", this.isHooked());
        view.putBoolean("Reeling", this.isReeling());
        view.putString("HookItem", Registries.ITEM.getId(this.getStack().getItem()).toString());
        view.putDouble("LaunchX", this.launchPos.x);
        view.putDouble("LaunchY", this.launchPos.y);
        view.putDouble("LaunchZ", this.launchPos.z);
        view.putDouble("AnchorX", this.anchorPos.x);
        view.putDouble("AnchorY", this.anchorPos.y);
        view.putDouble("AnchorZ", this.anchorPos.z);
        view.putInt("AnchorBlockX", this.anchorBlockPos.getX());
        view.putInt("AnchorBlockY", this.anchorBlockPos.getY());
        view.putInt("AnchorBlockZ", this.anchorBlockPos.getZ());
        view.putString("AnchorSide", this.getAnchorSide().asString());
        view.putDouble("RopeLength", this.ropeLength);
    }

    @Override
    protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        this.useMode = UseMode.byName(view.getString("UseMode", UseMode.HAND.name()));
        this.maxRange = view.getDouble("MaxRange", DEFAULT_RANGE);
        this.shouldReturnItem = view.getBoolean("ShouldReturnItem", false);
        this.reelTriggered = view.getBoolean("ReelTriggered", false);
        this.reelHolding = view.getBoolean("ReelHolding", false);
        this.reelingTicks = view.getInt("ReelingTicks", 0);
        this.controllerHand = readControllerHand(view);
        this.getDataTracker().set(HOOKED, view.getBoolean("Hooked", false));
        this.getDataTracker().set(REELING, view.getBoolean("Reeling", false));
        this.setHookStack(readHookStack(view));
        this.launchPos = new Vec3d(
                view.getDouble("LaunchX", this.getX()),
                view.getDouble("LaunchY", this.getY()),
                view.getDouble("LaunchZ", this.getZ())
        );
        this.anchorPos = new Vec3d(
                view.getDouble("AnchorX", this.getX()),
                view.getDouble("AnchorY", this.getY()),
                view.getDouble("AnchorZ", this.getZ())
        );
        this.anchorBlockPos = new BlockPos(
                view.getInt("AnchorBlockX", this.getBlockX()),
                view.getInt("AnchorBlockY", this.getBlockY()),
                view.getInt("AnchorBlockZ", this.getBlockZ())
        );
        Direction savedSide = Direction.byId(view.getString("AnchorSide", Direction.UP.asString()));
        this.getDataTracker().set(ANCHOR_SIDE, savedSide == null ? Direction.UP : savedSide);
        this.ropeLength = view.getDouble("RopeLength", this.maxRange);
        this.setNoGravity(this.isHooked());
    }

    @Override
    public void onRemoved() {
        this.unregisterActiveHook();
        super.onRemoved();
    }

    private void applyTether() {
        Entity owner = this.getOwner();
        if (!(owner instanceof PlayerEntity player) || player.isRemoved()) {
            this.release(false);
            return;
        }

        if (this.reelHolding) {
            this.holdReeledPlayer(player);
            return;
        }

        Vec3d playerAnchor = player.getEyePos();
        Vec3d toAnchor = this.anchorPos.subtract(playerAnchor);
        double distance = toAnchor.length();
        if (distance < 0.01D) {
            return;
        }

        Vec3d direction = toAnchor.normalize();
        Vec3d velocity = player.getVelocity();
        boolean changedVelocity = false;

        if (this.isReeling()) {
            this.reelingTicks++;

            if (distance <= REEL_SETTLE_DISTANCE || this.shouldSettleAfterCollision(player, distance)) {
                settleAtAnchor(player, velocity);
                return;
            }

            double radialSpeed = velocity.dotProduct(direction);
            if (radialSpeed < 0.0D) {
                velocity = velocity.subtract(direction.multiply(radialSpeed));
                radialSpeed = 0.0D;
            }

            double pull = Math.max(0.0D, Math.min(REEL_PULL, MAX_REEL_SPEED - radialSpeed));
            if (pull > 0.0D) {
                velocity = velocity.add(direction.multiply(pull));
                changedVelocity = true;
            }
        } else {
            double stretch = Math.max(0.0D, distance - this.ropeLength);
            if (stretch > 0.0D) {
                double radialSpeed = velocity.dotProduct(direction);
                if (radialSpeed < 0.0D) {
                    velocity = velocity.subtract(direction.multiply(radialSpeed));
                }
                velocity = velocity.add(direction.multiply(Math.min(stretch * ROPE_CORRECTION, MAX_ROPE_CORRECTION)));
                changedVelocity = true;
            }
        }

        if (changedVelocity) {
            player.setVelocity(velocity);
            player.velocityModified = true;
            player.fallDistance = 0.0D;
        }
    }

    private boolean shouldSettleAfterCollision(PlayerEntity player, double distance) {
        return this.reelingTicks >= MIN_REEL_TICKS_BEFORE_COLLISION_SETTLE
                && distance <= REEL_COLLISION_SETTLE_DISTANCE
                && this.isBlockedByAnchorSurface(player);
    }

    private void settleAtAnchor(PlayerEntity player, Vec3d velocity) {
        this.reelHolding = this.useMode == UseMode.REEL_CROSSBOW && this.reelTriggered;
        if (this.isBlockedByAnchorSurface(player)) {
            velocity = this.removeVelocityIntoAnchorSurface(velocity);
        }

        player.setVelocity(velocity);
        player.velocityModified = true;
        player.fallDistance = 0.0D;
        this.getDataTracker().set(REELING, false);
    }

    private void holdReeledPlayer(PlayerEntity player) {
        Vec3d playerAnchor = player.getEyePos();
        Vec3d toAnchor = this.anchorPos.subtract(playerAnchor);
        double distance = toAnchor.length();
        if (distance < 0.01D) {
            player.fallDistance = 0.0D;
            return;
        }

        Vec3d velocity = player.getVelocity();
        double stretch = distance - REEL_HOLD_RADIUS;
        if (stretch > 0.0D) {
            Vec3d direction = toAnchor.normalize();
            double radialSpeed = velocity.dotProduct(direction);
            double targetInwardSpeed = Math.min(MAX_REEL_HOLD_SPEED, Math.max(MIN_REEL_HOLD_SPEED, stretch * REEL_HOLD_CORRECTION));
            if (radialSpeed < targetInwardSpeed) {
                velocity = velocity.add(direction.multiply(targetInwardSpeed - radialSpeed));
            }

            if (this.isBlockedByAnchorSurface(player)) {
                velocity = this.removeVelocityIntoAnchorSurface(velocity);
            }
            player.setVelocity(velocity);
            player.velocityModified = true;
        }

        player.fallDistance = 0.0D;
    }

    private boolean isBlockedByAnchorSurface(PlayerEntity player) {
        return player.horizontalCollision || (player.verticalCollision && !player.isOnGround());
    }

    private Vec3d removeVelocityIntoAnchorSurface(Vec3d velocity) {
        Vec3d surfaceNormal = this.getAnchorSide().getDoubleVector();
        double surfaceSpeed = velocity.dotProduct(surfaceNormal);
        return surfaceSpeed < 0.0D ? velocity.subtract(surfaceNormal.multiply(surfaceSpeed)) : velocity;
    }

    private Vec3d getOwnerPos() {
        Entity owner = this.getOwner();
        return owner == null ? this.getPos() : owner.getEyePos();
    }

    private boolean isControllerStillHeld() {
        Entity owner = this.getOwner();
        if (!(owner instanceof PlayerEntity player)) {
            return true;
        }

        ItemStack controllerStack = player.getStackInHand(this.controllerHand);
        ItemStack weaponStack = this.getWeaponStack();
        if (this.useMode == UseMode.REEL_CROSSBOW) {
            return controllerStack.isOf(ModItems.GRAPPLING_CROSSBOW);
        }

        if (this.useMode == UseMode.HAND) {
            return ModItems.isGrapplingHook(controllerStack)
                    && (weaponStack.isEmpty() || controllerStack.isOf(weaponStack.getItem()));
        }

        return true;
    }

    private static Hand readControllerHand(ReadView view) {
        try {
            return Hand.valueOf(view.getString("ControllerHand", Hand.MAIN_HAND.name()));
        } catch (IllegalArgumentException exception) {
            return Hand.MAIN_HAND;
        }
    }

    private void returnHookItem() {
        Entity owner = this.getOwner();
        if (owner instanceof PlayerEntity player) {
            player.getInventory().offerOrDrop(this.getStack().copyWithCount(1));
            this.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, SoundCategory.PLAYERS, 0.6F, 1.2F);
        }
    }

    private void setHookStack(ItemStack stack) {
        ItemStack hookStack = ModItems.isGrapplingHook(stack) ? stack.copyWithCount(1) : new ItemStack(ModItems.IRON_GRAPPLING_HOOK);
        this.setStack(hookStack);
        this.getDataTracker().set(HOOK_STACK, hookStack);
    }

    private static ItemStack readHookStack(ReadView view) {
        String hookItemId = view.getString("HookItem", Registries.ITEM.getId(ModItems.IRON_GRAPPLING_HOOK).toString());
        try {
            Item item = Registries.ITEM.get(Identifier.of(hookItemId));
            ItemStack stack = new ItemStack(item);
            return ModItems.isGrapplingHook(stack) ? stack : new ItemStack(ModItems.IRON_GRAPPLING_HOOK);
        } catch (RuntimeException exception) {
            return new ItemStack(ModItems.IRON_GRAPPLING_HOOK);
        }
    }

    private void claimOrDiscardDuplicate() {
        Entity owner = this.getOwner();
        if (!(owner instanceof PlayerEntity player)) {
            return;
        }

        Integer activeId = ACTIVE_HOOKS.get(player.getUuid());
        if (activeId == null) {
            ACTIVE_HOOKS.put(player.getUuid(), this.getId());
            return;
        }

        if (activeId != this.getId()) {
            Entity activeEntity = ((ServerWorld) this.getWorld()).getEntityById(activeId);
            if (activeEntity instanceof GrapplingHookEntity activeHook && !activeHook.isRemoved()) {
                this.discard();
            } else {
                ACTIVE_HOOKS.put(player.getUuid(), this.getId());
            }
        }
    }

    private void unregisterActiveHook() {
        Entity owner = this.getOwner();
        if (owner instanceof PlayerEntity player) {
            Integer activeId = ACTIVE_HOOKS.get(player.getUuid());
            if (activeId != null && activeId == this.getId()) {
                ACTIVE_HOOKS.remove(player.getUuid());
            }
        }
    }

    public enum UseMode {
        HAND,
        CROSSBOW,
        REEL_CROSSBOW;

        private static UseMode byName(String name) {
            for (UseMode mode : values()) {
                if (mode.name().equals(name)) {
                    return mode;
                }
            }
            return HAND;
        }
    }
}
