package dev.laakirun.veyloria.server.game;

public final class CommonMobAiSettings {
    public static final double HOME_WANDER_RADIUS = 15.0D;
    public static final double COMBAT_LEASH_RADIUS = 60.0D;
    public static final double RETURN_STOP_RADIUS = 3.0D;
    public static final int CANNOT_REACH_TICKS = 100;
    public static final double GROUP_RADIUS = 12.0D;
    public static final double REGEN_PER_TICK = 1.0D / 40.0D;
    public static final long RECENT_WINDOW_TICKS = 20L * 15L;
    public static final long COMBAT_MEMORY_TICKS = 20L * 8L;
    public static final int TARGET_SCAN_INTERVAL_TICKS = 10;
    public static final long AGGRO_RENEW_THRESHOLD_TICKS = 40L;

    private CommonMobAiSettings() {
    }
}
