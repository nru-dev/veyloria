package dev.laakirun.veyloria.common.model;

import com.google.gson.JsonObject;
import java.util.Map;

public record BaseStats(int power, int vitality, int armor, int crit, int haste) {
    public static final BaseStats ZERO = new BaseStats(0, 0, 0, 0, 0);

    public BaseStats add(BaseStats other) {
        return new BaseStats(
            power + other.power,
            vitality + other.vitality,
            armor + other.armor,
            crit + other.crit,
            haste + other.haste
        );
    }

    public BaseStats scale(double multiplier) {
        return new BaseStats(
            (int) Math.round(power * multiplier),
            (int) Math.round(vitality * multiplier),
            (int) Math.round(armor * multiplier),
            (int) Math.round(crit * multiplier),
            (int) Math.round(haste * multiplier)
        );
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("power", power);
        object.addProperty("vitality", vitality);
        object.addProperty("armor", armor);
        object.addProperty("crit", crit);
        object.addProperty("haste", haste);
        return object;
    }

    public static BaseStats fromMap(Map<?, ?> map) {
        if (map == null) {
            return ZERO;
        }
        return new BaseStats(
            asInt(map.get("power")),
            asInt(map.get("vitality")),
            asInt(map.get("armor")),
            asInt(map.get("crit")),
            asInt(map.get("haste"))
        );
    }

    public static BaseStats fromJson(JsonObject object) {
        if (object == null) {
            return ZERO;
        }
        return new BaseStats(
            read(object, "power"),
            read(object, "vitality"),
            read(object, "armor"),
            read(object, "crit"),
            read(object, "haste")
        );
    }

    private static int read(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsInt() : 0;
    }

    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Integer.parseInt(string);
        }
        return 0;
    }
}
