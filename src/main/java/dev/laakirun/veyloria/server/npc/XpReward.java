package dev.laakirun.veyloria.server.npc;

import dev.laakirun.veyloria.common.model.CharacterProfile;
import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import dev.laakirun.veyloria.server.game.ServerMarkers;
import dev.laakirun.veyloria.server.profile.ExperienceGainResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public record XpReward(int amount) implements NpcReward {
    @Override
    public String typeId() {
        return "xp";
    }

    @Override
    public void apply(ServerPlayer player) {
        if (player == null || amount <= 0) {
            return;
        }
        VeyloriaServerRuntime runtime = VeyloriaServerRuntime.instance();
        CharacterProfile profile = runtime.characterService() == null ? null : runtime.characterService().loadedProfile(player.getUUID());
        if (profile == null || runtime.levelService() == null || runtime.characterService() == null) {
            return;
        }
        ExperienceGainResult gain = runtime.levelService().grantExperience(profile, amount);
        runtime.characterService().save(profile);
        ServerMarkers.sendGain(player, gain.gainedXp(), 0);
        if (gain.leveledUp()) {
            player.sendSystemMessage(Component.literal("Level up: " + gain.previousLevel() + " -> " + gain.newLevel()));
        }
    }
}
