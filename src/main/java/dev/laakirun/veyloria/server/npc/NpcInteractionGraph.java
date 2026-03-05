package dev.laakirun.veyloria.server.npc;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NpcInteractionGraph {
    private final String startNodeId;
    private final Map<String, NpcInteractionNode> nodes;

    public NpcInteractionGraph(String startNodeId, Map<String, NpcInteractionNode> nodes) {
        this.startNodeId = startNodeId == null ? "" : startNodeId;
        this.nodes = nodes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(nodes));
    }

    public String startNodeId() {
        return startNodeId;
    }

    public Map<String, NpcInteractionNode> nodes() {
        return nodes;
    }

    public NpcInteractionNode node(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return null;
        }
        return nodes.get(nodeId);
    }
}
