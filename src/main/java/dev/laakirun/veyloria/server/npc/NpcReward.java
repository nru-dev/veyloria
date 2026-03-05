package dev.laakirun.veyloria.server.npc;

import net.minecraft.server.level.ServerPlayer;

public interface NpcReward {
    String typeId();

    void apply(ServerPlayer player);
}
