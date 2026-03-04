package dev.laakirun.veyloria.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class VeyloriaClientState {
    private static final VeyloriaClientState INSTANCE = new VeyloriaClientState();

    private boolean authRequired;
    private boolean registeredAccount;
    private boolean authenticated;
    private int copper;
    private String lastError = "";
    private final List<Notification> notifications = new ArrayList<>();

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
        lastError = "";
        notifications.clear();
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

    public String lastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public void pushNotification(String text, long expiresAtTick) {
        notifications.add(new Notification(text, expiresAtTick));
    }

    public List<Notification> notifications() {
        return List.copyOf(notifications);
    }

    public void prune(long gameTick) {
        Iterator<Notification> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().expiresAtTick() <= gameTick) {
                iterator.remove();
            }
        }
    }

    public record Notification(String text, long expiresAtTick) {
    }
}
