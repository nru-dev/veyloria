package dev.laakirun.veyloria.server.config;

import dev.laakirun.veyloria.common.config.CombatConfig;
import dev.laakirun.veyloria.common.config.RatesConfig;
import dev.laakirun.veyloria.common.config.ServerConfig;
import dev.laakirun.veyloria.common.config.VeyloriaPaths;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public final class ConfigService {
    private static final Logger LOGGER = LoggerFactory.getLogger("veyloria.db");
    private final Yaml yaml;

    public ConfigService() {
        DumperOptions options = new DumperOptions();
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        this.yaml = new Yaml(options);
    }

    public ServerConfig loadServerConfig() {
        Path configPath = VeyloriaPaths.configDir().resolve("server.yml");
        ensureDefault(configPath, "veyloria-defaults/server.yml");
        Map<String, Object> raw = readYaml(configPath);
        Map<String, Object> combat = map(raw.get("combat"));
        ServerConfig config = new ServerConfig(
            string(raw, "database_path", ServerConfig.defaults().databasePath()),
            string(raw, "seed_path", ServerConfig.defaults().seedPath()),
            string(raw, "migration_path", ServerConfig.defaults().migrationPath()),
            integer(raw, "spawn_tick_interval", ServerConfig.defaults().spawnTickInterval()),
            integer(raw, "spawn_activation_radius", ServerConfig.defaults().spawnActivationRadius()),
            integer(raw, "auth_lock_tick_interval", ServerConfig.defaults().authLockTickInterval()),
            integer(raw, "max_active_mobs_per_dimension", ServerConfig.defaults().maxActiveMobsPerDimension()),
            new CombatConfig(
                decimal(combat, "player_base_damage", CombatConfig.defaults().playerBaseDamage()),
                decimal(combat, "player_base_health", CombatConfig.defaults().playerBaseHealth()),
                decimal(combat, "vitality_health_bonus", CombatConfig.defaults().vitalityHealthBonus()),
                decimal(combat, "armor_damage_reduction_factor", CombatConfig.defaults().armorDamageReductionFactor()),
                decimal(combat, "crit_multiplier", CombatConfig.defaults().critMultiplier()),
                decimal(combat, "crit_base_chance", CombatConfig.defaults().critBaseChance())
            )
        );
        LOGGER.info("Loaded server config from {}", configPath);
        return config;
    }

    public RatesConfig loadRatesConfig() {
        Path configPath = VeyloriaPaths.configDir().resolve("rates.yml");
        ensureDefault(configPath, "veyloria-defaults/rates.yml");
        Map<String, Object> raw = readYaml(configPath);
        RatesConfig config = new RatesConfig(
            decimal(raw, "xp_rate", RatesConfig.defaults().xpRate()),
            decimal(raw, "currency_rate", RatesConfig.defaults().currencyRate()),
            decimal(raw, "resource_drop_rate", RatesConfig.defaults().resourceDropRate()),
            decimal(raw, "equipment_drop_rate", RatesConfig.defaults().equipmentDropRate()),
            decimal(raw, "consumable_drop_rate", RatesConfig.defaults().consumableDropRate()),
            decimal(raw, "boss_respawn_rate", RatesConfig.defaults().bossRespawnRate())
        );
        LOGGER.info("Loaded rates config from {}", configPath);
        return config;
    }

    private void ensureDefault(Path configPath, String resourcePath) {
        if (Files.exists(configPath)) {
            return;
        }
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing default config resource " + resourcePath);
            }
            Files.createDirectories(configPath.getParent());
            Files.write(configPath, stream.readAllBytes());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write default config " + configPath, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readYaml(Path configPath) {
        try (InputStream stream = Files.newInputStream(configPath)) {
            Object loaded = yaml.load(stream);
            return loaded instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load YAML " + configPath, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static double decimal(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
