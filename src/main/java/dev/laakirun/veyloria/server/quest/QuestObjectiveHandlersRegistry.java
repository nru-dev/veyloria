package dev.laakirun.veyloria.server.quest;

import dev.laakirun.veyloria.common.VeyloriaConstants;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

public final class QuestObjectiveHandlersRegistry {
    public static final ResourceLocation KILL_BY_DISPOSITION =
        ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "kill_by_disposition");
    public static final ResourceLocation COMBAT_TIME =
        ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "combat_time");

    private final Map<ResourceLocation, QuestObjectiveHandler> handlers = new ConcurrentHashMap<>();

    public void registerBuiltins() {
        handlers.clear();
        register(KILL_BY_DISPOSITION, new KillByDispositionObjectiveHandler());
        register(COMBAT_TIME, new CombatTimeObjectiveHandler());
    }

    public void register(ResourceLocation type, QuestObjectiveHandler handler) {
        if (type == null || handler == null) {
            return;
        }
        handlers.put(type, handler);
    }

    public QuestObjectiveHandler get(ResourceLocation type) {
        if (type == null) {
            return null;
        }
        return handlers.get(type);
    }

    public Iterable<QuestObjectiveHandler> all() {
        return handlers.values();
    }
}
