package dev.laakirun.veyloria.common.config;

public record CombatConfig(
    double playerBaseDamage,
    double playerBaseHealth,
    double vitalityHealthBonus,
    double armorDamageReductionFactor,
    double critMultiplier,
    double critBaseChance
) {
    public static CombatConfig defaults() {
        return new CombatConfig(4.0D, 20.0D, 4.0D, 0.035D, 1.5D, 0.02D);
    }
}
