package dev.laakirun.veyloria.common.model;

public enum ItemCategory {
    RESOURCE,
    CONSUMABLE,
    EQUIPMENT,
    CURRENCY_TOKEN,
    MISC;

    public static ItemCategory fromId(String id) {
        return valueOf(id.trim().toUpperCase());
    }
}
