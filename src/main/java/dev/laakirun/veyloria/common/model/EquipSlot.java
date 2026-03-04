package dev.laakirun.veyloria.common.model;

public enum EquipSlot {
    WEAPON,
    AMMO,
    HELMET,
    PENDANT,
    CHEST,
    LEGS,
    BOOTS,
    RING,
    ACCESSORY;

    public static EquipSlot fromId(String id) {
        return id == null ? null : valueOf(id.trim().toUpperCase());
    }
}
