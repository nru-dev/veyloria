package dev.laakirun.veyloria.common.entity;

import dev.laakirun.veyloria.common.targeting.TargetingProfile;
import dev.laakirun.veyloria.common.targeting.TargetingService;
import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class HomingArrowEntity extends Arrow {
    private static final EntityDataAccessor<Optional<UUID>> DATA_TARGET_UUID =
        SynchedEntityData.defineId(HomingArrowEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final String TAG_TARGET_UUID = "targetUuid";
    private static final String TAG_LAST_SEEN_TICK = "lastSeenTick";
    private static final String TAG_LAST_KNOWN_POS_X = "lastKnownPosX";
    private static final String TAG_LAST_KNOWN_POS_Y = "lastKnownPosY";
    private static final String TAG_LAST_KNOWN_POS_Z = "lastKnownPosZ";
    private static final String TAG_HOMING_ACTIVE = "homingActive";

    private Vec3 lastKnownPos;
    private int lastSeenTick = Integer.MIN_VALUE;
    private boolean homingActive = true;

    public HomingArrowEntity(EntityType<? extends HomingArrowEntity> entityType, Level level) {
        super(entityType, level);
    }

    public UUID targetUuid() {
        return entityData.get(DATA_TARGET_UUID).orElse(null);
    }

    public void setTargetUuid(UUID targetUuid) {
        entityData.set(DATA_TARGET_UUID, Optional.ofNullable(targetUuid));
        if (targetUuid == null) {
            homingActive = false;
            lastKnownPos = null;
            lastSeenTick = Integer.MIN_VALUE;
        } else {
            homingActive = true;
        }
    }

    public void setTarget(LivingEntity target) {
        if (target == null) {
            setTargetUuid(null);
            return;
        }
        setTargetUuid(target.getUUID());
        lastKnownPos = TargetingService.defaultTargetPoint(target);
        lastSeenTick = tickCount;
        homingActive = true;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public void tick() {
        if (inGround || onGround()) {
            setNoGravity(false);
            super.tick();
            homingActive = false;
            return;
        }
        setNoGravity(true);
        super.tick();
        if (inGround || onGround()) {
            setNoGravity(false);
            homingActive = false;
            return;
        }
        if (level().isClientSide() || !homingActive || isRemoved()) {
            return;
        }
        TargetingProfile profile = resolveProfile();
        LivingEntity target = resolveTarget();
        if (target == null) {
            homingActive = false;
            return;
        }

        TargetingService targetingService = resolveTargetingService();
        Vec3 targetPoint = TargetingService.defaultTargetPoint(target);
        boolean hasLos = targetingService.hasLineOfSight(this, target, targetPoint);
        if (hasLos) {
            lastKnownPos = targetPoint;
            lastSeenTick = tickCount;
            steerTowards(targetPoint, profile.clampedTurnRate());
            return;
        }

        int memoryTicks = profile.clampedMemoryTicks();
        if (lastKnownPos != null && memoryTicks > 0 && tickCount - lastSeenTick <= memoryTicks) {
            steerTowards(lastKnownPos, profile.clampedTurnRate());
            return;
        }
        homingActive = false;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) {
            return false;
        }
        TargetingProfile profile = resolveProfile();
        if (!profile.targetOnlyHit() || !(target instanceof LivingEntity)) {
            return true;
        }
        UUID expectedTarget = targetUuid();
        return expectedTarget == null || expectedTarget.equals(target.getUUID());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TARGET_UUID, Optional.empty());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID target = targetUuid();
        if (target != null) {
            tag.putUUID(TAG_TARGET_UUID, target);
        }
        tag.putInt(TAG_LAST_SEEN_TICK, lastSeenTick);
        if (lastKnownPos != null) {
            tag.putDouble(TAG_LAST_KNOWN_POS_X, lastKnownPos.x);
            tag.putDouble(TAG_LAST_KNOWN_POS_Y, lastKnownPos.y);
            tag.putDouble(TAG_LAST_KNOWN_POS_Z, lastKnownPos.z);
        }
        tag.putBoolean(TAG_HOMING_ACTIVE, homingActive);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTargetUuid(tag.hasUUID(TAG_TARGET_UUID) ? tag.getUUID(TAG_TARGET_UUID) : null);
        lastSeenTick = tag.getInt(TAG_LAST_SEEN_TICK);
        if (tag.contains(TAG_LAST_KNOWN_POS_X) && tag.contains(TAG_LAST_KNOWN_POS_Y) && tag.contains(TAG_LAST_KNOWN_POS_Z)) {
            lastKnownPos = new Vec3(
                tag.getDouble(TAG_LAST_KNOWN_POS_X),
                tag.getDouble(TAG_LAST_KNOWN_POS_Y),
                tag.getDouble(TAG_LAST_KNOWN_POS_Z)
            );
        } else {
            lastKnownPos = null;
        }
        homingActive = tag.getBoolean(TAG_HOMING_ACTIVE);
    }

    private LivingEntity resolveTarget() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        UUID target = targetUuid();
        if (target == null) {
            return null;
        }
        Entity entity = serverLevel.getEntity(target);
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) {
            return null;
        }
        return living;
    }

    private void steerTowards(Vec3 desiredPoint, double turnRate) {
        Vec3 velocity = getDeltaMovement();
        double speed = velocity.length();
        if (speed <= 0.0001D) {
            return;
        }

        Vec3 desiredDirection = desiredPoint.subtract(position());
        if (desiredDirection.lengthSqr() <= 0.0001D) {
            return;
        }

        Vec3 currentDir = velocity.normalize();
        Vec3 targetDir = desiredDirection.normalize();
        double t = Math.max(0.0D, Math.min(1.0D, turnRate));
        Vec3 blended = currentDir.scale(1.0D - t).add(targetDir.scale(t));
        if (blended.lengthSqr() <= 0.0001D) {
            return;
        }
        Vec3 updated = blended.normalize().scale(speed);
        setDeltaMovement(updated);
        hasImpulse = true;
    }

    private TargetingProfile resolveProfile() {
        VeyloriaServerRuntime runtime = VeyloriaServerRuntime.instance();
        TargetingProfile profile = runtime.targetingProfile();
        return profile == null ? TargetingProfile.defaults() : profile;
    }

    private TargetingService resolveTargetingService() {
        VeyloriaServerRuntime runtime = VeyloriaServerRuntime.instance();
        TargetingService service = runtime.targetingService();
        return service == null ? new TargetingService() : service;
    }
}
