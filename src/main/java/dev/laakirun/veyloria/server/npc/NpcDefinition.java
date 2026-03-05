package dev.laakirun.veyloria.server.npc;

import dev.laakirun.veyloria.common.npc.NpcAppearance;
import dev.laakirun.veyloria.common.npc.NpcRole;
import dev.laakirun.veyloria.common.npc.NpcStats;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class NpcDefinition {
    private final ResourceLocation id;
    private final String displayName;
    private final String nameKey;
    private final Set<NpcRole> roles;
    private final NpcAppearance appearance;
    private final NpcStats stats;
    private final int respawnSeconds;
    private final NpcInteractionGraph interactionGraph;
    private final List<String> questPoolIds;
    private final List<String> questChainIds;
    private final String tradeTableId;
    private final String forgeTableId;

    private NpcDefinition(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.displayName = builder.displayName == null ? "" : builder.displayName;
        this.nameKey = builder.nameKey == null ? "" : builder.nameKey;
        this.roles = Set.copyOf(new LinkedHashSet<>(builder.roles));
        this.appearance = builder.appearance == null ? NpcAppearance.WITHER : builder.appearance;
        this.stats = builder.stats == null ? new NpcStats(40.0D, 0.0D, 1.0D) : builder.stats;
        this.respawnSeconds = Math.max(1, builder.respawnSeconds);
        this.interactionGraph = builder.interactionGraph == null ? new NpcInteractionGraph("", java.util.Map.of()) : builder.interactionGraph;
        this.questPoolIds = List.copyOf(builder.questPoolIds);
        this.questChainIds = List.copyOf(builder.questChainIds);
        this.tradeTableId = builder.tradeTableId == null ? "" : builder.tradeTableId;
        this.forgeTableId = builder.forgeTableId == null ? "" : builder.forgeTableId;
    }

    public ResourceLocation id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String nameKey() {
        return nameKey;
    }

    public Set<NpcRole> roles() {
        return roles;
    }

    public NpcAppearance appearance() {
        return appearance;
    }

    public NpcStats stats() {
        return stats;
    }

    public int respawnSeconds() {
        return respawnSeconds;
    }

    public NpcInteractionGraph interactionGraph() {
        return interactionGraph;
    }

    public List<String> questPoolIds() {
        return questPoolIds;
    }

    public List<String> questChainIds() {
        return questChainIds;
    }

    public String tradeTableId() {
        return tradeTableId;
    }

    public String forgeTableId() {
        return forgeTableId;
    }

    public NpcInteractionNode startNode() {
        return interactionGraph.node(interactionGraph.startNodeId());
    }

    public NpcInteractionNode node(String nodeId) {
        return interactionGraph.node(nodeId);
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private String displayName;
        private String nameKey;
        private final Set<NpcRole> roles = new LinkedHashSet<>();
        private NpcAppearance appearance = NpcAppearance.WITHER;
        private NpcStats stats = new NpcStats(40.0D, 0.0D, 1.0D);
        private int respawnSeconds = 60;
        private NpcInteractionGraph interactionGraph;
        private final List<String> questPoolIds = new java.util.ArrayList<>();
        private final List<String> questChainIds = new java.util.ArrayList<>();
        private String tradeTableId = "";
        private String forgeTableId = "";

        private Builder(ResourceLocation id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder nameKey(String nameKey) {
            this.nameKey = nameKey;
            return this;
        }

        public Builder addRole(NpcRole role) {
            if (role != null) {
                this.roles.add(role);
            }
            return this;
        }

        public Builder appearance(NpcAppearance appearance) {
            this.appearance = appearance;
            return this;
        }

        public Builder stats(NpcStats stats) {
            this.stats = stats;
            return this;
        }

        public Builder respawnSeconds(int respawnSeconds) {
            this.respawnSeconds = respawnSeconds;
            return this;
        }

        public Builder interactionGraph(NpcInteractionGraph interactionGraph) {
            this.interactionGraph = interactionGraph;
            return this;
        }

        public Builder questPoolIds(List<String> questPoolIds) {
            this.questPoolIds.clear();
            if (questPoolIds != null) {
                this.questPoolIds.addAll(questPoolIds);
            }
            return this;
        }

        public Builder questChainIds(List<String> questChainIds) {
            this.questChainIds.clear();
            if (questChainIds != null) {
                this.questChainIds.addAll(questChainIds);
            }
            return this;
        }

        public Builder tradeTableId(String tradeTableId) {
            this.tradeTableId = tradeTableId;
            return this;
        }

        public Builder forgeTableId(String forgeTableId) {
            this.forgeTableId = forgeTableId;
            return this;
        }

        public NpcDefinition build() {
            return new NpcDefinition(this);
        }
    }
}
