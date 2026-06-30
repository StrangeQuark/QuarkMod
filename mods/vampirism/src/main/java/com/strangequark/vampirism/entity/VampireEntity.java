package com.strangequark.vampirism.entity;

import com.strangequark.vampirism.VampirismMod;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityInteraction;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.conversion.EntityConversionContext;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class VampireEntity extends ZombieEntity {
    public static final Identifier DEFAULT_SKIN = VampirismMod.id("textures/entity/vampire.png");
    private static final String SKIN_KEY = "Skin";
    private static final String SPAWN_AS_BAT_KEY = "SpawnAsBat";
    private static final String CONVERSION_TIME_KEY = "ConversionTime";
    private static final String CONVERSION_PLAYER_KEY = "ConversionPlayer";
    private static final float BAT_TRANSFORM_HEALTH_FRACTION = 0.2F;
    private static final float SPAWN_AS_BAT_CHANCE = 0.2F;
    private static final int PASSIVE_HEAL_INTERVAL_TICKS = 20;
    private static final float PASSIVE_HEAL_AMOUNT = 1.0F;
    private static final int BASE_CONVERSION_DELAY_TICKS = 3600;
    private static final int RANDOM_CONVERSION_DELAY_TICKS = 2401;
    private static final int DEFAULT_CONVERSION_TIME = -1;
    private static final int CONVERSION_BLOCK_SEARCH_RADIUS = 4;
    private static final int MAX_CONVERSION_SPEED_BLOCKS = 14;
    private static final TrackedData<String> SKIN =
            DataTracker.registerData(VampireEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Boolean> CONVERTING =
            DataTracker.registerData(VampireEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private boolean spawnAsBat;
    private int conversionTimer = DEFAULT_CONVERSION_TIME;
    @Nullable
    private UUID converter;

    public VampireEntity(EntityType<? extends VampireEntity> entityType, World world) {
        super(entityType, world);
    }

    public Identifier getSkinTexture() {
        Identifier skin = Identifier.tryParse(this.getDataTracker().get(SKIN));
        return skin == null ? DEFAULT_SKIN : skin;
    }

    public void setSkinTexture(Identifier skin) {
        this.getDataTracker().set(SKIN, skin.toString());
    }

    @Override
    protected boolean canConvertInWater() {
        return false;
    }

    @Override
    public void tick() {
        if (!this.getWorld().isClient && this.isAlive() && this.isConverting()) {
            this.conversionTimer -= this.getConversionRate();
            if (this.conversionTimer <= 0) {
                this.finishConversion((ServerWorld) this.getWorld());
            }
        }

        super.tick();
    }

    @Override
    protected void mobTick(ServerWorld world) {
        super.mobTick(world);

        if (this.isConverting()) {
            return;
        }

        if (this.spawnAsBat) {
            this.spawnAsBat = false;
            this.transformIntoBat(null);
            return;
        }

        if (this.shouldTransformIntoBat()) {
            this.transformIntoBat(this.getFleeThreat());
            return;
        }

        this.passiveHeal();
    }

    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, EntityData entityData) {
        EntityData initializedData = super.initialize(world, difficulty, spawnReason, entityData);
        if (spawnReason != SpawnReason.CONVERSION && this.getRandom().nextFloat() < SPAWN_AS_BAT_CHANCE) {
            this.spawnAsBat = true;
        }
        return initializedData;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SKIN, DEFAULT_SKIN.toString());
        builder.add(CONVERTING, false);
    }

    @Override
    protected void writeCustomData(WriteView view) {
        super.writeCustomData(view);
        view.putString(SKIN_KEY, this.getSkinTexture().toString());
        view.putBoolean(SPAWN_AS_BAT_KEY, this.spawnAsBat);
        view.putInt(CONVERSION_TIME_KEY, this.isConverting() ? this.conversionTimer : DEFAULT_CONVERSION_TIME);
        view.putNullable(CONVERSION_PLAYER_KEY, Uuids.INT_STREAM_CODEC, this.converter);
    }

    @Override
    protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        Identifier skin = Identifier.tryParse(view.getString(SKIN_KEY, DEFAULT_SKIN.toString()));
        this.setSkinTexture(skin == null ? DEFAULT_SKIN : skin);
        this.spawnAsBat = view.getBoolean(SPAWN_AS_BAT_KEY, false);

        int conversionTime = view.getInt(CONVERSION_TIME_KEY, DEFAULT_CONVERSION_TIME);
        if (conversionTime != DEFAULT_CONVERSION_TIME) {
            UUID conversionPlayer = view.read(CONVERSION_PLAYER_KEY, Uuids.INT_STREAM_CODEC).orElse(null);
            this.setConverting(conversionPlayer, conversionTime);
        } else {
            this.getDataTracker().set(CONVERTING, false);
            this.conversionTimer = DEFAULT_CONVERSION_TIME;
            this.converter = null;
        }
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack itemStack = player.getStackInHand(hand);
        if (!itemStack.isOf(Items.GOLDEN_APPLE)) {
            return super.interactMob(player, hand);
        }

        if (!this.hasStatusEffect(StatusEffects.WEAKNESS)) {
            return ActionResult.CONSUME;
        }

        itemStack.decrementUnlessCreative(1, player);
        if (!this.getWorld().isClient) {
            this.setConverting(player.getUuid(), this.random.nextInt(RANDOM_CONVERSION_DELAY_TICKS) + BASE_CONVERSION_DELAY_TICKS);
        }

        return ActionResult.SUCCESS_SERVER;
    }

    public boolean isConverting() {
        return this.getDataTracker().get(CONVERTING);
    }

    private void setConverting(@Nullable UUID uuid, int delay) {
        this.converter = uuid;
        this.conversionTimer = delay;
        this.spawnAsBat = false;
        this.getDataTracker().set(CONVERTING, true);
        this.removeStatusEffect(StatusEffects.WEAKNESS);
        this.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, delay, Math.min(this.getWorld().getDifficulty().getId() - 1, 0)));
        this.getWorld().sendEntityStatus(this, EntityStatuses.PLAY_CURE_ZOMBIE_VILLAGER_SOUND);
    }

    @Override
    public void handleStatus(byte status) {
        if (status == EntityStatuses.PLAY_CURE_ZOMBIE_VILLAGER_SOUND) {
            if (!this.isSilent()) {
                this.getWorld()
                        .playSoundClient(
                                this.getX(),
                                this.getEyeY(),
                                this.getZ(),
                                SoundEvents.ENTITY_ZOMBIE_VILLAGER_CURE,
                                this.getSoundCategory(),
                                1.0F + this.random.nextFloat(),
                                this.random.nextFloat() * 0.7F + 0.3F,
                                false
                        );
            }
        } else {
            super.handleStatus(status);
        }
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return !this.isConverting() && super.canImmediatelyDespawn(distanceSquared);
    }

    private boolean shouldTransformIntoBat() {
        if (!this.isAlive() || this.getHealth() > this.getMaxHealth() * BAT_TRANSFORM_HEALTH_FRACTION) {
            return false;
        }

        return this.getTarget() != null || this.getAttacker() != null || this.getLastAttacker() != null;
    }

    private void passiveHeal() {
        if (this.age % PASSIVE_HEAL_INTERVAL_TICKS == 0 && this.isAlive() && this.getHealth() < this.getMaxHealth()) {
            this.setHealth(Math.min(this.getMaxHealth(), this.getHealth() + PASSIVE_HEAL_AMOUNT));
        }
    }

    private LivingEntity getFleeThreat() {
        LivingEntity threat = this.getTarget();
        if (threat == null) {
            threat = this.getAttacker();
        }
        if (threat == null) {
            threat = this.getLastAttacker();
        }
        return threat;
    }

    private void transformIntoBat(LivingEntity threat) {
        final LivingEntity fleeThreat = threat;
        float remainingHealth = this.getHealth();
        EntityConversionContext context = EntityConversionContext.create(this, false, true);
        this.convertTo(ModEntities.VAMPIRIC_BAT, context, SpawnReason.CONVERSION, bat -> {
            bat.setHealth(Math.max(1.0F, Math.min(bat.getMaxHealth(), remainingHealth)));
            bat.setRoosting(false);
            if (fleeThreat != null) {
                bat.startFleeingFrom(fleeThreat);
            }
        });
    }

    private void finishConversion(ServerWorld world) {
        this.convertTo(EntityType.VILLAGER, EntityConversionContext.create(this, false, false), SpawnReason.CONVERSION, villager -> {
            for (EquipmentSlot equipmentSlot : this.dropForeignEquipment(
                    world,
                    stack -> !EnchantmentHelper.hasAnyEnchantmentsWith(stack, EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE)
            )) {
                StackReference stackReference = villager.getStackReference(equipmentSlot.getEntitySlotId() + 300);
                stackReference.set(this.getEquippedStack(equipmentSlot));
            }

            villager.initialize(world, world.getLocalDifficulty(villager.getBlockPos()), SpawnReason.CONVERSION, null);
            villager.reinitializeBrain(world);
            if (this.converter != null) {
                PlayerEntity player = world.getPlayerByUuid(this.converter);
                if (player instanceof ServerPlayerEntity) {
                    world.handleInteraction(EntityInteraction.ZOMBIE_VILLAGER_CURED, player, villager);
                }
            }

            villager.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 200, 0));
            if (!this.isSilent()) {
                world.syncWorldEvent(null, WorldEvents.ZOMBIE_VILLAGER_CURED, this.getBlockPos(), 0);
            }
        });
    }

    private int getConversionRate() {
        int rate = 1;
        if (this.random.nextFloat() < 0.01F) {
            int checkedBlocks = 0;
            BlockPos.Mutable mutable = new BlockPos.Mutable();

            for (int x = (int) this.getX() - CONVERSION_BLOCK_SEARCH_RADIUS;
                 x < (int) this.getX() + CONVERSION_BLOCK_SEARCH_RADIUS && checkedBlocks < MAX_CONVERSION_SPEED_BLOCKS;
                 x++) {
                for (int y = (int) this.getY() - CONVERSION_BLOCK_SEARCH_RADIUS;
                     y < (int) this.getY() + CONVERSION_BLOCK_SEARCH_RADIUS && checkedBlocks < MAX_CONVERSION_SPEED_BLOCKS;
                     y++) {
                    for (int z = (int) this.getZ() - CONVERSION_BLOCK_SEARCH_RADIUS;
                         z < (int) this.getZ() + CONVERSION_BLOCK_SEARCH_RADIUS && checkedBlocks < MAX_CONVERSION_SPEED_BLOCKS;
                         z++) {
                        BlockState blockState = this.getWorld().getBlockState(mutable.set(x, y, z));
                        if (blockState.isOf(Blocks.IRON_BARS) || blockState.getBlock() instanceof BedBlock) {
                            if (this.random.nextFloat() < 0.3F) {
                                rate++;
                            }

                            checkedBlocks++;
                        }
                    }
                }
            }
        }

        return rate;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_ZOMBIE_VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_ZOMBIE_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_ZOMBIE_VILLAGER_DEATH;
    }
}
