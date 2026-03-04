CREATE TABLE IF NOT EXISTS schema_migrations (
    version TEXT PRIMARY KEY,
    applied_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    minecraft_uuid TEXT NOT NULL UNIQUE,
    nickname TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS characters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL UNIQUE,
    level INTEGER NOT NULL,
    xp_current INTEGER NOT NULL,
    xp_total INTEGER NOT NULL,
    currency_copper INTEGER NOT NULL,
    base_stats_json TEXT NOT NULL,
    last_known_position TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY(account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS item_templates (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    item_category TEXT NOT NULL,
    vanilla_icon_item TEXT NOT NULL,
    model_id TEXT,
    stackable INTEGER NOT NULL,
    max_stack INTEGER NOT NULL,
    required_level INTEGER NOT NULL,
    rarity TEXT NOT NULL,
    equip_slot TEXT,
    base_stats_json TEXT NOT NULL,
    vendor_value INTEGER NOT NULL,
    enabled INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS loot_tables (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    drop_slots INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS loot_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    loot_table_id INTEGER NOT NULL,
    item_template_id INTEGER NOT NULL,
    drop_weight REAL NOT NULL,
    min_quantity INTEGER NOT NULL,
    max_quantity INTEGER NOT NULL,
    is_guaranteed INTEGER NOT NULL,
    enabled INTEGER NOT NULL,
    FOREIGN KEY(loot_table_id) REFERENCES loot_tables(id) ON DELETE CASCADE,
    FOREIGN KEY(item_template_id) REFERENCES item_templates(id) ON DELETE CASCADE,
    UNIQUE(loot_table_id, item_template_id)
);

CREATE TABLE IF NOT EXISTS mob_templates (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    mob_type TEXT NOT NULL,
    level INTEGER NOT NULL,
    entity_model TEXT NOT NULL,
    hostility_type TEXT NOT NULL,
    base_damage REAL NOT NULL,
    base_hp REAL NOT NULL,
    move_speed REAL NOT NULL,
    attack_speed REAL NOT NULL,
    aggro_radius REAL NOT NULL,
    leash_radius REAL NOT NULL,
    loot_table_id INTEGER,
    currency_min INTEGER NOT NULL,
    currency_max INTEGER NOT NULL,
    xp_override INTEGER,
    enabled INTEGER NOT NULL,
    FOREIGN KEY(loot_table_id) REFERENCES loot_tables(id)
);

CREATE TABLE IF NOT EXISTS mob_spawn_groups (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    mob_template_id INTEGER NOT NULL,
    dimension TEXT NOT NULL,
    center_x REAL NOT NULL,
    center_y REAL NOT NULL,
    center_z REAL NOT NULL,
    radius_x REAL NOT NULL,
    radius_z REAL NOT NULL,
    min_alive INTEGER NOT NULL,
    max_alive INTEGER NOT NULL,
    respawn_seconds INTEGER NOT NULL,
    pack_size_min INTEGER NOT NULL,
    pack_size_max INTEGER NOT NULL,
    pack_spread_min REAL NOT NULL,
    pack_spread_max REAL NOT NULL,
    enabled INTEGER NOT NULL,
    FOREIGN KEY(mob_template_id) REFERENCES mob_templates(id) ON DELETE CASCADE
);
