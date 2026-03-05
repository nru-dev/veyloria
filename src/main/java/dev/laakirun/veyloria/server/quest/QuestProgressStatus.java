package dev.laakirun.veyloria.server.quest;

public enum QuestProgressStatus {
    ACTIVE,
    READY_TO_TURN_IN;

    public static QuestProgressStatus from(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        for (QuestProgressStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return ACTIVE;
    }
}
