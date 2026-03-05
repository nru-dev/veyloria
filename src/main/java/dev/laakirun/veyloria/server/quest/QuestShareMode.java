package dev.laakirun.veyloria.server.quest;

public enum QuestShareMode {
    PERSONAL,
    PARTY,
    NEARBY_PARTY;

    public static QuestShareMode from(String id) {
        if (id == null || id.isBlank()) {
            return PERSONAL;
        }
        for (QuestShareMode mode : values()) {
            if (mode.name().equalsIgnoreCase(id)) {
                return mode;
            }
        }
        return PERSONAL;
    }
}
