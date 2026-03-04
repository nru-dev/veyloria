package dev.laakirun.veyloria.common.config;

public record RatesConfig(
    double xpRate,
    double currencyRate,
    double resourceDropRate,
    double equipmentDropRate,
    double consumableDropRate,
    double bossRespawnRate
) {
    public static RatesConfig defaults() {
        return new RatesConfig(1.0D, 1.0D, 1.0D, 1.0D, 1.0D, 1.0D);
    }
}
