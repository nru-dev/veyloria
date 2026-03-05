package dev.laakirun.veyloria.server.quest;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class PlayerQuestState {
    private static final String TAG_ACTIVE = "active";
    private static final String TAG_COMPLETED = "completed";
    private static final String TAG_COOLDOWNS = "cooldowns";
    private static final String TAG_CONTINUATIONS = "continuations";

    private static final String TAG_QUEST_ID = "questId";
    private static final String TAG_PROGRESS = "progress";
    private static final String TAG_TICK = "tick";
    private static final String TAG_NEXT_AVAILABLE_TICK = "nextAvailableTick";
    private static final String TAG_NPC_INSTANCE_ID = "npcInstanceId";
    private static final String TAG_INSTANCE_LEVEL = "instanceLevel";

    private final Map<String, QuestProgress> active = new LinkedHashMap<>();
    private final Map<String, Long> completed = new LinkedHashMap<>();
    private final Map<String, Long> cooldowns = new LinkedHashMap<>();
    private final Map<String, QuestContinuationUnlock> continuations = new LinkedHashMap<>();

    public static PlayerQuestState fromTag(CompoundTag tag) {
        PlayerQuestState state = new PlayerQuestState();

        ListTag activeList = tag.getList(TAG_ACTIVE, Tag.TAG_COMPOUND);
        for (int i = 0; i < activeList.size(); i++) {
            CompoundTag entry = activeList.getCompound(i);
            String questId = entry.getString(TAG_QUEST_ID);
            if (questId.isBlank()) {
                continue;
            }
            state.active.put(questId, QuestProgress.fromTag(entry.getCompound(TAG_PROGRESS)));
        }

        ListTag completedList = tag.getList(TAG_COMPLETED, Tag.TAG_COMPOUND);
        for (int i = 0; i < completedList.size(); i++) {
            CompoundTag entry = completedList.getCompound(i);
            String questId = entry.getString(TAG_QUEST_ID);
            if (questId.isBlank()) {
                continue;
            }
            state.completed.put(questId, entry.getLong(TAG_TICK));
        }

        ListTag cooldownList = tag.getList(TAG_COOLDOWNS, Tag.TAG_COMPOUND);
        for (int i = 0; i < cooldownList.size(); i++) {
            CompoundTag entry = cooldownList.getCompound(i);
            String questId = entry.getString(TAG_QUEST_ID);
            if (questId.isBlank()) {
                continue;
            }
            state.cooldowns.put(questId, entry.getLong(TAG_NEXT_AVAILABLE_TICK));
        }

        ListTag continuationList = tag.getList(TAG_CONTINUATIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < continuationList.size(); i++) {
            CompoundTag entry = continuationList.getCompound(i);
            String questId = entry.getString(TAG_QUEST_ID);
            if (questId.isBlank()) {
                continue;
            }
            state.continuations.put(questId, new QuestContinuationUnlock(entry.getString(TAG_NPC_INSTANCE_ID), entry.getInt(TAG_INSTANCE_LEVEL)));
        }

        return state;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        ListTag activeList = new ListTag();
        for (Map.Entry<String, QuestProgress> entry : active.entrySet()) {
            CompoundTag activeTag = new CompoundTag();
            activeTag.putString(TAG_QUEST_ID, entry.getKey());
            activeTag.put(TAG_PROGRESS, entry.getValue().save());
            activeList.add(activeTag);
        }
        tag.put(TAG_ACTIVE, activeList);

        ListTag completedList = new ListTag();
        for (Map.Entry<String, Long> entry : completed.entrySet()) {
            CompoundTag completedTag = new CompoundTag();
            completedTag.putString(TAG_QUEST_ID, entry.getKey());
            completedTag.putLong(TAG_TICK, entry.getValue());
            completedList.add(completedTag);
        }
        tag.put(TAG_COMPLETED, completedList);

        ListTag cooldownList = new ListTag();
        for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
            CompoundTag cooldownTag = new CompoundTag();
            cooldownTag.putString(TAG_QUEST_ID, entry.getKey());
            cooldownTag.putLong(TAG_NEXT_AVAILABLE_TICK, entry.getValue());
            cooldownList.add(cooldownTag);
        }
        tag.put(TAG_COOLDOWNS, cooldownList);

        ListTag continuationList = new ListTag();
        for (Map.Entry<String, QuestContinuationUnlock> entry : continuations.entrySet()) {
            CompoundTag continuationTag = new CompoundTag();
            continuationTag.putString(TAG_QUEST_ID, entry.getKey());
            continuationTag.putString(TAG_NPC_INSTANCE_ID, entry.getValue().npcInstanceId());
            continuationTag.putInt(TAG_INSTANCE_LEVEL, entry.getValue().instanceLevel());
            continuationList.add(continuationTag);
        }
        tag.put(TAG_CONTINUATIONS, continuationList);

        return tag;
    }

    public Map<String, QuestProgress> active() {
        return active;
    }

    public Map<String, Long> completed() {
        return completed;
    }

    public Map<String, Long> cooldowns() {
        return cooldowns;
    }

    public Map<String, QuestContinuationUnlock> continuations() {
        return continuations;
    }
}
