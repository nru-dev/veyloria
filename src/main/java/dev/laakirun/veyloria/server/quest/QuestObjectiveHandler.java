package dev.laakirun.veyloria.server.quest;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public interface QuestObjectiveHandler {
    default void onAccept(ServerPlayer player, QuestProgress progress, QuestObjectiveDefinition objective, QuestService questService, long gameTime) {
    }

    default boolean onPlayerKilled(ServerPlayer player, LivingEntity victim, QuestProgress progress, QuestObjectiveDefinition objective,
                                   QuestService questService, long gameTime) {
        return false;
    }

    default void onPlayerDealtDamage(ServerPlayer player, long gameTime) {
    }

    default void onPlayerTookDamage(ServerPlayer player, long gameTime) {
    }

    default boolean onTick(ServerPlayer player, QuestProgress progress, QuestObjectiveDefinition objective, QuestService questService, long gameTime) {
        return false;
    }

    boolean isComplete(ServerPlayer player, QuestProgress progress, QuestObjectiveDefinition objective, QuestService questService, long gameTime);

    String progressText(ServerPlayer player, QuestProgress progress, QuestObjectiveDefinition objective, QuestService questService, long gameTime);
}
