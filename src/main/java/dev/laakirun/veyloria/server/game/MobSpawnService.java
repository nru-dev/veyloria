package dev.laakirun.veyloria.server.game;

import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import dev.laakirun.veyloria.server.content.MobSpawnGroup;
import dev.laakirun.veyloria.server.content.MobTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public final class MobSpawnService {
    private final Random random = new Random();
    private final Map<UUID, MobInstance> trackedMobs = new ConcurrentHashMap<>();
    private final Map<Long, Long> nextSpawnTickByGroup = new ConcurrentHashMap<>();

    public void tick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        for (ServerLevel level : server.getAllLevels()) {
            int activeInDimension = (int) trackedMobs.keySet().stream()
                .map(level::getEntity)
                .filter(LivingEntity.class::isInstance)
                .count();
            for (MobSpawnGroup group : VeyloriaServerRuntime.instance().contentService().spawnGroups()) {
                if (!matches(level, group)) {
                    continue;
                }
                if (!hasAuthenticatedPlayersNearby(level, group)) {
                    continue;
                }
                if (activeInDimension >= VeyloriaServerRuntime.instance().serverConfig().maxActiveMobsPerDimension()) {
                    return;
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
                spawnGroup(level, group, missing);
                nextSpawnTickByGroup.put(group.id(), gameTime + adjustedRespawnTicks(group, VeyloriaServerRuntime.instance().contentService().mobTemplate(group.mobTemplateId())));
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

    public void recordHit(UUID entityUuid, UUID playerUuid, long gameTick) {
        MobInstance instance = trackedMobs.get(entityUuid);
        if (instance != null) {
            instance.recordParticipant(playerUuid, gameTick);
        }
    }

    public MobInstance remove(UUID entityUuid) {
        return trackedMobs.remove(entityUuid);
    }

    public List<ServerPlayer> eligibleParticipants(ServerLevel level, MobInstance instance) {
        List<ServerPlayer> players = new ArrayList<>();
        LivingEntity entity = (LivingEntity) level.getEntity(instance.entityUuid());
        if (entity == null) {
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

    private boolean matches(ServerLevel level, MobSpawnGroup group) {
        ResourceKey<Level> key = level.dimension();
        return key.location().toString().equals(group.dimension());
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

    private int rollPackSize(MobSpawnGroup group) {
        if (group.packSizeMax() <= group.packSizeMin()) {
            return group.packSizeMin();
        }
        return group.packSizeMin() + random.nextInt(group.packSizeMax() - group.packSizeMin() + 1);
    }

    private long adjustedRespawnTicks(MobSpawnGroup group, MobTemplate template) {
        double base = group.respawnSeconds() * 20.0D;
        if (template != null && template.mobType().name().equals("BOSS")) {
            return Math.max(20L, Math.round(base / VeyloriaServerRuntime.instance().ratesConfig().bossRespawnRate()));
        }
        return Math.round(base);
    }

    @SuppressWarnings("unchecked")
    private void spawnGroup(ServerLevel level, MobSpawnGroup group, int count) {
        MobTemplate template = VeyloriaServerRuntime.instance().contentService().mobTemplate(group.mobTemplateId());
        if (template == null) {
            return;
        }
        EntityType<?> rawType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(template.entityModel()));
        if (!(rawType instanceof EntityType<?> entityType)) {
            return;
        }
        for (int index = 0; index < count; index++) {
            if (!(entityType.create(level) instanceof Mob mob)) {
                continue;
            }
            double x = randomInRange(group.centerX() - group.radiusX(), group.centerX() + group.radiusX());
            double z = randomInRange(group.centerZ() - group.radiusZ(), group.centerZ() + group.radiusZ());
            BlockPos pos = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, group.centerY(), z));
            mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            applyTemplate(mob, template);
            level.addFreshEntity(mob);
            trackedMobs.put(mob.getUUID(), new MobInstance(mob.getUUID(), template.id(), group.id()));
        }
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

    private double randomInRange(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }
}
