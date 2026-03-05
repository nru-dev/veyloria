package dev.laakirun.veyloria.server.quest;

public record QuestContinuationUnlock(String npcInstanceId, int instanceLevel) {
    public QuestContinuationUnlock {
        npcInstanceId = npcInstanceId == null ? "" : npcInstanceId;
        instanceLevel = Math.max(1, instanceLevel);
    }
}
