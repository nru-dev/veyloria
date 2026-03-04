package dev.laakirun.veyloria.common.model;

public enum HostilityType {
    FRIENDLY,
    NEUTRAL,
    HOSTILE;

    public static HostilityType fromId(String id) {
        return valueOf(id.trim().toUpperCase());
    }
}
