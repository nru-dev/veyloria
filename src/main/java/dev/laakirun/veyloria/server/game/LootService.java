package dev.laakirun.veyloria.server.game;

import dev.laakirun.veyloria.common.config.RatesConfig;
import dev.laakirun.veyloria.common.model.ItemCategory;
import dev.laakirun.veyloria.server.content.ContentService;
import dev.laakirun.veyloria.server.content.ItemTemplate;
import dev.laakirun.veyloria.server.content.LootEntryDefinition;
import dev.laakirun.veyloria.server.content.LootTableDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LootService {
    private static final Logger LOGGER = LoggerFactory.getLogger("veyloria.loot");

    private final ContentService contentService;
    private final Random random = new Random();

    public LootService(ContentService contentService) {
        this.contentService = contentService;
    }

    public List<LootRoll> roll(long lootTableId, RatesConfig ratesConfig) {
        LootTableDefinition table = contentService.lootTable(lootTableId);
        if (table == null) {
            LOGGER.debug("Missing loot table {}, returning empty result", lootTableId);
            return List.of();
        }
        List<LootRoll> result = new ArrayList<>();
        List<LootEntryDefinition> optional = new ArrayList<>();
        for (LootEntryDefinition entry : table.entries()) {
            ItemTemplate template = contentService.itemById(entry.itemTemplateId());
            if (template == null) {
                continue;
            }
            if (entry.guaranteed()) {
                result.add(new LootRoll(template, quantity(entry)));
            } else {
                optional.add(entry);
            }
        }

        int remainingSlots = Math.max(0, table.dropSlots() - result.size());
        List<LootEntryDefinition> pool = new ArrayList<>(optional);
        for (int slot = 0; slot < remainingSlots && !pool.isEmpty(); slot++) {
            LootEntryDefinition selected = weightedPick(pool, ratesConfig);
            pool.remove(selected);
            ItemTemplate template = contentService.itemById(selected.itemTemplateId());
            if (template != null) {
                result.add(new LootRoll(template, quantity(selected)));
            }
        }
        LOGGER.debug("Loot table {} rolled {} entries", lootTableId, result.size());
        return result;
    }

    private LootEntryDefinition weightedPick(List<LootEntryDefinition> pool, RatesConfig ratesConfig) {
        double totalWeight = 0.0D;
        List<EntryWeight> weights = new ArrayList<>(pool.size());
        for (LootEntryDefinition entry : pool) {
            ItemTemplate template = contentService.itemById(entry.itemTemplateId());
            if (template == null) {
                continue;
            }
            double effectiveWeight = entry.dropWeight() * rateFor(template.category(), ratesConfig);
            weights.add(new EntryWeight(entry, effectiveWeight));
            totalWeight += effectiveWeight;
        }
        if (weights.isEmpty() || totalWeight <= 0.0D) {
            return pool.get(random.nextInt(pool.size()));
        }
        double roll = random.nextDouble() * totalWeight;
        double current = 0.0D;
        for (EntryWeight weight : weights) {
            current += weight.weight();
            if (roll <= current) {
                return weight.entry();
            }
        }
        return weights.stream().max(Comparator.comparingDouble(EntryWeight::weight)).orElseThrow().entry();
    }

    private int quantity(LootEntryDefinition entry) {
        if (entry.maxQuantity() <= entry.minQuantity()) {
            return entry.minQuantity();
        }
        return entry.minQuantity() + random.nextInt(entry.maxQuantity() - entry.minQuantity() + 1);
    }

    private double rateFor(ItemCategory category, RatesConfig ratesConfig) {
        return switch (category) {
            case RESOURCE -> ratesConfig.resourceDropRate();
            case EQUIPMENT -> ratesConfig.equipmentDropRate();
            case CONSUMABLE -> ratesConfig.consumableDropRate();
            default -> 1.0D;
        };
    }

    private record EntryWeight(LootEntryDefinition entry, double weight) {
    }
}
