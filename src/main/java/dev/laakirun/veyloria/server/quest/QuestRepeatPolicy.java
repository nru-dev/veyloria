package dev.laakirun.veyloria.server.quest;

public record QuestRepeatPolicy(QuestRepeatType type, int cooldownSeconds) {
    public QuestRepeatPolicy {
        type = type == null ? QuestRepeatType.ONCE : type;
        cooldownSeconds = Math.max(0, cooldownSeconds);
    }

    public static QuestRepeatPolicy once() {
        return new QuestRepeatPolicy(QuestRepeatType.ONCE, 0);
    }

    public static QuestRepeatPolicy repeatable() {
        return new QuestRepeatPolicy(QuestRepeatType.REPEATABLE, 0);
    }

    public static QuestRepeatPolicy cooldown(int cooldownSeconds) {
        return new QuestRepeatPolicy(QuestRepeatType.COOLDOWN, cooldownSeconds);
    }
}
