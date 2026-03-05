package dev.laakirun.veyloria.server.game;

import dev.laakirun.veyloria.common.model.CharacterProfile;
import dev.laakirun.veyloria.common.model.BaseStats;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public final class ServerMarkers {
    public static final String AUTH_REQUIRED = "[veyloria:auth_required]";
    public static final String AUTH_OK = "[veyloria:auth_ok]";
    public static final String PROFILE = "[veyloria:profile]";
    public static final String GAIN = "[veyloria:gain]";
    public static final String LOOT = "[veyloria:loot]";
    public static final String ERROR = "[veyloria:error]";
    public static final String BARS = "[veyloria:bars]";
    public static final String TARGET = "[veyloria:target]";
    public static final String ZONE = "[veyloria:zone]";
    public static final String STRUCTURE = "[veyloria:structure]";
    public static final String DUNGEON = "[veyloria:dungeon]";

    private ServerMarkers() {
    }

    public static void sendAuthRequired(ServerPlayer player, boolean registered) {
        player.sendSystemMessage(Component.literal(AUTH_REQUIRED + "|registered=" + registered));
    }

    public static void sendAuthOk(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(AUTH_OK));
    }

    public static void sendProfile(ServerPlayer player, CharacterProfile profile, int xpToNextLevel, int manaCurrent, int manaMax,
                                   BaseStats totalStats) {
        player.sendSystemMessage(Component.literal(
            PROFILE +
                "|level=" + profile.level() +
                "|xpCurrent=" + profile.xpCurrent() +
                "|xpNext=" + xpToNextLevel +
                "|copper=" + profile.currencyCopper() +
                "|mana=" + manaCurrent +
                "|manaMax=" + manaMax +
                "|power=" + totalStats.power() +
                "|vitality=" + totalStats.vitality() +
                "|armor=" + totalStats.armor() +
                "|crit=" + totalStats.crit() +
                "|haste=" + totalStats.haste()
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

    public static void sendBars(ServerPlayer viewer, UUID subjectUuid, int hpCurrent, int hpMax, int manaCurrent, int manaMax) {
        viewer.sendSystemMessage(Component.literal(
            BARS +
                "|uuid=" + subjectUuid +
                "|hp=" + hpCurrent +
                "|hpMax=" + hpMax +
                "|mana=" + manaCurrent +
                "|manaMax=" + manaMax
        ));
    }

    public static void sendTarget(ServerPlayer player, UUID targetUuid) {
        player.sendSystemMessage(Component.literal(
            TARGET + "|uuid=" + (targetUuid == null ? "" : targetUuid)
        ));
    }

    public static void sendZone(ServerPlayer player, int zoneIndex, String zoneName, String levelRange) {
        player.sendSystemMessage(Component.literal(
            ZONE
                + "|index=" + zoneIndex
                + "|name=" + zoneName
                + "|range=" + levelRange
        ));
    }

    public static void sendStructure(ServerPlayer player, String structureId, String displayName, String localizedName) {
        player.sendSystemMessage(Component.literal(
            STRUCTURE
                + "|id=" + (structureId == null ? "" : structureId)
                + "|display=" + (displayName == null ? "" : displayName)
                + "|name=" + (localizedName == null ? "" : localizedName)
        ));
    }

    public static void sendDungeon(ServerPlayer player, String dungeonName, String subtitle) {
        player.sendSystemMessage(Component.literal(
            DUNGEON
                + "|name=" + (dungeonName == null ? "" : dungeonName)
                + "|subtitle=" + (subtitle == null ? "" : subtitle)
        ));
    }
}
