package dev.laakirun.veyloria.common.item;

import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class PlayerLoadoutData implements INBTSerializable<CompoundTag> {
    public static final int SLOT_MAIN_WEAPON = 0;
    public static final int SLOT_SECONDARY_WEAPON = 1;
    public static final int SLOT_RANGED_WEAPON = 2;
    public static final int SLOT_HELMET = 3;
    public static final int SLOT_CHEST = 4;
    public static final int SLOT_LEGS = 5;
    public static final int SLOT_BOOTS = 6;
    public static final int SLOT_CONSUMABLE_1 = 7;
    public static final int SLOT_CONSUMABLE_2 = 8;
    public static final int SLOT_CONSUMABLE_3 = 9;
    public static final int SLOT_CONSUMABLE_4 = 10;
    public static final int SLOT_PENDANT = 11;
    public static final int SLOT_RING_1 = 12;
    public static final int SLOT_RING_2 = 13;
    public static final int SLOT_ACCESSORY_1 = 14;
    public static final int SLOT_ACCESSORY_2 = 15;
    public static final int SLOT_AMMO = 16;
    public static final int SLOT_COUNT = 17;
    public static final int AMMO_SLOT_MAX_STACK = 64;

    private static final String TAG_ACTIVE_SLOT = "activeWeaponSlot";
    private static final String TAG_ITEMS = "items";
    private static final String TAG_SLOT = "slot";
    private static final String TAG_ITEM = "item";
    private static final String TAG_STORED_COUNT = "storedCount";
    private static final int ITEM_STACK_CODEC_MAX_COUNT = 99;

    private final ItemStack[] items = new ItemStack[SLOT_COUNT];
    private int activeSlot = SLOT_MAIN_WEAPON;

    public PlayerLoadoutData() {
        clear();
    }

    public ItemStack getItem(int slot) {
        return isValidSlot(slot) ? items[slot] : ItemStack.EMPTY;
    }

    public void setItem(int slot, ItemStack stack) {
        if (!isValidSlot(slot)) {
            return;
        }
        items[slot] = normalize(slot, stack);
    }

    public int activeSlot() {
        return activeSlot;
    }

    public void setActiveSlot(int activeSlot) {
        if (isWeaponSlot(activeSlot)) {
            this.activeSlot = activeSlot;
        }
    }

    public ItemStack activeItem() {
        return items[activeSlot];
    }

    public List<ItemStack> items() {
        return List.of(items);
    }

    public void clear() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            items[i] = ItemStack.EMPTY;
        }
        activeSlot = SLOT_MAIN_WEAPON;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag root = new CompoundTag();
        root.putInt(TAG_ACTIVE_SLOT, activeSlot);
        ListTag list = new ListTag();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = items[slot];
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(TAG_SLOT, slot);
            ItemStack stackToSave = stack;
            int actualCount = stack.getCount();
            if (actualCount > ITEM_STACK_CODEC_MAX_COUNT) {
                stackToSave = stack.copy();
                stackToSave.setCount(ITEM_STACK_CODEC_MAX_COUNT);
                entry.putInt(TAG_STORED_COUNT, actualCount);
            }
            entry.put(TAG_ITEM, stackToSave.saveOptional(provider));
            list.add(entry);
        }
        root.put(TAG_ITEMS, list);
        return root;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        clear();
        setActiveSlot(tag.getInt(TAG_ACTIVE_SLOT));
        ListTag list = tag.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getInt(TAG_SLOT);
            if (!isValidSlot(slot)) {
                continue;
            }
            ItemStack restored = normalize(slot, ItemStack.parseOptional(provider, entry.getCompound(TAG_ITEM)));
            if (!restored.isEmpty() && entry.contains(TAG_STORED_COUNT, Tag.TAG_INT)) {
                restored = withRestoredCount(slot, restored, entry.getInt(TAG_STORED_COUNT));
            }
            items[slot] = restored;
        }
    }

    private static ItemStack withRestoredCount(int slot, ItemStack stack, int count) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack restored = stack.copy();
        restored.setCount(count);
        return normalize(slot, restored);
    }

    private static ItemStack normalize(int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack normalized = stack.copy();
        int count = normalized.getCount();
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        int maxCount = maxCountForSlot(slot, normalized);
        if (count > maxCount) {
            normalized.setCount(maxCount);
        }
        return normalized;
    }

    private static int maxCountForSlot(int slot, ItemStack stack) {
        if (isAmmoSlot(slot)) {
            return AMMO_SLOT_MAX_STACK;
        }
        if (isStackableLoadoutSlot(slot)) {
            return Math.min(64, stack.getMaxStackSize());
        }
        return 1;
    }

    private static boolean isValidSlot(int slot) {
        return slot >= 0 && slot < SLOT_COUNT;
    }

    public static boolean isActionSlot(int slot) {
        return isWeaponSlot(slot);
    }

    public static boolean isWeaponSlot(int slot) {
        return slot >= SLOT_MAIN_WEAPON && slot <= SLOT_RANGED_WEAPON;
    }

    public static boolean isConsumableSlot(int slot) {
        return slot >= SLOT_CONSUMABLE_1 && slot <= SLOT_CONSUMABLE_4;
    }

    public static boolean isAccessorySlot(int slot) {
        return slot >= SLOT_PENDANT && slot <= SLOT_ACCESSORY_2;
    }

    public static boolean isArmorSlot(int slot) {
        return slot >= SLOT_HELMET && slot <= SLOT_BOOTS;
    }

    public static boolean isAmmoSlot(int slot) {
        return slot == SLOT_AMMO;
    }

    public static boolean isStackableLoadoutSlot(int slot) {
        return isConsumableSlot(slot) || isAmmoSlot(slot);
    }

    public static boolean contributesToStats(int slot) {
        return isWeaponSlot(slot) || isArmorSlot(slot) || isAccessorySlot(slot);
    }

    public static int maxStackForLoadoutSlot(int slot) {
        if (isAmmoSlot(slot)) {
            return AMMO_SLOT_MAX_STACK;
        }
        return 64;
    }
}
