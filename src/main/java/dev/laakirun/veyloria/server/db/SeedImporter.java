package dev.laakirun.veyloria.server.db;

import com.google.gson.reflect.TypeToken;
import dev.laakirun.veyloria.common.model.EquipSlot;
import dev.laakirun.veyloria.common.model.HostilityType;
import dev.laakirun.veyloria.common.model.ItemCategory;
import dev.laakirun.veyloria.common.model.MobType;
import dev.laakirun.veyloria.common.model.Rarity;
import dev.laakirun.veyloria.common.util.JsonUtils;
import dev.laakirun.veyloria.server.content.SeedDtos.ItemTemplateSeed;
import dev.laakirun.veyloria.server.content.SeedDtos.LootEntrySeed;
import dev.laakirun.veyloria.server.content.SeedDtos.LootTableSeed;
import dev.laakirun.veyloria.server.content.SeedDtos.MobSpawnGroupSeed;
import dev.laakirun.veyloria.server.content.SeedDtos.MobTemplateSeed;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SeedImporter {
    private static final Logger LOGGER = LoggerFactory.getLogger("veyloria.db");

    private final DatabaseManager databaseManager;

    public SeedImporter(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void importSeeds() {
        try (Connection connection = databaseManager.connection()) {
            connection.setAutoCommit(false);
            upsertItemTemplates(connection, readList("data/veyloria/seeds/item_templates.json", new TypeToken<List<ItemTemplateSeed>>() {
            }));
            upsertLootTables(connection, readList("data/veyloria/seeds/loot_tables.json", new TypeToken<List<LootTableSeed>>() {
            }));
            upsertLootEntries(connection, readList("data/veyloria/seeds/loot_entries.json", new TypeToken<List<LootEntrySeed>>() {
            }));
            upsertMobTemplates(connection, readList("data/veyloria/seeds/mob_templates.json", new TypeToken<List<MobTemplateSeed>>() {
            }));
            replaceSpawnGroups(connection, readList("data/veyloria/seeds/mob_spawn_groups.json", new TypeToken<List<MobSpawnGroupSeed>>() {
            }));
            connection.commit();
            LOGGER.info("Seed data imported");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to import seed data", exception);
        }
    }

    private void upsertItemTemplates(Connection connection, List<ItemTemplateSeed> seeds) throws SQLException {
        String sql = """
            INSERT INTO item_templates(code, name, item_category, vanilla_icon_item, model_id, stackable, max_stack, required_level, rarity, equip_slot, base_stats_json, vendor_value, enabled)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(code) DO UPDATE SET
                name=excluded.name,
                item_category=excluded.item_category,
                vanilla_icon_item=excluded.vanilla_icon_item,
                model_id=excluded.model_id,
                stackable=excluded.stackable,
                max_stack=excluded.max_stack,
                required_level=excluded.required_level,
                rarity=excluded.rarity,
                equip_slot=excluded.equip_slot,
                base_stats_json=excluded.base_stats_json,
                vendor_value=excluded.vendor_value,
                enabled=excluded.enabled
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ItemTemplateSeed seed : seeds) {
                statement.setString(1, seed.code);
                statement.setString(2, seed.name);
                statement.setString(3, ItemCategory.fromId(seed.item_category).name());
                statement.setString(4, seed.vanilla_icon_item);
                statement.setString(5, seed.model_id);
                statement.setInt(6, seed.stackable ? 1 : 0);
                statement.setInt(7, seed.max_stack);
                statement.setInt(8, seed.required_level);
                statement.setString(9, Rarity.fromId(seed.rarity).name());
                statement.setString(10, seed.equip_slot == null ? null : EquipSlot.fromId(seed.equip_slot).name());
                statement.setString(11, seed.base_stats_json == null ? "{}" : seed.base_stats_json.toString());
                statement.setInt(12, seed.vendor_value);
                statement.setInt(13, seed.enabled ? 1 : 0);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void upsertLootTables(Connection connection, List<LootTableSeed> seeds) throws SQLException {
        String sql = """
            INSERT INTO loot_tables(name, drop_slots)
            VALUES(?, ?)
            ON CONFLICT(name) DO UPDATE SET drop_slots=excluded.drop_slots
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (LootTableSeed seed : seeds) {
                statement.setString(1, seed.name);
                statement.setInt(2, seed.drop_slots);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void upsertLootEntries(Connection connection, List<LootEntrySeed> seeds) throws SQLException {
        String sql = """
            INSERT INTO loot_entries(loot_table_id, item_template_id, drop_weight, min_quantity, max_quantity, is_guaranteed, enabled)
            VALUES(?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(loot_table_id, item_template_id) DO UPDATE SET
                drop_weight=excluded.drop_weight,
                min_quantity=excluded.min_quantity,
                max_quantity=excluded.max_quantity,
                is_guaranteed=excluded.is_guaranteed,
                enabled=excluded.enabled
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (LootEntrySeed seed : seeds) {
                statement.setLong(1, findId(connection, "loot_tables", "name", seed.loot_table));
                statement.setLong(2, findId(connection, "item_templates", "code", seed.item_template));
                statement.setDouble(3, seed.drop_weight);
                statement.setInt(4, seed.min_quantity);
                statement.setInt(5, seed.max_quantity);
                statement.setInt(6, seed.is_guaranteed ? 1 : 0);
                statement.setInt(7, seed.enabled ? 1 : 0);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void upsertMobTemplates(Connection connection, List<MobTemplateSeed> seeds) throws SQLException {
        String sql = """
            INSERT INTO mob_templates(code, name, mob_type, level, entity_model, hostility_type, base_damage, base_hp, move_speed, attack_speed, aggro_radius, leash_radius, loot_table_id, currency_min, currency_max, xp_override, enabled)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(code) DO UPDATE SET
                name=excluded.name,
                mob_type=excluded.mob_type,
                level=excluded.level,
                entity_model=excluded.entity_model,
                hostility_type=excluded.hostility_type,
                base_damage=excluded.base_damage,
                base_hp=excluded.base_hp,
                move_speed=excluded.move_speed,
                attack_speed=excluded.attack_speed,
                aggro_radius=excluded.aggro_radius,
                leash_radius=excluded.leash_radius,
                loot_table_id=excluded.loot_table_id,
                currency_min=excluded.currency_min,
                currency_max=excluded.currency_max,
                xp_override=excluded.xp_override,
                enabled=excluded.enabled
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (MobTemplateSeed seed : seeds) {
                statement.setString(1, seed.code);
                statement.setString(2, seed.name);
                statement.setString(3, MobType.fromId(seed.mob_type).name());
                statement.setInt(4, seed.level);
                statement.setString(5, seed.entity_model);
                statement.setString(6, HostilityType.fromId(seed.hostility_type).name());
                statement.setDouble(7, seed.base_damage);
                statement.setDouble(8, seed.base_hp);
                statement.setDouble(9, seed.move_speed);
                statement.setDouble(10, seed.attack_speed);
                statement.setDouble(11, seed.aggro_radius);
                statement.setDouble(12, seed.leash_radius);
                if (seed.loot_table == null) {
                    statement.setObject(13, null);
                } else {
                    statement.setLong(13, findId(connection, "loot_tables", "name", seed.loot_table));
                }
                statement.setInt(14, seed.currency_min);
                statement.setInt(15, seed.currency_max);
                if (seed.xp_override == null) {
                    statement.setObject(16, null);
                } else {
                    statement.setInt(16, seed.xp_override);
                }
                statement.setInt(17, seed.enabled ? 1 : 0);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void replaceSpawnGroups(Connection connection, List<MobSpawnGroupSeed> seeds) throws SQLException {
        connection.createStatement().execute("DELETE FROM mob_spawn_groups");
        String sql = """
            INSERT INTO mob_spawn_groups(mob_template_id, dimension, center_x, center_y, center_z, radius_x, radius_z, min_alive, max_alive, respawn_seconds, pack_size_min, pack_size_max, pack_spread_min, pack_spread_max, enabled)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (MobSpawnGroupSeed seed : seeds) {
                statement.setLong(1, findId(connection, "mob_templates", "code", seed.mob_template));
                statement.setString(2, seed.dimension);
                statement.setDouble(3, seed.center_x);
                statement.setDouble(4, seed.center_y);
                statement.setDouble(5, seed.center_z);
                statement.setDouble(6, seed.radius_x);
                statement.setDouble(7, seed.radius_z);
                statement.setInt(8, seed.min_alive);
                statement.setInt(9, seed.max_alive);
                statement.setInt(10, seed.respawn_seconds);
                statement.setInt(11, seed.pack_size_min);
                statement.setInt(12, seed.pack_size_max);
                statement.setDouble(13, seed.pack_spread_min);
                statement.setDouble(14, seed.pack_spread_max);
                statement.setInt(15, seed.enabled ? 1 : 0);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private long findId(Connection connection, String table, String column, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM " + table + " WHERE " + column + " = ?")) {
            statement.setString(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
            }
        }
        throw new IllegalStateException("Missing referenced seed value " + table + "." + column + "=" + value);
    }

    private static <T> T readList(String resourcePath, TypeToken<T> type) {
        try (InputStream stream = SeedImporter.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing seed resource " + resourcePath);
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonUtils.GSON.fromJson(reader, type.getType());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read seed resource " + resourcePath, exception);
        }
    }
}
