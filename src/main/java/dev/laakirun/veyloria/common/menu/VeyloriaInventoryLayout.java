package dev.laakirun.veyloria.common.menu;

import dev.laakirun.veyloria.common.item.PlayerLoadoutData;
import java.util.List;

public final class VeyloriaInventoryLayout {
    public static final int PANEL_WIDTH = 380;
    public static final int PANEL_HEIGHT = 214;
    public static final int STORAGE_GRID_X = 196;
    public static final int STORAGE_GRID_Y = 52;
    public static final int STORAGE_HOTBAR_X = 205;
    public static final int STORAGE_HOTBAR_Y = 170;
    public static final int PLAYER_MODEL_X = 92;
    public static final int PLAYER_MODEL_Y = 108;
    public static final int PLAYER_MODEL_SCALE = 46;

    public static final List<SlotAnchor> LOADOUT_SLOTS = List.of(
        new SlotAnchor(PlayerLoadoutData.SLOT_HELMET, 20, 34, "Head", SlotGroup.ARMOR),
        new SlotAnchor(PlayerLoadoutData.SLOT_PENDANT, 20, 58, "Charm", SlotGroup.ACCESSORY),
        new SlotAnchor(PlayerLoadoutData.SLOT_CHEST, 20, 110, "Chest", SlotGroup.ARMOR),
        new SlotAnchor(PlayerLoadoutData.SLOT_ACCESSORY_1, 146, 34, "A1", SlotGroup.ACCESSORY),
        new SlotAnchor(PlayerLoadoutData.SLOT_ACCESSORY_2, 146, 58, "A2", SlotGroup.ACCESSORY),
        new SlotAnchor(PlayerLoadoutData.SLOT_LEGS, 146, 82, "Legs", SlotGroup.ARMOR),
        new SlotAnchor(PlayerLoadoutData.SLOT_BOOTS, 146, 106, "Boots", SlotGroup.ARMOR),
        new SlotAnchor(PlayerLoadoutData.SLOT_RING_1, 146, 134, "R1", SlotGroup.ACCESSORY),
        new SlotAnchor(PlayerLoadoutData.SLOT_RING_2, 146, 158, "R2", SlotGroup.ACCESSORY),
        new SlotAnchor(PlayerLoadoutData.SLOT_CONSUMABLE_1, 20, 146, "4", SlotGroup.CONSUMABLE),
        new SlotAnchor(PlayerLoadoutData.SLOT_CONSUMABLE_2, 42, 146, "5", SlotGroup.CONSUMABLE),
        new SlotAnchor(PlayerLoadoutData.SLOT_CONSUMABLE_3, 20, 168, "6", SlotGroup.CONSUMABLE),
        new SlotAnchor(PlayerLoadoutData.SLOT_CONSUMABLE_4, 42, 168, "7", SlotGroup.CONSUMABLE),
        new SlotAnchor(PlayerLoadoutData.SLOT_MAIN_WEAPON, 68, 188, "1", SlotGroup.WEAPON),
        new SlotAnchor(PlayerLoadoutData.SLOT_SECONDARY_WEAPON, 90, 188, "2", SlotGroup.WEAPON),
        new SlotAnchor(PlayerLoadoutData.SLOT_RANGED_WEAPON, 112, 188, "3", SlotGroup.WEAPON),
        new SlotAnchor(PlayerLoadoutData.SLOT_AMMO, 134, 188, "Ammo", SlotGroup.AMMO)
    );

    private VeyloriaInventoryLayout() {
    }

    public static SlotAnchor anchor(int slotIndex) {
        for (SlotAnchor anchor : LOADOUT_SLOTS) {
            if (anchor.slotIndex() == slotIndex) {
                return anchor;
            }
        }
        return null;
    }

    public record SlotAnchor(int slotIndex, int x, int y, String label, SlotGroup group) {
    }

    public enum SlotGroup {
        WEAPON,
        AMMO,
        ARMOR,
        ACCESSORY,
        CONSUMABLE
    }
}
