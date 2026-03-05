package dev.laakirun.veyloria.server.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class QuestObjectiveDefinition {
    private final ResourceLocation type;
    private final CompoundTag params;
    private final QuestShareMode shareMode;
    private final String displayText;
    private final String displayKey;
    private final double targetValue;

    public QuestObjectiveDefinition(ResourceLocation type, CompoundTag params, QuestShareMode shareMode, String displayText, String displayKey,
                                    double targetValue) {
        this.type = type;
        this.params = params == null ? new CompoundTag() : params.copy();
        this.shareMode = shareMode == null ? QuestShareMode.PERSONAL : shareMode;
        this.displayText = displayText == null ? "" : displayText;
        this.displayKey = displayKey == null ? "" : displayKey;
        this.targetValue = targetValue;
    }

    public ResourceLocation type() {
        return type;
    }

    public CompoundTag params() {
        return params.copy();
    }

    public QuestShareMode shareMode() {
        return shareMode;
    }

    public String displayText() {
        return displayText;
    }

    public String displayKey() {
        return displayKey;
    }

    public double targetValue() {
        return targetValue;
    }
}
