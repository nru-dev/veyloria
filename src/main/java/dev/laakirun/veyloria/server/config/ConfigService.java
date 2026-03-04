package dev.laakirun.veyloria.server.config;

import dev.laakirun.veyloria.common.config.CombatConfig;
import dev.laakirun.veyloria.common.config.RatesConfig;
import dev.laakirun.veyloria.common.config.ServerConfig;
import dev.laakirun.veyloria.common.config.VeyloriaPaths;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigService {
    private static final Logger LOGGER = LoggerFactory.getLogger("veyloria.db");

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
        try {
            List<String> lines = Files.readAllLines(configPath, StandardCharsets.UTF_8);
            Map<String, Object> parsed = parseSimpleYaml(lines);
            return parsed.isEmpty() ? Map.of() : parsed;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load YAML " + configPath, exception);
        }
    }

    private static Map<String, Object> parseSimpleYaml(List<String> lines) {
        record Node(int indent, Map<String, Object> map) {
        }

        Map<String, Object> root = new LinkedHashMap<>();
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(new Node(-1, root));

        for (String rawLine : lines) {
            if (rawLine == null) {
                continue;
            }
            String line = rawLine.replace('\t', ' ');
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int delimiter = trimmed.indexOf(':');
            if (delimiter <= 0) {
                continue;
            }

            int indent = leadingSpaces(line);
            while (stack.size() > 1 && indent <= stack.peek().indent()) {
                stack.pop();
            }
            Map<String, Object> current = stack.peek().map();

            String key = trimmed.substring(0, delimiter).trim();
            String rawValue = trimmed.substring(delimiter + 1).trim();
            String value = stripInlineComment(rawValue).trim();

            if (value.isEmpty()) {
                Map<String, Object> nested = new LinkedHashMap<>();
                current.put(key, nested);
                stack.push(new Node(indent, nested));
                continue;
            }
            current.put(key, parseScalar(value));
        }

        return root;
    }

    private static int leadingSpaces(String value) {
        int index = 0;
        while (index < value.length() && value.charAt(index) == ' ') {
            index++;
        }
        return index;
    }

    private static String stripInlineComment(String value) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (current == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (!inSingle && !inDouble && current == '#') {
                if (index == 0 || Character.isWhitespace(value.charAt(index - 1))) {
                    return value.substring(0, index);
                }
            }
        }
        return value;
    }

    private static Object parseScalar(String raw) {
        String value = unquote(raw);
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        try {
            if (value.contains(".") || value.contains("e") || value.contains("E")) {
                return Double.parseDouble(value);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private static String unquote(String value) {
        if (value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1);
        }
        return value;
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
