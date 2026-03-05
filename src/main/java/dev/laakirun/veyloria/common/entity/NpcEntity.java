package dev.laakirun.veyloria.common.entity;

import dev.laakirun.veyloria.common.npc.NpcAppearance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class NpcEntity extends PathfinderMob {
    public static final String TAG_DEFINITION_ID = "veyloria_npc_definition_id";
    public static final String TAG_INSTANCE_ID = "veyloria_npc_instance_id";
    public static final String TAG_APPEARANCE_ID = "veyloria_npc_appearance_id";

    private static final EntityDataAccessor<String> DATA_DEFINITION_ID =
        SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_INSTANCE_ID =
        SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_APPEARANCE_ID =
        SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);

    public NpcEntity(EntityType<? extends NpcEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.0D)
            .add(Attributes.ARMOR, 0.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DEFINITION_ID, "");
        builder.define(DATA_INSTANCE_ID, "");
        builder.define(DATA_APPEARANCE_ID, NpcAppearance.WITHER.id());
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide()) {
            getNavigation().stop();
        }
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        String definitionId = definitionIdRaw();
        String instanceId = instanceId();
        String appearanceId = appearance().id();
        if (!definitionId.isBlank()) {
            tag.putString(TAG_DEFINITION_ID, definitionId);
        }
        if (!instanceId.isBlank()) {
            tag.putString(TAG_INSTANCE_ID, instanceId);
        }
        tag.putString(TAG_APPEARANCE_ID, appearanceId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        String definitionId = tag.getString(TAG_DEFINITION_ID);
        String instanceId = tag.getString(TAG_INSTANCE_ID);
        String appearanceId = tag.contains(TAG_APPEARANCE_ID) ? tag.getString(TAG_APPEARANCE_ID) : NpcAppearance.WITHER.id();
        setNpcData(definitionId, instanceId, NpcAppearance.fromId(appearanceId));
    }

    public void setNpcData(String definitionId, String instanceId, NpcAppearance appearance) {
        String safeDefinitionId = definitionId == null ? "" : definitionId;
        String safeInstanceId = instanceId == null ? "" : instanceId;
        NpcAppearance safeAppearance = appearance == null ? NpcAppearance.WITHER : appearance;
        entityData.set(DATA_DEFINITION_ID, safeDefinitionId);
        entityData.set(DATA_INSTANCE_ID, safeInstanceId);
        entityData.set(DATA_APPEARANCE_ID, safeAppearance.id());
        getPersistentData().putString(TAG_DEFINITION_ID, safeDefinitionId);
        getPersistentData().putString(TAG_INSTANCE_ID, safeInstanceId);
        getPersistentData().putString(TAG_APPEARANCE_ID, safeAppearance.id());
    }

    public String definitionIdRaw() {
        String raw = entityData.get(DATA_DEFINITION_ID);
        if (raw == null || raw.isBlank()) {
            raw = getPersistentData().getString(TAG_DEFINITION_ID);
        }
        return raw == null ? "" : raw;
    }

    public ResourceLocation definitionId() {
        String raw = definitionIdRaw();
        return raw.isBlank() ? null : ResourceLocation.tryParse(raw);
    }

    public String instanceId() {
        String raw = entityData.get(DATA_INSTANCE_ID);
        if (raw == null || raw.isBlank()) {
            raw = getPersistentData().getString(TAG_INSTANCE_ID);
        }
        return raw == null ? "" : raw;
    }

    public NpcAppearance appearance() {
        String raw = entityData.get(DATA_APPEARANCE_ID);
        if (raw == null || raw.isBlank()) {
            raw = getPersistentData().getString(TAG_APPEARANCE_ID);
        }
        return NpcAppearance.fromId(raw);
    }

    public boolean canBeInteractedBy(Player player) {
        return player != null && player.isAlive() && isAlive();
    }
}
