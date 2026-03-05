package dev.laakirun.veyloria.server.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class QuestParamResolver {
    public CompoundTag resolve(QuestObjectiveDefinition objective, int instanceLevel, ResourceLocation locationId, long rollSeed) {
        CompoundTag resolved = objective == null ? new CompoundTag() : objective.params();
        resolved.putInt("questLevel", Math.max(1, instanceLevel));
        if (locationId != null) {
            resolved.putString("resolvedLocationId", locationId.toString());
        }
        resolved.putLong("rollSeed", rollSeed);
        return resolved;
    }
}
