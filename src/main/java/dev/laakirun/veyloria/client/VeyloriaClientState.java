package dev.laakirun.veyloria.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VeyloriaClientState {
    private static final VeyloriaClientState INSTANCE = new VeyloriaClientState();

    private boolean authRequired;
    private boolean registeredAccount;
    private boolean authenticated;
    private int copper;
    private int mana;
    private int manaMax;
    private String lastError = "";
    private final List<Notification> notifications = new ArrayList<>();
    private final List<Notification> notificationsView = Collections.unmodifiableList(notifications);
    private final Map<UUID, ResourceBars> barsByPlayer = new HashMap<>();

    private VeyloriaClientState() {
    }

    public static VeyloriaClientState instance() {
        return INSTANCE;
    }

    public void reset() {
        authRequired = false;
        registeredAccount = false;
        authenticated = false;
        copper = 0;
        mana = 0;
        manaMax = 0;
        lastError = "";
        notifications.clear();
        barsByPlayer.clear();
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

    public void setCopper(int copper) {
        this.copper = copper;
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

    public void prune(long gameTick) {
        Iterator<Notification> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().expiresAtTick() <= gameTick) {
                iterator.remove();
            }
        }
        barsByPlayer.entrySet().removeIf(entry -> entry.getValue().expiresAtTick() <= gameTick);
    }

    public record Notification(String text, long expiresAtTick) {
    }

    public record ResourceBars(int health, int healthMax, int mana, int manaMax, long expiresAtTick) {
    }
}
