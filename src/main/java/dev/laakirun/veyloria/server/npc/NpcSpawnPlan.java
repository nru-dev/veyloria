package dev.laakirun.veyloria.server.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record NpcSpawnPlan(
    String instanceId,
    ResourceLocation definitionId,
    ResourceKey<Level> dimension,
    BlockPos spawnPos,
    String groupKey
) {
    public NpcSpawnPlan {
        instanceId = instanceId == null ? "" : instanceId;
        groupKey = groupKey == null ? "" : groupKey;
    }
}
