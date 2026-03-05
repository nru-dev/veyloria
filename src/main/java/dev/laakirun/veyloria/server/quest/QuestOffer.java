package dev.laakirun.veyloria.server.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record QuestOffer(
    String questId,
    int instanceLevel,
    long rollSeed,
    long generatedAtTick
) {
    private static final String TAG_QUEST_ID = "questId";
    private static final String TAG_INSTANCE_LEVEL = "instanceLevel";
    private static final String TAG_ROLL_SEED = "rollSeed";
    private static final String TAG_GENERATED_AT_TICK = "generatedAtTick";

    public QuestOffer {
        questId = questId == null ? "" : questId;
        instanceLevel = Math.max(1, instanceLevel);
    }

    public ResourceLocation questIdLocation() {
        return questId.isBlank() ? null : ResourceLocation.tryParse(questId);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_QUEST_ID, questId);
        tag.putInt(TAG_INSTANCE_LEVEL, instanceLevel);
        tag.putLong(TAG_ROLL_SEED, rollSeed);
        tag.putLong(TAG_GENERATED_AT_TICK, generatedAtTick);
        return tag;
    }

    public static QuestOffer fromTag(CompoundTag tag) {
        return new QuestOffer(
            tag.getString(TAG_QUEST_ID),
            tag.getInt(TAG_INSTANCE_LEVEL),
            tag.getLong(TAG_ROLL_SEED),
            tag.getLong(TAG_GENERATED_AT_TICK)
        );
    }
}
