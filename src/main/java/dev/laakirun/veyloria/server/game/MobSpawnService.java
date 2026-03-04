package dev.laakirun.veyloria.server.game;

import dev.laakirun.veyloria.common.model.HostilityType;
import dev.laakirun.veyloria.common.model.MobType;
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
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MobSpawnService {
    private static final Logger LOGGER = LoggerFactory.getLogger("veyloria.spawn");
    private static final long NEUTRAL_RETALIATE_TICKS = 20L * 20L;
    private static final long UNREACHABLE_EVADE_TICKS = 20L * 3L;
    private static final long EVADE_IMMUNITY_TICKS = 20L * 2L;
    private static final double COMBAT_MIN_DISTANCE = 1.8D;
    private static final double COMBAT_MAX_DISTANCE = 2.8D;
    private static final double COMBAT_RETREAT_STEP = 1.4D;
    private static final String TAG_CUSTOM_MOB = "veyloria_custom_mob";
    private static final String TAG_TEMPLATE_ID = "veyloria_template_id";
    private static final String TAG_SPAWN_GROUP_ID = "veyloria_spawn_group_id";

    private final Random random = new Random();
    private final Map<UUID, MobInstance> trackedMobs = new ConcurrentHashMap<>();
    private final Map<Long, Long> nextSpawnTickByGroup = new ConcurrentHashMap<>();
    private final Map<UUID, AggroState> neutralAggro = new ConcurrentHashMap<>();
    private final Map<UUID, Long> blockedAttackSinceTick = new ConcurrentHashMap<>();
    private final Map<UUID, Long> evadingUntilTick = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, List<MobSpawnGroup>>> spawnIndexByDimensionChunk = new ConcurrentHashMap<>();
    private final Map<String, Boolean> startupRareSpawnsDone = new ConcurrentHashMap<>();
    private List<MobSpawnGroup> indexedGroupsSnapshot = List.of();

    public void tick(MinecraftServer server) {
        ensureSpawnIndex();
        long gameTime = server.overworld().getGameTime();
        clearExpiredNeutralAggro(gameTime);

        for (ServerLevel level : server.getAllLevels()) {
            cleanupTrackedMobs(level);
            spawnRareGroupsOnStartup(level, gameTime);
            updateHostilityTargets(level, gameTime);
            maintainCombatDistance(level, gameTime);
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
        if (instance != null) {
            instance.recordParticipant(playerUuid, gameTick);
            instance.recordThreat(playerUuid, damage);
            UUID topThreat = instance.topThreatTarget();
            Entity entity = level.getEntity(entityUuid);
            if (topThreat != null && entity instanceof Mob mob) {
                ServerPlayer topThreatPlayer = level.getServer().getPlayerList().getPlayer(topThreat);
                if (topThreatPlayer != null && topThreatPlayer.isAlive()) {
                    mob.setTarget(topThreatPlayer);
                }
            }
        }
    }

    public MobInstance remove(UUID entityUuid) {
        neutralAggro.remove(entityUuid);
        blockedAttackSinceTick.remove(entityUuid);
        evadingUntilTick.remove(entityUuid);
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
            blockedAttackSinceTick.remove(mobUuid);
            evadingUntilTick.remove(mobUuid);
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
        double packCenterX = 0.0D;
        double packCenterZ = 0.0D;
        boolean centerResolved = false;
        for (int attempt = 0; attempt < 16; attempt++) {
            double candidateX = randomInRange(group.centerX() - group.radiusX(), group.centerX() + group.radiusX());
            double candidateZ = randomInRange(group.centerZ() - group.radiusZ(), group.centerZ() + group.radiusZ());
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
            double x = packCenterX;
            double z = packCenterZ;
            if (index > 0) {
                boolean resolved = false;
                for (int attempt = 0; attempt < 8; attempt++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    double spread = rollPackSpread(group);
                    double candidateX = clamp(packCenterX + Math.cos(angle) * spread,
                        group.centerX() - group.radiusX(), group.centerX() + group.radiusX());
                    double candidateZ = clamp(packCenterZ + Math.sin(angle) * spread,
                        group.centerZ() - group.radiusZ(), group.centerZ() + group.radiusZ());
                    if (!isAllowedSpawnPosition(group, candidateX, candidateZ)) {
                        continue;
                    }
                    x = candidateX;
                    z = candidateZ;
                    resolved = true;
                    break;
                }
                if (!resolved && !isAllowedSpawnPosition(group, x, z)) {
                    continue;
                }
            }
            BlockPos pos = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, group.centerY(), z));
            mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            applyTemplate(mob, template);
            markManagedMob(mob, template.id(), group.id());
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
        double damage = Math.max(template.baseDamage() * 0.35D, tunedDamage * template.mobType().damageModifier());
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
        mob.setCustomName(Component.literal(formatName(template)));
    }

    private String formatName(MobTemplate template) {
        return "[" + template.level() + "] " + switch (template.mobType()) {
            case NORMAL -> template.name();
            case ELITE -> "Элитный " + template.name();
            case BOSS -> "Босс " + template.name();
        };
    }

    private void updateHostilityTargets(ServerLevel level, long gameTick) {
        for (Map.Entry<UUID, MobInstance> entry : trackedMobs.entrySet()) {
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            if (isEvading(entry.getKey(), gameTick)) {
                mob.setTarget(null);
                mob.getNavigation().stop();
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
                    ServerPlayer topThreatTarget = highestThreatTarget(level, entry.getValue());
                    if (topThreatTarget != null) {
                        mob.setTarget(topThreatTarget);
                        break;
                    }
                    LivingEntity currentTarget = mob.getTarget();
                    if (currentTarget == null || !currentTarget.isAlive() || !(currentTarget instanceof ServerPlayer)) {
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

    private ServerPlayer highestThreatTarget(ServerLevel level, MobInstance instance) {
        UUID targetUuid = instance.topThreatTarget();
        if (targetUuid == null) {
            return null;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(targetUuid);
        if (player == null || !player.isAlive()) {
            return null;
        }
        if (!VeyloriaServerRuntime.instance().authService().sessionManager().isAuthenticated(player.getUUID())) {
            return null;
        }
        return player;
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
            triggerEvade(level, mob, entry.getValue(), group, level.getGameTime(), "leash");
        }
    }

    private void maintainCombatDistance(ServerLevel level, long gameTick) {
        for (Map.Entry<UUID, MobInstance> entry : trackedMobs.entrySet()) {
            Entity raw = level.getEntity(entry.getKey());
            if (!(raw instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            if (!(mob.getTarget() instanceof ServerPlayer target) || !target.isAlive()) {
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
                hasPath = mob.getNavigation().moveTo(target, 1.0D);
            } else if (distance < COMBAT_MIN_DISTANCE) {
                Vec3 delta = mob.position().subtract(target.position());
                if (delta.lengthSqr() > 0.0001D) {
                    Vec3 retreat = delta.normalize().scale(COMBAT_RETREAT_STEP);
                    mob.getNavigation().moveTo(mob.getX() + retreat.x, mob.getY(), mob.getZ() + retreat.z, 1.0D);
                }
            } else {
                mob.getNavigation().stop();
            }

            boolean hasLineOfSight = mob.hasLineOfSight(target);
            boolean inAttackRange = distance >= COMBAT_MIN_DISTANCE && distance <= COMBAT_MAX_DISTANCE;
            boolean blocked = !hasLineOfSight || (!inAttackRange && !hasPath);
            if (!blocked) {
                blockedAttackSinceTick.remove(entry.getKey());
                continue;
            }
            long blockedSince = blockedAttackSinceTick.computeIfAbsent(entry.getKey(), ignored -> gameTick);
            if (gameTick - blockedSince < UNREACHABLE_EVADE_TICKS) {
                continue;
            }
            MobSpawnGroup group = VeyloriaServerRuntime.instance().contentService().spawnGroup(entry.getValue().spawnGroupId());
            if (group == null) {
                blockedAttackSinceTick.remove(entry.getKey());
                continue;
            }
            triggerEvade(level, mob, entry.getValue(), group, gameTick, "unreachable");
        }
    }

    private void triggerEvade(ServerLevel level, Mob mob, MobInstance instance, MobSpawnGroup group, long gameTick, String reason) {
        BlockPos resetPos = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            BlockPos.containing(group.centerX(), group.centerY(), group.centerZ()));
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

    private void spawnRareGroupsOnStartup(ServerLevel level, long gameTime) {
        String dimensionId = level.dimension().location().toString();
        if (startupRareSpawnsDone.putIfAbsent(dimensionId, true) != null) {
            return;
        }
        int spawnedTotal = 0;
        for (MobSpawnGroup group : VeyloriaServerRuntime.instance().contentService().spawnGroups()) {
            if (!dimensionId.equals(group.dimension())) {
                continue;
            }
            MobTemplate template = VeyloriaServerRuntime.instance().contentService().mobTemplate(group.mobTemplateId());
            if (!isStartupRareGroup(group, template)) {
                continue;
            }
            level.getChunk(blockToChunk(group.centerX()), blockToChunk(group.centerZ()));
            int alive = countAlive(level, group.id());
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
            spawnedTotal += spawned;
        }
        LOGGER.info("Startup rare spawn bootstrap in {} produced {} entities", dimensionId, spawnedTotal);
    }

    private boolean isStartupRareGroup(MobSpawnGroup group, MobTemplate template) {
        if (template == null || template.mobType() == MobType.NORMAL) {
            return false;
        }
        return group.minAlive() > 0 && group.maxAlive() == 1 && group.respawnSeconds() >= 600;
    }

    private static void markManagedMob(Mob mob, long templateId, long spawnGroupId) {
        mob.getPersistentData().putBoolean(TAG_CUSTOM_MOB, true);
        mob.getPersistentData().putLong(TAG_TEMPLATE_ID, templateId);
        mob.getPersistentData().putLong(TAG_SPAWN_GROUP_ID, spawnGroupId);
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

    private record AggroState(UUID targetUuid, long expiresAtTick) {
    }
}
