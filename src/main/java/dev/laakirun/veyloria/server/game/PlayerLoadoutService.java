package dev.laakirun.veyloria.server.game;

import dev.laakirun.veyloria.common.item.PlayerLoadoutData;
import dev.laakirun.veyloria.common.item.RpgItemData;
import dev.laakirun.veyloria.common.model.EquipSlot;
import dev.laakirun.veyloria.common.model.ItemCategory;
import dev.laakirun.veyloria.common.network.VeyloriaNetwork;
import dev.laakirun.veyloria.common.registry.VeyloriaAttachments;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PlayerLoadoutService {
    private final Map<UUID, MirrorState> mirrorStates = new ConcurrentHashMap<>();
    private final Map<UUID, ConsumableUseState> consumableUseStates = new ConcurrentHashMap<>();

    public PlayerLoadoutData loadout(ServerPlayer player) {
        return player.getData(VeyloriaAttachments.PLAYER_LOADOUT);
    }

    public void initializePlayer(ServerPlayer player) {
        applyLoadout(player);
    }

    public void unload(UUID playerUuid) {
        mirrorStates.remove(playerUuid);
        consumableUseStates.remove(playerUuid);
    }

    public ItemStack currentWeapon(ServerPlayer player) {
        return loadout(player).activeItem();
    }

    public boolean isUsingConsumable(ServerPlayer player) {
        return consumableUseStates.containsKey(player.getUUID());
    }

    public void sanitizePickupMirrorSlot(ServerPlayer player) {
        PlayerLoadoutData loadout = loadout(player);
        if (moveUnexpectedMirrorItemToStorage(player, loadout)) {
            applyLoadout(player);
        }
    }

    public void resumeConsumableUse(ServerPlayer player) {
        ConsumableUseState state = consumableUseStates.get(player.getUUID());
        if (state == null) {
            return;
        }
        player.getInventory().selected = PlayerLoadoutData.ACTIVE_MIRROR_INVENTORY_SLOT;
        player.startUsingItem(InteractionHand.OFF_HAND);
    }

    public void finishConsumableUse(ServerPlayer player, ItemStack resultStack) {
        if (!consumableUseStates.containsKey(player.getUUID())) {
            return;
        }
        broadcastMenus(player);
    }

    public void tick(ServerPlayer player, int playerLevel) {
        PlayerLoadoutData loadout = loadout(player);
        ConsumableUseState consumableUseState = consumableUseStates.get(player.getUUID());
        boolean changed = clearOffhand(player, loadout, consumableUseState);
        if (consumableUseState == null) {
            syncRuntimeChanges(player, loadout);
        } else {
            changed |= syncConsumableUse(player, loadout, consumableUseState);
        }
        changed |= reserveMirrorSlot(player, loadout);
        changed |= stripTooHighLevel(player, loadout, playerLevel);
        player.getInventory().selected = PlayerLoadoutData.ACTIVE_MIRROR_INVENTORY_SLOT;
        if (consumableUseState != null && player.isUsingItem()) {
            updateMirrorState(player, loadout);
            return;
        }
        if (changed || !mirrorsMatch(player, loadout)) {
            applyLoadout(player);
        } else {
            updateMirrorState(player, loadout);
        }
    }

    public void selectActionSlot(ServerPlayer player, int actionSlot) {
        if (!PlayerLoadoutData.isWeaponSlot(actionSlot)) {
            return;
        }
        PlayerLoadoutData loadout = loadout(player);
        ConsumableUseState consumableUseState = consumableUseStates.remove(player.getUUID());
        if (consumableUseState != null) {
            player.stopUsingItem();
            cancelConsumableUse(player, loadout, consumableUseState.slot());
        }
        syncRuntimeChanges(player, loadout);
        loadout.setActiveSlot(actionSlot);
        applyLoadout(player);
    }

    public void useConsumable(ServerPlayer player, int consumableSlot) {
        if (!PlayerLoadoutData.isConsumableSlot(consumableSlot)) {
            return;
        }
        PlayerLoadoutData loadout = loadout(player);
        ItemStack consumable = loadout.getItem(consumableSlot);
        if (!isConsumableItem(consumable, RpgItemUtils.read(consumable))) {
            return;
        }

        ConsumableUseState existingState = consumableUseStates.remove(player.getUUID());
        if (existingState != null) {
            cancelConsumableUse(player, loadout, existingState.slot());
        }

        player.stopUsingItem();
        player.setItemSlot(EquipmentSlot.OFFHAND, consumable.copy());
        InteractionResult result = player.gameMode.useItem(player, player.level(), player.getOffhandItem(), InteractionHand.OFF_HAND);
        if (!result.consumesAction() && !player.isUsingItem()) {
            loadout.setItem(consumableSlot, player.getOffhandItem().copy());
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            notifyConsumableUseState(player, consumableSlot, false);
            applyLoadout(player);
            return;
        }

        if (player.isUsingItem()) {
            consumableUseStates.put(player.getUUID(), new ConsumableUseState(consumableSlot));
            notifyConsumableUseState(player, consumableSlot, true);
            updateMirrorState(player, loadout);
            broadcastMenus(player);
            return;
        }

        storeConsumableOutcome(player, loadout, consumableSlot, player.getOffhandItem());
        applyLoadout(player);
    }

    public void setLoadoutItem(ServerPlayer player, int slot, ItemStack stack) {
        if (!isValidForLoadoutSlot(slot, stack)) {
            return;
        }
        loadout(player).setItem(slot, stack);
        applyLoadout(player);
    }

    public ItemStack removeLoadoutItem(ServerPlayer player, int slot) {
        PlayerLoadoutData loadout = loadout(player);
        ItemStack removed = loadout.getItem(slot).copy();
        loadout.setItem(slot, ItemStack.EMPTY);
        applyLoadout(player);
        return removed;
    }

    public int preferredLoadoutSlot(ItemStack stack, PlayerLoadoutData loadout) {
        if (stack == null || stack.isEmpty()) {
            return -1;
        }
        RpgItemData item = RpgItemUtils.read(stack);
        if (isAmmoItem(stack, item)) {
            return preferredAmmoSlot(stack, loadout);
        }
        if (item != null && item.equipSlot() != null) {
            return switch (item.equipSlot()) {
                case HELMET -> slotAvailable(loadout, PlayerLoadoutData.SLOT_HELMET);
                case PENDANT -> slotAvailable(loadout, PlayerLoadoutData.SLOT_PENDANT);
                case CHEST -> slotAvailable(loadout, PlayerLoadoutData.SLOT_CHEST);
                case LEGS -> slotAvailable(loadout, PlayerLoadoutData.SLOT_LEGS);
                case BOOTS -> slotAvailable(loadout, PlayerLoadoutData.SLOT_BOOTS);
                case RING -> preferredDuplicateSlot(loadout, PlayerLoadoutData.SLOT_RING_1, PlayerLoadoutData.SLOT_RING_2);
                case ACCESSORY -> preferredDuplicateSlot(loadout, PlayerLoadoutData.SLOT_ACCESSORY_1, PlayerLoadoutData.SLOT_ACCESSORY_2);
                case AMMO -> preferredAmmoSlot(stack, loadout);
                case WEAPON -> preferredWeaponSlot(item, loadout);
            };
        }
        if (isConsumableItem(stack, item)) {
            return preferredConsumableSlot(stack, loadout);
        }
        return -1;
    }

    public static boolean isValidForLoadoutSlot(int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        RpgItemData item = RpgItemUtils.read(stack);
        if (PlayerLoadoutData.isConsumableSlot(slot)) {
            return isConsumableItem(stack, item);
        }
        if (PlayerLoadoutData.isAmmoSlot(slot)) {
            return isAmmoItem(stack, item);
        }
        if (item == null || item.equipSlot() == null) {
            return false;
        }
        return switch (slot) {
            case PlayerLoadoutData.SLOT_MAIN_WEAPON, PlayerLoadoutData.SLOT_SECONDARY_WEAPON ->
                item.equipSlot() == EquipSlot.WEAPON && !isRangedWeapon(item);
            case PlayerLoadoutData.SLOT_RANGED_WEAPON ->
                item.equipSlot() == EquipSlot.WEAPON && isRangedWeapon(item);
            case PlayerLoadoutData.SLOT_HELMET -> item.equipSlot() == EquipSlot.HELMET;
            case PlayerLoadoutData.SLOT_CHEST -> item.equipSlot() == EquipSlot.CHEST;
            case PlayerLoadoutData.SLOT_LEGS -> item.equipSlot() == EquipSlot.LEGS;
            case PlayerLoadoutData.SLOT_BOOTS -> item.equipSlot() == EquipSlot.BOOTS;
            case PlayerLoadoutData.SLOT_PENDANT -> item.equipSlot() == EquipSlot.PENDANT;
            case PlayerLoadoutData.SLOT_RING_1, PlayerLoadoutData.SLOT_RING_2 -> item.equipSlot() == EquipSlot.RING;
            case PlayerLoadoutData.SLOT_ACCESSORY_1, PlayerLoadoutData.SLOT_ACCESSORY_2 -> item.equipSlot() == EquipSlot.ACCESSORY;
            default -> false;
        };
    }

    private static int preferredWeaponSlot(RpgItemData item, PlayerLoadoutData loadout) {
        if (isRangedWeapon(item)) {
            return slotAvailable(loadout, PlayerLoadoutData.SLOT_RANGED_WEAPON);
        }
        int main = slotAvailable(loadout, PlayerLoadoutData.SLOT_MAIN_WEAPON);
        if (main >= 0) {
            return main;
        }
        return slotAvailable(loadout, PlayerLoadoutData.SLOT_SECONDARY_WEAPON);
    }

    private static int preferredAmmoSlot(ItemStack stack, PlayerLoadoutData loadout) {
        ItemStack stored = loadout.getItem(PlayerLoadoutData.SLOT_AMMO);
        if (hasStackRoom(PlayerLoadoutData.SLOT_AMMO, stored, stack) || stored.isEmpty()) {
            return PlayerLoadoutData.SLOT_AMMO;
        }
        return -1;
    }

    private static int preferredConsumableSlot(ItemStack stack, PlayerLoadoutData loadout) {
        for (int slot = PlayerLoadoutData.SLOT_CONSUMABLE_1; slot <= PlayerLoadoutData.SLOT_CONSUMABLE_4; slot++) {
            ItemStack stored = loadout.getItem(slot);
            if (hasStackRoom(slot, stored, stack)) {
                return slot;
            }
        }
        for (int slot = PlayerLoadoutData.SLOT_CONSUMABLE_1; slot <= PlayerLoadoutData.SLOT_CONSUMABLE_4; slot++) {
            if (loadout.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private void applyLoadout(ServerPlayer player) {
        PlayerLoadoutData loadout = loadout(player);
        ConsumableUseState consumableUseState = consumableUseStates.get(player.getUUID());
        reserveMirrorSlot(player, loadout);
        clearOffhand(player, loadout, consumableUseState);
        player.getInventory().selected = PlayerLoadoutData.ACTIVE_MIRROR_INVENTORY_SLOT;
        player.getInventory().setItem(PlayerLoadoutData.ACTIVE_MIRROR_INVENTORY_SLOT, loadout.activeItem().copy());
        player.setItemSlot(EquipmentSlot.HEAD, loadout.getItem(PlayerLoadoutData.SLOT_HELMET).copy());
        player.setItemSlot(EquipmentSlot.CHEST, loadout.getItem(PlayerLoadoutData.SLOT_CHEST).copy());
        player.setItemSlot(EquipmentSlot.LEGS, loadout.getItem(PlayerLoadoutData.SLOT_LEGS).copy());
        player.setItemSlot(EquipmentSlot.FEET, loadout.getItem(PlayerLoadoutData.SLOT_BOOTS).copy());
        updateMirrorState(player, loadout);
        PacketDistributor.sendToPlayer(player, VeyloriaNetwork.loadoutSnapshot(loadout, player.registryAccess()));
        broadcastMenus(player);
    }

    private void syncRuntimeChanges(ServerPlayer player, PlayerLoadoutData loadout) {
        MirrorState previous = mirrorStates.get(player.getUUID());
        if (previous == null) {
            return;
        }
        ItemStack activeMirror = player.getInventory().getItem(PlayerLoadoutData.ACTIVE_MIRROR_INVENTORY_SLOT);
        if (!ItemStack.matches(previous.mirrorItem(), activeMirror)
            && isValidForLoadoutSlot(loadout.activeSlot(), activeMirror)
            && isExpectedMirrorMutation(previous.mirrorItem(), activeMirror)) {
            loadout.setItem(loadout.activeSlot(), activeMirror);
        }
        syncArmorChanges(player, loadout, previous);
    }

    private boolean syncConsumableUse(ServerPlayer player, PlayerLoadoutData loadout, ConsumableUseState consumableUseState) {
        MirrorState previous = mirrorStates.get(player.getUUID());
        if (previous != null) {
            syncArmorChanges(player, loadout, previous);
        }
        if (player.isUsingItem()) {
            return false;
        }
        completeConsumableUse(player, loadout, consumableUseState);
        consumableUseStates.remove(player.getUUID());
        return true;
    }

    private void syncArmorChanges(ServerPlayer player, PlayerLoadoutData loadout, MirrorState previous) {
        if (!ItemStack.matches(previous.helmet(), player.getItemBySlot(EquipmentSlot.HEAD))) {
            loadout.setItem(PlayerLoadoutData.SLOT_HELMET, player.getItemBySlot(EquipmentSlot.HEAD));
        }
        if (!ItemStack.matches(previous.chest(), player.getItemBySlot(EquipmentSlot.CHEST))) {
            loadout.setItem(PlayerLoadoutData.SLOT_CHEST, player.getItemBySlot(EquipmentSlot.CHEST));
        }
        if (!ItemStack.matches(previous.legs(), player.getItemBySlot(EquipmentSlot.LEGS))) {
            loadout.setItem(PlayerLoadoutData.SLOT_LEGS, player.getItemBySlot(EquipmentSlot.LEGS));
        }
        if (!ItemStack.matches(previous.boots(), player.getItemBySlot(EquipmentSlot.FEET))) {
            loadout.setItem(PlayerLoadoutData.SLOT_BOOTS, player.getItemBySlot(EquipmentSlot.FEET));
        }
    }

    private boolean mirrorsMatch(ServerPlayer player, PlayerLoadoutData loadout) {
        return ItemStack.matches(player.getInventory().getItem(PlayerLoadoutData.ACTIVE_MIRROR_INVENTORY_SLOT), loadout.activeItem())
            && ItemStack.matches(player.getItemBySlot(EquipmentSlot.HEAD), loadout.getItem(PlayerLoadoutData.SLOT_HELMET))
            && ItemStack.matches(player.getItemBySlot(EquipmentSlot.CHEST), loadout.getItem(PlayerLoadoutData.SLOT_CHEST))
            && ItemStack.matches(player.getItemBySlot(EquipmentSlot.LEGS), loadout.getItem(PlayerLoadoutData.SLOT_LEGS))
            && ItemStack.matches(player.getItemBySlot(EquipmentSlot.FEET), loadout.getItem(PlayerLoadoutData.SLOT_BOOTS));
    }

    private boolean stripTooHighLevel(ServerPlayer player, PlayerLoadoutData loadout, int playerLevel) {
        boolean changed = false;
        for (int slot = 0; slot < PlayerLoadoutData.SLOT_COUNT; slot++) {
            ItemStack stack = loadout.getItem(slot);
            RpgItemData item = RpgItemUtils.read(stack);
            if (item == null || item.requiredLevel() <= playerLevel) {
                continue;
            }
            loadout.setItem(slot, ItemStack.EMPTY);
            placeInStorageOrDrop(player, stack.copy());
            changed = true;
        }
        return changed;
    }

    private boolean clearOffhand(ServerPlayer player, PlayerLoadoutData loadout, ConsumableUseState consumableUseState) {
        ItemStack offhand = player.getOffhandItem();
        if (offhand.isEmpty()) {
            return false;
        }
        if (consumableUseState != null) {
            return false;
        }
        MirrorState mirror = mirrorStates.get(player.getUUID());
        boolean mirroredActiveItem = ItemStack.matches(offhand, loadout.activeItem())
            || (mirror != null && ItemStack.matches(offhand, mirror.mirrorItem()));
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        if (mirroredActiveItem) {
            player.getInventory().setItem(PlayerLoadoutData.ACTIVE_MIRROR_INVENTORY_SLOT, loadout.activeItem().copy());
        } else {
            placeInStorageOrDrop(player, offhand.copy());
        }
        return true;
    }

    private boolean reserveMirrorSlot(ServerPlayer player, PlayerLoadoutData loadout) {
        ItemStack current = player.getInventory().getItem(PlayerLoadoutData.ACTIVE_MIRROR_INVENTORY_SLOT);
        if (current.isEmpty()) {
            return false;
        }
        MirrorState mirror = mirrorStates.get(player.getUUID());
        boolean expectedActive = ItemStack.matches(current, loadout.activeItem());
        boolean knownMirror = mirror != null && ItemStack.matches(current, mirror.mirrorItem());
        if (expectedActive || knownMirror) {
            return false;
        }
        player.getInventory().setItem(PlayerLoadoutData.ACTIVE_MIRROR_INVENTORY_SLOT, ItemStack.EMPTY);
        placeInStorageOrDrop(player, current.copy());
        return true;
    }

    private boolean moveUnexpectedMirrorItemToStorage(ServerPlayer player, PlayerLoadoutData loadout) {
        ItemStack current = player.getInventory().getItem(PlayerLoadoutData.ACTIVE_MIRROR_INVENTORY_SLOT);
        if (current.isEmpty()) {
            return false;
        }
        MirrorState mirror = mirrorStates.get(player.getUUID());
        ItemStack previousMirror = mirror == null ? loadout.activeItem() : mirror.mirrorItem();
        if (isExpectedMirrorMutation(previousMirror, current)) {
            return false;
        }
        player.getInventory().setItem(PlayerLoadoutData.ACTIVE_MIRROR_INVENTORY_SLOT, ItemStack.EMPTY);
        placeInStorageOrDrop(player, current.copy());
        return true;
    }

    private void placeInStorageOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack remaining = stack.copy();
        mergeIntoStorage(player, remaining);
        if (!remaining.isEmpty() && !placeIntoEmptyStorageSlot(player, remaining)) {
            player.drop(remaining, false);
        }
    }

    private void mergeIntoStorage(ServerPlayer player, ItemStack incoming) {
        if (incoming.isEmpty() || !incoming.isStackable()) {
            return;
        }
        for (int slot = 1; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stored = player.getInventory().getItem(slot);
            if (stored.isEmpty() || !ItemStack.isSameItemSameComponents(stored, incoming)) {
                continue;
            }
            int free = Math.min(stored.getMaxStackSize(), player.getInventory().getMaxStackSize()) - stored.getCount();
            if (free <= 0) {
                continue;
            }
            int moved = Math.min(free, incoming.getCount());
            stored.grow(moved);
            incoming.shrink(moved);
            if (incoming.isEmpty()) {
                return;
            }
        }
    }

    private boolean placeIntoEmptyStorageSlot(ServerPlayer player, ItemStack incoming) {
        for (int slot = 1; slot < player.getInventory().getContainerSize(); slot++) {
            if (!player.getInventory().getItem(slot).isEmpty()) {
                continue;
            }
            player.getInventory().setItem(slot, incoming.copy());
            incoming.setCount(0);
            return true;
        }
        return false;
    }

    private void updateMirrorState(ServerPlayer player, PlayerLoadoutData loadout) {
        mirrorStates.put(player.getUUID(), new MirrorState(
            loadout.activeItem().copy(),
            loadout.getItem(PlayerLoadoutData.SLOT_HELMET).copy(),
            loadout.getItem(PlayerLoadoutData.SLOT_CHEST).copy(),
            loadout.getItem(PlayerLoadoutData.SLOT_LEGS).copy(),
            loadout.getItem(PlayerLoadoutData.SLOT_BOOTS).copy()
        ));
    }

    private static int slotAvailable(PlayerLoadoutData loadout, int slotIndex) {
        return loadout.getItem(slotIndex).isEmpty() ? slotIndex : -1;
    }

    private static int preferredDuplicateSlot(PlayerLoadoutData loadout, int primary, int secondary) {
        int first = slotAvailable(loadout, primary);
        if (first >= 0) {
            return first;
        }
        return slotAvailable(loadout, secondary);
    }

    private static boolean isRangedWeapon(RpgItemData item) {
        return switch (item.weaponType()) {
            case "bow", "wand" -> true;
            default -> false;
        };
    }

    private static boolean isAmmoItem(ItemStack stack, RpgItemData item) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(ItemTags.ARROWS)) {
            return true;
        }
        return item != null && item.equipSlot() == EquipSlot.AMMO;
    }

    private static boolean hasStackRoom(int loadoutSlot, ItemStack stored, ItemStack incoming) {
        if (stored.isEmpty() || incoming.isEmpty() || !stored.isStackable()) {
            return false;
        }
        if (!ItemStack.isSameItemSameComponents(stored, incoming)) {
            return false;
        }
        int maxCount = PlayerLoadoutData.isAmmoSlot(loadoutSlot)
            ? PlayerLoadoutData.AMMO_SLOT_MAX_STACK
            : Math.min(stored.getMaxStackSize(), incoming.getMaxStackSize());
        return stored.getCount() < maxCount;
    }

    private static boolean isConsumableItem(ItemStack stack, RpgItemData item) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (item != null && item.category() == ItemCategory.CONSUMABLE) {
            return true;
        }
        UseAnim useAnimation = stack.getUseAnimation();
        return useAnimation == UseAnim.EAT || useAnimation == UseAnim.DRINK;
    }

    private static boolean isExpectedMirrorMutation(ItemStack previous, ItemStack current) {
        if (previous == null || previous.isEmpty()) {
            return current == null || current.isEmpty();
        }
        if (current == null || current.isEmpty()) {
            return true;
        }
        if (!ItemStack.isSameItem(previous, current)) {
            return false;
        }
        ItemStack previousNormalized = previous.copy();
        ItemStack currentNormalized = current.copy();
        previousNormalized.setCount(1);
        currentNormalized.setCount(1);
        previousNormalized.remove(DataComponents.DAMAGE);
        currentNormalized.remove(DataComponents.DAMAGE);
        return ItemStack.isSameItemSameComponents(previousNormalized, currentNormalized);
    }

    private void completeConsumableUse(ServerPlayer player, PlayerLoadoutData loadout, ConsumableUseState consumableUseState) {
        storeConsumableOutcome(player, loadout, consumableUseState.slot(), player.getOffhandItem());
    }

    private void cancelConsumableUse(ServerPlayer player, PlayerLoadoutData loadout, int slot) {
        storeConsumableOutcome(player, loadout, slot, player.getOffhandItem());
    }

    private void broadcastMenus(ServerPlayer player) {
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != null && player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
    }

    private void notifyConsumableUseState(ServerPlayer player, int slot, boolean active) {
        PacketDistributor.sendToPlayer(player, VeyloriaNetwork.consumableUseState(slot, active));
    }

    private void storeConsumableOutcome(ServerPlayer player, PlayerLoadoutData loadout, int slot, ItemStack outcomeStack) {
        ItemStack stored = outcomeStack == null ? ItemStack.EMPTY : outcomeStack.copy();
        if (isValidForLoadoutSlot(slot, stored)) {
            loadout.setItem(slot, stored);
        } else {
            loadout.setItem(slot, ItemStack.EMPTY);
            placeInStorageOrDrop(player, stored);
        }
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        notifyConsumableUseState(player, slot, false);
    }

    private record MirrorState(
        ItemStack mirrorItem,
        ItemStack helmet,
        ItemStack chest,
        ItemStack legs,
        ItemStack boots
    ) {
    }

    private record ConsumableUseState(int slot) {
    }
}
