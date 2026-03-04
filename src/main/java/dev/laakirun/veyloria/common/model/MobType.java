package dev.laakirun.veyloria.common.model;

public enum MobType {
    NORMAL(3, 1.0D, 1.0D),
    ELITE(9, 1.35D, 3.0D),
    BOSS(36, 1.8D, 12.0D);

    private final int baseXp;
    private final double damageModifier;
    private final double healthModifier;

    MobType(int baseXp, double damageModifier, double healthModifier) {
        this.baseXp = baseXp;
        this.damageModifier = damageModifier;
        this.healthModifier = healthModifier;
    }

    public int baseXp() {
        return baseXp;
    }

    public double damageModifier() {
        return damageModifier;
    }

    public double healthModifier() {
        return healthModifier;
    }

    public static MobType fromId(String id) {
        return valueOf(id.trim().toUpperCase());
    }
}
