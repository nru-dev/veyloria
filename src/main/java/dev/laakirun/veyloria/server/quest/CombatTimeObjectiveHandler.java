package dev.laakirun.veyloria.server.quest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

public final class CombatTimeObjectiveHandler implements QuestObjectiveHandler {
    private static final String COUNTER_COMBAT_TICKS = "combatTicks";

    private final Map<UUID, Long> lastDealtTick = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTakenTick = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAggroSeenTick = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAggroScanTick = new ConcurrentHashMap<>();

    @Override
    public void onAccept(ServerPlayer player, QuestProgress progress, QuestObjectiveDefinition objective, QuestService questService, long gameTime) {
        progress.setCounterLong(COUNTER_COMBAT_TICKS, 0L);
    }

    @Override
    public void onPlayerDealtDamage(ServerPlayer player, long gameTime) {
        if (player != null) {
            lastDealtTick.put(player.getUUID(), gameTime);
        }
    }

    @Override
    public void onPlayerTookDamage(ServerPlayer player, long gameTime) {
        if (player != null) {
            lastTakenTick.put(player.getUUID(), gameTime);
        }
    }

    @Override
    public boolean onTick(ServerPlayer player, QuestProgress progress, QuestObjectiveDefinition objective, QuestService questService, long gameTime) {
        if (player == null || player.level().isClientSide()) {
            return false;
        }
        UUID playerId = player.getUUID();
        long lastScan = lastAggroScanTick.getOrDefault(playerId, Long.MIN_VALUE / 4);
        if (gameTime - lastScan >= questService.combatAggroScanIntervalTicks()) {
            if (hasAggroMobNearby(player, questService.combatAggroRadius())) {
                lastAggroSeenTick.put(playerId, gameTime);
            }
            lastAggroScanTick.put(playerId, gameTime);
        }

        long window = questService.combatSilenceWindowTicks();
        boolean requireAggro = progress.resolvedParams().getBoolean("requireAggro");
        boolean recentDeal = gameTime - lastDealtTick.getOrDefault(playerId, Long.MIN_VALUE / 4) <= window;
        boolean recentTaken = gameTime - lastTakenTick.getOrDefault(playerId, Long.MIN_VALUE / 4) <= window;
        boolean recentAggro = gameTime - lastAggroSeenTick.getOrDefault(playerId, Long.MIN_VALUE / 4) <= window;
        boolean combatActive = recentDeal || recentTaken || (requireAggro && recentAggro);
        boolean continuous = !progress.resolvedParams().contains("continuous") || progress.resolvedParams().getBoolean("continuous");

        long before = progress.counterLong(COUNTER_COMBAT_TICKS);
        long after = before;
        if (combatActive) {
            after = before + 1L;
        } else if (continuous) {
            after = 0L;
        }
        if (after != before) {
            progress.setCounterLong(COUNTER_COMBAT_TICKS, after);
            return true;
        }
        return false;
    }

    @Override
    public boolean isComplete(ServerPlayer player, QuestProgress progress, QuestObjectiveDefinition objective, QuestService questService, long gameTime) {
        int seconds = requiredSeconds(progress, objective);
        return progress.counterLong(COUNTER_COMBAT_TICKS) >= seconds * 20L;
    }

    @Override
    public String progressText(ServerPlayer player, QuestProgress progress, QuestObjectiveDefinition objective, QuestService questService, long gameTime) {
        double current = progress.counterLong(COUNTER_COMBAT_TICKS) / 20.0D;
        double required = requiredSeconds(progress, objective);
        return String.format(java.util.Locale.ROOT, "%.1f/%.1f сек", Math.min(current, required), required);
    }

    private boolean hasAggroMobNearby(ServerPlayer player, double radius) {
        AABB area = player.getBoundingBox().inflate(radius);
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, area, Mob::isAlive)) {
            if (mob.getTarget() == player) {
                return true;
            }
        }
        return false;
    }

    private int requiredSeconds(QuestProgress progress, QuestObjectiveDefinition objective) {
        int seconds = progress.resolvedParams().getInt("seconds");
        if (seconds > 0) {
            return seconds;
        }
        if (objective.targetValue() > 0) {
            return (int) Math.round(objective.targetValue());
        }
        return 10;
    }
}
