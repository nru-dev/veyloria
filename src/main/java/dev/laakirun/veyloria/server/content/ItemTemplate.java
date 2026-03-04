package dev.laakirun.veyloria.server.content;

import dev.laakirun.veyloria.common.model.BaseStats;
import dev.laakirun.veyloria.common.model.EquipSlot;
import dev.laakirun.veyloria.common.model.ItemCategory;
import dev.laakirun.veyloria.common.model.Rarity;

public record ItemTemplate(
    long id,
    String code,
    String name,
    ItemCategory category,
    String vanillaIconItem,
    String modelId,
    boolean stackable,
    int maxStack,
    int requiredLevel,
    Rarity rarity,
    EquipSlot equipSlot,
    BaseStats baseStats,
    int vendorValue,
    boolean enabled
) {
}
