package dev.laakirun.veyloria.common.model;

public enum EquipSlot {
    WEAPON,
    HELMET,
    CHEST,
    LEGS,
    BOOTS;

    public static EquipSlot fromId(String id) {
        return id == null ? null : valueOf(id.trim().toUpperCase());
    }
}
