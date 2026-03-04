package dev.laakirun.veyloria.common.targeting;

import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;

public record TargetingProfile(
    double fovDegrees,
    double rangeBlocks,
    boolean requireLosForLock,
    int memoryTicks,
    double turnRate,
    boolean targetOnlyHit,
    Predicate<LivingEntity> targetFilter,
    int stickyTicks
) {
    public static final double DEFAULT_FOV_DEGREES = 135.0D;
    public static final double DEFAULT_RANGE_BLOCKS = 20.0D;
    public static final int DEFAULT_MEMORY_TICKS = 8;
    public static final double DEFAULT_TURN_RATE = 0.20D;
    public static final boolean DEFAULT_TARGET_ONLY_HIT = true;
    public static final int DEFAULT_STICKY_TICKS = 6;

    private static final Predicate<LivingEntity> LIVING_FILTER = entity -> entity != null;

    public static TargetingProfile defaults() {
        return new TargetingProfile(
            DEFAULT_FOV_DEGREES,
            DEFAULT_RANGE_BLOCKS,
            true,
            DEFAULT_MEMORY_TICKS,
            DEFAULT_TURN_RATE,
            DEFAULT_TARGET_ONLY_HIT,
            LIVING_FILTER,
            DEFAULT_STICKY_TICKS
        );
    }

    public double fovThresholdDot() {
        double clamped = Math.max(1.0D, Math.min(179.0D, fovDegrees));
        return Math.cos(Math.toRadians(clamped * 0.5D));
    }

    public double clampedRangeBlocks() {
        return Math.max(1.0D, rangeBlocks);
    }

    public int clampedMemoryTicks() {
        return Math.max(0, memoryTicks);
    }

    public double clampedTurnRate() {
        return Math.max(0.0D, Math.min(1.0D, turnRate));
    }

    public int clampedStickyTicks() {
        return Math.max(0, stickyTicks);
    }

    public Predicate<LivingEntity> effectiveTargetFilter() {
        return targetFilter == null ? LIVING_FILTER : targetFilter;
    }
}
