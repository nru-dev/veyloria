package dev.laakirun.veyloria.server.auth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {
    private final Map<Long, UUID> accountToPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerToAccount = new ConcurrentHashMap<>();

    public boolean hasActiveSession(long accountId, UUID currentPlayer) {
        UUID activePlayer = accountToPlayer.get(accountId);
        return activePlayer != null && !activePlayer.equals(currentPlayer);
    }

    public void register(long accountId, UUID playerUuid) {
        accountToPlayer.put(accountId, playerUuid);
        playerToAccount.put(playerUuid, accountId);
    }

    public void unregister(UUID playerUuid) {
        Long accountId = playerToAccount.remove(playerUuid);
        if (accountId != null) {
            accountToPlayer.remove(accountId, playerUuid);
        }
    }

    public boolean isAuthenticated(UUID playerUuid) {
        return playerToAccount.containsKey(playerUuid);
    }

    public Long accountId(UUID playerUuid) {
        return playerToAccount.get(playerUuid);
    }
}
