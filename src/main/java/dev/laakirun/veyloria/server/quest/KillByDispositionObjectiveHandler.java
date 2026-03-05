package dev.laakirun.veyloria.server.quest;

import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class KillByDispositionObjectiveHandler implements QuestObjectiveHandler {
    private static final String COUNTER_PROGRESS = "progress";

    @Override
    public void onAccept(ServerPlayer player, QuestProgress progress, QuestObjectiveDefinition objective, QuestService questService, long gameTime) {
        progress.setCounterInt(COUNTER_PROGRESS, 0);
    }

    @Override
    public boolean onPlayerKilled(ServerPlayer player, LivingEntity victim, QuestProgress progress, QuestObjectiveDefinition objective,
                                  QuestService questService, long gameTime) {
        if (player == null || victim == null) {
            return false;
        }
        String expected = progress.resolvedParams().getString("disposition").toUpperCase(Locale.ROOT);
        if (expected.isBlank()) {
            return false;
        }
        String actual = questService.resolveDispositionId(victim).toUpperCase(Locale.ROOT);
        if (!expected.equals(actual)) {
            return false;
        }
        String locationRaw = progress.resolvedParams().getString("locationId");
        if (!locationRaw.isBlank()) {
            ResourceLocation expectedLocation = ResourceLocation.tryParse(locationRaw);
            ResourceLocation actualLocation = questService.locationService().resolveLocationId((net.minecraft.server.level.ServerLevel) victim.level(),
                victim.blockPosition());
            if (expectedLocation != null && !expectedLocation.equals(actualLocation)) {
                return false;
            }
        }
        int current = progress.counterInt(COUNTER_PROGRESS);
        progress.setCounterInt(COUNTER_PROGRESS, current + 1);
        return true;
    }

    @Override
    public boolean isComplete(ServerPlayer player, QuestProgress progress, QuestObjectiveDefinition objective, QuestService questService, long gameTime) {
        return progress.counterInt(COUNTER_PROGRESS) >= requiredCount(progress, objective);
    }

    @Override
    public String progressText(ServerPlayer player, QuestProgress progress, QuestObjectiveDefinition objective, QuestService questService, long gameTime) {
        int required = requiredCount(progress, objective);
        int value = Math.min(required, progress.counterInt(COUNTER_PROGRESS));
        return value + "/" + required;
    }

    private int requiredCount(QuestProgress progress, QuestObjectiveDefinition objective) {
        int fromParams = progress.resolvedParams().getInt("count");
        if (fromParams > 0) {
            return fromParams;
        }
        if (objective.targetValue() > 0) {
            return (int) Math.round(objective.targetValue());
        }
        return 1;
    }
}
