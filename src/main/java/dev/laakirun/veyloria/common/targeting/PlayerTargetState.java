package dev.laakirun.veyloria.common.targeting;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class PlayerTargetState implements INBTSerializable<CompoundTag> {
    private static final String TAG_CURRENT_TARGET = "currentTargetUuid";
    private static final String TAG_LAST_UPDATE_TICK = "lastUpdateTick";

    private UUID currentTargetUuid;
    private long lastUpdateTick;

    public UUID currentTargetUuid() {
        return currentTargetUuid;
    }

    public long lastUpdateTick() {
        return lastUpdateTick;
    }

    public void update(UUID targetUuid, long tick) {
        currentTargetUuid = targetUuid;
        lastUpdateTick = Math.max(0L, tick);
    }

    public void clear(long tick) {
        currentTargetUuid = null;
        lastUpdateTick = Math.max(0L, tick);
    }

    public boolean isStickyFresh(long gameTick, int stickyTicks) {
        if (stickyTicks <= 0 || currentTargetUuid == null) {
            return false;
        }
        return gameTick - lastUpdateTick <= stickyTicks;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (currentTargetUuid != null) {
            tag.putUUID(TAG_CURRENT_TARGET, currentTargetUuid);
        }
        tag.putLong(TAG_LAST_UPDATE_TICK, lastUpdateTick);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        currentTargetUuid = tag.hasUUID(TAG_CURRENT_TARGET) ? tag.getUUID(TAG_CURRENT_TARGET) : null;
        lastUpdateTick = Math.max(0L, tag.getLong(TAG_LAST_UPDATE_TICK));
    }
}
