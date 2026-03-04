package dev.laakirun.veyloria.common.model;

public enum Rarity {
    COMMON(1.0D),
    RARE(1.2D),
    EPIC(1.5D),
    LEGENDARY(1.9D);

    private final double multiplier;

    Rarity(double multiplier) {
        this.multiplier = multiplier;
    }

    public double multiplier() {
        return multiplier;
    }

    public static Rarity fromId(String id) {
        return valueOf(id.trim().toUpperCase());
    }
}
