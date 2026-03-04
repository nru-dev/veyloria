package dev.laakirun.veyloria.server.game;

import dev.laakirun.veyloria.common.model.HostilityType;
import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import dev.laakirun.veyloria.server.content.MobSpawnGroup;
import dev.laakirun.veyloria.server.content.MobTemplate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MobSpawnService {
    private static final Logger LOGGER = LoggerFactory.getLogger("veyloria.spawn");
    private static final long NEUTRAL_RETALIATE_TICKS = 20L * 20L;

    private final Random random = new Random();
    private final Map<UUID, MobInstance> trackedMobs = new ConcurrentHashMap<>();
    private final Map<Long, Long> nextSpawnTickByGroup = new ConcurrentHashMap<>();
    private final Map<UUID, AggroState> neutralAggro = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, List<MobSpawnGroup>>> spawnIndexByDimensionChunk = new ConcurrentHashMap<>();
    private List<MobSpawnGroup> indexedGroupsSnapshot = List.of();

    public void tick(MinecraftServer server) {
        ensureSpawnIndex();
        long gameTime = server.overworld().getGameTime();
        clearExpiredNeutralAggro(gameTime);

        for (ServerLevel level : server.getAllLevels()) {
            cleanupTrackedMobs(level);
            updateHostilityTargets(level, gameTime);
            enforceLeashes(level);

            int activeInDimension = countActiveInDimension(level);
            for (MobSpawnGroup group : candidateGroups(level)) {
                if (activeInDimension >= VeyloriaServerRuntime.instance().serverConfig().maxActiveMobsPerDimension()) {
                    break;
                }
                if (!hasAuthenticatedPlayersNearby(level, group)) {
                    continue;
                }
                long nextTick = nextSpawnTickByGroup.getOrDefault(group.id(), 0L);
                if (gameTime < nextTick) {
                    continue;
                }
                int alive = countAlive(level, group.id());
                if (alive >= group.minAlive()) {
                    continue;
                }
                int missing = Math.min(group.maxAlive() - alive, rollPackSize(group));
                if (missing <= 0) {
                    continue;
                }
                int spawned = spawnGroup(level, group, missing);
                if (spawned <= 0) {
                    continue;
                }
                activeInDimension += spawned;
                MobTemplate template = VeyloriaServerRuntime.instance().contentService().mobTemplate(group.mobTemplateId());
                nextSpawnTickByGroup.put(group.id(), gameTime + adjustedRespawnTicks(group, VeyloriaServerRuntime.instance().contentService().mobTemplate(group.mobTemplateId())));
                LOGGER.debug("Spawned {} mobs in group {} ({}) at {}", spawned, group.id(),
                    template == null ? "unknown_template" : template.code(), level.dimension().location());
            }
        }
    }

    public MobInstance mobInstance(UUID entityUuid) {
        return trackedMobs.get(entityUuid);
    }

    public MobTemplate template(UUID entityUuid) {
        MobInstance instance = trackedMobs.get(entityUuid);
        return instance == null ? null : VeyloriaServerRuntime.instance().contentService().mobTemplate(instance.templateId());
    }

    public HostilityType hostility(UUID entityUuid) {
        MobTemplate template = template(entityUuid);
        return template == null ? null : template.hostilityType();
    }

    public void recordHit(UUID entityUuid, UUID playerUuid, long gameTick) {
        MobInstance instance = trackedMobs.get(entityUuid);
        if (instance != null) {
            instance.recordParticipant(playerUuid, gameTick);
        }
    }

    public MobInstance remove(UUID entityUuid) {
        neutralAggro.remove(entityUuid);
        return trackedMobs.remove(entityUuid);
    }

    public void markNeutralAggro(UUID mobUuid, UUID targetUuid, long gameTick) {
        neutralAggro.put(mobUuid, new AggroState(targetUuid, gameTick + NEUTRAL_RETALIATE_TICKS));
    }

    public boolean canNeutralDamage(UUID mobUuid, UUID targetUuid, long gameTick) {
        AggroState state = neutralAggro.get(mobUuid);
        if (state == null || state.expiresAtTick() < gameTick) {
            neutralAggro.remove(mobUuid);
            return false;
        }
        return state.targetUuid().equals(targetUuid);
    }

    public List<ServerPlayer> eligibleParticipants(ServerLevel level, MobInstance instance) {
        List<ServerPlayer> players = new ArrayList<>();
        Entity rawEntity = level.getEntity(instance.entityUuid());
        if (!(rawEntity instanceof LivingEntity entity)) {
            return players;
        }
        instance.participants().keySet().forEach(playerUuid -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUuid);
            if (player != null && player.distanceToSqr(entity) <= (32.0D * 32.0D)) {
                players.add(player);
            }
        });
        return players;
    }

    private List<MobSpawnGroup> candidateGroups(ServerLevel level) {
        Map<Long, List<MobSpawnGroup>> byChunk = spawnIndexByDimensionChunk.get(level.dimension().location().toString());
        if (byChunk == null || byChunk.isEmpty()) {
            return List.of();
        }
        Map<Long, MobSpawnGroup> candidates = new LinkedHashMap<>();
        int chunkRadius = Math.max(1, (int) Math.ceil(VeyloriaServerRuntime.instance().serverConfig().spawnActivationRadius() / 16.0D));
        for (ServerPlayer player : level.players()) {
            if (!VeyloriaServerRuntime.instance().authService().sessionManager().isAuthenticated(player.getUUID())) {
                continue;
            }
            ChunkPos playerChunk = player.chunkPosition();
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                    List<MobSpawnGroup> groups = byChunk.get(chunkKey(playerChunk.x + dx, playerChunk.z + dz));
                    if (groups == null) {
                        continue;
                    }
                    for (MobSpawnGroup group : groups) {
                        candidates.putIfAbsent(group.id(), group);
                    }
                }
            }
        }
        return List.copyOf(candidates.values());
    }

    private boolean hasAuthenticatedPlayersNearby(ServerLevel level, MobSpawnGroup group) {
        double radius = VeyloriaServerRuntime.instance().serverConfig().spawnActivationRadius();
        for (ServerPlayer player : level.players()) {
            if (!VeyloriaServerRuntime.instance().authService().sessionManager().isAuthenticated(player.getUUID())) {
                continue;
            }
            if (player.distanceToSqr(group.centerX(), group.centerY(), group.centerZ()) <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    private int countActiveInDimension(ServerLevel level) {
        String dimensionId = level.dimension().location().toString();
        int count = 0;
        for (Map.Entry<UUID, MobInstance> entry : trackedMobs.entrySet()) {
            MobSpawnGroup group = VeyloriaServerRuntime.instance().contentService().spawnGroup(entry.getValue().spawnGroupId());
            if (group == null || !dimensionId.equals(group.dimension())) {
                continue;
            }
            Entity entity = level.getEntity(entry.getKey());
            if (entity instanceof LivingEntity living && living.isAlive()) {
                count++;
            }
        }
        return count;
    }

    private void cleanupTrackedMobs(ServerLevel level) {
        String dimensionId = level.dimension().location().toString();
        List<UUID> orphaned = new ArrayList<>();
        for (Map.Entry<UUID, MobInstance> entry : trackedMobs.entrySet()) {
            MobSpawnGroup group = VeyloriaServerRuntime.instance().contentService().spawnGroup(entry.getValue().spawnGroupId());
            if (group == null) {
                orphaned.add(entry.getKey());
                continue;
            }
            if (!dimensionId.equals(group.dimension())) {
                continue;
            }
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                orphaned.add(entry.getKey());
            }
        }
        for (UUID mobUuid : orphaned) {
            trackedMobs.remove(mobUuid);
            neutralAggro.remove(mobUuid);
        }
    }

    private int countAlive(ServerLevel level, long spawnGroupId) {
        int count = 0;
        for (MobInstance instance : trackedMobs.values()) {
            if (instance.spawnGroupId() != spawnGroupId) {
                continue;
            }
            if (level.getEntity(instance.entityUuid()) instanceof LivingEntity living && living.isAlive()) {
                count++;
            }
        }
        return count;
    }

    private void ensureSpawnIndex() {
        List<MobSpawnGroup> groups = VeyloriaServerRuntime.instance().contentService().spawnGroups();
        if (Objects.equals(indexedGroupsSnapshot, groups)) {
            return;
        }
        Map<String, Map<Long, List<MobSpawnGroup>>> rebuilt = new HashMap<>();
        for (MobSpawnGroup group : groups) {
            int minChunkX = blockToChunk(group.centerX() - group.radiusX());
            int maxChunkX = blockToChunk(group.centerX() + group.radiusX());
            int minChunkZ = blockToChunk(group.centerZ() - group.radiusZ());
            int maxChunkZ = blockToChunk(group.centerZ() + group.radiusZ());
            Map<Long, List<MobSpawnGroup>> byChunk = rebuilt.computeIfAbsent(group.dimension(), ignored -> new HashMap<>());
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    byChunk.computeIfAbsent(chunkKey(chunkX, chunkZ), ignored -> new ArrayList<>()).add(group);
                }
            }
        }
        spawnIndexByDimensionChunk.clear();
        spawnIndexByDimensionChunk.putAll(rebuilt);
        indexedGroupsSnapshot = List.copyOf(groups);
        LOGGER.info("Indexed {} spawn groups for {} dimensions", groups.size(), spawnIndexByDimensionChunk.size());
    }

    private int rollPackSize(MobSpawnGroup group) {
        if (group.packSizeMax() <= group.packSizeMin()) {
            return group.packSizeMin();
        }
        return group.packSizeMin() + random.nextInt(group.packSizeMax() - group.packSizeMin() + 1);
    }

    private double rollPackSpread(MobSpawnGroup group) {
        if (group.packSpreadMax() <= group.packSpreadMin()) {
            return Math.max(0.0D, group.packSpreadMin());
        }
        return randomInRange(group.packSpreadMin(), group.packSpreadMax());
    }

    private long adjustedRespawnTicks(MobSpawnGroup group, MobTemplate template) {
        double base = group.respawnSeconds() * 20.0D;
        if (template != null && template.mobType().name().equals("BOSS")) {
            return Math.max(20L, Math.round(base / VeyloriaServerRuntime.instance().ratesConfig().bossRespawnRate()));
        }
        return Math.round(base);
    }

    @SuppressWarnings("unchecked")
    private int spawnGroup(ServerLevel level, MobSpawnGroup group, int count) {
        MobTemplate template = VeyloriaServerRuntime.instance().contentService().mobTemplate(group.mobTemplateId());
        if (template == null) {
            return 0;
        }
        EntityType<?> rawType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(template.entityModel()));
        if (!(rawType instanceof EntityType<?> entityType)) {
            return 0;
        }
        double packCenterX = randomInRange(group.centerX() - group.radiusX(), group.centerX() + group.radiusX());
        double packCenterZ = randomInRange(group.centerZ() - group.radiusZ(), group.centerZ() + group.radiusZ());
        int spawned = 0;
        for (int index = 0; index < count; index++) {
            if (!(entityType.create(level) instanceof Mob mob)) {
                continue;
            }
            double x = packCenterX;
            double z = packCenterZ;
            if (index > 0) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double spread = rollPackSpread(group);
                x = clamp(packCenterX + Math.cos(angle) * spread, group.centerX() - group.radiusX(), group.centerX() + group.radiusX());
                z = clamp(packCenterZ + Math.sin(angle) * spread, group.centerZ() - group.radiusZ(), group.centerZ() + group.radiusZ());
            }
            BlockPos pos = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, group.centerY(), z));
            mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            applyTemplate(mob, template);
            level.addFreshEntity(mob);
            trackedMobs.put(mob.getUUID(), new MobInstance(mob.getUUID(), template.id(), group.id()));
            spawned++;
        }
        return spawned;
    }

    private void applyTemplate(Mob mob, MobTemplate template) {
        double maxHealth = template.baseHp() * (1 + 0.18D * (template.level() - 1)) * template.mobType().healthModifier();
        double damage = template.baseDamage() * (1 + 0.10D * (template.level() - 1)) * template.mobType().damageModifier();
        if (mob.getAttribute(Attributes.MAX_HEALTH) != null) {
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        }
        if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(damage);
        }
        if (mob.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(template.moveSpeed());
        }
        if (mob.getAttribute(Attributes.ATTACK_SPEED) != null) {
            mob.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(template.attackSpeed());
        }
        if (mob.getAttribute(Attributes.FOLLOW_RANGE) != null) {
            mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(template.aggroRadius());
        }
        mob.setHealth((float) maxHealth);
        mob.setPersistenceRequired();
        mob.setCustomNameVisible(true);
        mob.setCustomName(Component.literal(formatName(template)));
    }

    private String formatName(MobTemplate template) {
        return "[" + template.level() + "] " + switch (template.mobType()) {
            case NORMAL -> template.name();
            case ELITE -> "Elite " + template.name();
            case BOSS -> "Boss " + template.name();
        };
    }

    private void updateHostilityTargets(ServerLevel level, long gameTick) {
        for (Map.Entry<UUID, MobInstance> entry : trackedMobs.entrySet()) {
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            MobTemplate template = VeyloriaServerRuntime.instance().contentService().mobTemplate(entry.getValue().templateId());
            if (template == null) {
                continue;
            }
            switch (template.hostilityType()) {
                case FRIENDLY -> {
                    if (mob.getTarget() instanceof ServerPlayer) {
                        mob.setTarget(null);
                    }
                    LivingEntity hostileTarget = findNearestMobByHostility(level, mob, HostilityType.HOSTILE, template.aggroRadius());
                    if (hostileTarget != null) {
                        mob.setTarget(hostileTarget);
                    }
                }
                case HOSTILE -> {
                    LivingEntity currentTarget = mob.getTarget();
                    if (currentTarget == null || !currentTarget.isAlive()) {
                        LivingEntity friendlyTarget = findNearestMobByHostility(level, mob, HostilityType.FRIENDLY, template.aggroRadius());
                        if (friendlyTarget != null) {
                            mob.setTarget(friendlyTarget);
                        }
                    }
                }
                case NEUTRAL -> {
                    if (mob.getTarget() instanceof ServerPlayer player
                        && !canNeutralDamage(mob.getUUID(), player.getUUID(), gameTick)) {
                        mob.setTarget(null);
                    }
                }
            }
        }
    }

    private LivingEntity findNearestMobByHostility(ServerLevel level, Mob source, HostilityType desiredHostility, double radius) {
        double maxDistanceSqr = Math.max(1.0D, radius * radius);
        double bestDistance = maxDistanceSqr;
        LivingEntity selected = null;
        for (UUID candidateUuid : trackedMobs.keySet()) {
            if (candidateUuid.equals(source.getUUID())) {
                continue;
            }
            MobTemplate candidateTemplate = template(candidateUuid);
            if (candidateTemplate == null || candidateTemplate.hostilityType() != desiredHostility) {
                continue;
            }
            Entity candidateEntity = level.getEntity(candidateUuid);
            if (!(candidateEntity instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }
            double distance = source.distanceToSqr(living);
            if (distance <= bestDistance) {
                bestDistance = distance;
                selected = living;
            }
        }
        return selected;
    }

    private void enforceLeashes(ServerLevel level) {
        for (Map.Entry<UUID, MobInstance> entry : trackedMobs.entrySet()) {
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            MobTemplate template = VeyloriaServerRuntime.instance().contentService().mobTemplate(entry.getValue().templateId());
            MobSpawnGroup group = VeyloriaServerRuntime.instance().contentService().spawnGroup(entry.getValue().spawnGroupId());
            if (template == null || group == null) {
                continue;
            }
            if (template.leashRadius() <= 0.0D) {
                continue;
            }
            if (mob.distanceToSqr(group.centerX(), group.centerY(), group.centerZ()) <= template.leashRadius() * template.leashRadius()) {
                continue;
            }
            BlockPos resetPos = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(group.centerX(), group.centerY(), group.centerZ()));
            mob.getNavigation().stop();
            mob.setTarget(null);
            mob.teleportTo(resetPos.getX() + 0.5D, resetPos.getY(), resetPos.getZ() + 0.5D);
            LOGGER.debug("Leashed mob {} back to spawn group {}", entry.getKey(), group.id());
        }
    }

    private void clearExpiredNeutralAggro(long gameTick) {
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, AggroState> entry : neutralAggro.entrySet()) {
            if (entry.getValue().expiresAtTick() < gameTick) {
                expired.add(entry.getKey());
            }
        }
        for (UUID mobUuid : expired) {
            neutralAggro.remove(mobUuid);
        }
    }

    private static int blockToChunk(double block) {
        return (int) Math.floor(block / 16.0D);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double randomInRange(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private record AggroState(UUID targetUuid, long expiresAtTick) {
    }
}
