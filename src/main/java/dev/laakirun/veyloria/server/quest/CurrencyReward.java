package dev.laakirun.veyloria.server.quest;

import dev.laakirun.veyloria.common.model.CharacterProfile;
import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import dev.laakirun.veyloria.server.game.ServerMarkers;
import dev.laakirun.veyloria.server.npc.NpcReward;
import net.minecraft.server.level.ServerPlayer;

public record CurrencyReward(String currencyId, int amount) implements NpcReward {
    public CurrencyReward {
        currencyId = currencyId == null ? "copper" : currencyId;
        amount = Math.max(0, amount);
    }

    @Override
    public String typeId() {
        return "currency";
    }

    @Override
    public void apply(ServerPlayer player) {
        if (player == null || amount <= 0) {
            return;
        }
        if (!"copper".equalsIgnoreCase(currencyId)) {
            return;
        }
        VeyloriaServerRuntime runtime = VeyloriaServerRuntime.instance();
        CharacterProfile profile = runtime.characterService() == null ? null : runtime.characterService().loadedProfile(player.getUUID());
        if (profile == null || runtime.characterService() == null) {
            return;
        }
        profile.addCurrency(amount);
        runtime.characterService().save(profile);
        ServerMarkers.sendGain(player, 0, amount);
    }
}
