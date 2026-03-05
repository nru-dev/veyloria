package dev.laakirun.veyloria.server.quest;

public record QuestNpcEntry(
    String questId,
    String title,
    String progressText,
    int instanceLevel,
    QuestNpcActionType actionType,
    boolean available,
    String reason
) {
}
