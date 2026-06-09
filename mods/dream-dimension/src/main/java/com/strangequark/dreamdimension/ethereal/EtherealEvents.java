package com.strangequark.dreamdimension.ethereal;

import com.strangequark.dreamdimension.DreamDimensionMod;
import com.strangequark.dreamdimension.block.ModBlocks;
import com.strangequark.dreamdimension.item.ModItems;
import com.strangequark.dreamdimension.world.DreamDimensionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
import net.minecraft.world.World;

public final class EtherealEvents {
    private static final String ETHEREAL_MOB_TAG = "quarkmod.ethereal";
    private static final String ETHEREAL_ROLL_TAG = "quarkmod.ethereal_checked";
    private static final String ETHEREAL_PROJECTILE_TAG = "quarkmod.ethereal_projectile";
    private static final String ETHEREAL_NBT_KEY = "quarkmod_ethereal";
    private static final String ETHEREAL_BASE_TYPE_NBT_KEY = "quarkmod_ethereal_base_type";
    private static final String ETHEREAL_VARIANT_NBT_KEY = "quarkmod_ethereal_variant";
    private static final String ETHEREAL_NAME_PREFIX = "Ethereal ";
    private static final double ETHEREAL_SPAWN_CHANCE = 0.08D;
    private static final int ETHEREAL_AURA_INTERVAL_TICKS = 60;
    private static final float ETHEREAL_SKELETON_BOW_DROP_CHANCE = 0.125F;
    private static final float ETHEREAL_SKELETON_ARROW_DROP_CHANCE = 0.55F;
    private static final double ETHEREAL_AURA_RANGE_SQUARED = 64.0D;
    private static final Identifier ETHEREAL_HEALTH_MODIFIER = DreamDimensionMod.id("ethereal_health");
    private static final Identifier ETHEREAL_SPEED_MODIFIER = DreamDimensionMod.id("ethereal_speed");
    private static final Identifier ETHEREAL_DAMAGE_MODIFIER = DreamDimensionMod.id("ethereal_damage");
    private static final Identifier ETHEREAL_FOLLOW_RANGE_MODIFIER = DreamDimensionMod.id("ethereal_follow_range");
    private static final Identifier ETHEREAL_SCALE_MODIFIER = DreamDimensionMod.id("ethereal_scale");

    private EtherealEvents() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register(EtherealEvents::maybeMarkDreamMob);
        ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, conversionContext) -> {
            if (isEtherealEntity(previous)) {
                converted.addCommandTag(ETHEREAL_MOB_TAG);
                converted.addCommandTag(ETHEREAL_ROLL_TAG);
                setupEtherealMob(converted);
                return;
            }
            maybeMarkDreamMob(converted, converted.getWorld());
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(EtherealEvents::allowEtherealDamage);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(EtherealEvents::applyEtherealOnHit);
        ServerLivingEntityEvents.AFTER_DEATH.register(EtherealEvents::dropEtherealSkeletonLoot);
        ServerTickEvents.END_WORLD_TICK.register(EtherealEvents::tickEtherealAuras);
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player.isCreative()) {
                return true;
            }

            ItemStack tool = player.getMainHandStack();
            return canBreakEtherealBlock(state, tool);
        });
    }

    public static boolean isEtherealDamageSource(DamageSource source) {
        return isEtherealEntity(source.getAttacker()) || isEtherealEntity(source.getSource()) || isEtherealProjectile(source.getSource());
    }

    public static void markEtherealProjectile(Entity projectile) {
        if (projectile == null) {
            return;
        }

        projectile.addCommandTag(ETHEREAL_PROJECTILE_TAG);
        NbtComponent existingData = projectile.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = existingData == null ? new NbtCompound() : existingData.copyNbt();
        nbt.putBoolean(ETHEREAL_NBT_KEY, true);
        projectile.setComponent(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    public static boolean isEtherealEntity(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }

        if (entity instanceof EtherealEntityAccess access && access.quarkmod$isEtherealVariant()) {
            return true;
        }

        if (entity.getCommandTags().contains(ETHEREAL_MOB_TAG)) {
            return true;
        }

        NbtComponent customData = entity.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null && customData.copyNbt().getBoolean(ETHEREAL_NBT_KEY, false)) {
            return true;
        }

        Text customName = entity.getCustomName();
        return customName != null && customName.getString().startsWith(ETHEREAL_NAME_PREFIX);
    }

    private static boolean isEtherealProjectile(Entity entity) {
        if (entity == null) {
            return false;
        }

        if (entity.getCommandTags().contains(ETHEREAL_PROJECTILE_TAG)) {
            return true;
        }

        NbtComponent customData = entity.get(DataComponentTypes.CUSTOM_DATA);
        return customData != null && customData.copyNbt().getBoolean(ETHEREAL_NBT_KEY, false);
    }

    private static void maybeMarkDreamMob(Entity entity, World world) {
        if (!isDreamWorld(world) || !(entity instanceof MobEntity mob) || entity instanceof VillagerEntity) {
            return;
        }

        if (isEtherealEntity(mob)) {
            setupEtherealMob(mob);
            return;
        }

        if (mob.getCommandTags().contains(ETHEREAL_ROLL_TAG)) {
            return;
        }

        mob.addCommandTag(ETHEREAL_ROLL_TAG);
        if (mob.getRandom().nextDouble() >= ETHEREAL_SPAWN_CHANCE) {
            return;
        }

        mob.addCommandTag(ETHEREAL_MOB_TAG);
        setupEtherealMob(mob);
    }

    private static void setupEtherealMob(MobEntity mob) {
        boolean gainedHealth = addModifierIfMissing(
                mob,
                EntityAttributes.MAX_HEALTH,
                ETHEREAL_HEALTH_MODIFIER,
                0.75D
        );
        addModifierIfMissing(mob, EntityAttributes.MOVEMENT_SPEED, ETHEREAL_SPEED_MODIFIER, 0.15D);
        addModifierIfMissing(mob, EntityAttributes.ATTACK_DAMAGE, ETHEREAL_DAMAGE_MODIFIER, 0.35D);
        addModifierIfMissing(mob, EntityAttributes.FOLLOW_RANGE, ETHEREAL_FOLLOW_RANGE_MODIFIER, 0.35D);
        boolean gainedScale = addModifierIfMissing(mob, EntityAttributes.SCALE, ETHEREAL_SCALE_MODIFIER, 0.12D);

        if (gainedHealth) {
            mob.setHealth(mob.getMaxHealth());
        }

        if (gainedScale) {
            mob.calculateDimensions();
        }

        applyEtherealNbtData(mob);
        if (mob instanceof EtherealEntityAccess access) {
            access.quarkmod$setEtherealVariant(true);
        }
        mob.removeStatusEffect(StatusEffects.GLOWING);
        mob.setGlowing(false);
        if (mob.getCustomName() == null) {
            mob.setCustomName(Text.literal(ETHEREAL_NAME_PREFIX).append(mob.getType().getName()));
            mob.setCustomNameVisible(false);
        }
    }

    private static void applyEtherealNbtData(MobEntity mob) {
        NbtComponent existingData = mob.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = existingData == null ? new NbtCompound() : existingData.copyNbt();
        nbt.putBoolean(ETHEREAL_NBT_KEY, true);
        nbt.putString(ETHEREAL_BASE_TYPE_NBT_KEY, EntityType.getId(mob.getType()).toString());
        nbt.putInt(ETHEREAL_VARIANT_NBT_KEY, Math.floorMod(mob.getUuid().hashCode(), 4));
        mob.setComponent(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private static boolean addModifierIfMissing(
            MobEntity mob,
            RegistryEntry<EntityAttribute> attribute,
            Identifier id,
            double value
    ) {
        EntityAttributeInstance attributeInstance = mob.getAttributeInstance(attribute);
        if (attributeInstance == null || attributeInstance.hasModifier(id)) {
            return false;
        }

        attributeInstance.addPersistentModifier(new EntityAttributeModifier(
                id,
                value,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
        return true;
    }

    private static boolean allowEtherealDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!isEtherealEntity(entity)) {
            return true;
        }

        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }

        if (isEtherealProjectile(source.getSource())) {
            return true;
        }

        ItemStack weapon = source.getWeaponStack();
        return ModItems.isEtherealWeaponOrTool(weapon);
    }

    private static void applyEtherealOnHit(
            LivingEntity entity,
            DamageSource source,
            float baseDamageTaken,
            float damageTaken,
            boolean blocked
    ) {
        if (blocked || !isEtherealDamageSource(source) || isEtherealEntity(entity)) {
            return;
        }

        Entity attacker = source.getAttacker();
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0, false, true, true), attacker);
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 1, false, true, true), attacker);
    }

    private static void dropEtherealSkeletonLoot(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof AbstractSkeletonEntity skeleton) || !isEtherealEntity(entity) || !(entity.getWorld() instanceof ServerWorld world)) {
            return;
        }

        if (skeleton.getRandom().nextFloat() < ETHEREAL_SKELETON_BOW_DROP_CHANCE) {
            ItemStack bow = new ItemStack(ModItems.ETHEREAL_BOW);
            bow.setDamage(skeleton.getRandom().nextBetween(0, bow.getMaxDamage() / 2));
            skeleton.dropStack(world, bow);
        }

        if (skeleton.getRandom().nextFloat() < ETHEREAL_SKELETON_ARROW_DROP_CHANCE) {
            skeleton.dropStack(world, new ItemStack(ModItems.ETHEREAL_ARROW, skeleton.getRandom().nextBetween(2, 6)));
        }
    }

    private static void tickEtherealAuras(ServerWorld world) {
        if (!isDreamWorld(world) || world.getTime() % ETHEREAL_AURA_INTERVAL_TICKS != 0L) {
            return;
        }

        for (MobEntity mob : world.getEntitiesByType(
                TypeFilter.instanceOf(MobEntity.class),
                mob -> mob.isAlive() && isEtherealEntity(mob)
        )) {
            LivingEntity target = mob.getTarget();
            if (target == null
                    || !target.isAlive()
                    || isEtherealEntity(target)
                    || mob.squaredDistanceTo(target) > ETHEREAL_AURA_RANGE_SQUARED) {
                continue;
            }

            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 0, false, true, true), mob);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 30, 0, false, true, true), mob);
            world.spawnParticles(
                    ParticleTypes.END_ROD,
                    target.getX(),
                    target.getY() + target.getHeight() * 0.5D,
                    target.getZ(),
                    14,
                    0.35D,
                    0.45D,
                    0.35D,
                    0.015D
            );
            mob.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.5F);
        }
    }

    private static boolean canBreakEtherealBlock(BlockState state, ItemStack tool) {
        if (state.isIn(ModBlocks.ETHEREAL_PICKAXE_MINEABLE) && !ModItems.isEtherealPickaxe(tool)) {
            return false;
        }
        return !state.isIn(ModBlocks.ETHEREAL_AXE_MINEABLE) || ModItems.isEtherealAxe(tool);
    }

    private static boolean isDreamWorld(World world) {
        return world instanceof ServerWorld && world.getRegistryKey().equals(DreamDimensionEvents.DREAM_WORLD);
    }
}
