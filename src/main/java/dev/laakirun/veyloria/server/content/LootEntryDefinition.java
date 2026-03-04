package dev.laakirun.veyloria.server.content;

public record LootEntryDefinition(
    long id,
    long lootTableId,
    long itemTemplateId,
    double dropWeight,
    int minQuantity,
    int maxQuantity,
    boolean guaranteed,
    boolean enabled
) {
}
