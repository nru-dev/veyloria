package dev.laakirun.veyloria.server.quest;

public record QuestLevelPolicy(
    QuestLevelPolicyType type,
    int fixedLevel,
    int minLevel,
    int maxLevel,
    int offsetMin,
    int offsetMax
) {
    public QuestLevelPolicy {
        type = type == null ? QuestLevelPolicyType.FIXED : type;
        fixedLevel = Math.max(1, fixedLevel);
        minLevel = Math.max(1, minLevel);
        maxLevel = Math.max(minLevel, maxLevel);
        offsetMin = Math.max(-50, offsetMin);
        offsetMax = Math.max(offsetMin, offsetMax);
    }

    public static QuestLevelPolicy fixed(int level) {
        return new QuestLevelPolicy(QuestLevelPolicyType.FIXED, level, 1, 1, 0, 0);
    }

    public static QuestLevelPolicy range(int minLevel, int maxLevel) {
        return new QuestLevelPolicy(QuestLevelPolicyType.RANGE, 1, minLevel, maxLevel, 0, 0);
    }

    public static QuestLevelPolicy scaledToLocation() {
        return new QuestLevelPolicy(QuestLevelPolicyType.SCALED_TO_LOCATION, 1, 1, 1, 0, 0);
    }

    public static QuestLevelPolicy scaledToLocationWithOffset(int offsetMin, int offsetMax) {
        return new QuestLevelPolicy(QuestLevelPolicyType.SCALED_TO_LOCATION_WITH_OFFSET, 1, 1, 1, offsetMin, offsetMax);
    }
}
