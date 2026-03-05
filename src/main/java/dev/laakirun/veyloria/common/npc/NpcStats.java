package dev.laakirun.veyloria.common.npc;

public record NpcStats(double maxHealth, double armor, double knockbackResistance) {
    public NpcStats {
        maxHealth = Math.max(1.0D, maxHealth);
        armor = Math.max(0.0D, armor);
        knockbackResistance = Math.max(0.0D, knockbackResistance);
    }
}
