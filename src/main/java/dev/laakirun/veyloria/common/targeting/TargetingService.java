package dev.laakirun.veyloria.common.targeting;

import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class TargetingService {
    private static final double LOOK_HITBOX_INFLATE = 0.05D;
    private static final double CURSOR_ANGLE_WEIGHT = 0.90D;
    private static final double CURSOR_DISTANCE_WEIGHT = 0.10D;
    private static final double STICKY_SCORE_MULTIPLIER = 0.85D;
    private static final double MIN_DISTANCE_SQR = 0.0001D;

    public LivingEntity findBestTarget(Player player, TargetingProfile profile) {
        return findBestTarget(player, profile, null, gameTick(player));
    }

    public LivingEntity findBestTarget(Player player, TargetingProfile profile, PlayerTargetState currentState, long gameTick) {
        if (player == null || profile == null || player.level() == null) {
            return null;
        }

        double range = profile.clampedRangeBlocks();
        double maxDistanceSqr = range * range;
        double minDot = profile.fovThresholdDot();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        if (look.lengthSqr() <= MIN_DISTANCE_SQR) {
            look = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
        }
        Vec3 lookNorm = look.normalize();
        Vec3 lookEnd = eye.add(lookNorm.scale(range));
        AABB scanBox = player.getBoundingBox().inflate(range);

        LivingEntity lookedTarget = null;
        double bestLookHitDistanceSqr = Double.MAX_VALUE;
        LivingEntity bestCursorTarget = null;
        double bestCursorScore = Double.MAX_VALUE;
        UUID stickyTarget = currentState == null ? null : currentState.currentTargetUuid();
        boolean stickyFresh = currentState != null && currentState.isStickyFresh(gameTick, profile.clampedStickyTicks());
        for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class, scanBox)) {
            if (!isLockCandidate(player, candidate, profile)) {
                continue;
            }

            Vec3 targetPoint = defaultTargetPoint(candidate);
            Vec3 toTarget = targetPoint.subtract(eye);
            double distanceSqr = toTarget.lengthSqr();
            if (distanceSqr <= MIN_DISTANCE_SQR || distanceSqr > maxDistanceSqr) {
                continue;
            }

            double distance = Math.sqrt(distanceSqr);
            Vec3 direction = toTarget.scale(1.0D / distance);
            double dot = lookNorm.dot(direction);
            if (dot < minDot) {
                continue;
            }
            if (profile.requireLosForLock() && !hasLineOfSight(player, candidate, targetPoint)) {
                continue;
            }

            var lookHit = candidate.getBoundingBox().inflate(LOOK_HITBOX_INFLATE).clip(eye, lookEnd);
            if (lookHit.isPresent()) {
                double hitDistanceSqr = eye.distanceToSqr(lookHit.get());
                if (hitDistanceSqr < bestLookHitDistanceSqr) {
                    bestLookHitDistanceSqr = hitDistanceSqr;
                    lookedTarget = candidate;
                }
            }

            double angleScore = 1.0D - dot;
            double distanceScore = distance / range;
            double cursorScore = CURSOR_ANGLE_WEIGHT * angleScore + CURSOR_DISTANCE_WEIGHT * distanceScore;
            if (stickyFresh && stickyTarget != null && stickyTarget.equals(candidate.getUUID())) {
                cursorScore *= STICKY_SCORE_MULTIPLIER;
            }
            if (cursorScore < bestCursorScore) {
                bestCursorScore = cursorScore;
                bestCursorTarget = candidate;
            }
        }

        return lookedTarget != null ? lookedTarget : bestCursorTarget;
    }

    public boolean isLockCandidate(Player player, LivingEntity candidate, TargetingProfile profile) {
        if (player == null || candidate == null || profile == null) {
            return false;
        }
        if (!candidate.isAlive() || candidate.isRemoved() || candidate.getUUID().equals(player.getUUID())) {
            return false;
        }
        if (candidate.level() != player.level()) {
            return false;
        }
        if (candidate.isInvisible()) {
            return false;
        }
        if (candidate instanceof Player otherPlayer && otherPlayer.isSpectator()) {
            return false;
        }
        return profile.effectiveTargetFilter().test(candidate);
    }

    public boolean hasLineOfSight(Entity fromEntity, Entity toEntity, Vec3 targetPoint) {
        if (fromEntity == null || toEntity == null || fromEntity.level() == null || fromEntity.level() != toEntity.level()) {
            return false;
        }
        Vec3 from = eyeOrCenter(fromEntity);
        Vec3 to = targetPoint == null ? defaultTargetPoint(toEntity) : targetPoint;
        if (from.distanceToSqr(to) <= MIN_DISTANCE_SQR) {
            return true;
        }
        BlockHitResult hitResult = fromEntity.level().clip(
            new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, fromEntity)
        );
        if (hitResult.getType() == HitResult.Type.MISS) {
            return true;
        }
        return hitResult.getLocation().distanceToSqr(to) <= 0.09D;
    }

    public static Vec3 defaultTargetPoint(Entity target) {
        if (target == null) {
            return Vec3.ZERO;
        }
        return target.position().add(0.0D, target.getBbHeight() * 0.65D, 0.0D);
    }

    public static long gameTick(Entity entity) {
        if (entity == null || entity.level() == null) {
            return 0L;
        }
        return entity.level().getGameTime();
    }

    private static Vec3 eyeOrCenter(Entity entity) {
        if (entity instanceof LivingEntity livingEntity) {
            return livingEntity.getEyePosition();
        }
        return entity.position();
    }
}
