package dev.laakirun.veyloria.server.npc;

import java.util.List;

public record NpcInteractionAction(
    String actionId,
    String payload,
    String label,
    String nextNodeId,
    List<NpcReward> rewards
) {
    public NpcInteractionAction {
        actionId = actionId == null ? "" : actionId;
        payload = payload == null ? "" : payload;
        label = label == null ? "" : label;
        nextNodeId = nextNodeId == null ? "" : nextNodeId;
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
    }
}
