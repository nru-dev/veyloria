package dev.laakirun.veyloria.client;

import dev.laakirun.veyloria.common.item.PlayerLoadoutData;
import dev.laakirun.veyloria.common.model.BaseStats;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public final class VeyloriaClientState {
    private static final VeyloriaClientState INSTANCE = new VeyloriaClientState();

    private boolean authRequired;
    private boolean registeredAccount;
    private boolean authenticated;
    private int level = 1;
    private int xpCurrent;
    private int xpNext = 1;
    private int copper;
    private int mana;
    private int manaMax;
    private BaseStats totalStats = BaseStats.ZERO;
    private int activeLoadoutSlot = PlayerLoadoutData.SLOT_MAIN_WEAPON;
    private int autoConsumableSlot = -1;
    private long autoConsumableDeadlineTick;
    private UUID currentTargetUuid;
    private long currentTargetExpiresAtTick;
    private String lastError = "";
    private final List<Notification> notifications = new ArrayList<>();
    private final List<Notification> notificationsView = Collections.unmodifiableList(notifications);
    private final List<QuestEntry> activeQuests = new ArrayList<>();
    private final List<QuestEntry> activeQuestsView = Collections.unmodifiableList(activeQuests);
    private final Map<UUID, ResourceBars> barsByPlayer = new HashMap<>();
    private final ItemStack[] loadoutItems = new ItemStack[PlayerLoadoutData.SLOT_COUNT];
    private ZoneAnnouncement zoneAnnouncement;

    private VeyloriaClientState() {
        clearLoadout();
    }

    public static VeyloriaClientState instance() {
        return INSTANCE;
    }

    public void reset() {
        authRequired = false;
        registeredAccount = false;
        authenticated = false;
        level = 1;
        xpCurrent = 0;
        xpNext = 1;
        copper = 0;
        mana = 0;
        manaMax = 0;
        totalStats = BaseStats.ZERO;
        autoConsumableSlot = -1;
        autoConsumableDeadlineTick = 0L;
        currentTargetUuid = null;
        currentTargetExpiresAtTick = 0L;
        lastError = "";
        notifications.clear();
        activeQuests.clear();
        barsByPlayer.clear();
        zoneAnnouncement = null;
        clearLoadout();
    }

    public boolean authRequired() {
        return authRequired;
    }

    public void setAuthRequired(boolean authRequired, boolean registeredAccount) {
        this.authRequired = authRequired;
        this.registeredAccount = registeredAccount;
        if (authRequired) {
            this.authenticated = false;
        }
    }

    public boolean registeredAccount() {
        return registeredAccount;
    }

    public boolean authenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        if (authenticated) {
            this.authRequired = false;
            this.lastError = "";
        }
    }

    public int copper() {
        return copper;
    }

    public int level() {
        return level;
    }

    public int xpCurrent() {
        return xpCurrent;
    }

    public int xpNext() {
        return xpNext;
    }

    public void setCopper(int copper) {
        this.copper = copper;
    }

    public void setProfile(int level, int xpCurrent, int xpNext, int copper, int mana, int manaMax, BaseStats totalStats) {
        this.level = Math.max(1, level);
        this.xpCurrent = Math.max(0, xpCurrent);
        this.xpNext = Math.max(1, xpNext);
        this.totalStats = totalStats == null ? BaseStats.ZERO : totalStats;
        setCopper(copper);
        setMana(mana, manaMax);
    }

    public int mana() {
        return mana;
    }

    public void setMana(int mana, int manaMax) {
        this.mana = Math.max(0, mana);
        this.manaMax = Math.max(0, manaMax);
        if (this.manaMax == 0) {
            this.mana = 0;
        } else if (this.mana > this.manaMax) {
            this.mana = this.manaMax;
        }
    }

    public int manaMax() {
        return manaMax;
    }

    public BaseStats totalStats() {
        return totalStats;
    }

    public int activeLoadoutSlot() {
        return activeLoadoutSlot;
    }

    public int autoConsumableSlot() {
        return autoConsumableSlot;
    }

    public long autoConsumableDeadlineTick() {
        return autoConsumableDeadlineTick;
    }

    public boolean isAutoUsingConsumable() {
        return autoConsumableSlot >= PlayerLoadoutData.SLOT_CONSUMABLE_1
            && autoConsumableSlot <= PlayerLoadoutData.SLOT_CONSUMABLE_4;
    }

    public UUID currentTargetUuid() {
        return currentTargetUuid;
    }

    public void setCurrentTarget(UUID targetUuid, long expiresAtTick) {
        currentTargetUuid = targetUuid;
        currentTargetExpiresAtTick = Math.max(0L, expiresAtTick);
    }

    public void startAutoConsumableUse(int slot, long deadlineTick) {
        if (!PlayerLoadoutData.isConsumableSlot(slot)) {
            stopAutoConsumableUse();
            return;
        }
        autoConsumableSlot = slot;
        autoConsumableDeadlineTick = Math.max(0L, deadlineTick);
    }

    public void stopAutoConsumableUse() {
        autoConsumableSlot = -1;
        autoConsumableDeadlineTick = 0L;
    }

    public ItemStack loadoutItem(int slot) {
        if (slot < 0 || slot >= loadoutItems.length) {
            return ItemStack.EMPTY;
        }
        return loadoutItems[slot];
    }

    public void setLoadout(PlayerLoadoutData loadout) {
        if (loadout == null) {
            clearLoadout();
            return;
        }
        for (int slot = 0; slot < PlayerLoadoutData.SLOT_COUNT; slot++) {
            loadoutItems[slot] = loadout.getItem(slot).copy();
        }
        activeLoadoutSlot = loadout.activeSlot();
    }

    public String lastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public void pushNotification(String text, long expiresAtTick) {
        notifications.add(new Notification(text, expiresAtTick));
    }

    public void setPlayerBars(UUID playerUuid, int health, int healthMax, int mana, int manaMax, long expiresAtTick) {
        if (playerUuid == null) {
            return;
        }
        barsByPlayer.put(playerUuid, new ResourceBars(
            Math.max(0, health),
            Math.max(1, healthMax),
            Math.max(0, mana),
            Math.max(0, manaMax),
            expiresAtTick
        ));
    }

    public ResourceBars playerBars(UUID playerUuid) {
        return barsByPlayer.get(playerUuid);
    }

    public List<Notification> notifications() {
        return notificationsView;
    }

    public void showZoneAnnouncement(String zoneName, String levelRange, long shownAtTick) {
        if (zoneName == null || zoneName.isBlank()) {
            return;
        }
        zoneAnnouncement = new ZoneAnnouncement(zoneName, levelRange == null ? "" : levelRange, Math.max(0L, shownAtTick));
    }

    public ZoneAnnouncement zoneAnnouncement() {
        return zoneAnnouncement;
    }

    public void setQuestState(CompoundTag stateTag) {
        activeQuests.clear();
        if (stateTag == null) {
            return;
        }
        ListTag list = stateTag.getList("active", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            activeQuests.add(new QuestEntry(
                entry.getString("questId"),
                entry.getString("title"),
                entry.getString("objective"),
                entry.getString("progress"),
                entry.getBoolean("readyToTurnIn"),
                entry.getInt("level")
            ));
        }
    }

    public List<QuestEntry> activeQuests() {
        return activeQuestsView;
    }

    public QuestEntry trackedQuest() {
        return activeQuests.isEmpty() ? null : activeQuests.get(0);
    }

    public void prune(long gameTick) {
        Iterator<Notification> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().expiresAtTick() <= gameTick) {
                iterator.remove();
            }
        }
        barsByPlayer.entrySet().removeIf(entry -> entry.getValue().expiresAtTick() <= gameTick);
        if (isAutoUsingConsumable() && autoConsumableDeadlineTick > 0L && autoConsumableDeadlineTick <= gameTick) {
            stopAutoConsumableUse();
        }
        if (currentTargetUuid != null && currentTargetExpiresAtTick > 0L && currentTargetExpiresAtTick <= gameTick) {
            currentTargetUuid = null;
            currentTargetExpiresAtTick = 0L;
        }
        if (zoneAnnouncement != null && zoneAnnouncement.shownAtTick() + ZoneAnnouncement.TOTAL_DURATION_TICKS <= gameTick) {
            zoneAnnouncement = null;
        }
    }

    private void clearLoadout() {
        for (int slot = 0; slot < loadoutItems.length; slot++) {
            loadoutItems[slot] = ItemStack.EMPTY;
        }
        activeLoadoutSlot = PlayerLoadoutData.SLOT_MAIN_WEAPON;
    }

    public record Notification(String text, long expiresAtTick) {
    }

    public record ResourceBars(int health, int healthMax, int mana, int manaMax, long expiresAtTick) {
    }

    public record ZoneAnnouncement(String zoneName, String levelRange, long shownAtTick) {
        public static final int FADE_IN_TICKS = 16;
        public static final int HOLD_TICKS = 56;
        public static final int FADE_OUT_TICKS = 28;
        public static final int TOTAL_DURATION_TICKS = FADE_IN_TICKS + HOLD_TICKS + FADE_OUT_TICKS;
    }

    public record QuestEntry(String questId, String title, String objective, String progress, boolean readyToTurnIn, int level) {
    }
}
