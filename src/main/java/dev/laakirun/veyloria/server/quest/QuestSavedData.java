package dev.laakirun.veyloria.server.quest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class QuestSavedData extends SavedData {
    private static final String DATA_KEY = "veyloria_player_quests";
    private static final String TAG_PLAYERS = "players";
    private static final String TAG_PLAYER_UUID = "playerUuid";
    private static final String TAG_PLAYER_STATE = "state";

    private final Map<UUID, PlayerQuestState> players = new LinkedHashMap<>();

    public static QuestSavedData get(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            return new QuestSavedData();
        }
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(QuestSavedData::new, QuestSavedData::load), DATA_KEY);
    }

    public static QuestSavedData load(CompoundTag tag) {
        QuestSavedData data = new QuestSavedData();
        ListTag list = tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String rawUuid = entry.getString(TAG_PLAYER_UUID);
            if (rawUuid.isBlank()) {
                continue;
            }
            try {
                UUID uuid = UUID.fromString(rawUuid);
                data.players.put(uuid, PlayerQuestState.fromTag(entry.getCompound(TAG_PLAYER_STATE)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    public static QuestSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        return load(tag);
    }

    public PlayerQuestState state(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        return players.computeIfAbsent(playerUuid, ignored -> new PlayerQuestState());
    }

    public Map<UUID, PlayerQuestState> players() {
        return players;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, PlayerQuestState> entry : players.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putString(TAG_PLAYER_UUID, entry.getKey().toString());
            playerTag.put(TAG_PLAYER_STATE, entry.getValue().save());
            list.add(playerTag);
        }
        tag.put(TAG_PLAYERS, list);
        return tag;
    }
}
