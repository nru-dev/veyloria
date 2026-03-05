package dev.laakirun.veyloria.server.npc;

import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface NpcActionHandler {
    void handle(ServerPlayer player, NpcService.ActionContext context);
}
