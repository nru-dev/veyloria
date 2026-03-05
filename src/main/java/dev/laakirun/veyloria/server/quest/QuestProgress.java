package dev.laakirun.veyloria.server.quest;

import net.minecraft.nbt.CompoundTag;

public final class QuestProgress {
    private static final String TAG_QUEST_ID = "questId";
    private static final String TAG_GIVER_NPC_INSTANCE_ID = "giverNpcInstanceId";
    private static final String TAG_INSTANCE_LEVEL = "instanceLevel";
    private static final String TAG_OBJECTIVE_INDEX = "objectiveIndex";
    private static final String TAG_COUNTERS = "counters";
    private static final String TAG_STARTED_TICK = "startedTick";
    private static final String TAG_STATUS = "status";
    private static final String TAG_RESOLVED_PARAMS = "resolvedParams";

    private String questId;
    private String giverNpcInstanceId;
    private int instanceLevel;
    private int objectiveIndex;
    private CompoundTag counters;
    private long startedTick;
    private QuestProgressStatus status;
    private CompoundTag resolvedParams;

    public QuestProgress(String questId, String giverNpcInstanceId, int instanceLevel, int objectiveIndex, CompoundTag counters, long startedTick,
                         QuestProgressStatus status, CompoundTag resolvedParams) {
        this.questId = questId == null ? "" : questId;
        this.giverNpcInstanceId = giverNpcInstanceId == null ? "" : giverNpcInstanceId;
        this.instanceLevel = Math.max(1, instanceLevel);
        this.objectiveIndex = Math.max(0, objectiveIndex);
        this.counters = counters == null ? new CompoundTag() : counters.copy();
        this.startedTick = Math.max(0L, startedTick);
        this.status = status == null ? QuestProgressStatus.ACTIVE : status;
        this.resolvedParams = resolvedParams == null ? new CompoundTag() : resolvedParams.copy();
    }

    public static QuestProgress fromTag(CompoundTag tag) {
        return new QuestProgress(
            tag.getString(TAG_QUEST_ID),
            tag.getString(TAG_GIVER_NPC_INSTANCE_ID),
            tag.getInt(TAG_INSTANCE_LEVEL),
            tag.getInt(TAG_OBJECTIVE_INDEX),
            tag.getCompound(TAG_COUNTERS),
            tag.getLong(TAG_STARTED_TICK),
            QuestProgressStatus.from(tag.getString(TAG_STATUS)),
            tag.getCompound(TAG_RESOLVED_PARAMS)
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_QUEST_ID, questId);
        tag.putString(TAG_GIVER_NPC_INSTANCE_ID, giverNpcInstanceId);
        tag.putInt(TAG_INSTANCE_LEVEL, instanceLevel);
        tag.putInt(TAG_OBJECTIVE_INDEX, objectiveIndex);
        tag.put(TAG_COUNTERS, counters.copy());
        tag.putLong(TAG_STARTED_TICK, startedTick);
        tag.putString(TAG_STATUS, status.name());
        tag.put(TAG_RESOLVED_PARAMS, resolvedParams.copy());
        return tag;
    }

    public String questId() {
        return questId;
    }

    public String giverNpcInstanceId() {
        return giverNpcInstanceId;
    }

    public int instanceLevel() {
        return instanceLevel;
    }

    public int objectiveIndex() {
        return objectiveIndex;
    }

    public void setObjectiveIndex(int objectiveIndex) {
        this.objectiveIndex = Math.max(0, objectiveIndex);
    }

    public CompoundTag counters() {
        return counters;
    }

    public long startedTick() {
        return startedTick;
    }

    public QuestProgressStatus status() {
        return status;
    }

    public void setStatus(QuestProgressStatus status) {
        this.status = status == null ? QuestProgressStatus.ACTIVE : status;
    }

    public CompoundTag resolvedParams() {
        return resolvedParams;
    }

    public void setResolvedParams(CompoundTag resolvedParams) {
        this.resolvedParams = resolvedParams == null ? new CompoundTag() : resolvedParams.copy();
    }

    public int counterInt(String key) {
        return counters.getInt(key);
    }

    public void setCounterInt(String key, int value) {
        counters.putInt(key, Math.max(0, value));
    }

    public long counterLong(String key) {
        return counters.getLong(key);
    }

    public void setCounterLong(String key, long value) {
        counters.putLong(key, Math.max(0L, value));
    }
}
