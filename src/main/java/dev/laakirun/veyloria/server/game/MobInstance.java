package dev.laakirun.veyloria.server.game;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MobInstance {
    private final UUID entityUuid;
    private final long templateId;
    private final long spawnGroupId;
    private final Map<UUID, Long> participantTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Double> threatByPlayer = new ConcurrentHashMap<>();

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

    public void recordThreat(UUID playerUuid, double damage) {
        threatByPlayer.merge(playerUuid, Math.max(0.0D, damage), Double::sum);
    }

    public Map<UUID, Long> participants() {
        return participantTicks;
    }

    public UUID topThreatTarget() {
        UUID selected = null;
        double topThreat = -1.0D;
        for (Map.Entry<UUID, Double> entry : threatByPlayer.entrySet()) {
            if (entry.getValue() > topThreat) {
                topThreat = entry.getValue();
                selected = entry.getKey();
            }
        }
        return selected;
    }

    public void clearCombatState() {
        participantTicks.clear();
        threatByPlayer.clear();
    }
}
