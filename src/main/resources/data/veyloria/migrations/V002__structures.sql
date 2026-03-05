CREATE TABLE IF NOT EXISTS structure_templates (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    structure_type TEXT NOT NULL,
    schematic_file TEXT NOT NULL,
    size_x INTEGER NOT NULL,
    size_y INTEGER NOT NULL,
    size_z INTEGER NOT NULL,
    enabled INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS structure_spawn_rules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    structure_template_id INTEGER NOT NULL,
    dimension TEXT NOT NULL,
    zone_min INTEGER NOT NULL,
    zone_max INTEGER NOT NULL,
    count_min_per_zone INTEGER NOT NULL,
    count_max_per_zone INTEGER NOT NULL,
    road_distance_min REAL NOT NULL,
    road_distance_max REAL NOT NULL,
    min_distance_between REAL NOT NULL,
    near_spawn_rules_json TEXT NOT NULL,
    inside_spawn_rules_json TEXT NOT NULL,
    enabled INTEGER NOT NULL,
    FOREIGN KEY(structure_template_id) REFERENCES structure_templates(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS structure_instances (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    structure_template_id INTEGER NOT NULL,
    structure_spawn_rule_id INTEGER NOT NULL,
    world_seed INTEGER NOT NULL,
    dimension TEXT NOT NULL,
    zone_index INTEGER NOT NULL,
    origin_x INTEGER NOT NULL,
    origin_y INTEGER NOT NULL,
    origin_z INTEGER NOT NULL,
    rotation_quadrants INTEGER NOT NULL,
    placed INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY(structure_template_id) REFERENCES structure_templates(id) ON DELETE CASCADE,
    FOREIGN KEY(structure_spawn_rule_id) REFERENCES structure_spawn_rules(id) ON DELETE CASCADE,
    UNIQUE(world_seed, dimension, zone_index, structure_spawn_rule_id, origin_x, origin_y, origin_z)
);

CREATE INDEX IF NOT EXISTS idx_structure_spawn_rules_dimension_zone
    ON structure_spawn_rules(dimension, zone_min, zone_max, enabled);

CREATE INDEX IF NOT EXISTS idx_structure_instances_world_dimension_zone
    ON structure_instances(world_seed, dimension, zone_index);

CREATE INDEX IF NOT EXISTS idx_structure_instances_placed
    ON structure_instances(placed, dimension);
