package dev.laakirun.veyloria.server.game;

import dev.laakirun.veyloria.common.item.RpgItemData;
import dev.laakirun.veyloria.common.model.BaseStats;
import dev.laakirun.veyloria.common.model.EquipSlot;
import dev.laakirun.veyloria.common.model.ItemCategory;
import dev.laakirun.veyloria.common.model.Rarity;
import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import dev.laakirun.veyloria.server.content.MobTemplate;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GearDropService {
    private static final Logger LOGGER = LoggerFactory.getLogger("veyloria.loot");
    private static final long DROP_DESPAWN_TICKS = 20L * 180L;

    private final Random random = new Random();
    private final Map<UUID, DropMeta> trackedDrops = new ConcurrentHashMap<>();

    public void tick(MinecraftServer server) {
        for (Map.Entry<UUID, DropMeta> entry : trackedDrops.entrySet()) {
            DropMeta meta = entry.getValue();
            ServerLevel level = findLevel(server, meta.dimension());
            if (level == null) {
                trackedDrops.remove(entry.getKey());
                continue;
            }
            if (level.getGameTime() < meta.expiresAtTick()) {
                continue;
            }
            if (level.getEntity(entry.getKey()) instanceof ItemEntity drop && drop.isAlive()) {
                drop.discard();
            }
            trackedDrops.remove(entry.getKey());
        }
    }

    public void rollAndDrop(ServerLevel level, LivingEntity deadMob, MobTemplate template) {
        Rarity rarity = rollRarity(template);
        if (rarity == null) {
            return;
        }
        int itemLevel = rollItemLevel(template.level());
        ItemStack stack = shouldDropWeapon(template)
            ? createWeapon(itemLevel, rarity)
            : createArmor(itemLevel, rarity);
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity drop = new ItemEntity(level, deadMob.getX(), deadMob.getY() + 0.2D, deadMob.getZ(), stack);
        drop.setPickUpDelay(20);
        if (!level.addFreshEntity(drop)) {
            return;
        }
        trackedDrops.put(drop.getUUID(), new DropMeta(level.dimension().location().toString(), level.getGameTime() + DROP_DESPAWN_TICKS));
        LOGGER.debug("Dropped gear {} rarity {} level {}", stack.getHoverName().getString(), rarity, itemLevel);
    }

    private Rarity rollRarity(MobTemplate template) {
        double roll = random.nextDouble() * 100.0D;
        double common;
        double uncommon;
        double rare;
        double epic;
        double legendary;
        switch (template.mobType()) {
            case NORMAL -> {
                common = 18.0D;
                uncommon = 6.0D;
                rare = 1.6D;
                epic = 0.30D;
                legendary = 0.05D;
            }
            case ELITE -> {
                common = 26.0D;
                uncommon = 12.0D;
                rare = 4.5D;
                epic = 1.2D;
                legendary = 0.2D;
            }
            case BOSS -> {
                common = 35.0D;
                uncommon = 26.0D;
                rare = 18.0D;
                epic = 8.0D;
                legendary = 2.0D;
            }
            default -> {
                return null;
            }
        }
        double equipmentRate = Math.max(0.0D, VeyloriaServerRuntime.instance().ratesConfig().equipmentDropRate());
        common *= equipmentRate;
        uncommon *= equipmentRate;
        rare *= equipmentRate;
        epic *= equipmentRate;
        legendary *= equipmentRate;
        if (roll < legendary) {
            return Rarity.LEGENDARY;
        }
        roll -= legendary;
        if (roll < epic) {
            return Rarity.EPIC;
        }
        roll -= epic;
        if (roll < rare) {
            return Rarity.RARE;
        }
        roll -= rare;
        if (roll < uncommon) {
            return Rarity.UNCOMMON;
        }
        roll -= uncommon;
        if (roll < common) {
            return Rarity.COMMON;
        }
        return null;
    }

    private boolean shouldDropWeapon(MobTemplate template) {
        double chance = switch (template.mobType()) {
            case NORMAL -> 0.22D;
            case ELITE -> 0.30D;
            case BOSS -> 0.40D;
        };
        return random.nextDouble() < chance;
    }

    private int rollItemLevel(int mobLevel) {
        int min = Math.max(1, mobLevel - 2);
        int max = Math.min(80, mobLevel + 2);
        if (max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    private ItemStack createArmor(int itemLevel, Rarity rarity) {
        ArmorTheme theme = rollArmorTheme();
        EquipSlot slot = rollArmorSlot();
        ArmorMaterial material = resolveMaterial(theme, itemLevel);
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(material.itemId(slot)));
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        double rarityMul = rarityMultiplier(rarity);
        boolean common = rarity == Rarity.COMMON;
        int stamina = common ? 0 : (int) Math.round(theme.baseStamina(itemLevel) * rarityMul);
        int armorBase = (int) Math.round(material.baseArmor(itemLevel) * rarityMul);
        int main = common ? 0 : (int) Math.round(theme.baseMainStat(itemLevel) * rarityMul);
        int support = rarity.ordinal() >= Rarity.RARE.ordinal()
            ? (int) Math.round(theme.baseSupportStat(itemLevel) * rarityMul)
            : 0;
        int armorBonus = 0;
        if (!common) {
            if (theme == ArmorTheme.TANK) {
                armorBonus = Math.max(1, (int) Math.round((2 + itemLevel * 0.10D) * rarityMul));
                stamina += Math.max(1, support);
            } else if (theme == ArmorTheme.STRENGTH
                && rarity.ordinal() >= Rarity.RARE.ordinal()
                && random.nextDouble() < 0.26D) {
                armorBonus = Math.max(1, (int) Math.round((1 + itemLevel * 0.06D) * rarityMul));
                stamina += Math.max(1, support / 2);
            }
        }
        int armor = armorBase + armorBonus;

        int strength = 0;
        int agility = 0;
        int intellect = 0;
        switch (theme) {
            case STRENGTH -> {
                strength = main;
                if (support > 0) {
                    if (random.nextBoolean()) {
                        agility = support;
                    } else {
                        intellect = support;
                    }
                }
            }
            case AGILITY -> {
                agility = main;
                if (support > 0) {
                    if (random.nextBoolean()) {
                        strength = support;
                    } else {
                        intellect = support;
                    }
                }
            }
            case INTELLECT -> {
                intellect = main;
                if (support > 0) {
                    if (random.nextBoolean()) {
                        strength = support;
                    } else {
                        agility = support;
                    }
                }
            }
            case TANK -> {
                strength = Math.max(1, (int) Math.round(main * 0.72D));
                stamina += Math.max(1, support);
            }
        }
        BaseStats stats = new BaseStats(strength, stamina, armor, agility, intellect);
        String name = rarityLabel(rarity) + " " + theme.prefix(material) + " " + slotName(slot);
        RpgItemData data = new RpgItemData(
            "dropped_armor_" + itemLevel + "_" + theme.name().toLowerCase(),
            ItemCategory.EQUIPMENT,
            rarity,
            itemLevel,
            slot,
            stats,
            "",
            false,
            0.0D,
            0,
            0.0D,
            0,
            0,
            false,
            rarity == Rarity.LEGENDARY,
            armorBonus > 0,
            itemLevel,
            name
        );
        return createStack(item, data, name, rarityColor(rarity));
    }

    private ItemStack createWeapon(int itemLevel, Rarity rarity) {
        WeaponKind kind = rollWeaponKind();
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(kind.itemId(itemLevel)));
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        double rarityMul = rarityMultiplier(rarity);
        int main = (int) Math.round(kind.baseMainStat(itemLevel) * rarityMul);
        int stamina = (int) Math.round(kind.baseStamina(itemLevel) * rarityMul);
        BaseStats stats = switch (kind) {
            case SWORD -> new BaseStats(main, stamina, 0, 0, 0);
            case AXE -> new BaseStats((int) Math.round(main * 0.75D), (int) Math.round(stamina * 1.2D), 0, 0, 0);
            case BOW -> new BaseStats(0, stamina, 0, main, 0);
            case WAND -> new BaseStats(0, stamina, 0, 0, main);
        };
        double aoeChance = kind.baseAoeChance() + rarity.ordinal() * 0.02D;
        int aoeTargets = switch (kind) {
            case SWORD -> 3;
            case AXE -> 4;
            case BOW -> 5;
            case WAND -> 0;
        };
        double homingChance = kind == WeaponKind.BOW ? 0.10D + rarity.ordinal() * 0.06D : 0.0D;
        boolean aoeHealing = kind == WeaponKind.WAND && rarity.ordinal() >= Rarity.RARE.ordinal() && random.nextDouble() < (0.18D + rarity.ordinal() * 0.04D);
        int manaCost = kind == WeaponKind.WAND ? (aoeHealing ? 45 : 22) : 0;
        int healPower = kind == WeaponKind.WAND ? (int) Math.round((6 + itemLevel * 0.95D) * rarityMul) : 0;
        String name = rarityLabel(rarity) + " " + kind.prefix() + " " + weaponCoreName(kind);
        RpgItemData data = new RpgItemData(
            "dropped_weapon_" + itemLevel + "_" + kind.name().toLowerCase(),
            ItemCategory.EQUIPMENT,
            rarity,
            itemLevel,
            EquipSlot.WEAPON,
            stats,
            kind.id(),
            kind == WeaponKind.SWORD,
            clamp(aoeChance),
            aoeTargets,
            clamp(homingChance),
            manaCost,
            healPower,
            aoeHealing,
            rarity == Rarity.LEGENDARY,
            false,
            itemLevel,
            name
        );
        return createStack(item, data, name, rarityColor(rarity));
    }

    private ItemStack createStack(Item item, RpgItemData data, String displayName, ChatFormatting color) {
        ItemStack stack = new ItemStack(item, 1);
        CompoundTag root = new CompoundTag();
        root.put(RpgItemData.ROOT_KEY, data.toTag());
        CustomData.set(DataComponents.CUSTOM_DATA, stack, root);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(displayName).withStyle(color));
        return stack;
    }

    private ArmorTheme rollArmorTheme() {
        double roll = random.nextDouble();
        if (roll < 0.16D) {
            return ArmorTheme.TANK;
        }
        if (roll < 0.46D) {
            return ArmorTheme.STRENGTH;
        }
        if (roll < 0.74D) {
            return ArmorTheme.AGILITY;
        }
        return ArmorTheme.INTELLECT;
    }

    private EquipSlot rollArmorSlot() {
        return switch (random.nextInt(4)) {
            case 0 -> EquipSlot.HELMET;
            case 1 -> EquipSlot.CHEST;
            case 2 -> EquipSlot.LEGS;
            default -> EquipSlot.BOOTS;
        };
    }

    private static ArmorMaterial resolveMaterial(ArmorTheme theme, int itemLevel) {
        return switch (theme) {
            case INTELLECT -> ArmorMaterial.CLOTH;
            case AGILITY -> ArmorMaterial.LEATHER;
            case STRENGTH -> itemLevel < 35 ? ArmorMaterial.CHAIN : ArmorMaterial.PLATE;
            case TANK -> itemLevel < 30 ? ArmorMaterial.CHAIN : ArmorMaterial.PLATE;
        };
    }

    private WeaponKind rollWeaponKind() {
        double roll = random.nextDouble();
        if (roll < 0.34D) {
            return WeaponKind.SWORD;
        }
        if (roll < 0.58D) {
            return WeaponKind.AXE;
        }
        if (roll < 0.80D) {
            return WeaponKind.BOW;
        }
        return WeaponKind.WAND;
    }

    private static String slotName(EquipSlot slot) {
        return switch (slot) {
            case HELMET -> "шлем";
            case CHEST -> "доспех";
            case LEGS -> "поножи";
            case BOOTS -> "сапоги";
            default -> "предмет";
        };
    }

    private String weaponCoreName(WeaponKind kind) {
        List<String> pool = switch (kind) {
            case SWORD -> List.of("Клинок Грозы", "Меч Закатного Ветра", "Гибельный Рубеж", "Рассекатель Бури");
            case AXE -> List.of("Топор Дозора", "Секира Каменного Щита", "Раскол Земли", "Кара Оплота");
            case BOW -> List.of("Лук Лунной Тени", "Шепот Сокола", "Струна Леса", "Северный Выстрел");
            case WAND -> List.of("Жезл Светлой Руки", "Палочка Изумрудной Зари", "Луч Миротворца", "Сердце Рощи");
        };
        return pool.get(random.nextInt(pool.size()));
    }

    private static String rarityLabel(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> "Обычный";
            case UNCOMMON -> "Необычный";
            case RARE -> "Редкий";
            case EPIC -> "Эпический";
            case LEGENDARY -> "Легендарный";
        };
    }

    private static ChatFormatting rarityColor(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> ChatFormatting.WHITE;
            case UNCOMMON -> ChatFormatting.GREEN;
            case RARE -> ChatFormatting.BLUE;
            case EPIC -> ChatFormatting.DARK_PURPLE;
            case LEGENDARY -> ChatFormatting.GOLD;
        };
    }

    private static double rarityMultiplier(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 1.0D;
            case UNCOMMON -> 1.18D;
            case RARE -> 1.42D;
            case EPIC -> 1.92D;
            case LEGENDARY -> 2.6D;
        };
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(0.95D, value));
    }

    private static ServerLevel findLevel(MinecraftServer server, String dimensionId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimensionId)) {
                return level;
            }
        }
        return null;
    }

    private enum ArmorTheme {
        STRENGTH("Стальной", 4.0D, 3.4D, 1.2D),
        AGILITY("Теневой", 3.2D, 2.8D, 0.9D),
        INTELLECT("Мистический", 2.6D, 2.6D, 0.8D),
        TANK("Оплотный", 5.2D, 2.8D, 1.6D);

        private final String prefix;
        private final double staminaFactor;
        private final double mainStatFactor;
        private final double supportFactor;

        ArmorTheme(String prefix, double staminaFactor, double mainStatFactor, double supportFactor) {
            this.prefix = prefix;
            this.staminaFactor = staminaFactor;
            this.mainStatFactor = mainStatFactor;
            this.supportFactor = supportFactor;
        }

        int baseStamina(int level) {
            return Math.max(1, (int) Math.round(2.0D + level * staminaFactor * 0.20D));
        }

        int baseMainStat(int level) {
            return Math.max(0, (int) Math.round(1.0D + level * mainStatFactor * 0.16D));
        }

        int baseSupportStat(int level) {
            return Math.max(0, (int) Math.round(1.0D + level * supportFactor * 0.10D));
        }

        String prefix(ArmorMaterial material) {
            if (this == INTELLECT) {
                return "Тканый";
            }
            if (this == AGILITY) {
                return "Кожаный";
            }
            if (this == TANK) {
                return "Оплотный";
            }
            return material == ArmorMaterial.CHAIN ? "Кольчужный" : "Железный";
        }
    }

    private enum ArmorMaterial {
        CLOTH(0.35D, "minecraft:leather_helmet", "minecraft:leather_chestplate", "minecraft:leather_leggings", "minecraft:leather_boots"),
        LEATHER(0.60D, "minecraft:leather_helmet", "minecraft:leather_chestplate", "minecraft:leather_leggings", "minecraft:leather_boots"),
        CHAIN(0.85D, "minecraft:chainmail_helmet", "minecraft:chainmail_chestplate", "minecraft:chainmail_leggings", "minecraft:chainmail_boots"),
        PLATE(1.12D, "minecraft:iron_helmet", "minecraft:iron_chestplate", "minecraft:iron_leggings", "minecraft:iron_boots");

        private final double armorFactor;
        private final String helmet;
        private final String chest;
        private final String legs;
        private final String boots;

        ArmorMaterial(double armorFactor, String helmet, String chest, String legs, String boots) {
            this.armorFactor = armorFactor;
            this.helmet = helmet;
            this.chest = chest;
            this.legs = legs;
            this.boots = boots;
        }

        int baseArmor(int level) {
            return Math.max(1, (int) Math.round(1.0D + level * armorFactor * 0.34D));
        }

        String itemId(EquipSlot slot) {
            return switch (slot) {
                case HELMET -> helmet;
                case CHEST -> chest;
                case LEGS -> legs;
                case BOOTS -> boots;
                default -> chest;
            };
        }
    }

    private enum WeaponKind {
        SWORD("sword_2h", "Двуручный", 4.4D, 1.1D, 0.18D),
        AXE("axe", "Танковый", 2.9D, 2.3D, 0.22D),
        BOW("bow", "Стрелковый", 3.8D, 1.0D, 0.16D),
        WAND("wand", "Целительский", 3.5D, 1.4D, 0.0D);

        private final String id;
        private final String prefix;
        private final double mainStatFactor;
        private final double staminaFactor;
        private final double aoeChance;

        WeaponKind(String id, String prefix, double mainStatFactor, double staminaFactor, double aoeChance) {
            this.id = id;
            this.prefix = prefix;
            this.mainStatFactor = mainStatFactor;
            this.staminaFactor = staminaFactor;
            this.aoeChance = aoeChance;
        }

        String id() {
            return id;
        }

        String prefix() {
            return prefix;
        }

        int baseMainStat(int level) {
            return Math.max(2, (int) Math.round(2.0D + level * mainStatFactor * 0.24D));
        }

        int baseStamina(int level) {
            return Math.max(1, (int) Math.round(1.0D + level * staminaFactor * 0.14D));
        }

        double baseAoeChance() {
            return aoeChance;
        }

        String itemId(int level) {
            return switch (this) {
                case SWORD -> level >= 55 ? "minecraft:netherite_sword" : (level >= 30 ? "minecraft:diamond_sword" : "minecraft:iron_sword");
                case AXE -> level >= 55 ? "minecraft:netherite_axe" : (level >= 30 ? "minecraft:diamond_axe" : "minecraft:iron_axe");
                case BOW -> level >= 35 ? "minecraft:crossbow" : "minecraft:bow";
                case WAND -> level >= 50 ? "minecraft:end_rod" : "minecraft:blaze_rod";
            };
        }
    }

    private record DropMeta(String dimension, long expiresAtTick) {
    }
}
