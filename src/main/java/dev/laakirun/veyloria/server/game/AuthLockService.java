package dev.laakirun.veyloria.server.game;

import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class AuthLockService {
    private final Set<UUID> lockedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Vec3> anchorPositions = new ConcurrentHashMap<>();

    public void lock(ServerPlayer player) {
        lockedPlayers.add(player.getUUID());
        anchorPositions.put(player.getUUID(), player.position());
    }

    public void unlock(ServerPlayer player) {
        lockedPlayers.remove(player.getUUID());
        anchorPositions.remove(player.getUUID());
    }

    public boolean isLocked(ServerPlayer player) {
        return lockedPlayers.contains(player.getUUID());
    }

    public boolean isAuthenticated(ServerPlayer player) {
        return VeyloriaServerRuntime.instance().authService().sessionManager().isAuthenticated(player.getUUID());
    }

    public void enforce(ServerPlayer player) {
        if (!isLocked(player)) {
            return;
        }
        Vec3 anchor = anchorPositions.get(player.getUUID());
        if (anchor != null && player.distanceToSqr(anchor) > 0.01D) {
            player.teleportTo(anchor.x, anchor.y, anchor.z);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
    }
}
