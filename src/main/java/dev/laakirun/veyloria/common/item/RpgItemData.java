package dev.laakirun.veyloria.common.item;

import dev.laakirun.veyloria.common.model.BaseStats;
import dev.laakirun.veyloria.common.model.EquipSlot;
import dev.laakirun.veyloria.common.model.ItemCategory;
import dev.laakirun.veyloria.common.model.Rarity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class RpgItemData implements INBTSerializable<CompoundTag> {
    public static final String ROOT_KEY = "veyloria_item";
    private String templateCode = "";
    private ItemCategory category = ItemCategory.MISC;
    private Rarity rarity = Rarity.COMMON;
    private int requiredLevel;
    private EquipSlot equipSlot;
    private BaseStats rolledStats = BaseStats.ZERO;
    private String weaponType = "";
    private boolean twoHanded;
    private double aoeChance;
    private int aoeTargets;
    private double homingChance;
    private int manaCost;
    private int healPower;
    private boolean aoeHealing;
    private boolean legendaryEffect;
    private boolean armorBoosted;
    private int itemLevel;
    private String fantasyName = "";

    public RpgItemData() {
    }

    public RpgItemData(
        String templateCode,
        ItemCategory category,
        Rarity rarity,
        int requiredLevel,
        EquipSlot equipSlot,
        BaseStats rolledStats
    ) {
        this(templateCode, category, rarity, requiredLevel, equipSlot, rolledStats,
            "", false, 0.0D, 0, 0.0D, 0, 0, false, false, false, requiredLevel, templateCode);
    }

    public RpgItemData(
        String templateCode,
        ItemCategory category,
        Rarity rarity,
        int requiredLevel,
        EquipSlot equipSlot,
        BaseStats rolledStats,
        String weaponType,
        boolean twoHanded,
        double aoeChance,
        int aoeTargets,
        double homingChance,
        int manaCost,
        int healPower,
        boolean aoeHealing,
        boolean legendaryEffect,
        boolean armorBoosted,
        int itemLevel,
        String fantasyName
    ) {
        this.templateCode = templateCode;
        this.category = category;
        this.rarity = rarity;
        this.requiredLevel = requiredLevel;
        this.equipSlot = equipSlot;
        this.rolledStats = rolledStats;
        this.weaponType = weaponType == null ? "" : weaponType;
        this.twoHanded = twoHanded;
        this.aoeChance = aoeChance;
        this.aoeTargets = aoeTargets;
        this.homingChance = homingChance;
        this.manaCost = manaCost;
        this.healPower = healPower;
        this.aoeHealing = aoeHealing;
        this.legendaryEffect = legendaryEffect;
        this.armorBoosted = armorBoosted;
        this.itemLevel = itemLevel;
        this.fantasyName = fantasyName == null ? "" : fantasyName;
    }

    public String templateCode() {
        return templateCode;
    }

    public ItemCategory category() {
        return category;
    }

    public Rarity rarity() {
        return rarity;
    }

    public int requiredLevel() {
        return requiredLevel;
    }

    public EquipSlot equipSlot() {
        return equipSlot;
    }

    public BaseStats rolledStats() {
        return rolledStats;
    }

    public String weaponType() {
        return weaponType;
    }

    public boolean twoHanded() {
        return twoHanded;
    }

    public double aoeChance() {
        return aoeChance;
    }

    public int aoeTargets() {
        return aoeTargets;
    }

    public double homingChance() {
        return homingChance;
    }

    public int manaCost() {
        return manaCost;
    }

    public int healPower() {
        return healPower;
    }

    public boolean aoeHealing() {
        return aoeHealing;
    }

    public boolean legendaryEffect() {
        return legendaryEffect;
    }

    public boolean armorBoosted() {
        return armorBoosted;
    }

    public int itemLevel() {
        return itemLevel;
    }

    public String fantasyName() {
        return fantasyName;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return toTag();
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("templateCode", templateCode);
        tag.putString("category", category.name());
        tag.putString("rarity", rarity.name());
        tag.putInt("requiredLevel", requiredLevel);
        if (equipSlot != null) {
            tag.putString("equipSlot", equipSlot.name());
        }
        tag.putInt("power", rolledStats.power());
        tag.putInt("vitality", rolledStats.vitality());
        tag.putInt("armor", rolledStats.armor());
        tag.putInt("crit", rolledStats.crit());
        tag.putInt("haste", rolledStats.haste());
        tag.putString("weaponType", weaponType);
        tag.putBoolean("twoHanded", twoHanded);
        tag.putDouble("aoeChance", aoeChance);
        tag.putInt("aoeTargets", aoeTargets);
        tag.putDouble("homingChance", homingChance);
        tag.putInt("manaCost", manaCost);
        tag.putInt("healPower", healPower);
        tag.putBoolean("aoeHealing", aoeHealing);
        tag.putBoolean("legendaryEffect", legendaryEffect);
        tag.putBoolean("armorBoosted", armorBoosted);
        tag.putInt("itemLevel", itemLevel);
        tag.putString("fantasyName", fantasyName);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        load(tag);
    }

    public void load(CompoundTag tag) {
        templateCode = tag.getString("templateCode");
        category = ItemCategory.fromId(tag.getString("category"));
        rarity = Rarity.fromId(tag.getString("rarity"));
        requiredLevel = tag.getInt("requiredLevel");
        equipSlot = tag.contains("equipSlot") ? EquipSlot.fromId(tag.getString("equipSlot")) : null;
        rolledStats = new BaseStats(
            tag.getInt("power"),
            tag.getInt("vitality"),
            tag.getInt("armor"),
            tag.getInt("crit"),
            tag.getInt("haste")
        );
        weaponType = tag.getString("weaponType");
        twoHanded = tag.getBoolean("twoHanded");
        aoeChance = tag.contains("aoeChance") ? tag.getDouble("aoeChance") : 0.0D;
        aoeTargets = tag.contains("aoeTargets") ? tag.getInt("aoeTargets") : 0;
        homingChance = tag.contains("homingChance") ? tag.getDouble("homingChance") : 0.0D;
        manaCost = tag.contains("manaCost") ? tag.getInt("manaCost") : 0;
        healPower = tag.contains("healPower") ? tag.getInt("healPower") : 0;
        aoeHealing = tag.getBoolean("aoeHealing");
        legendaryEffect = tag.getBoolean("legendaryEffect");
        armorBoosted = tag.getBoolean("armorBoosted");
        itemLevel = tag.contains("itemLevel") ? tag.getInt("itemLevel") : requiredLevel;
        fantasyName = tag.contains("fantasyName") ? tag.getString("fantasyName") : templateCode;
    }

    public static RpgItemData fromTag(CompoundTag tag) {
        RpgItemData data = new RpgItemData();
        data.load(tag);
        return data;
    }
}
