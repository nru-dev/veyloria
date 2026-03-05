package dev.laakirun.veyloria.server.content;

import com.google.gson.JsonObject;
import dev.laakirun.veyloria.common.model.BaseStats;
import dev.laakirun.veyloria.common.model.EquipSlot;
import dev.laakirun.veyloria.common.model.HostilityType;
import dev.laakirun.veyloria.common.model.ItemCategory;
import dev.laakirun.veyloria.common.model.MobType;
import dev.laakirun.veyloria.common.model.Rarity;
import dev.laakirun.veyloria.common.util.JsonUtils;
import dev.laakirun.veyloria.server.db.DatabaseManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ContentService {
    private static final Logger LOGGER = LoggerFactory.getLogger("veyloria.db");

    private final DatabaseManager databaseManager;
    private final Map<Long, ItemTemplate> itemsById = new LinkedHashMap<>();
    private final Map<String, ItemTemplate> itemsByCode = new LinkedHashMap<>();
    private final Map<Long, LootTableDefinition> lootTablesById = new LinkedHashMap<>();
    private final Map<Long, MobTemplate> mobTemplatesById = new LinkedHashMap<>();
    private final Map<Long, MobSpawnGroup> spawnGroupsById = new LinkedHashMap<>();
    private final Map<Long, StructureTemplate> structureTemplatesById = new LinkedHashMap<>();
    private final Map<String, StructureTemplate> structureTemplatesByCode = new LinkedHashMap<>();
    private final Map<Long, StructureSpawnRule> structureSpawnRulesById = new LinkedHashMap<>();
    private List<MobSpawnGroup> spawnGroupsSnapshot = List.of();
    private List<StructureTemplate> structureTemplatesSnapshot = List.of();
    private List<StructureSpawnRule> structureSpawnRulesSnapshot = List.of();

    public ContentService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void reload() {
        itemsById.clear();
        itemsByCode.clear();
        lootTablesById.clear();
        mobTemplatesById.clear();
        spawnGroupsById.clear();
        structureTemplatesById.clear();
        structureTemplatesByCode.clear();
        structureSpawnRulesById.clear();
        spawnGroupsSnapshot = List.of();
        structureTemplatesSnapshot = List.of();
        structureSpawnRulesSnapshot = List.of();

        try (Connection connection = databaseManager.connection()) {
            loadItems(connection);
            loadLootTables(connection);
            loadMobTemplates(connection);
            loadSpawnGroups(connection);
            loadStructureTemplates(connection);
            loadStructureSpawnRules(connection);
            refreshSnapshots();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load content", exception);
        }
        LOGGER.info("Loaded {} items, {} loot tables, {} mob templates, {} spawn groups, {} structure templates and {} structure rules",
            itemsById.size(),
            lootTablesById.size(),
            mobTemplatesById.size(),
            spawnGroupsById.size(),
            structureTemplatesById.size(),
            structureSpawnRulesById.size());
    }

    public ItemTemplate itemByCode(String code) {
        return itemsByCode.get(code);
    }

    public ItemTemplate itemById(long id) {
        return itemsById.get(id);
    }

    public LootTableDefinition lootTable(long id) {
        return lootTablesById.get(id);
    }

    public MobTemplate mobTemplate(long id) {
        return mobTemplatesById.get(id);
    }

    public List<MobSpawnGroup> spawnGroups() {
        return spawnGroupsSnapshot;
    }

    public MobSpawnGroup spawnGroup(long id) {
        return spawnGroupsById.get(id);
    }

    public StructureTemplate structureTemplate(long id) {
        return structureTemplatesById.get(id);
    }

    public StructureTemplate structureTemplate(String code) {
        return structureTemplatesByCode.get(code);
    }

    public List<StructureTemplate> structureTemplates() {
        return structureTemplatesSnapshot;
    }

    public StructureSpawnRule structureSpawnRule(long id) {
        return structureSpawnRulesById.get(id);
    }

    public List<StructureSpawnRule> structureSpawnRules() {
        return structureSpawnRulesSnapshot;
    }

    private void refreshSnapshots() {
        spawnGroupsSnapshot = List.copyOf(spawnGroupsById.values());
        structureTemplatesSnapshot = List.copyOf(structureTemplatesById.values());
        structureSpawnRulesSnapshot = List.copyOf(structureSpawnRulesById.values());
    }

    private void loadItems(Connection connection) throws SQLException {
        try (ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM item_templates WHERE enabled = 1")) {
            while (resultSet.next()) {
                JsonObject statsJson = JsonUtils.GSON.fromJson(resultSet.getString("base_stats_json"), JsonObject.class);
                ItemTemplate itemTemplate = new ItemTemplate(
                    resultSet.getLong("id"),
                    resultSet.getString("code"),
                    resultSet.getString("name"),
                    ItemCategory.valueOf(resultSet.getString("item_category")),
                    resultSet.getString("vanilla_icon_item"),
                    resultSet.getString("model_id"),
                    resultSet.getInt("stackable") == 1,
                    resultSet.getInt("max_stack"),
                    resultSet.getInt("required_level"),
                    Rarity.valueOf(resultSet.getString("rarity")),
                    resultSet.getString("equip_slot") == null ? null : EquipSlot.valueOf(resultSet.getString("equip_slot")),
                    BaseStats.fromJson(statsJson),
                    resultSet.getInt("vendor_value"),
                    true
                );
                itemsById.put(itemTemplate.id(), itemTemplate);
                itemsByCode.put(itemTemplate.code(), itemTemplate);
            }
        }
    }

    private void loadLootTables(Connection connection) throws SQLException {
        Map<Long, List<LootEntryDefinition>> groupedEntries = new LinkedHashMap<>();
        try (ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM loot_entries WHERE enabled = 1")) {
            while (resultSet.next()) {
                LootEntryDefinition entry = new LootEntryDefinition(
                    resultSet.getLong("id"),
                    resultSet.getLong("loot_table_id"),
                    resultSet.getLong("item_template_id"),
                    resultSet.getDouble("drop_weight"),
                    resultSet.getInt("min_quantity"),
                    resultSet.getInt("max_quantity"),
                    resultSet.getInt("is_guaranteed") == 1,
                    true
                );
                groupedEntries.computeIfAbsent(entry.lootTableId(), ignored -> new ArrayList<>()).add(entry);
            }
        }
        try (ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM loot_tables")) {
            while (resultSet.next()) {
                long id = resultSet.getLong("id");
                lootTablesById.put(id, new LootTableDefinition(
                    id,
                    resultSet.getString("name"),
                    resultSet.getInt("drop_slots"),
                    List.copyOf(groupedEntries.getOrDefault(id, List.of()))
                ));
            }
        }
    }

    private void loadMobTemplates(Connection connection) throws SQLException {
        try (ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM mob_templates WHERE enabled = 1")) {
            while (resultSet.next()) {
                MobTemplate mobTemplate = new MobTemplate(
                    resultSet.getLong("id"),
                    resultSet.getString("code"),
                    resultSet.getString("name"),
                    MobType.valueOf(resultSet.getString("mob_type")),
                    resultSet.getInt("level"),
                    resultSet.getString("entity_model"),
                    HostilityType.valueOf(resultSet.getString("hostility_type")),
                    resultSet.getDouble("base_damage"),
                    resultSet.getDouble("base_hp"),
                    resultSet.getDouble("move_speed"),
                    resultSet.getDouble("attack_speed"),
                    resultSet.getDouble("aggro_radius"),
                    resultSet.getDouble("leash_radius"),
                    resultSet.getObject("loot_table_id") == null ? null : resultSet.getLong("loot_table_id"),
                    resultSet.getInt("currency_min"),
                    resultSet.getInt("currency_max"),
                    resultSet.getObject("xp_override") == null ? null : resultSet.getInt("xp_override"),
                    true
                );
                mobTemplatesById.put(mobTemplate.id(), mobTemplate);
            }
        }
    }

    private void loadSpawnGroups(Connection connection) throws SQLException {
        try (ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM mob_spawn_groups WHERE enabled = 1")) {
            while (resultSet.next()) {
                MobSpawnGroup group = new MobSpawnGroup(
                    resultSet.getLong("id"),
                    resultSet.getLong("mob_template_id"),
                    resultSet.getString("dimension"),
                    resultSet.getDouble("center_x"),
                    resultSet.getDouble("center_y"),
                    resultSet.getDouble("center_z"),
                    resultSet.getDouble("radius_x"),
                    resultSet.getDouble("radius_z"),
                    resultSet.getInt("min_alive"),
                    resultSet.getInt("max_alive"),
                    resultSet.getInt("respawn_seconds"),
                    resultSet.getInt("pack_size_min"),
                    resultSet.getInt("pack_size_max"),
                    resultSet.getDouble("pack_spread_min"),
                    resultSet.getDouble("pack_spread_max"),
                    true
                );
                spawnGroupsById.put(group.id(), group);
            }
        }
    }

    private void loadStructureTemplates(Connection connection) throws SQLException {
        try (ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM structure_templates WHERE enabled = 1")) {
            while (resultSet.next()) {
                StructureTemplate template = new StructureTemplate(
                    resultSet.getLong("id"),
                    resultSet.getString("code"),
                    resultSet.getString("name"),
                    resultSet.getString("structure_type"),
                    resultSet.getString("schematic_file"),
                    resultSet.getInt("size_x"),
                    resultSet.getInt("size_y"),
                    resultSet.getInt("size_z"),
                    true
                );
                structureTemplatesById.put(template.id(), template);
                structureTemplatesByCode.put(template.code(), template);
            }
        }
    }

    private void loadStructureSpawnRules(Connection connection) throws SQLException {
        try (ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM structure_spawn_rules WHERE enabled = 1")) {
            while (resultSet.next()) {
                StructureSpawnRule rule = new StructureSpawnRule(
                    resultSet.getLong("id"),
                    resultSet.getLong("structure_template_id"),
                    resultSet.getString("dimension"),
                    resultSet.getInt("zone_min"),
                    resultSet.getInt("zone_max"),
                    resultSet.getInt("count_min_per_zone"),
                    resultSet.getInt("count_max_per_zone"),
                    resultSet.getDouble("road_distance_min"),
                    resultSet.getDouble("road_distance_max"),
                    resultSet.getDouble("min_distance_between"),
                    resultSet.getString("near_spawn_rules_json"),
                    resultSet.getString("inside_spawn_rules_json"),
                    true
                );
                structureSpawnRulesById.put(rule.id(), rule);
            }
        }
    }
}
