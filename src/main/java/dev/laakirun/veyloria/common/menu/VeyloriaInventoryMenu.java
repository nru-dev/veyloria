package dev.laakirun.veyloria.common.menu;

import dev.laakirun.veyloria.common.item.PlayerLoadoutData;
import dev.laakirun.veyloria.common.item.RpgItemData;
import dev.laakirun.veyloria.common.model.ItemCategory;
import dev.laakirun.veyloria.common.registry.VeyloriaMenus;
import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import dev.laakirun.veyloria.server.game.PlayerLoadoutService;
import dev.laakirun.veyloria.server.game.RpgItemUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

public final class VeyloriaInventoryMenu extends AbstractContainerMenu {
    public static final int LOADOUT_SLOT_COUNT = PlayerLoadoutData.SLOT_COUNT;
    public static final int STORAGE_START_INDEX = LOADOUT_SLOT_COUNT;
    public static final int STORAGE_SLOT_COUNT = 35;
    public static final int STORAGE_END_INDEX = STORAGE_START_INDEX + STORAGE_SLOT_COUNT;

    private final Container loadoutContainer;
    private final ContainerData activeActionSlotData;
    private final Player owner;

    public VeyloriaInventoryMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf ignored) {
        this(containerId, playerInventory, new SimpleContainer(LOADOUT_SLOT_COUNT), new SimpleContainerData(1));
    }

    public VeyloriaInventoryMenu(int containerId, Inventory playerInventory, ServerPlayer player) {
        this(containerId, playerInventory, new ServerLoadoutContainer(player), new ServerActiveActionSlotData(player));
    }

    private VeyloriaInventoryMenu(int containerId, Inventory playerInventory, Container loadoutContainer, ContainerData activeActionSlotData) {
        super(VeyloriaMenus.VEYLORIA_INVENTORY.get(), containerId);
        checkContainerSize(loadoutContainer, LOADOUT_SLOT_COUNT);
        checkContainerDataCount(activeActionSlotData, 1);
        this.loadoutContainer = loadoutContainer;
        this.activeActionSlotData = activeActionSlotData;
        this.owner = playerInventory.player;
        this.addDataSlots(activeActionSlotData);

        addLoadoutSlots();
        addStorageSlots(playerInventory);
    }

    private void addLoadoutSlots() {
        for (int slotIndex = 0; slotIndex < LOADOUT_SLOT_COUNT; slotIndex++) {
            VeyloriaInventoryLayout.SlotAnchor anchor = VeyloriaInventoryLayout.anchor(slotIndex);
            if (anchor == null) {
                continue;
            }
            this.addSlot(new LoadoutSlot(loadoutContainer, anchor.slotIndex(), anchor.x(), anchor.y()));
        }
    }

    private void addStorageSlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int inventorySlot = column + row * 9 + 9;
                int x = VeyloriaInventoryLayout.STORAGE_GRID_X + column * 18;
                int y = VeyloriaInventoryLayout.STORAGE_GRID_Y + row * 18;
                this.addSlot(new Slot(playerInventory, inventorySlot, x, y));
            }
        }

        for (int column = 0; column < 8; column++) {
            int inventorySlot = column + 1;
            int x = VeyloriaInventoryLayout.STORAGE_HOTBAR_X + column * 18;
            int y = VeyloriaInventoryLayout.STORAGE_HOTBAR_Y;
            this.addSlot(new Slot(playerInventory, inventorySlot, x, y));
        }
    }

    public int activeActionSlot() {
        return activeActionSlotData.get(0);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = slot.getItem();
        ItemStack copy = sourceStack.copy();
        if (index < LOADOUT_SLOT_COUNT) {
            if (!moveItemStackTo(sourceStack, STORAGE_START_INDEX, STORAGE_END_INDEX, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            int loadoutSlot = preferredLoadoutSlot(sourceStack);
            if (loadoutSlot < 0) {
                return ItemStack.EMPTY;
            }
            if (PlayerLoadoutData.isAmmoSlot(loadoutSlot)) {
                if (!moveAmmoToLoadout(sourceStack)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(sourceStack, loadoutSlot, loadoutSlot + 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (sourceStack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, sourceStack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    private int preferredLoadoutSlot(ItemStack stack) {
        if (owner instanceof ServerPlayer serverPlayer) {
            PlayerLoadoutService service = VeyloriaServerRuntime.instance().playerLoadoutService();
            return service.preferredLoadoutSlot(stack, service.loadout(serverPlayer));
        }

        RpgItemData item = RpgItemUtils.read(stack);
        if (item != null && item.equipSlot() != null) {
            return switch (item.equipSlot()) {
                case HELMET -> slotAvailable(PlayerLoadoutData.SLOT_HELMET);
                case PENDANT -> slotAvailable(PlayerLoadoutData.SLOT_PENDANT);
                case CHEST -> slotAvailable(PlayerLoadoutData.SLOT_CHEST);
                case LEGS -> slotAvailable(PlayerLoadoutData.SLOT_LEGS);
                case BOOTS -> slotAvailable(PlayerLoadoutData.SLOT_BOOTS);
                case RING -> preferredDuplicateSlot(PlayerLoadoutData.SLOT_RING_1, PlayerLoadoutData.SLOT_RING_2);
                case ACCESSORY -> preferredDuplicateSlot(PlayerLoadoutData.SLOT_ACCESSORY_1, PlayerLoadoutData.SLOT_ACCESSORY_2);
                case AMMO -> preferredAmmoSlot(stack);
                case WEAPON -> preferredWeaponSlot(item);
            };
        }
        if (isAmmoItem(stack, item)) {
            return preferredAmmoSlot(stack);
        }
        if (isConsumableItem(stack, item)) {
            return preferredConsumableSlot(stack);
        }
        return -1;
    }

    private int preferredWeaponSlot(RpgItemData item) {
        if (isRangedWeapon(item)) {
            return slotAvailable(PlayerLoadoutData.SLOT_RANGED_WEAPON);
        }
        int main = slotAvailable(PlayerLoadoutData.SLOT_MAIN_WEAPON);
        if (main >= 0) {
            return main;
        }
        return slotAvailable(PlayerLoadoutData.SLOT_SECONDARY_WEAPON);
    }

    private int preferredAmmoSlot(ItemStack stack) {
        ItemStack stored = loadoutContainer.getItem(PlayerLoadoutData.SLOT_AMMO);
        if (hasStackRoom(PlayerLoadoutData.SLOT_AMMO, stored, stack) || stored.isEmpty()) {
            return PlayerLoadoutData.SLOT_AMMO;
        }
        return -1;
    }

    private int preferredConsumableSlot(ItemStack stack) {
        for (int slot = PlayerLoadoutData.SLOT_CONSUMABLE_1; slot <= PlayerLoadoutData.SLOT_CONSUMABLE_4; slot++) {
            ItemStack stored = loadoutContainer.getItem(slot);
            if (hasStackRoom(slot, stored, stack)) {
                return slot;
            }
        }
        for (int slot = PlayerLoadoutData.SLOT_CONSUMABLE_1; slot <= PlayerLoadoutData.SLOT_CONSUMABLE_4; slot++) {
            if (loadoutContainer.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private int slotAvailable(int slotIndex) {
        return loadoutContainer.getItem(slotIndex).isEmpty() ? slotIndex : -1;
    }

    private int preferredDuplicateSlot(int primary, int secondary) {
        int first = slotAvailable(primary);
        if (first >= 0) {
            return first;
        }
        return slotAvailable(secondary);
    }

    private static boolean isRangedWeapon(RpgItemData item) {
        return switch (item.weaponType()) {
            case "bow", "wand" -> true;
            default -> false;
        };
    }

    private static boolean isAmmoItem(ItemStack stack, RpgItemData item) {
        return PlayerLoadoutService.isValidForLoadoutSlot(PlayerLoadoutData.SLOT_AMMO, stack);
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

    private boolean moveAmmoToLoadout(ItemStack sourceStack) {
        if (sourceStack.isEmpty()) {
            return false;
        }
        int ammoSlot = PlayerLoadoutData.SLOT_AMMO;
        ItemStack stored = loadoutContainer.getItem(ammoSlot);
        if (stored.isEmpty()) {
            int moved = Math.min(PlayerLoadoutData.AMMO_SLOT_MAX_STACK, sourceStack.getCount());
            if (moved <= 0) {
                return false;
            }
            ItemStack placed = sourceStack.copy();
            placed.setCount(moved);
            loadoutContainer.setItem(ammoSlot, placed);
            sourceStack.shrink(moved);
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(stored, sourceStack)) {
            return false;
        }
        int free = PlayerLoadoutData.AMMO_SLOT_MAX_STACK - stored.getCount();
        if (free <= 0) {
            return false;
        }
        int moved = Math.min(free, sourceStack.getCount());
        stored.grow(moved);
        loadoutContainer.setItem(ammoSlot, stored);
        sourceStack.shrink(moved);
        return moved > 0;
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

    private static final class LoadoutSlot extends Slot {
        private final int loadoutSlot;

        private LoadoutSlot(Container container, int loadoutSlot, int x, int y) {
            super(container, loadoutSlot, x, y);
            this.loadoutSlot = loadoutSlot;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return PlayerLoadoutService.isValidForLoadoutSlot(loadoutSlot, stack);
        }

        @Override
        public int getMaxStackSize() {
            return PlayerLoadoutData.isStackableLoadoutSlot(loadoutSlot)
                ? PlayerLoadoutData.maxStackForLoadoutSlot(loadoutSlot)
                : 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return PlayerLoadoutData.isStackableLoadoutSlot(loadoutSlot)
                ? PlayerLoadoutData.maxStackForLoadoutSlot(loadoutSlot)
                : 1;
        }
    }

    private static final class ServerLoadoutContainer implements Container {
        private final ServerPlayer player;

        private ServerLoadoutContainer(ServerPlayer player) {
            this.player = player;
        }

        @Override
        public int getContainerSize() {
            return LOADOUT_SLOT_COUNT;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < LOADOUT_SLOT_COUNT; i++) {
                if (!getItem(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return VeyloriaServerRuntime.instance().playerLoadoutService().loadout(player).getItem(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack current = getItem(slot);
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack removed = current.split(amount);
            setItem(slot, current);
            return removed;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack current = getItem(slot).copy();
            setItem(slot, ItemStack.EMPTY);
            return current;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            VeyloriaServerRuntime.instance().playerLoadoutService().setLoadoutItem(player, slot, stack);
        }

        @Override
        public void setChanged() {
            VeyloriaServerRuntime.instance().playerLoadoutService().initializePlayer(player);
        }

        @Override
        public boolean stillValid(Player player) {
            return player == this.player && player.isAlive();
        }

        @Override
        public void clearContent() {
            for (int slot = 0; slot < LOADOUT_SLOT_COUNT; slot++) {
                setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private static final class ServerActiveActionSlotData implements ContainerData {
        private final ServerPlayer player;

        private ServerActiveActionSlotData(ServerPlayer player) {
            this.player = player;
        }

        @Override
        public int get(int index) {
            return index == 0 ? VeyloriaServerRuntime.instance().playerLoadoutService().loadout(player).activeSlot() : 0;
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                VeyloriaServerRuntime.instance().playerLoadoutService().selectActionSlot(player, value);
            }
        }

        @Override
        public int getCount() {
            return 1;
        }
    }
}
