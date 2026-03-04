package dev.laakirun.veyloria.server.content;

import java.util.List;

public record LootTableDefinition(long id, String name, int dropSlots, List<LootEntryDefinition> entries) {
}
