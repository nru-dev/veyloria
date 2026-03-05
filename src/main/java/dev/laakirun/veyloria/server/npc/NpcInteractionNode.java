package dev.laakirun.veyloria.server.npc;

import java.util.List;

public record NpcInteractionNode(
    String nodeId,
    String message,
    List<NpcInteractionAction> actions
) {
    public NpcInteractionNode {
        nodeId = nodeId == null ? "" : nodeId;
        message = message == null ? "" : message;
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public NpcInteractionAction action(String actionId) {
        return action(actionId, "");
    }

    public NpcInteractionAction action(String actionId, String payload) {
        if (actionId == null || actionId.isBlank()) {
            return null;
        }
        for (NpcInteractionAction action : actions) {
            if (action.actionId().equals(actionId) && (payload == null || payload.isBlank() || action.payload().equals(payload))) {
                return action;
            }
        }
        return null;
    }
}
