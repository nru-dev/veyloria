package dev.laakirun.veyloria.server.npc;

import dev.laakirun.veyloria.server.quest.QuestOffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class NpcStoredInstance {
    private static final String TAG_INSTANCE_ID = "instanceId";
    private static final String TAG_DEFINITION_ID = "definitionId";
    private static final String TAG_DIMENSION_ID = "dimensionId";
    private static final String TAG_SPAWN_X = "spawnX";
    private static final String TAG_SPAWN_Y = "spawnY";
    private static final String TAG_SPAWN_Z = "spawnZ";
    private static final String TAG_ENTITY_UUID = "entityUuid";
    private static final String TAG_NEXT_RESPAWN_TICK = "nextRespawnTick";
    private static final String TAG_GROUP_KEY = "groupKey";
    private static final String TAG_LOCATION_ID = "locationId";
    private static final String TAG_OFFERS = "offers";

    private String instanceId;
    private String definitionId;
    private String dimensionId;
    private BlockPos spawnPos;
    private UUID entityUuid;
    private long nextRespawnTick;
    private String groupKey;
    private String locationId;
    private final List<QuestOffer> offers;

    public NpcStoredInstance(String instanceId, String definitionId, String dimensionId, BlockPos spawnPos, UUID entityUuid, long nextRespawnTick,
                             String groupKey) {
        this(instanceId, definitionId, dimensionId, spawnPos, entityUuid, nextRespawnTick, groupKey, "", List.of());
    }

    public NpcStoredInstance(String instanceId, String definitionId, String dimensionId, BlockPos spawnPos, UUID entityUuid, long nextRespawnTick,
                             String groupKey, String locationId, List<QuestOffer> offers) {
        this.instanceId = instanceId == null ? "" : instanceId;
        this.definitionId = definitionId == null ? "" : definitionId;
        this.dimensionId = dimensionId == null ? "" : dimensionId;
        this.spawnPos = spawnPos == null ? BlockPos.ZERO : spawnPos;
        this.entityUuid = entityUuid;
        this.nextRespawnTick = Math.max(0L, nextRespawnTick);
        this.groupKey = groupKey == null ? "" : groupKey;
        this.locationId = locationId == null ? "" : locationId;
        this.offers = new ArrayList<>();
        setOffers(offers);
    }

    public static NpcStoredInstance fromTag(CompoundTag tag) {
        String instanceId = tag.getString(TAG_INSTANCE_ID);
        String definitionId = tag.getString(TAG_DEFINITION_ID);
        String dimensionId = tag.getString(TAG_DIMENSION_ID);
        BlockPos spawnPos = new BlockPos(tag.getInt(TAG_SPAWN_X), tag.getInt(TAG_SPAWN_Y), tag.getInt(TAG_SPAWN_Z));
        UUID entityUuid = tag.hasUUID(TAG_ENTITY_UUID) ? tag.getUUID(TAG_ENTITY_UUID) : null;
        long nextRespawnTick = tag.getLong(TAG_NEXT_RESPAWN_TICK);
        String groupKey = tag.getString(TAG_GROUP_KEY);
        String locationId = tag.getString(TAG_LOCATION_ID);
        List<QuestOffer> offers = new ArrayList<>();
        ListTag offerList = tag.getList(TAG_OFFERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < offerList.size(); i++) {
            QuestOffer offer = QuestOffer.fromTag(offerList.getCompound(i));
            if (!offer.questId().isBlank()) {
                offers.add(offer);
            }
        }
        return new NpcStoredInstance(instanceId, definitionId, dimensionId, spawnPos, entityUuid, nextRespawnTick, groupKey, locationId, offers);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_INSTANCE_ID, instanceId);
        tag.putString(TAG_DEFINITION_ID, definitionId);
        tag.putString(TAG_DIMENSION_ID, dimensionId);
        tag.putInt(TAG_SPAWN_X, spawnPos.getX());
        tag.putInt(TAG_SPAWN_Y, spawnPos.getY());
        tag.putInt(TAG_SPAWN_Z, spawnPos.getZ());
        if (entityUuid != null) {
            tag.putUUID(TAG_ENTITY_UUID, entityUuid);
        }
        tag.putLong(TAG_NEXT_RESPAWN_TICK, nextRespawnTick);
        tag.putString(TAG_GROUP_KEY, groupKey);
        tag.putString(TAG_LOCATION_ID, locationId);
        ListTag offerList = new ListTag();
        for (QuestOffer offer : offers) {
            offerList.add(offer.save());
        }
        tag.put(TAG_OFFERS, offerList);
        return tag;
    }

    public String instanceId() {
        return instanceId;
    }

    public String definitionIdRaw() {
        return definitionId;
    }

    public ResourceLocation definitionId() {
        return definitionId == null || definitionId.isBlank() ? null : ResourceLocation.tryParse(definitionId);
    }

    public String dimensionId() {
        return dimensionId;
    }

    public BlockPos spawnPos() {
        return spawnPos;
    }

    public UUID entityUuid() {
        return entityUuid;
    }

    public long nextRespawnTick() {
        return nextRespawnTick;
    }

    public String groupKey() {
        return groupKey;
    }

    public String locationIdRaw() {
        return locationId;
    }

    public ResourceLocation locationId() {
        return locationId == null || locationId.isBlank() ? null : ResourceLocation.tryParse(locationId);
    }

    public List<QuestOffer> offers() {
        return List.copyOf(offers);
    }

    public void setDefinitionId(ResourceLocation definitionId) {
        this.definitionId = definitionId == null ? "" : definitionId.toString();
    }

    public void setDimensionId(String dimensionId) {
        this.dimensionId = dimensionId == null ? "" : dimensionId;
    }

    public void setSpawnPos(BlockPos spawnPos) {
        this.spawnPos = spawnPos == null ? BlockPos.ZERO : spawnPos;
    }

    public void setEntityUuid(UUID entityUuid) {
        this.entityUuid = entityUuid;
    }

    public void setNextRespawnTick(long nextRespawnTick) {
        this.nextRespawnTick = Math.max(0L, nextRespawnTick);
    }

    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey == null ? "" : groupKey;
    }

    public void setLocationId(ResourceLocation locationId) {
        this.locationId = locationId == null ? "" : locationId.toString();
    }

    public void setOffers(List<QuestOffer> offers) {
        this.offers.clear();
        if (offers != null) {
            for (QuestOffer offer : offers) {
                if (offer != null && !offer.questId().isBlank()) {
                    this.offers.add(offer);
                }
            }
        }
    }
}
