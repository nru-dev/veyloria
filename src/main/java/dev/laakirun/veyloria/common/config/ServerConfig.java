package dev.laakirun.veyloria.common.config;

import java.util.LinkedHashMap;
import java.util.Map;

public record ServerConfig(
    String databasePath,
    String seedPath,
    String migrationPath,
    int spawnTickInterval,
    int spawnActivationRadius,
    int authLockTickInterval,
    int maxActiveMobsPerDimension,
    Map<Integer, Double> zoneDensityMultipliers,
    Map<Integer, Double> zonePackMultipliers,
    CombatConfig combat
) {
    public ServerConfig {
        zoneDensityMultipliers = normalizeZoneMultipliers(zoneDensityMultipliers, defaultZoneDensityMultipliers());
        zonePackMultipliers = normalizeZoneMultipliers(zonePackMultipliers, defaultZonePackMultipliers());
    }

    public static ServerConfig defaults() {
        return new ServerConfig(
            "data/veyloria/rpg.db",
            "data/veyloria/seeds",
            "data/veyloria/migrations",
            20,
            224,
            1,
            520,
            defaultZoneDensityMultipliers(),
            defaultZonePackMultipliers(),
            CombatConfig.defaults()
        );
    }

    public double zoneDensityMultiplier(int zoneIndex) {
        return zoneDensityMultipliers.getOrDefault(zoneIndex, 1.0D);
    }

    public double zonePackMultiplier(int zoneIndex) {
        return zonePackMultipliers.getOrDefault(zoneIndex, 1.0D);
    }

    private static Map<Integer, Double> defaultZoneDensityMultipliers() {
        Map<Integer, Double> defaults = new LinkedHashMap<>();
        defaults.put(1, 1.20D);
        defaults.put(2, 1.30D);
        defaults.put(3, 1.40D);
        defaults.put(4, 1.55D);
        defaults.put(5, 1.70D);
        defaults.put(6, 1.85D);
        defaults.put(7, 2.00D);
        return defaults;
    }

    private static Map<Integer, Double> defaultZonePackMultipliers() {
        Map<Integer, Double> defaults = new LinkedHashMap<>();
        defaults.put(1, 1.10D);
        defaults.put(2, 1.15D);
        defaults.put(3, 1.20D);
        defaults.put(4, 1.28D);
        defaults.put(5, 1.35D);
        defaults.put(6, 1.42D);
        defaults.put(7, 1.50D);
        return defaults;
    }

    private static Map<Integer, Double> normalizeZoneMultipliers(Map<Integer, Double> raw, Map<Integer, Double> fallback) {
        Map<Integer, Double> normalized = new LinkedHashMap<>(fallback);
        if (raw != null && !raw.isEmpty()) {
            for (Map.Entry<Integer, Double> entry : raw.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0.0D) {
                    continue;
                }
                normalized.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(normalized);
    }
}
