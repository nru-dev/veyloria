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
        this.templateCode = templateCode;
        this.category = category;
        this.rarity = rarity;
        this.requiredLevel = requiredLevel;
        this.equipSlot = equipSlot;
        this.rolledStats = rolledStats;
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
    }

    public static RpgItemData fromTag(CompoundTag tag) {
        RpgItemData data = new RpgItemData();
        data.load(tag);
        return data;
    }
}
