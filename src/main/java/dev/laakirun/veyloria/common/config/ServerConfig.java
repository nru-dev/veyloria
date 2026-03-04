package dev.laakirun.veyloria.common.config;

public record ServerConfig(
    String databasePath,
    String seedPath,
    String migrationPath,
    int spawnTickInterval,
    int spawnActivationRadius,
    int authLockTickInterval,
    int maxActiveMobsPerDimension,
    CombatConfig combat
) {
    public static ServerConfig defaults() {
        return new ServerConfig(
            "data/veyloria/rpg.db",
            "data/veyloria/seeds",
            "data/veyloria/migrations",
            20,
            224,
            1,
            520,
            CombatConfig.defaults()
        );
    }
}
