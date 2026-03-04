package dev.laakirun.veyloria.server.game;

import dev.laakirun.veyloria.common.model.CharacterProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ServerMarkers {
    public static final String AUTH_REQUIRED = "[veyloria:auth_required]";
    public static final String AUTH_OK = "[veyloria:auth_ok]";
    public static final String PROFILE = "[veyloria:profile]";
    public static final String GAIN = "[veyloria:gain]";
    public static final String LOOT = "[veyloria:loot]";
    public static final String ERROR = "[veyloria:error]";

    private ServerMarkers() {
    }

    public static void sendAuthRequired(ServerPlayer player, boolean registered) {
        player.sendSystemMessage(Component.literal(AUTH_REQUIRED + "|registered=" + registered));
    }

    public static void sendAuthOk(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(AUTH_OK));
    }

    public static void sendProfile(ServerPlayer player, CharacterProfile profile, int xpToNextLevel) {
        player.sendSystemMessage(Component.literal(
            PROFILE +
                "|level=" + profile.level() +
                "|xpCurrent=" + profile.xpCurrent() +
                "|xpNext=" + xpToNextLevel +
                "|copper=" + profile.currencyCopper()
        ));
    }

    public static void sendGain(ServerPlayer player, int xp, int copper) {
        player.sendSystemMessage(Component.literal(GAIN + "|xp=" + xp + "|copper=" + copper));
    }

    public static void sendLoot(ServerPlayer player, String itemName, int quantity) {
        player.sendSystemMessage(Component.literal(LOOT + "|name=" + itemName + "|quantity=" + quantity));
    }

    public static void sendError(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(ERROR + "|message=" + message));
    }
}
