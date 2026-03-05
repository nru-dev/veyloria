package dev.laakirun.veyloria.server.quest;

public record QuestActionResult(boolean success, String message) {
    public static QuestActionResult ok(String message) {
        return new QuestActionResult(true, message == null ? "" : message);
    }

    public static QuestActionResult fail(String message) {
        return new QuestActionResult(false, message == null ? "" : message);
    }
}
