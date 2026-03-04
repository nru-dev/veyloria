package dev.laakirun.veyloria.server.game;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MobInstance {
    private final UUID entityUuid;
    private final long templateId;
    private final long spawnGroupId;
    private final Map<UUID, Long> participantTicks = new ConcurrentHashMap<>();

    public MobInstance(UUID entityUuid, long templateId, long spawnGroupId) {
        this.entityUuid = entityUuid;
        this.templateId = templateId;
        this.spawnGroupId = spawnGroupId;
    }

    public UUID entityUuid() {
        return entityUuid;
    }

    public long templateId() {
        return templateId;
    }

    public long spawnGroupId() {
        return spawnGroupId;
    }

    public void recordParticipant(UUID playerUuid, long gameTick) {
        participantTicks.put(playerUuid, gameTick);
    }

    public Map<UUID, Long> participants() {
        return participantTicks;
    }
}
