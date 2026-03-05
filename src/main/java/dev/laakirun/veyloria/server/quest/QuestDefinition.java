package dev.laakirun.veyloria.server.quest;

import dev.laakirun.veyloria.server.npc.NpcReward;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class QuestDefinition {
    private final ResourceLocation id;
    private final String title;
    private final String titleKey;
    private final String description;
    private final String descriptionKey;
    private final ResourceLocation locationId;
    private final QuestRepeatPolicy repeatPolicy;
    private final boolean offerable;
    private final ResourceLocation parentQuestId;
    private final ResourceLocation nextQuestId;
    private final String chainId;
    private final ResourceLocation giverNpcDefinitionId;
    private final ResourceLocation turnInNpcDefinitionId;
    private final QuestLevelPolicy levelPolicy;
    private final List<QuestObjectiveDefinition> objectives;
    private final List<NpcReward> rewards;

    private QuestDefinition(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.title = builder.title == null ? "" : builder.title;
        this.titleKey = builder.titleKey == null ? "" : builder.titleKey;
        this.description = builder.description == null ? "" : builder.description;
        this.descriptionKey = builder.descriptionKey == null ? "" : builder.descriptionKey;
        this.locationId = builder.locationId;
        this.repeatPolicy = builder.repeatPolicy == null ? QuestRepeatPolicy.once() : builder.repeatPolicy;
        this.offerable = builder.offerable;
        this.parentQuestId = builder.parentQuestId;
        this.nextQuestId = builder.nextQuestId;
        this.chainId = builder.chainId == null ? "" : builder.chainId;
        this.giverNpcDefinitionId = builder.giverNpcDefinitionId;
        this.turnInNpcDefinitionId = builder.turnInNpcDefinitionId;
        this.levelPolicy = builder.levelPolicy == null ? QuestLevelPolicy.fixed(1) : builder.levelPolicy;
        this.objectives = List.copyOf(builder.objectives);
        this.rewards = List.copyOf(builder.rewards);
    }

    public ResourceLocation id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String titleKey() {
        return titleKey;
    }

    public String description() {
        return description;
    }

    public String descriptionKey() {
        return descriptionKey;
    }

    public ResourceLocation locationId() {
        return locationId;
    }

    public QuestRepeatPolicy repeatPolicy() {
        return repeatPolicy;
    }

    public boolean offerable() {
        return offerable;
    }

    public ResourceLocation parentQuestId() {
        return parentQuestId;
    }

    public ResourceLocation nextQuestId() {
        return nextQuestId;
    }

    public String chainId() {
        return chainId;
    }

    public ResourceLocation giverNpcDefinitionId() {
        return giverNpcDefinitionId;
    }

    public ResourceLocation turnInNpcDefinitionId() {
        return turnInNpcDefinitionId;
    }

    public QuestLevelPolicy levelPolicy() {
        return levelPolicy;
    }

    public List<QuestObjectiveDefinition> objectives() {
        return objectives;
    }

    public List<NpcReward> rewards() {
        return rewards;
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private String title;
        private String titleKey;
        private String description;
        private String descriptionKey;
        private ResourceLocation locationId;
        private QuestRepeatPolicy repeatPolicy = QuestRepeatPolicy.once();
        private boolean offerable;
        private ResourceLocation parentQuestId;
        private ResourceLocation nextQuestId;
        private String chainId;
        private ResourceLocation giverNpcDefinitionId;
        private ResourceLocation turnInNpcDefinitionId;
        private QuestLevelPolicy levelPolicy = QuestLevelPolicy.fixed(1);
        private final List<QuestObjectiveDefinition> objectives = new ArrayList<>();
        private final List<NpcReward> rewards = new ArrayList<>();

        private Builder(ResourceLocation id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder titleKey(String titleKey) {
            this.titleKey = titleKey;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder descriptionKey(String descriptionKey) {
            this.descriptionKey = descriptionKey;
            return this;
        }

        public Builder locationId(ResourceLocation locationId) {
            this.locationId = locationId;
            return this;
        }

        public Builder repeatPolicy(QuestRepeatPolicy repeatPolicy) {
            this.repeatPolicy = repeatPolicy;
            return this;
        }

        public Builder offerable(boolean offerable) {
            this.offerable = offerable;
            return this;
        }

        public Builder parentQuestId(ResourceLocation parentQuestId) {
            this.parentQuestId = parentQuestId;
            return this;
        }

        public Builder nextQuestId(ResourceLocation nextQuestId) {
            this.nextQuestId = nextQuestId;
            return this;
        }

        public Builder chainId(String chainId) {
            this.chainId = chainId;
            return this;
        }

        public Builder giverNpcDefinitionId(ResourceLocation giverNpcDefinitionId) {
            this.giverNpcDefinitionId = giverNpcDefinitionId;
            return this;
        }

        public Builder turnInNpcDefinitionId(ResourceLocation turnInNpcDefinitionId) {
            this.turnInNpcDefinitionId = turnInNpcDefinitionId;
            return this;
        }

        public Builder levelPolicy(QuestLevelPolicy levelPolicy) {
            this.levelPolicy = levelPolicy;
            return this;
        }

        public Builder addObjective(QuestObjectiveDefinition objective) {
            if (objective != null) {
                this.objectives.add(objective);
            }
            return this;
        }

        public Builder objectives(List<QuestObjectiveDefinition> objectives) {
            this.objectives.clear();
            if (objectives != null) {
                this.objectives.addAll(objectives);
            }
            return this;
        }

        public Builder addReward(NpcReward reward) {
            if (reward != null) {
                this.rewards.add(reward);
            }
            return this;
        }

        public Builder rewards(List<NpcReward> rewards) {
            this.rewards.clear();
            if (rewards != null) {
                this.rewards.addAll(rewards);
            }
            return this;
        }

        public QuestDefinition build() {
            return new QuestDefinition(this);
        }
    }
}
