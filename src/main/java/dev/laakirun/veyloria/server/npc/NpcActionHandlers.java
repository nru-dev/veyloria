package dev.laakirun.veyloria.server.npc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcActionHandlers {
    private final Map<String, NpcActionHandler> handlers = new ConcurrentHashMap<>();

    public void register(String actionId, NpcActionHandler handler) {
        if (actionId == null || actionId.isBlank() || handler == null) {
            return;
        }
        handlers.put(actionId, handler);
    }

    public NpcActionHandler get(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            return null;
        }
        return handlers.get(actionId);
    }
}
