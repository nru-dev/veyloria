package dev.laakirun.veyloria.server.content;

import com.google.gson.JsonObject;

public final class SeedDtos {
    private SeedDtos() {
    }

    public static final class ItemTemplateSeed {
        public String code;
        public String name;
        public String item_category;
        public String vanilla_icon_item;
        public String model_id;
        public boolean stackable;
        public int max_stack;
        public int required_level;
        public String rarity;
        public String equip_slot;
        public JsonObject base_stats_json;
        public int vendor_value;
        public boolean enabled;
    }

    public static final class LootTableSeed {
        public String name;
        public int drop_slots;
    }

    public static final class LootEntrySeed {
        public String loot_table;
        public String item_template;
        public double drop_weight;
        public int min_quantity;
        public int max_quantity;
        public boolean is_guaranteed;
        public boolean enabled;
    }

    public static final class MobTemplateSeed {
        public String code;
        public String name;
        public String mob_type;
        public int level;
        public String entity_model;
        public String hostility_type;
        public double base_damage;
        public double base_hp;
        public double move_speed;
        public double attack_speed;
        public double aggro_radius;
        public double leash_radius;
        public String loot_table;
        public int currency_min;
        public int currency_max;
        public Integer xp_override;
        public boolean enabled;
    }

    public static final class MobSpawnGroupSeed {
        public String mob_template;
        public String dimension;
        public double center_x;
        public double center_y;
        public double center_z;
        public double radius_x;
        public double radius_z;
        public int min_alive;
        public int max_alive;
        public int respawn_seconds;
        public int pack_size_min;
        public int pack_size_max;
        public double pack_spread_min;
        public double pack_spread_max;
        public boolean enabled;
    }

    public static final class StructureTemplateSeed {
        public String code;
        public String name;
        public String structure_type;
        public String schematic_file;
        public int size_x;
        public int size_y;
        public int size_z;
        public boolean enabled;
    }

    public static final class StructureSpawnRuleSeed {
        public String structure_template;
        public String dimension;
        public int zone_min;
        public int zone_max;
        public int count_min_per_zone;
        public int count_max_per_zone;
        public double road_distance_min;
        public double road_distance_max;
        public double min_distance_between;
        public String near_spawn_rules_json;
        public String inside_spawn_rules_json;
        public boolean enabled;
    }
}
