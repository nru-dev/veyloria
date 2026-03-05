package dev.laakirun.veyloria.server.game;

import net.minecraft.ChatFormatting;
import dev.laakirun.veyloria.common.model.HostilityType;
import dev.laakirun.veyloria.common.model.MobType;
import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import dev.laakirun.veyloria.server.auth.AuthService;
import dev.laakirun.veyloria.server.content.ContentService;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MobSpawnService {
    private static final Logger LOGGER = LoggerFactory.getLogger("veyloria.spawn");
    private static final long NEUTRAL_RETALIATE_TICKS = 20L * 20L;
    private static final long UNREACHABLE_EVADE_TICKS = 20L * 3L;
    private static final long EVADE_IMMUNITY_TICKS = 20L * 2L;
    private static final double COMBAT_MIN_DISTANCE = 2.0D;
    private static final double COMBAT_MAX_DISTANCE = 3.3D;
    private static final double COMBAT_RETREAT_STEP = 1.6D;
    private static final double COMBAT_STRAFE_STEP = 0.9D;
    private static final double MIN_BLOCKED_PATH_DISTANCE = 8.0D;
    private static final double LEASH_RADIUS_MULTIPLIER = 4.8D;
    private static final double HARD_LEASH_EVADE_MULTIPLIER = 1.22D;
    private static final double LEASH_MIN_RADIUS = 72.0D;
    private static final double IDLE_ROAM_RADIUS_MULTIPLIER = 3.4D;
    private static final double IDLE_ROAM_MIN_RADIUS = 44.0D;
    private static final long NAMEPLATE_HEARTBEAT_TICKS = 20L;
    private static final int NAMEPLATE_SEGMENTS = 12;
    private static final int MIN_ACTIVE_MOBS_FLOOR = 520;
    private static final int MIN_ACTIVATION_RADIUS = 224;
    private static final double UNSEEN_UNLOAD_RADIUS_MULTIPLIER = 1.9D;
    private static final double UNSEEN_UNLOAD_MIN_RADIUS = 300.0D;
    private static final double NORMAL_DENSITY_MULTIPLIER = 3.0D;
    private static final double NORMAL_PACK_MULTIPLIER = 2.3D;
    private static final double MOB_DAMAGE_TUNING_MULTIPLIER = 1.45D;
    private static final double MIN_INTER_MOB_DISTANCE = 3.0D;
    private static final int ZONE_SPAWN_MARGIN = 4;
    private static final double LOADED_SPAWN_RADIUS = 156.0D;
    private static final int GROUP_MIN_SIZE = 1;
    private static final int GROUP_MAX_SIZE = 6;
    private static final int TARGET_UPDATE_SHARDS = 2;
    private static final int COMBAT_UPDATE_SHARDS = 2;
    private static final int LEASH_UPDATE_SHARDS = 3;
    private static final int NAMEPLATE_UPDATE_SHARDS = 2;
    private static final String TAG_CUSTOM_MOB = "veyloria_custom_mob";
    private static final String TAG_TEMPLATE_ID = "veyloria_template_id";
    private static final String TAG_SPAWN_GROUP_ID = "veyloria_spawn_group_id";
    private static final String TAG_SPAWN_X = "veyloria_spawn_x";
    private static final String TAG_SPAWN_Y = "veyloria_spawn_y";
    private static final String TAG_SPAWN_Z = "veyloria_spawn_z";

    private final Random random = new Random();
    private final Map<UUID, MobInstance> trackedMobs = new ConcurrentHashMap<>();
    private final Map<Long, Long> nextSpawnTickByGroup = new ConcurrentHashMap<>();
    private final Map<UUID, AggroState> neutralAggro = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextManagedAttackTickByMob = new ConcurrentHashMap<>();
    private final Map<UUID, Long> blockedAttackSinceTick = new ConcurrentHashMap<>();
    private final Map<UUID, Long> evadingUntilTick = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, List<MobSpawnGroup>>> spawnIndexByDimensionChunk = new ConcurrentHashMap<>();
    private final Map<String, Boolean> startupRareSpawnsDone = new ConcurrentHashMap<>();
    private final Map<UUID, NameplateState> nameplateStateByMob = new ConcurrentHashMap<>();
    private List<MobSpawnGroup> indexedGroupsSnapshot = List.of();

    public void tick(MinecraftServer server) {
        ensureSpawnIndex();
        long gameTime = server.overworld().getGameTime();
        clearExpiredNeutralAggro(gameTime);
        VeyloriaServerRuntime runtime = VeyloriaServerRuntime.instance();
        var serverConfig = runtime.serverConfig();
        AuthService authService = runtime.authService();
        ContentService contentService = runtime.contentService();
        int maxActivePerDimension = Math.max(serverConfig.maxActiveMobsPerDimension(), MIN_ACTIVE_MOBS_FLOOR);
        double activationRadius = Math.max(serverConfig.spawnActivationRadius(), MIN_ACTIVATION_RADIUS);
        double activationRadiusSqr = activationRadius * activationRadius;

        for (ServerLevel level : server.getAllLevels()) {
            cleanupTrackedMobs(level, contentService);
            List<ServerPlayer> authenticatedPlayers = authenticatedPlayers(level, authService);
            unloadUnseenMobs(level, authenticatedPlayers,
                Math.max(UNSEEN_UNLOAD_MIN_RADIUS, activationRadius * UNSEEN_UNLOAD_RADIUS_MULTIPLIER));
            Map<Long, Integer> aliveByGroup = aliveCountsByGroup(level, contentService);
            Map<Long, MobTemplate> templateCache = new HashMap<>();
            Map<Long, MobSpawnGroup> groupCache = new HashMap<>();
            spawnRareGroupsOnStartup(level, gameTime, aliveByGroup, contentService);
            updateHostilityTargets(level, gameTime, templateCache, contentService, authService);
            maintainCombatDistance(level, gameTime, templateCache, groupCache);
            performIdleRoaming(level, gameTime, templateCache, groupCache);
            enforceLeashes(level, gameTime, templateCache, groupCache);

            if (authenticatedPlayers.isEmpty()) {
                continue;
            }

            int activeInDimension = totalAlive(aliveByGroup);
            for (MobSpawnGroup group : candidateGroups(level, authenticatedPlayers, activationRadius)) {
                if (activeInDimension >= maxActivePerDimension) {
                    break;
                }
                MobTemplate template = contentService.mobTemplate(group.mobTemplateId());
                if (template == null) {
                    continue;
                }
                if (!hasAuthenticatedPlayersNearby(authenticatedPlayers, group, activationRadiusSqr)) {
                    continue;
                }
                long nextTick = nextSpawnTickByGroup.getOrDefault(group.id(), 0L);
                if (gameTime < nextTick) {
                    continue;
                }
                int alive = aliveByGroup.getOrDefault(group.id(), 0);
                int targetMinAlive = desiredMinAlive(group, template);
                int targetMaxAlive = desiredMaxAlive(group, template);
                if (alive >= targetMinAlive) {
                    continue;
                }
                int missing = Math.min(Math.max(0, targetMaxAlive - alive), rollPackSize(group, template));
                if (missing <= 0) {
                    continue;
                }
                int spawned = spawnGroup(level, group, missing);
                if (spawned <= 0) {
                    continue;
                }
                activeInDimension += spawned;
                aliveByGroup.merge(group.id(), spawned, Integer::sum);
                nextSpawnTickByGroup.put(group.id(), gameTime + adjustedRespawnTicks(group, template));
                LOGGER.debug("Spawned {} mobs in group {} ({}) at {}", spawned, group.id(),
                    template == null ? "unknown_template" : template.code(), level.dimension().location());
            }
        }
    }

    public MobInstance mobInstance(UUID entityUuid) {
        return trackedMobs.get(entityUuid);
    }

    public boolean isManagedMob(Entity entity) {
        return entity.getPersistentData().getBoolean(TAG_CUSTOM_MOB);
    }

    public void registerManagedMob(Mob mob) {
        if (!isManagedMob(mob)) {
            return;
        }
        long templateId = mob.getPersistentData().getLong(TAG_TEMPLATE_ID);
        long spawnGroupId = mob.getPersistentData().getLong(TAG_SPAWN_GROUP_ID);
        if (templateId <= 0 || spawnGroupId <= 0) {
            return;
        }
        ensureSpawnAnchorTag(mob);
        trackedMobs.putIfAbsent(mob.getUUID(), new MobInstance(mob.getUUID(), templateId, spawnGroupId));
    }

    public MobTemplate template(UUID entityUuid) {
        MobInstance instance = trackedMobs.get(entityUuid);
        return instance == null ? null : VeyloriaServerRuntime.instance().contentService().mobTemplate(instance.templateId());
    }

    public HostilityType hostility(UUID entityUuid) {
        MobTemplate template = template(entityUuid);
        return template == null ? null : template.hostilityType();
    }

    public void recordHit(ServerLevel level, UUID entityUuid, UUID playerUuid, long gameTick, double damage) {
        if (isEvading(entityUuid, gameTick)) {
            return;
        }
        MobInstance instance = trackedMobs.get(entityUuid);
        MobTemplate template = instance == null ? null : VeyloriaServerRuntime.instance().contentService().mobTemplate(instance.templateId());
        Entity entity = level.getEntity(entityUuid);
        if (template != null && template.hostilityType() == HostilityType.NEUTRAL) {
            markNeutralAggro(entityUuid, playerUuid, gameTick);
        }
        if (instance != null) {
            instance.recordParticipant(playerUuid, gameTick);
            instance.recordThreat(playerUuid, damage);
            UUID topThreat = instance.topThreatTarget();
            if (topThreat != null && entity instanceof Mob mob) {
                ServerPlayer topThreatPlayer = level.getServer().getPlayerList().getPlayer(topThreat);
                if (topThreatPlayer != null && topThreatPlayer.isAlive()) {
                    mob.setTarget(topThreatPlayer);
                }
            }
        }
        if (template != null && entity instanceof Mob mob) {
            updateNameplate(mob, template, gameTick);
        }
    }

    public MobInstance remove(UUID entityUuid) {
        neutralAggro.remove(entityUuid);
        nextManagedAttackTickByMob.remove(entityUuid);
        blockedAttackSinceTick.remove(entityUuid);
        evadingUntilTick.remove(entityUuid);
        nameplateStateByMob.remove(entityUuid);
        return trackedMobs.remove(entityUuid);
    }

    public boolean isEvading(UUID entityUuid, long gameTick) {
        Long untilTick = evadingUntilTick.get(entityUuid);
        if (untilTick == null) {
            return false;
        }
        if (untilTick < gameTick) {
            evadingUntilTick.remove(entityUuid);
            return false;
        }
        return true;
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
        for (UUID playerUuid : instance.participants().keySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUuid);
            if (player != null && player.distanceToSqr(entity) <= (32.0D * 32.0D)) {
                players.add(player);
            }
        }
        return players;
    }

    private List<MobSpawnGroup> candidateGroups(ServerLevel level, List<ServerPlayer> authenticatedPlayers, double activationRadius) {
        Map<Long, List<MobSpawnGroup>> byChunk = spawnIndexByDimensionChunk.get(level.dimension().location().toString());
        if (byChunk == null || byChunk.isEmpty() || authenticatedPlayers.isEmpty()) {
            return List.of();
        }
        Map<Long, MobSpawnGroup> candidates = new LinkedHashMap<>();
        int chunkRadius = Math.max(1, (int) Math.ceil(activationRadius / 16.0D));
        for (ServerPlayer player : authenticatedPlayers) {
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

    private boolean hasAuthenticatedPlayersNearby(List<ServerPlayer> authenticatedPlayers, MobSpawnGroup group, double radiusSqr) {
        for (ServerPlayer player : authenticatedPlayers) {
            if (player.distanceToSqr(group.centerX(), group.centerY(), group.centerZ()) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }

    private List<ServerPlayer> authenticatedPlayers(ServerLevel level, AuthService authService) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (authService.sessionManager().isAuthenticated(player.getUUID())) {
                players.add(player);
            }
        }
        return players;
    }

    private Map<Long, Integer> aliveCountsByGroup(ServerLevel level, ContentService contentService) {
        Map<Long, Integer> aliveByGroup = new HashMap<>();
        String dimensionId = level.dimension().location().toString();
        for (Map.Entry<UUID, MobInstance> entry : trackedMobs.entrySet()) {
            MobSpawnGroup group = contentService.spawnGroup(entry.getValue().spawnGroupId());
            if (group == null || !dimensionId.equals(group.dimension())) {
                continue;
            }
            Entity entity = level.getEntity(entry.getKey());
            if (entity instanceof LivingEntity living && living.isAlive()) {
                aliveByGroup.merge(group.id(), 1, Integer::sum);
            }
        }
        return aliveByGroup;
    }

    private int totalAlive(Map<Long, Integer> aliveByGroup) {
        int total = 0;
        for (Integer count : aliveByGroup.values()) {
            total += count;
        }
        return total;
    }

    private void cleanupTrackedMobs(ServerLevel level, ContentService contentService) {
        String dimensionId = level.dimension().location().toString();
        List<UUID> orphaned = new ArrayList<>();
        for (Map.Entry<UUID, MobInstance> entry : trackedMobs.entrySet()) {
            MobSpawnGroup group = contentService.spawnGroup(entry.getValue().spawnGroupId());
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
                continue;
            }
            if (TestWorldLayoutService.OVERWORLD_DIMENSION.equals(dimensionId)
                && (living.getY() < TestWorldLayoutService.FLAT_BEDROCK_Y - 1.0D
                || living.getY() > TestWorldLayoutService.FLAT_GRASS_Y + 40.0D)) {
                living.discard();
                orphaned.add(entry.getKey());
            }
        }
        for (UUID mobUuid : orphaned) {
            trackedMobs.remove(mobUuid);
            neutralAggro.remove(mobUuid);
            blockedAttackSinceTick.remove(mobUuid);
            evadingUntilTick.remove(mobUuid);
            nameplateStateByMob.remove(mobUuid);
        }
    }

    private void unloadUnseenMobs(ServerLevel level, List<ServerPlayer> authenticatedPlayers, double unloadRadius) {
        String dimensionId = level.dimension().location().toString();
        double unloadRadiusSqr = unloadRadius * unloadRadius;
        List<UUID> stale = new ArrayList<>();
        for (Map.Entry<UUID, MobInstance> entry : trackedMobs.entrySet()) {
            Entity raw = level.getEntity(entry.getKey());
            if (!(raw instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            if (hasNearbyAuthenticatedPlayer(mob, authenticatedPlayers, unloadRadiusSqr)) {
                continue;
            }
            stale.add(mob.getUUID());
        }
        if (stale.isEmpty()) {
            return;
        }
        for (UUID mobUuid : stale) {
            Entity raw = level.getEntity(mobUuid);
            if (raw != null && raw.isAlive()) {
                raw.discard();
            }
            remove(mobUuid);
        }
        LOGGER.debug("Unloaded {} unseen mobs in {}", stale.size(), dimensionId);
    }

    private static boolean hasNearbyAuthenticatedPlayer(Mob mob, List<ServerPlayer> players, double radiusSqr) {
        for (ServerPlayer player : players) {
            if (player.isAlive() && player.distanceToSqr(mob) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }

    private void ensureSpawnIndex() {
        List<MobSpawnGroup> groups = VeyloriaServerRuntime.instance().contentService().spawnGroups();
        if (indexedGroupsSnapshot == groups || Objects.equals(indexedGroupsSnapshot, groups)) {
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
        indexedGroupsSnapshot = groups;
        LOGGER.info("Indexed {} spawn groups for {} dimensions", groups.size(), spawnIndexByDimensionChunk.size());
    }

    private int rollPackSize(MobSpawnGroup group, MobTemplate template) {
        if (template.mobType() == MobType.BOSS) {
            return 1;
        }
        return GROUP_MIN_SIZE + random.nextInt(GROUP_MAX_SIZE - GROUP_MIN_SIZE + 1);
    }

    private int scaledPackSize(int value, MobSpawnGroup group, MobTemplate template) {
        if (template.mobType() != MobType.NORMAL) {
            return Math.max(1, value);
        }
        int zone = TestWorldLayoutService.zoneIndex(group.dimension(), group.centerZ());
        double zoneMultiplier = Math.max(0.40D, VeyloriaServerRuntime.instance().serverConfig().zonePackMultiplier(zone));
        return Math.max(1, (int) Math.ceil(value * NORMAL_PACK_MULTIPLIER * zoneMultiplier));
    }

    private int desiredMinAlive(MobSpawnGroup group, MobTemplate template) {
        if (template.mobType() == MobType.BOSS) {
            return 1;
        }
        return GROUP_MIN_SIZE;
    }

    private int desiredMaxAlive(MobSpawnGroup group, MobTemplate template) {
        if (template.mobType() == MobType.BOSS) {
            return 1;
        }
        return GROUP_MAX_SIZE;
    }

    private double rollPackSpread(MobSpawnGroup group) {
        if (group.packSpreadMax() <= group.packSpreadMin()) {
            return Math.max(0.0D, group.packSpreadMin());
        }
        return randomInRange(group.packSpreadMin(), group.packSpreadMax());
    }

    private long adjustedRespawnTicks(MobSpawnGroup group, MobTemplate template) {
        double base = group.respawnSeconds() * 20.0D;
        if (template != null && template.mobType() == MobType.BOSS) {
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
        ZoneSpawnBounds zoneBounds = resolveZoneSpawnBounds(group, template);
        ZoneSpawnBounds effectiveBounds = resolveSpawnBoundsNearPlayers(level, group, zoneBounds);
        double minSpawnX = effectiveBounds.minX();
        double maxSpawnX = effectiveBounds.maxX();
        double minSpawnZ = effectiveBounds.minZ();
        double maxSpawnZ = effectiveBounds.maxZ();
        double packCenterX = 0.0D;
        double packCenterZ = 0.0D;
        boolean centerResolved = false;
        for (int attempt = 0; attempt < 16; attempt++) {
            double candidateX = randomInRange(minSpawnX, maxSpawnX);
            double candidateZ = randomInRange(minSpawnZ, maxSpawnZ);
            if (!isAllowedSpawnPosition(group, candidateX, candidateZ)) {
                continue;
            }
            packCenterX = candidateX;
            packCenterZ = candidateZ;
            centerResolved = true;
            break;
        }
        if (!centerResolved) {
            return 0;
        }

        int spawned = 0;
        for (int index = 0; index < count; index++) {
            if (!(entityType.create(level) instanceof Mob mob)) {
                continue;
            }
            Double spawnX = null;
            Double spawnY = null;
            Double spawnZ = null;
            for (int attempt = 0; attempt < 12; attempt++) {
                double candidateX;
                double candidateZ;
                if (index == 0 && attempt == 0) {
                    candidateX = packCenterX;
                    candidateZ = packCenterZ;
                } else {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    double spreadBase = Math.max(3.0D, rollPackSpread(group));
                    double spread = spreadBase * (0.55D + random.nextDouble());
                    candidateX = clamp(packCenterX + Math.cos(angle) * spread,
                        minSpawnX, maxSpawnX);
                    candidateZ = clamp(packCenterZ + Math.sin(angle) * spread,
                        minSpawnZ, maxSpawnZ);
                }
                if (!isAllowedSpawnPosition(group, candidateX, candidateZ)) {
                    continue;
                }
                BlockPos pos = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(candidateX, group.centerY(), candidateZ));
                if (!level.hasChunkAt(pos)) {
                    continue;
                }
                double resolvedX = pos.getX() + 0.5D;
                double resolvedY = pos.getY();
                double resolvedZ = pos.getZ() + 0.5D;
                if (isTooCloseToManagedMob(level, resolvedX, resolvedY, resolvedZ, MIN_INTER_MOB_DISTANCE)) {
                    continue;
                }
                spawnX = resolvedX;
                spawnY = resolvedY;
                spawnZ = resolvedZ;
                break;
            }
            if (spawnX == null || spawnY == null || spawnZ == null) {
                continue;
            }
            mob.moveTo(spawnX, spawnY, spawnZ, random.nextFloat() * 360.0F, 0.0F);
            applyTemplate(mob, template);
            suppressDaylightBurn(level, mob, template);
            markManagedMob(mob, template.id(), group.id(), spawnX, spawnY, spawnZ);
            if (level.addFreshEntity(mob)) {
                trackedMobs.put(mob.getUUID(), new MobInstance(mob.getUUID(), template.id(), group.id()));
                spawned++;
            }
        }
        return spawned;
    }

    private void applyTemplate(Mob mob, MobTemplate template) {
        double expectedPlayerHit = PlayerStatService.estimatedUngearedDamage(template.level());
        double targetHealth = expectedPlayerHit * 15.0D * template.mobType().healthModifier();
        double seededHealth = template.baseHp() * (1 + 0.06D * (template.level() - 1));
        double maxHealth = Math.max(seededHealth, targetHealth);

        double expectedPlayerHealth = PlayerStatService.estimatedUngearedHealth(template.level());
        double attacksPerSecond = Math.max(0.7D, template.attackSpeed());
        double targetTimeToKillPlayer = switch (template.mobType()) {
            case NORMAL -> 30.0D;
            case ELITE -> 24.0D;
            case BOSS -> 18.0D;
        };
        double tunedDamage = (expectedPlayerHealth / targetTimeToKillPlayer) / attacksPerSecond;
        double damage = Math.max(template.baseDamage() * 0.45D,
            tunedDamage * template.mobType().damageModifier() * MOB_DAMAGE_TUNING_MULTIPLIER);
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
        if (mob.getAttribute(Attributes.SCALE) != null) {
            double scale = switch (template.mobType()) {
                case NORMAL -> 1.0D;
                case ELITE -> 1.18D;
                case BOSS -> 1.45D;
            };
            mob.getAttribute(Attributes.SCALE).setBaseValue(scale);
        }
        mob.setHealth((float) maxHealth);
        mob.setPersistenceRequired();
        mob.setCustomNameVisible(true);
        mob.setCustomName(buildNameplate(template, (int) Math.ceil(maxHealth), (int) Math.ceil(maxHealth)));
    }

    private void updateHostilityTargets(ServerLevel level, long gameTick, Map<Long, MobTemplate> templateCache,
                                        ContentService contentService, AuthService authService) {
        for (Map.Entry<UUID, MobInstance> entry : trackedMobs.entrySet()) {
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            MobTemplate template = templateForInstance(entry.getValue(), templateCache, contentService);
            if (template == null) {
                continue;
            }
            boolean updateTargetNow = shouldRunShard(mob.getUUID(), gameTick, TARGET_UPDATE_SHARDS);
            if (isEvading(entry.getKey(), gameTick)) {
                if (updateTargetNow) {
                    mob.setTarget(null);
                    mob.getNavigation().stop();
                }
                if (shouldRunShard(mob.getUUID(), gameTick, NAMEPLATE_UPDATE_SHARDS)) {
                    updateNameplate(mob, template, gameTick);
                }
                continue;
            }
            if (updateTargetNow || template.hostilityType() == HostilityType.NEUTRAL) {
                switch (template.hostilityType()) {
                    case FRIENDLY -> {
                        if (mob.getTarget() instanceof ServerPlayer) {
                            mob.setTarget(null);
                        } else if (mob.getTarget() instanceof Mob friendlyTargetMob) {
                            HostilityType hostility = hostility(friendlyTargetMob.getUUID());
                            if (hostility != HostilityType.HOSTILE) {
                                mob.setTarget(null);
                            }
                        }
                        LivingEntity hostileTarget = findNearestMobByHostility(level, mob, HostilityType.HOSTILE,
                            template.aggroRadius(), templateCache, contentService);
                        if (hostileTarget != null) {
                            mob.setTarget(hostileTarget);
                        }
                    }
                    case HOSTILE -> {
                        ServerPlayer topThreatTarget = highestThreatTarget(level, entry.getValue(), authService);
                        if (topThreatTarget != null) {
                            mob.setTarget(topThreatTarget);
                            break;
                        }
                        if (mob.getTarget() instanceof Mob hostileTargetMob) {
                            HostilityType hostility = hostility(hostileTargetMob.getUUID());
                            if (hostility != HostilityType.FRIENDLY) {
                                mob.setTarget(null);
                            }
                        }
                        LivingEntity currentTarget = mob.getTarget();
                        if (currentTarget == null || !currentTarget.isAlive() || !(currentTarget instanceof ServerPlayer)) {
                            LivingEntity friendlyTarget = findNearestMobByHostility(level, mob, HostilityType.FRIENDLY,
                                template.aggroRadius(), templateCache, contentService);
                            if (friendlyTarget != null) {
                                mob.setTarget(friendlyTarget);
                            }
                        }
                    }
                    case NEUTRAL -> {
                        ServerPlayer aggroTarget = neutralAggroTarget(level, mob.getUUID(), gameTick, authService);
                        if (aggroTarget == null) {
                            mob.setTarget(null);
                        } else if (!(mob.getTarget() instanceof ServerPlayer currentTarget)
                            || !currentTarget.getUUID().equals(aggroTarget.getUUID())) {
                            mob.setTarget(aggroTarget);
                        }
                    }
                }
            }
            suppressDaylightBurn(level, mob, template);
            if (shouldRunShard(mob.getUUID(), gameTick, NAMEPLATE_UPDATE_SHARDS)) {
                updateNameplate(mob, template, gameTick);
            }
        }
    }

    private ServerPlayer highestThreatTarget(ServerLevel level, MobInstance instance, AuthService authService) {
        UUID targetUuid = instance.topThreatTarget();
        if (targetUuid == null) {
            return null;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(targetUuid);
        if (player == null || !player.isAlive()) {
            return null;
        }
        if (!authService.sessionManager().isAuthenticated(player.getUUID())) {
            return null;
        }
        return player;
    }

    private ServerPlayer neutralAggroTarget(ServerLevel level, UUID mobUuid, long gameTick, AuthService authService) {
        AggroState state = neutralAggro.get(mobUuid);
        if (state == null || state.expiresAtTick() < gameTick) {
            neutralAggro.remove(mobUuid);
            return null;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(state.targetUuid());
        if (player == null || !player.isAlive()) {
            return null;
        }
        if (!authService.sessionManager().isAuthenticated(player.getUUID())) {
            return null;
        }
        return player;
    }

    private LivingEntity findNearestMobByHostility(ServerLevel level, Mob source, HostilityType desiredHostility,
                                                   double radius, Map<Long, MobTemplate> templateCache,
                                                   ContentService contentService) {
        double safeRadius = Math.max(1.0D, radius);
        double maxDistanceSqr = safeRadius * safeRadius;
        double bestDistance = maxDistanceSqr;
        LivingEntity selected = null;
        AABB searchBounds = source.getBoundingBox().inflate(safeRadius);
        for (Mob candidate : level.getEntitiesOfClass(Mob.class, searchBounds,
            mob -> mob.isAlive() && !mob.getUUID().equals(source.getUUID()))) {
            MobInstance candidateInstance = trackedMobs.get(candidate.getUUID());
            if (candidateInstance == null) {
                continue;
            }
            MobTemplate candidateTemplate = templateForInstance(candidateInstance, templateCache, contentService);
            if (candidateTemplate == null || candidateTemplate.hostilityType() != desiredHostility) {
                continue;
            }
            double distance = source.distanceToSqr(candidate);
            if (distance <= bestDistance) {
                bestDistance = distance;
                selected = candidate;
            }
        }
        return selected;
    }

    private void enforceLeashes(ServerLevel level, long gameTick, Map<Long, MobTemplate> templateCache,
                                Map<Long, MobSpawnGroup> groupCache) {
        for (Map.Entry<UUID, MobInstance> entry : trackedMobs.entrySet()) {
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            if (!shouldRunShard(mob.getUUID(), gameTick, LEASH_UPDATE_SHARDS)) {
                continue;
            }
            MobTemplate template = templateForInstance(entry.getValue(), templateCache);
            MobSpawnGroup group = groupForInstance(entry.getValue(), groupCache);
            if (template == null || group == null) {
                continue;
            }
            if (template.leashRadius() <= 0.0D) {
                continue;
            }
            Vec3 anchor = spawnAnchor(mob, group);
            double softLeashRadius = Math.max(LEASH_MIN_RADIUS, template.leashRadius() * LEASH_RADIUS_MULTIPLIER);
            double hardLeashRadius = softLeashRadius * HARD_LEASH_EVADE_MULTIPLIER;
            double distanceToAnchorSqr = mob.distanceToSqr(anchor);
            if (distanceToAnchorSqr <= softLeashRadius * softLeashRadius) {
                continue;
            }
            if (distanceToAnchorSqr <= hardLeashRadius * hardLeashRadius && mob.getTarget() == null && !isEvading(mob.getUUID(), gameTick)) {
                mob.getNavigation().moveTo(anchor.x, anchor.y, anchor.z, 1.03D);
                continue;
            }
            triggerEvade(level, mob, entry.getValue(), group, anchor, gameTick, "leash_hard");
        }
    }

    private void maintainCombatDistance(ServerLevel level, long gameTick, Map<Long, MobTemplate> templateCache,
                                        Map<Long, MobSpawnGroup> groupCache) {
        AuthService authService = VeyloriaServerRuntime.instance().authService();
        for (Map.Entry<UUID, MobInstance> entry : trackedMobs.entrySet()) {
            Entity raw = level.getEntity(entry.getKey());
            if (!(raw instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            if (!shouldRunShard(mob.getUUID(), gameTick, COMBAT_UPDATE_SHARDS)) {
                continue;
            }
            MobTemplate template = templateForInstance(entry.getValue(), templateCache);
            if (template == null) {
                blockedAttackSinceTick.remove(entry.getKey());
                continue;
            }
            ServerPlayer target = null;
            if (mob.getTarget() instanceof ServerPlayer currentTarget && currentTarget.isAlive()) {
                target = currentTarget;
            } else if (template.hostilityType() == HostilityType.NEUTRAL) {
                ServerPlayer neutralAggroTarget = neutralAggroTarget(level, mob.getUUID(), gameTick, authService);
                if (neutralAggroTarget != null) {
                    mob.setTarget(neutralAggroTarget);
                    target = neutralAggroTarget;
                }
            }
            if (target == null) {
                blockedAttackSinceTick.remove(entry.getKey());
                continue;
            }
            if (isEvading(entry.getKey(), gameTick)) {
                mob.getNavigation().stop();
                mob.setTarget(null);
                blockedAttackSinceTick.remove(entry.getKey());
                continue;
            }
            double distance = mob.distanceTo(target);
            boolean hasPath = true;
            if (distance > COMBAT_MAX_DISTANCE) {
                hasPath = mob.getNavigation().moveTo(target, 1.05D);
            } else if (distance < COMBAT_MIN_DISTANCE) {
                Vec3 delta = mob.position().subtract(target.position());
                if (delta.lengthSqr() > 0.0001D) {
                    Vec3 retreat = delta.normalize().scale(COMBAT_RETREAT_STEP);
                    mob.getNavigation().moveTo(mob.getX() + retreat.x, mob.getY(), mob.getZ() + retreat.z, 1.0D);
                }
            } else {
                Vec3 toTarget = target.position().subtract(mob.position());
                if (toTarget.lengthSqr() > 0.001D && ((gameTick + Math.abs(mob.getUUID().hashCode())) % 16L == 0L)) {
                    Vec3 forward = toTarget.normalize();
                    Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
                    double sideSign = (mob.getUUID().getLeastSignificantBits() & 1L) == 0L ? 1.0D : -1.0D;
                    Vec3 strafe = side.scale(sideSign * COMBAT_STRAFE_STEP);
                    double ringDistance = (COMBAT_MIN_DISTANCE + COMBAT_MAX_DISTANCE) * 0.5D;
                    Vec3 holdPoint = target.position().subtract(forward.scale(ringDistance)).add(strafe);
                    hasPath = mob.getNavigation().moveTo(holdPoint.x, target.getY(), holdPoint.z, 0.96D);
                } else {
                    mob.getNavigation().stop();
                }
            }

            boolean hasLineOfSight = mob.hasLineOfSight(target);
            if (!hasLineOfSight && distance > COMBAT_MIN_DISTANCE) {
                hasPath = mob.getNavigation().moveTo(target, 1.08D);
            }
            attemptManagedRetaliation(mob, target, template, gameTick);
            boolean blocked = distance > MIN_BLOCKED_PATH_DISTANCE && !hasPath && !hasLineOfSight;
            if (!blocked) {
                blockedAttackSinceTick.remove(entry.getKey());
                continue;
            }
            long blockedSince = blockedAttackSinceTick.computeIfAbsent(entry.getKey(), ignored -> gameTick);
            if (gameTick - blockedSince < UNREACHABLE_EVADE_TICKS) {
                continue;
            }
            MobSpawnGroup group = groupForInstance(entry.getValue(), groupCache);
            if (group == null) {
                blockedAttackSinceTick.remove(entry.getKey());
                continue;
            }
            Vec3 anchor = spawnAnchor(mob, group);
            double hardLeash = Math.max(LEASH_MIN_RADIUS, template.leashRadius() * LEASH_RADIUS_MULTIPLIER) * HARD_LEASH_EVADE_MULTIPLIER;
            if (mob.distanceToSqr(anchor) <= hardLeash * hardLeash) {
                mob.getNavigation().moveTo(anchor.x, anchor.y, anchor.z, 1.08D);
                blockedAttackSinceTick.put(entry.getKey(), gameTick);
                continue;
            }
            triggerEvade(level, mob, entry.getValue(), group, anchor, gameTick, "unreachable");
        }
    }

    private void attemptManagedRetaliation(Mob mob, ServerPlayer target, MobTemplate template, long gameTick) {
        if (template.hostilityType() == HostilityType.FRIENDLY) {
            return;
        }
        var attackAttribute = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        boolean requiresCustomMelee = template.hostilityType() == HostilityType.NEUTRAL || attackAttribute == null;
        if (!requiresCustomMelee) {
            return;
        }
        if (template.hostilityType() == HostilityType.NEUTRAL
            && !canNeutralDamage(mob.getUUID(), target.getUUID(), gameTick)) {
            return;
        }
        if (!mob.hasLineOfSight(target) || mob.distanceTo(target) > COMBAT_MAX_DISTANCE + 0.35D) {
            return;
        }
        long nextAttackTick = nextManagedAttackTickByMob.getOrDefault(mob.getUUID(), 0L);
        if (gameTick < nextAttackTick) {
            return;
        }
        double attackDamage = attackAttribute == null ? Math.max(1.0D, template.baseDamage()) : attackAttribute.getValue();
        float damage = (float) Math.max(1.0D, attackDamage);
        target.hurt(mob.damageSources().mobAttack(mob), damage);
        long cooldown = Math.max(8L, Math.round(20.0D / Math.max(0.2D, template.attackSpeed())));
        nextManagedAttackTickByMob.put(mob.getUUID(), gameTick + cooldown);
    }

    private void performIdleRoaming(ServerLevel level, long gameTick, Map<Long, MobTemplate> templateCache,
                                    Map<Long, MobSpawnGroup> groupCache) {
        for (Map.Entry<UUID, MobInstance> entry : trackedMobs.entrySet()) {
            Entity raw = level.getEntity(entry.getKey());
            if (!(raw instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            if (!shouldRunShard(mob.getUUID(), gameTick, LEASH_UPDATE_SHARDS)) {
                continue;
            }
            if (mob.getTarget() != null || isEvading(mob.getUUID(), gameTick)) {
                continue;
            }
            MobTemplate template = templateForInstance(entry.getValue(), templateCache);
            MobSpawnGroup group = groupForInstance(entry.getValue(), groupCache);
            if (template == null || group == null) {
                continue;
            }
            int cadence = switch (template.hostilityType()) {
                case HOSTILE -> 14;
                case NEUTRAL -> 20;
                case FRIENDLY -> 28;
            };
            int jitter = Math.floorMod(mob.getUUID().hashCode(), cadence);
            if ((gameTick + jitter) % cadence != 0L) {
                continue;
            }
            Vec3 anchor = spawnAnchor(mob, group);
            double roamRadius = Math.max(IDLE_ROAM_MIN_RADIUS, template.leashRadius() * IDLE_ROAM_RADIUS_MULTIPLIER);
            double roamSpeed = switch (template.hostilityType()) {
                case HOSTILE -> 1.12D;
                case NEUTRAL -> 1.04D;
                case FRIENDLY -> 0.98D;
            };
            for (int attempt = 0; attempt < 10; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double distance = 8.0D + random.nextDouble() * roamRadius;
                double targetX = anchor.x + Math.cos(angle) * distance;
                double targetZ = anchor.z + Math.sin(angle) * distance;
                if (!isAllowedSpawnPosition(group, targetX, targetZ)) {
                    continue;
                }
                BlockPos targetPos = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(targetX, group.centerY(), targetZ));
                if (!mob.getNavigation().moveTo(targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D, roamSpeed)) {
                    continue;
                }
                break;
            }
        }
    }

    private void triggerEvade(ServerLevel level, Mob mob, MobInstance instance, MobSpawnGroup group, Vec3 anchor, long gameTick, String reason) {
        BlockPos resetPos = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            BlockPos.containing(anchor.x, anchor.y, anchor.z));
        mob.getNavigation().stop();
        mob.setTarget(null);
        mob.teleportTo(resetPos.getX() + 0.5D, resetPos.getY(), resetPos.getZ() + 0.5D);
        mob.setHealth(mob.getMaxHealth());
        instance.clearCombatState();
        neutralAggro.remove(mob.getUUID());
        blockedAttackSinceTick.remove(mob.getUUID());
        evadingUntilTick.put(mob.getUUID(), gameTick + EVADE_IMMUNITY_TICKS);
        LOGGER.debug("Mob {} evaded (reason={}, group={})", mob.getUUID(), reason, group.id());
    }

    private void clearExpiredNeutralAggro(long gameTick) {
        for (Map.Entry<UUID, AggroState> entry : neutralAggro.entrySet()) {
            if (entry.getValue().expiresAtTick() < gameTick) {
                neutralAggro.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private int spawnRareGroupsOnStartup(ServerLevel level, long gameTime, Map<Long, Integer> aliveByGroup,
                                         ContentService contentService) {
        String dimensionId = level.dimension().location().toString();
        if (startupRareSpawnsDone.putIfAbsent(dimensionId, true) != null) {
            return 0;
        }
        int spawnedTotal = 0;
        for (MobSpawnGroup group : contentService.spawnGroups()) {
            if (!dimensionId.equals(group.dimension())) {
                continue;
            }
            MobTemplate template = contentService.mobTemplate(group.mobTemplateId());
            if (!isStartupRareGroup(group, template)) {
                continue;
            }
            level.getChunk(blockToChunk(group.centerX()), blockToChunk(group.centerZ()));
            int alive = aliveByGroup.getOrDefault(group.id(), 0);
            if (alive >= group.minAlive()) {
                continue;
            }
            int toSpawn = Math.min(group.maxAlive() - alive, group.minAlive() - alive);
            if (toSpawn <= 0) {
                continue;
            }
            int spawned = spawnGroup(level, group, toSpawn);
            if (spawned <= 0) {
                continue;
            }
            nextSpawnTickByGroup.put(group.id(), gameTime + adjustedRespawnTicks(group, template));
            aliveByGroup.merge(group.id(), spawned, Integer::sum);
            spawnedTotal += spawned;
        }
        LOGGER.info("Startup rare spawn bootstrap in {} produced {} entities", dimensionId, spawnedTotal);
        return spawnedTotal;
    }

    private boolean isStartupRareGroup(MobSpawnGroup group, MobTemplate template) {
        if (template == null || template.mobType() == MobType.NORMAL) {
            return false;
        }
        return group.minAlive() > 0 && group.maxAlive() == 1 && group.respawnSeconds() >= 600;
    }

    private static void markManagedMob(Mob mob, long templateId, long spawnGroupId, double spawnX, double spawnY, double spawnZ) {
        mob.getPersistentData().putBoolean(TAG_CUSTOM_MOB, true);
        mob.getPersistentData().putLong(TAG_TEMPLATE_ID, templateId);
        mob.getPersistentData().putLong(TAG_SPAWN_GROUP_ID, spawnGroupId);
        mob.getPersistentData().putDouble(TAG_SPAWN_X, spawnX);
        mob.getPersistentData().putDouble(TAG_SPAWN_Y, spawnY);
        mob.getPersistentData().putDouble(TAG_SPAWN_Z, spawnZ);
    }

    private static void ensureSpawnAnchorTag(Mob mob) {
        if (mob.getPersistentData().contains(TAG_SPAWN_X)
            && mob.getPersistentData().contains(TAG_SPAWN_Y)
            && mob.getPersistentData().contains(TAG_SPAWN_Z)) {
            return;
        }
        mob.getPersistentData().putDouble(TAG_SPAWN_X, mob.getX());
        mob.getPersistentData().putDouble(TAG_SPAWN_Y, mob.getY());
        mob.getPersistentData().putDouble(TAG_SPAWN_Z, mob.getZ());
    }

    private static Vec3 spawnAnchor(Mob mob, MobSpawnGroup group) {
        ensureSpawnAnchorTag(mob);
        return new Vec3(
            mob.getPersistentData().getDouble(TAG_SPAWN_X),
            mob.getPersistentData().getDouble(TAG_SPAWN_Y),
            mob.getPersistentData().getDouble(TAG_SPAWN_Z)
        );
    }

    private static boolean shouldRunShard(UUID entityUuid, long gameTick, int shardCount) {
        if (shardCount <= 1) {
            return true;
        }
        int shard = Math.floorMod(entityUuid.hashCode(), shardCount);
        return shard == Math.floorMod(gameTick, shardCount);
    }

    private MobTemplate templateForInstance(MobInstance instance, Map<Long, MobTemplate> cache) {
        return templateForInstance(instance, cache, VeyloriaServerRuntime.instance().contentService());
    }

    private MobTemplate templateForInstance(MobInstance instance, Map<Long, MobTemplate> cache,
                                            ContentService contentService) {
        return cache.computeIfAbsent(instance.templateId(), contentService::mobTemplate);
    }

    private MobSpawnGroup groupForInstance(MobInstance instance, Map<Long, MobSpawnGroup> cache) {
        return cache.computeIfAbsent(instance.spawnGroupId(),
            id -> VeyloriaServerRuntime.instance().contentService().spawnGroup(id));
    }

    private boolean isTooCloseToManagedMob(ServerLevel level, double x, double y, double z, double radius) {
        AABB search = new AABB(x - radius, y - 1.5D, z - radius, x + radius, y + 2.5D, z + radius);
        for (Mob other : level.getEntitiesOfClass(Mob.class, search, mob -> mob.isAlive() && isManagedMob(mob))) {
            if (other.distanceToSqr(x, y, z) < radius * radius) {
                return true;
            }
        }
        return false;
    }

    private void updateNameplate(Mob mob, MobTemplate template, long gameTick) {
        int hpMax = Math.max(1, (int) Math.ceil(mob.getMaxHealth()));
        int hpCurrent = Math.max(0, (int) Math.ceil(mob.getHealth()));
        NameplateState state = nameplateStateByMob.get(mob.getUUID());
        if (state != null
            && state.hpCurrent() == hpCurrent
            && state.hpMax() == hpMax
            && gameTick - state.lastTick() < NAMEPLATE_HEARTBEAT_TICKS) {
            return;
        }
        mob.setCustomName(buildNameplate(template, hpCurrent, hpMax));
        nameplateStateByMob.put(mob.getUUID(), new NameplateState(hpCurrent, hpMax, gameTick));
    }

    public static Component buildNameplate(MobTemplate template, int hpCurrent, int hpMax) {
        ChatFormatting relationColor = switch (template.hostilityType()) {
            case FRIENDLY -> ChatFormatting.GREEN;
            case NEUTRAL -> ChatFormatting.YELLOW;
            case HOSTILE -> ChatFormatting.RED;
        };
        String title = "[" + template.level() + "] " + switch (template.mobType()) {
            case NORMAL -> template.name();
            case ELITE -> "Элитный " + template.name();
            case BOSS -> "Босс " + template.name();
        };
        int safeMax = Math.max(1, hpMax);
        int filled = (int) Math.round((Math.max(0, hpCurrent) / (double) safeMax) * NAMEPLATE_SEGMENTS);
        if (filled < 0) {
            filled = 0;
        } else if (filled > NAMEPLATE_SEGMENTS) {
            filled = NAMEPLATE_SEGMENTS;
        }
        int empty = NAMEPLATE_SEGMENTS - filled;
        Component hpBar = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal("#".repeat(filled)).withStyle(ChatFormatting.RED))
            .append(Component.literal("-".repeat(empty)).withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(" " + Math.max(0, hpCurrent) + "/" + safeMax).withStyle(ChatFormatting.GRAY));
        return Component.empty()
            .append(Component.literal(title).withStyle(relationColor))
            .append(Component.literal(" "))
            .append(hpBar);
    }

    private static void suppressDaylightBurn(ServerLevel level, Mob mob, MobTemplate template) {
        if (template.hostilityType() != HostilityType.HOSTILE) {
            return;
        }
        if (!isDirectDaylight(level, mob)) {
            return;
        }
        if (mob.getRemainingFireTicks() > 0) {
            mob.setRemainingFireTicks(0);
        }
    }

    public boolean isDaylightBurnScenario(Entity entity) {
        if (!(entity instanceof Mob mob)) {
            return false;
        }
        MobTemplate template = template(mob.getUUID());
        if (template == null || template.hostilityType() != HostilityType.HOSTILE) {
            return false;
        }
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        return isDirectDaylight(level, mob);
    }

    private static boolean isDirectDaylight(ServerLevel level, Mob mob) {
        if (!level.isDay()) {
            return false;
        }
        BlockPos pos = BlockPos.containing(mob.getX(), mob.getEyeY(), mob.getZ());
        return level.canSeeSky(pos);
    }

    private ZoneSpawnBounds resolveZoneSpawnBounds(MobSpawnGroup group, MobTemplate template) {
        if (template.mobType() != MobType.NORMAL) {
            return null;
        }
        if (!TestWorldLayoutService.OVERWORLD_DIMENSION.equals(group.dimension())) {
            return null;
        }
        int zoneIndex = TestWorldLayoutService.zoneIndex(group.dimension(), group.centerZ());
        if (zoneIndex < 1) {
            return null;
        }
        double minX = -TestWorldLayoutService.ZONE_HALF_WIDTH + ZONE_SPAWN_MARGIN;
        double maxX = TestWorldLayoutService.ZONE_HALF_WIDTH - ZONE_SPAWN_MARGIN;
        double southBoundary = TestWorldLayoutService.FIRST_ZONE_SOUTH_Z
            - (zoneIndex - 1) * TestWorldLayoutService.ZONE_LENGTH;
        double northBoundary = southBoundary - TestWorldLayoutService.ZONE_LENGTH + 1;
        double minZ = northBoundary + ZONE_SPAWN_MARGIN;
        double maxZ = southBoundary - ZONE_SPAWN_MARGIN;
        if (minX >= maxX || minZ >= maxZ) {
            return null;
        }
        return new ZoneSpawnBounds(minX, maxX, minZ, maxZ);
    }

    private ZoneSpawnBounds resolveSpawnBoundsNearPlayers(ServerLevel level, MobSpawnGroup group, ZoneSpawnBounds preferredBounds) {
        double baseMinX = preferredBounds == null ? group.centerX() - group.radiusX() : preferredBounds.minX();
        double baseMaxX = preferredBounds == null ? group.centerX() + group.radiusX() : preferredBounds.maxX();
        double baseMinZ = preferredBounds == null ? group.centerZ() - group.radiusZ() : preferredBounds.minZ();
        double baseMaxZ = preferredBounds == null ? group.centerZ() + group.radiusZ() : preferredBounds.maxZ();
        if (baseMinX >= baseMaxX || baseMinZ >= baseMaxZ) {
            return new ZoneSpawnBounds(group.centerX() - group.radiusX(), group.centerX() + group.radiusX(),
                group.centerZ() - group.radiusZ(), group.centerZ() + group.radiusZ());
        }
        AuthService authService = VeyloriaServerRuntime.instance().authService();
        if (authService == null) {
            return new ZoneSpawnBounds(baseMinX, baseMaxX, baseMinZ, baseMaxZ);
        }
        ServerPlayer anchor = nearestAuthenticatedPlayer(level, authService, group.centerX(), group.centerZ());
        if (anchor == null) {
            return new ZoneSpawnBounds(baseMinX, baseMaxX, baseMinZ, baseMaxZ);
        }
        double localMinX = Math.max(baseMinX, anchor.getX() - LOADED_SPAWN_RADIUS);
        double localMaxX = Math.min(baseMaxX, anchor.getX() + LOADED_SPAWN_RADIUS);
        double localMinZ = Math.max(baseMinZ, anchor.getZ() - LOADED_SPAWN_RADIUS);
        double localMaxZ = Math.min(baseMaxZ, anchor.getZ() + LOADED_SPAWN_RADIUS);
        if (localMinX >= localMaxX || localMinZ >= localMaxZ) {
            return new ZoneSpawnBounds(baseMinX, baseMaxX, baseMinZ, baseMaxZ);
        }
        return new ZoneSpawnBounds(localMinX, localMaxX, localMinZ, localMaxZ);
    }

    private static ServerPlayer nearestAuthenticatedPlayer(ServerLevel level, AuthService authService, double x, double z) {
        ServerPlayer nearest = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || !authService.sessionManager().isAuthenticated(player.getUUID())) {
                continue;
            }
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            double distanceSqr = dx * dx + dz * dz;
            if (distanceSqr < bestDistanceSqr) {
                bestDistanceSqr = distanceSqr;
                nearest = player;
            }
        }
        return nearest;
    }

    private static boolean isAllowedSpawnPosition(MobSpawnGroup group, double x, double z) {
        return !TestWorldLayoutService.isInSafeCorridor(group.dimension(), x, z)
            && !TestWorldLayoutService.isSeparatorLine(group.dimension(), z);
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

    private record NameplateState(int hpCurrent, int hpMax, long lastTick) {
    }

    private record ZoneSpawnBounds(double minX, double maxX, double minZ, double maxZ) {
    }

    private record AggroState(UUID targetUuid, long expiresAtTick) {
    }
}
