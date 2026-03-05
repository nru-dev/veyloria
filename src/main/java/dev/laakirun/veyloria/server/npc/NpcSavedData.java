package dev.laakirun.veyloria.server.npc;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class NpcSavedData extends SavedData {
    private static final String DATA_KEY = "veyloria_npc_instances";
    private static final String TAG_INSTANCES = "instances";
    private final Map<String, NpcStoredInstance> instances = new LinkedHashMap<>();

    public static NpcSavedData get(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            return new NpcSavedData();
        }
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(NpcSavedData::new, NpcSavedData::load), DATA_KEY);
    }

    public static NpcSavedData load(CompoundTag tag) {
        NpcSavedData data = new NpcSavedData();
        ListTag list = tag.getList(TAG_INSTANCES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            NpcStoredInstance instance = NpcStoredInstance.fromTag(list.getCompound(i));
            if (!instance.instanceId().isBlank()) {
                data.instances.put(instance.instanceId(), instance);
            }
        }
        return data;
    }

    public static NpcSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        return load(tag);
    }

    public Map<String, NpcStoredInstance> instances() {
        return instances;
    }

    public NpcStoredInstance get(String instanceId) {
        return instanceId == null ? null : instances.get(instanceId);
    }

    public void put(NpcStoredInstance instance) {
        if (instance == null || instance.instanceId().isBlank()) {
            return;
        }
        instances.put(instance.instanceId(), instance);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (NpcStoredInstance instance : instances.values()) {
            list.add(instance.save());
        }
        tag.put(TAG_INSTANCES, list);
        return tag;
    }
}
