package dev.laakirun.veyloria.server.profile;

import com.google.gson.JsonObject;
import dev.laakirun.veyloria.common.model.BaseStats;
import dev.laakirun.veyloria.common.model.CharacterProfile;
import dev.laakirun.veyloria.common.util.JsonUtils;
import dev.laakirun.veyloria.server.auth.AccountRecord;
import dev.laakirun.veyloria.server.db.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CharacterService {
    private final DatabaseManager databaseManager;
    private final Map<UUID, CharacterProfile> loadedProfiles = new ConcurrentHashMap<>();

    public CharacterService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public CharacterProfile loadOrCreate(AccountRecord account) {
        try (Connection connection = databaseManager.connection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT c.*, a.minecraft_uuid, a.nickname
                 FROM characters c
                 JOIN accounts a ON a.id = c.account_id
                 WHERE c.account_id = ?
                 """)) {
            statement.setLong(1, account.id());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    CharacterProfile profile = new CharacterProfile(
                        resultSet.getLong("account_id"),
                        UUID.fromString(resultSet.getString("minecraft_uuid")),
                        resultSet.getString("nickname"),
                        resultSet.getInt("level"),
                        resultSet.getInt("xp_current"),
                        resultSet.getInt("xp_total"),
                        resultSet.getInt("currency_copper"),
                        BaseStats.fromJson(JsonUtils.GSON.fromJson(resultSet.getString("base_stats_json"), JsonObject.class))
                    );
                    loadedProfiles.put(profile.minecraftUuid(), profile);
                    return profile;
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load profile", exception);
        }

        CharacterProfile profile = new CharacterProfile(account.id(), account.minecraftUuid(), account.nickname(), 1, 0, 0, 0, new BaseStats(2, 2, 1, 0, 0));
        saveNew(profile);
        loadedProfiles.put(profile.minecraftUuid(), profile);
        return profile;
    }

    private void saveNew(CharacterProfile profile) {
        try (Connection connection = databaseManager.connection();
             PreparedStatement statement = connection.prepareStatement("""
                 INSERT INTO characters(account_id, level, xp_current, xp_total, currency_copper, base_stats_json, last_known_position, created_at, updated_at)
                 VALUES(?, ?, ?, ?, ?, ?, NULL, ?, ?)
                 """)) {
            statement.setLong(1, profile.accountId());
            statement.setInt(2, profile.level());
            statement.setInt(3, profile.xpCurrent());
            statement.setInt(4, profile.xpTotal());
            statement.setInt(5, profile.currencyCopper());
            statement.setString(6, profile.baseStats().toJson().toString());
            statement.setString(7, Instant.now().toString());
            statement.setString(8, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to create profile", exception);
        }
    }

    public CharacterProfile loadedProfile(UUID uuid) {
        return loadedProfiles.get(uuid);
    }

    public void save(CharacterProfile profile) {
        try (Connection connection = databaseManager.connection();
             PreparedStatement statement = connection.prepareStatement("""
                 UPDATE characters
                 SET level = ?, xp_current = ?, xp_total = ?, currency_copper = ?, base_stats_json = ?, updated_at = ?
                 WHERE account_id = ?
                 """)) {
            statement.setInt(1, profile.level());
            statement.setInt(2, profile.xpCurrent());
            statement.setInt(3, profile.xpTotal());
            statement.setInt(4, profile.currencyCopper());
            statement.setString(5, profile.baseStats().toJson().toString());
            statement.setString(6, Instant.now().toString());
            statement.setLong(7, profile.accountId());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save profile", exception);
        }
    }

    public void unload(UUID uuid) {
        CharacterProfile profile = loadedProfiles.remove(uuid);
        if (profile != null) {
            save(profile);
        }
    }
}
