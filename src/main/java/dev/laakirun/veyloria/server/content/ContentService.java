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

    public ContentService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void reload() {
        itemsById.clear();
        itemsByCode.clear();
        lootTablesById.clear();
        mobTemplatesById.clear();
        spawnGroupsById.clear();

        try (Connection connection = databaseManager.connection()) {
            loadItems(connection);
            loadLootTables(connection);
            loadMobTemplates(connection);
            loadSpawnGroups(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load content", exception);
        }
        LOGGER.info("Loaded {} items, {} loot tables, {} mob templates and {} spawn groups",
            itemsById.size(), lootTablesById.size(), mobTemplatesById.size(), spawnGroupsById.size());
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
        return List.copyOf(spawnGroupsById.values());
    }

    public MobSpawnGroup spawnGroup(long id) {
        return spawnGroupsById.get(id);
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
}
