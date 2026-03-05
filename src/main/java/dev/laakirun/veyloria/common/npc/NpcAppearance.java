package dev.laakirun.veyloria.common.npc;

public enum NpcAppearance {
    WITHER("wither");

    private final String id;

    NpcAppearance(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static NpcAppearance fromId(String id) {
        if (id == null || id.isBlank()) {
            return WITHER;
        }
        for (NpcAppearance appearance : values()) {
            if (appearance.id.equalsIgnoreCase(id)) {
                return appearance;
            }
        }
        return WITHER;
    }
}
