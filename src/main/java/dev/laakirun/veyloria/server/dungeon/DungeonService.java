package dev.laakirun.veyloria.server.dungeon;

import dev.laakirun.veyloria.common.VeyloriaConstants;
import dev.laakirun.veyloria.common.model.CharacterProfile;
import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import dev.laakirun.veyloria.server.content.ContentService;
import dev.laakirun.veyloria.server.content.MobTemplate;
import dev.laakirun.veyloria.server.game.MobSpawnService;
import dev.laakirun.veyloria.server.game.ServerMarkers;
import dev.laakirun.veyloria.server.game.TestWorldLayoutService;
import dev.laakirun.veyloria.server.game.VeyloriaServerEvents;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DungeonService {
    private static final Logger SPAWN_LOGGER = LoggerFactory.getLogger("veyloria.spawn");
    private static final Logger COMBAT_LOGGER = LoggerFactory.getLogger("veyloria.combat");
    private static final Logger LOOT_LOGGER = LoggerFactory.getLogger("veyloria.loot");

    public static final String DUNGEON_DIMENSION_ID = VeyloriaConstants.MOD_ID + ":dungeon_depths";
    public static final ResourceKey<Level> DUNGEON_DIMENSION_KEY = ResourceKey.create(
        Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "dungeon_depths")
    );

    private static final String ENTRANCE_STRUCTURE_ID = VeyloriaConstants.MOD_ID + ":dungeon_cave";
    private static final String DUNGEON_LOOT_TABLE = "dungeon_chest";

    private static final String DUNGEON_MOB_CODE = "dungeon_crawler";
    private static final String DUNGEON_ELITE_CODE = "dungeon_watcher";
    private static final String DUNGEON_BOSS_CODE = "dungeon_boss_lurker";

    private static final String TAG_DUNGEON_ENTITY = "veyloria_dungeon_entity";
    private static final String TAG_DUNGEON_MOB = "veyloria_dungeon_mob";
    private static final String TAG_DUNGEON_NPC = "veyloria_dungeon_npc";
    private static final String TAG_DUNGEON_RUN = "veyloria_dungeon_run";

    private static final int INSTANCE_SPACING = 768;
    private static final int INSTANCE_BOUNDS_RADIUS = 112;
    private static final int INSTANCE_MIN_Y = 48;
    private static final int INSTANCE_MAX_Y = 106;
    private static final int FLOOR_Y = 72;

    private static final double ENTRANCE_TRIGGER_RADIUS = 10.0D;
    private static final long ENTRANCE_COOLDOWN_TICKS = 30L;
    private static final long PROMPT_INTERVAL_TICKS = 60L;
    private static final long COMBAT_LOCK_RELEASE_TICKS = 80L;
    private static final long UNREACHABLE_EVADE_TICKS = 20L * 3L;
    private static final long NAMEPLATE_HEARTBEAT_TICKS = 20L;
    private static final double MIN_BLOCKED_PATH_DISTANCE = 3.4D;
    private static final int MAX_SCALING_PLAYERS = 5;

    private final Random random = new Random();
    private final Map<UUID, DungeonRun> runsByPartyKey = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> runKeyByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> runKeyByMob = new ConcurrentHashMap<>();
    private final Map<UUID, MobTemplate> templateByMob = new ConcurrentHashMap<>();
    private final Map<UUID, Long> blockedAttackSinceTickByMob = new ConcurrentHashMap<>();
    private final Map<UUID, NameplateState> nameplateStateByMob = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> runKeyByNpc = new ConcurrentHashMap<>();
    private final Map<UUID, Long> entryCooldownByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPromptTickByPlayer = new ConcurrentHashMap<>();
    private int nextInstanceIndex = 1;

    public void tick(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            return;
        }
        ServerLevel dungeonLevel = server.getLevel(DUNGEON_DIMENSION_KEY);
        if (dungeonLevel == null) {
            return;
        }
        long gameTime = server.overworld().getGameTime();
        updateActivePlayers(server, dungeonLevel, gameTime);
        tickRuns(server, dungeonLevel, gameTime);
        processEntranceProximity(server, dungeonLevel, gameTime);
    }

    public void reset() {
        runsByPartyKey.clear();
        runKeyByPlayer.clear();
        runKeyByMob.clear();
        templateByMob.clear();
        blockedAttackSinceTickByMob.clear();
        nameplateStateByMob.clear();
        runKeyByNpc.clear();
        entryCooldownByPlayer.clear();
        lastPromptTickByPlayer.clear();
        nextInstanceIndex = 1;
    }

    public void onPlayerLoggedOut(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        runKeyByPlayer.remove(playerUuid);
    }

    public boolean isDungeonEntity(Entity entity) {
        return entity != null && entity.getPersistentData().getBoolean(TAG_DUNGEON_ENTITY);
    }

    public MobTemplate template(UUID entityUuid) {
        return templateByMob.get(entityUuid);
    }

    public void recordPlayerHit(UUID mobUuid, long gameTime) {
        if (mobUuid == null) {
            return;
        }
        UUID runKey = runKeyByMob.get(mobUuid);
        if (runKey == null) {
            return;
        }
        DungeonRun run = runsByPartyKey.get(runKey);
        if (run != null) {
            run.combatUntilTick = Math.max(run.combatUntilTick, gameTime + COMBAT_LOCK_RELEASE_TICKS);
        }
    }

    public boolean handleMobDeath(ServerLevel level, LivingEntity entity) {
        if (level == null || entity == null) {
            return false;
        }
        UUID entityUuid = entity.getUUID();
        UUID runKey = runKeyByMob.remove(entityUuid);
        MobTemplate template = templateByMob.remove(entityUuid);
        if (runKey == null || template == null) {
            return false;
        }
        DungeonRun run = runsByPartyKey.get(runKey);
        if (run == null) {
            return true;
        }

        run.aliveMobs.remove(entityUuid);
        run.anchorByMob.remove(entityUuid);
        blockedAttackSinceTickByMob.remove(entityUuid);
        nameplateStateByMob.remove(entityUuid);

        VeyloriaServerRuntime runtime = VeyloriaServerRuntime.instance();
        runtime.gearDropService().rollAndDrop(level, entity, template);

        List<ServerPlayer> recipients = new ArrayList<>();
        for (UUID playerUuid : run.activeMembers) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUuid);
            if (player != null && player.isAlive()) {
                recipients.add(player);
            }
        }
        int split = Math.max(1, recipients.size());

        for (ServerPlayer player : recipients) {
            CharacterProfile profile = runtime.characterService().loadedProfile(player.getUUID());
            if (profile == null) {
                continue;
            }
            int baseXp = runtime.levelService().computeMobExperience(
                profile.level(),
                template.level(),
                template.mobType(),
                template.xpOverride(),
                runtime.ratesConfig().xpRate()
            );
            int xp = Math.max(1, (int) Math.floor(baseXp / (double) split));
            runtime.levelService().grantExperience(profile, xp);
            int rolledCopper = template.currencyMin();
            if (template.currencyMax() > template.currencyMin()) {
                rolledCopper += level.getRandom().nextInt(template.currencyMax() - template.currencyMin() + 1);
            }
            int copper = (int) Math.round(rolledCopper * runtime.ratesConfig().currencyRate());
            profile.addCurrency(copper);
            ServerMarkers.sendGain(player, xp, copper);
            runtime.characterService().save(profile);
        }

        if (run.aliveMobs.isEmpty() && !run.completed) {
            run.completed = true;
            level.setBlock(run.exitMarkerPos, Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
            for (UUID playerUuid : run.activeMembers) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUuid);
                if (player != null) {
                    player.sendSystemMessage(Component.literal("Данж зачищен. Выход в конце открыт (присядьте у метки)."));
                }
            }
            SPAWN_LOGGER.info("Dungeon run {} completed", run.partyKey);
        }
        return true;
    }

    private void updateActivePlayers(MinecraftServer server, ServerLevel dungeonLevel, long gameTime) {
        for (DungeonRun run : runsByPartyKey.values()) {
            run.activeMembers.clear();
        }

        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : runKeyByPlayer.entrySet()) {
            UUID playerUuid = entry.getKey();
            UUID runKey = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            DungeonRun run = runsByPartyKey.get(runKey);
            if (player == null || run == null || !player.isAlive()) {
                toRemove.add(playerUuid);
                continue;
            }
            if (!(player.level() instanceof ServerLevel level)
                || !Objects.equals(level.dimension().location().toString(), DUNGEON_DIMENSION_ID)
                || !run.contains(player.blockPosition())) {
                toRemove.add(playerUuid);
                continue;
            }
            run.activeMembers.add(playerUuid);
            run.lastTouchedTick = gameTime;
        }
        for (UUID playerUuid : toRemove) {
            runKeyByPlayer.remove(playerUuid);
        }

        for (DungeonRun run : runsByPartyKey.values()) {
            if (run.activeMembers.isEmpty() && run.lastTouchedTick > 0L) {
                run.restartPending = true;
            }
            if (run.completed) {
                for (UUID playerUuid : List.copyOf(run.activeMembers)) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
                    if (player != null && player.isShiftKeyDown() && player.distanceToSqr(Vec3.atCenterOf(run.exitMarkerPos)) <= 9.0D) {
                        teleportPlayerOut(player, run);
                    }
                }
            }
        }
    }

    private void tickRuns(MinecraftServer server, ServerLevel dungeonLevel, long gameTime) {
        for (DungeonRun run : runsByPartyKey.values()) {
            boolean inCombat = false;
            for (UUID mobUuid : List.copyOf(run.aliveMobs)) {
                Entity raw = dungeonLevel.getEntity(mobUuid);
                if (!(raw instanceof Mob mob) || !mob.isAlive()) {
                    run.aliveMobs.remove(mobUuid);
                    run.anchorByMob.remove(mobUuid);
                    templateByMob.remove(mobUuid);
                    runKeyByMob.remove(mobUuid);
                    blockedAttackSinceTickByMob.remove(mobUuid);
                    nameplateStateByMob.remove(mobUuid);
                    continue;
                }
                MobTemplate template = templateByMob.get(mobUuid);
                MobAnchor anchor = run.anchorByMob.get(mobUuid);
                if (template == null || anchor == null) {
                    continue;
                }
                updateDungeonNameplate(mob, template, gameTime);
                if (tickDungeonMobAi(server, dungeonLevel, run, mob, template, anchor, gameTime)) {
                    inCombat = true;
                }
            }
            if (inCombat) {
                run.combatUntilTick = Math.max(run.combatUntilTick, gameTime + COMBAT_LOCK_RELEASE_TICKS);
            }
            if (!run.completed && run.aliveMobs.isEmpty()) {
                run.completed = true;
                dungeonLevel.setBlock(run.exitMarkerPos, Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
            }
        }
    }

    private boolean tickDungeonMobAi(MinecraftServer server,
                                     ServerLevel level,
                                     DungeonRun run,
                                     Mob mob,
                                     MobTemplate template,
                                     MobAnchor anchor,
                                     long gameTime) {
        BlockPos anchorPos = anchor.anchor();
        double anchorX = anchorPos.getX() + 0.5D;
        double anchorY = anchorPos.getY();
        double anchorZ = anchorPos.getZ() + 0.5D;
        if (!run.contains(mob.blockPosition())) {
            mob.setTarget(null);
            blockedAttackSinceTickByMob.remove(mob.getUUID());
            mob.teleportTo(anchorX, anchorY, anchorZ);
            mob.getNavigation().stop();
            return false;
        }

        ServerPlayer target = mob.getTarget() instanceof ServerPlayer playerTarget ? playerTarget : null;
        if (target == null
            || !target.isAlive()
            || !run.activeMembers.contains(target.getUUID())) {
            ServerPlayer replacement = findNearestRunPlayer(server, run, mob.blockPosition(), Math.max(10.0D, template.aggroRadius()));
            if (replacement != null) {
                mob.setTarget(replacement);
                target = replacement;
            } else {
                mob.setTarget(null);
                target = null;
            }
        }

        if (target == null) {
            blockedAttackSinceTickByMob.remove(mob.getUUID());
            if (mob.distanceToSqr(anchorX, anchorY, anchorZ) > 9.0D) {
                mob.getNavigation().moveTo(anchorX, anchorY, anchorZ, Math.max(0.85D, template.moveSpeed() * 2.6D));
            } else {
                mob.getNavigation().stop();
            }
            return false;
        }

        if (!(target.level() instanceof ServerLevel targetLevel)
            || !Objects.equals(targetLevel.dimension().location().toString(), DUNGEON_DIMENSION_ID)
            || !run.contains(target.blockPosition())) {
            mob.setTarget(null);
            blockedAttackSinceTickByMob.remove(mob.getUUID());
            return false;
        }

        double chaseSpeed = Math.max(0.95D, template.moveSpeed() * 3.1D);
        boolean hasPath = mob.getNavigation().moveTo(target, chaseSpeed);
        double distance = mob.distanceTo(target);
        boolean hasLineOfSight = mob.hasLineOfSight(target);
        if (!hasLineOfSight && distance > 2.8D) {
            hasPath = mob.getNavigation().moveTo(target, chaseSpeed + 0.08D);
        }

        boolean blocked = distance > MIN_BLOCKED_PATH_DISTANCE && !hasPath && !hasLineOfSight;
        if (!blocked) {
            blockedAttackSinceTickByMob.remove(mob.getUUID());
            return true;
        }

        long blockedSince = blockedAttackSinceTickByMob.computeIfAbsent(mob.getUUID(), ignored -> gameTime);
        if (gameTime - blockedSince < UNREACHABLE_EVADE_TICKS) {
            return true;
        }
        mob.setTarget(null);
        blockedAttackSinceTickByMob.remove(mob.getUUID());
        mob.getNavigation().moveTo(anchorX, anchorY, anchorZ, Math.max(0.9D, template.moveSpeed() * 2.4D));
        return false;
    }

    private ServerPlayer findNearestRunPlayer(MinecraftServer server, DungeonRun run, BlockPos anchor, double radius) {
        double radiusSqr = radius * radius;
        ServerPlayer nearest = null;
        double nearestDistance = radiusSqr;
        for (UUID playerUuid : run.activeMembers) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null || !player.isAlive()) {
                continue;
            }
            double distance = player.distanceToSqr(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
            if (distance <= nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void processEntranceProximity(MinecraftServer server, ServerLevel dungeonLevel, long gameTime) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        VeyloriaServerRuntime runtime = VeyloriaServerRuntime.instance();
        var structureService = runtime.structureService();
        if (structureService == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!runtime.authService().sessionManager().isAuthenticated(player.getUUID())) {
                continue;
            }
            if (!(player.level() instanceof ServerLevel level)
                || !Objects.equals(level.dimension().location().toString(), overworld.dimension().location().toString())) {
                continue;
            }
            Long cooldownUntil = entryCooldownByPlayer.get(player.getUUID());
            if (cooldownUntil != null && cooldownUntil > gameTime) {
                continue;
            }

            var entrance = structureService.nearestPlacedStructure(
                overworld,
                ENTRANCE_STRUCTURE_ID,
                player.getX(),
                player.getZ(),
                ENTRANCE_TRIGGER_RADIUS
            );
            if (entrance == null) {
                continue;
            }

            if (!isPortalContact(player)) {
                long lastPrompt = lastPromptTickByPlayer.getOrDefault(player.getUUID(), Long.MIN_VALUE / 4);
                if (gameTime - lastPrompt >= PROMPT_INTERVAL_TICKS) {
                    player.sendSystemMessage(Component.literal("Вы у входа в данж. Войдите в портал, чтобы начать инстанс."));
                    lastPromptTickByPlayer.put(player.getUUID(), gameTime);
                }
                continue;
            }

            attemptEnterDungeon(player, dungeonLevel, entrance.x(), entrance.y(), entrance.z(), gameTime);
        }
    }

    private void attemptEnterDungeon(ServerPlayer player,
                                     ServerLevel dungeonLevel,
                                     int entranceX,
                                     int entranceY,
                                     int entranceZ,
                                     long gameTime) {
        VeyloriaServerRuntime runtime = VeyloriaServerRuntime.instance();
        UUID partyId = runtime.partyService().partyIdOf(player.getUUID());
        UUID partyKey = partyId == null ? player.getUUID() : partyId;
        String sourceDimension = player.level().dimension().location().toString();
        int sourceZoneIndex = TestWorldLayoutService.zoneIndex(sourceDimension, entranceZ);
        int sourceZoneMaxLevel = TestWorldLayoutService.zoneMaxLevel(sourceZoneIndex);

        if (VeyloriaServerEvents.isPlayerInCombat(player, gameTime)) {
            ServerMarkers.sendError(player, "Нельзя входить в данж во время боя");
            nudgeOutOfPortal(player);
            return;
        }

        DungeonRun run = runsByPartyKey.get(partyKey);
        if (run == null || run.restartPending) {
            if (run != null) {
                clearRunEntities(run, dungeonLevel);
            }
            run = createRun(
                partyKey,
                dungeonLevel,
                sourceDimension,
                BlockPos.containing(entranceX, entranceY, entranceZ),
                sourceZoneIndex,
                sourceZoneMaxLevel
            );
            runsByPartyKey.put(partyKey, run);
        }

        if (isRunInCombat(run, gameTime) && !run.activeMembers.contains(player.getUUID())) {
            ServerMarkers.sendError(player, "В данже идёт бой, вход временно закрыт");
            nudgeOutOfPortal(player);
            return;
        }

        if (!run.scalingMembers.contains(player.getUUID())) {
            run.scalingMembers.add(player.getUUID());
            int requestedScale = Math.min(MAX_SCALING_PLAYERS, run.scalingMembers.size());
            if (requestedScale > run.scalingPlayers && !isRunInCombat(run, gameTime)) {
                applyScaling(run, requestedScale, dungeonLevel);
            }
        }

        player.teleportTo(dungeonLevel,
            run.lobbySpawnPos.getX() + 0.5D,
            run.lobbySpawnPos.getY(),
            run.lobbySpawnPos.getZ() + 0.5D,
            player.getYRot(),
            player.getXRot());
        runKeyByPlayer.put(player.getUUID(), run.partyKey);
        run.activeMembers.add(player.getUUID());
        run.lastTouchedTick = gameTime;
        entryCooldownByPlayer.put(player.getUUID(), gameTime + ENTRANCE_COOLDOWN_TICKS);
        ServerMarkers.sendDungeon(player, run.displayName, String.valueOf(run.mobLevel));
        player.sendSystemMessage(Component.literal("Вы вошли в данж: " + run.displayName));
    }

    private static boolean isPortalContact(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos base = player.blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockState state = level.getBlockState(base.offset(dx, dy, dz));
                    if (state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void nudgeOutOfPortal(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 backward = new Vec3(-look.x, 0.0D, -look.z);
        if (backward.lengthSqr() < 0.001D) {
            backward = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            backward = backward.normalize();
        }
        player.teleportTo(
            level,
            player.getX() + backward.x * 1.4D,
            player.getY(),
            player.getZ() + backward.z * 1.4D,
            player.getYRot(),
            player.getXRot()
        );
    }

    private DungeonRun createRun(UUID partyKey,
                                 ServerLevel dungeonLevel,
                                 String returnDimension,
                                 BlockPos returnPos,
                                 int sourceZoneIndex,
                                 int sourceZoneMaxLevel) {
        int index = nextInstanceIndex++;
        int grid = Math.max(0, index - 1);
        int gridX = grid % 32;
        int gridZ = grid / 32;
        int originX = gridX * INSTANCE_SPACING;
        int originZ = gridZ * INSTANCE_SPACING;
        long seed = random.nextLong();
        boolean bossOnLeft = (seed & 1L) == 0L;

        DungeonRun run = new DungeonRun(
            partyKey,
            "Пещера Разлома",
            index,
            seed,
            originX,
            FLOOR_Y,
            originZ,
            returnDimension,
            returnPos,
            sourceZoneIndex,
            Math.max(1, sourceZoneMaxLevel),
            bossOnLeft
        );

        generateDungeonGeometry(dungeonLevel, run);
        spawnLobbyNpc(dungeonLevel, run);
        spawnDungeonMobs(dungeonLevel, run);
        spawnRandomChests(dungeonLevel, run);

        SPAWN_LOGGER.info("Created dungeon run {} at instance {} (origin={}, {}, bossOnLeft={}, sourceZone={}, mobLevel={})",
            run.partyKey, run.instanceIndex, run.originX, run.originZ, run.bossOnLeft, run.sourceZoneIndex, run.mobLevel);
        return run;
    }

    private void generateDungeonGeometry(ServerLevel level, DungeonRun run) {
        int minX = run.originX - INSTANCE_BOUNDS_RADIUS;
        int maxX = run.originX + INSTANCE_BOUNDS_RADIUS;
        int minZ = run.originZ - INSTANCE_BOUNDS_RADIUS;
        int maxZ = run.originZ + INSTANCE_BOUNDS_RADIUS;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = INSTANCE_MIN_Y; y <= INSTANCE_MAX_Y; y++) {
                    level.setBlock(BlockPos.containing(x, y, z), Blocks.DEEPSLATE.defaultBlockState(), 2);
                }
            }
        }

        BlockPos lobby = BlockPos.containing(run.originX, run.floorY + 1, run.originZ);
        run.lobbySpawnPos = lobby;

        List<BlockPos> mainPath = List.of(
            offset(run, 0, 3, 8),
            offset(run, 2, 3, 18),
            offset(run, -2, 3, 28),
            offset(run, 3, 3, 38),
            offset(run, 0, 3, 48)
        );
        List<BlockPos> leftPath = List.of(
            offset(run, 0, 3, 48),
            offset(run, -10, 3, 58),
            offset(run, -20, 3, 68),
            offset(run, -32, 3, 80)
        );
        List<BlockPos> rightPath = List.of(
            offset(run, 0, 3, 48),
            offset(run, 10, 3, 58),
            offset(run, 20, 3, 68),
            offset(run, 32, 3, 80)
        );

        carveChamber(level, lobby, 10, 6, 10);
        carvePolyline(level, mainPath, 4, 3);
        carvePolyline(level, leftPath, 4, 3);
        carvePolyline(level, rightPath, 4, 3);

        BlockPos bossCenter = run.bossOnLeft ? offset(run, -40, 3, 92) : offset(run, 40, 3, 92);
        BlockPos sideDeadEnd = run.bossOnLeft ? offset(run, 34, 3, 86) : offset(run, -34, 3, 86);
        BlockPos exitCenter = run.bossOnLeft ? offset(run, -40, 3, 104) : offset(run, 40, 3, 104);

        run.bossCenterPos = bossCenter;
        run.exitMarkerPos = BlockPos.containing(exitCenter.getX(), run.floorY + 1, exitCenter.getZ());

        carveChamber(level, bossCenter, 11, 6, 11);
        carveChamber(level, sideDeadEnd, 7, 5, 7);
        carveChamber(level, exitCenter, 8, 5, 8);

        fillFloor(level, lobby, 9, Blocks.STONE_BRICKS.defaultBlockState());
        placePathFloor(level, mainPath, 3, Blocks.STONE.defaultBlockState());
        placePathFloor(level, leftPath, 3, Blocks.STONE.defaultBlockState());
        placePathFloor(level, rightPath, 3, Blocks.STONE.defaultBlockState());
        fillFloor(level, bossCenter, 8, Blocks.POLISHED_ANDESITE.defaultBlockState());
        fillFloor(level, sideDeadEnd, 5, Blocks.STONE.defaultBlockState());
        fillFloor(level, exitCenter, 6, Blocks.SMOOTH_STONE.defaultBlockState());

        placeTorchesOnPath(level, mainPath);
        placeTorchesOnPath(level, leftPath);
        placeTorchesOnPath(level, rightPath);

        level.setBlock(run.lobbySpawnPos.below(), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        level.setBlock(run.lobbySpawnPos, Blocks.LODESTONE.defaultBlockState(), 3);
        level.setBlock(run.exitMarkerPos.below(), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
        level.setBlock(run.exitMarkerPos, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
    }

    private void spawnLobbyNpc(ServerLevel level, DungeonRun run) {
        Villager npc = EntityType.VILLAGER.create(level);
        if (npc == null) {
            return;
        }
        BlockPos spawnPos = run.lobbySpawnPos.offset(2, 0, 0);
        npc.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 180.0F, 0.0F);
        npc.setCustomName(Component.literal("Смотритель данжа"));
        npc.setCustomNameVisible(true);
        npc.setPersistenceRequired();
        npc.setNoAi(true);
        npc.getPersistentData().putBoolean(TAG_DUNGEON_ENTITY, true);
        npc.getPersistentData().putBoolean(TAG_DUNGEON_NPC, true);
        npc.getPersistentData().putString(TAG_DUNGEON_RUN, run.partyKey.toString());
        if (level.addFreshEntity(npc)) {
            run.npcUuids.add(npc.getUUID());
            runKeyByNpc.put(npc.getUUID(), run.partyKey);
        }
    }

    private void spawnDungeonMobs(ServerLevel level, DungeonRun run) {
        ContentService content = VeyloriaServerRuntime.instance().contentService();
        if (content == null) {
            return;
        }
        MobTemplate normal = content.mobTemplate(DUNGEON_MOB_CODE);
        MobTemplate elite = content.mobTemplate(DUNGEON_ELITE_CODE);
        MobTemplate boss = content.mobTemplate(DUNGEON_BOSS_CODE);
        if (normal == null || elite == null || boss == null) {
            SPAWN_LOGGER.warn("Dungeon mob templates are missing (normal={}, elite={}, boss={})", normal, elite, boss);
            return;
        }
        int targetLevel = Math.max(1, run.mobLevel);
        MobTemplate normalScaled = withLevel(normal, targetLevel);
        MobTemplate eliteScaled = withLevel(elite, targetLevel);
        MobTemplate bossScaled = withLevel(boss, targetLevel);

        List<BlockPos> normalAnchors = new ArrayList<>(List.of(
            offset(run, 0, 1, 20), offset(run, 3, 1, 30), offset(run, -3, 1, 38), offset(run, 2, 1, 46),
            offset(run, -2, 1, 54), offset(run, 4, 1, 62), offset(run, -4, 1, 70), offset(run, 0, 1, 76),
            offset(run, -16, 1, 64), offset(run, -24, 1, 72), offset(run, 16, 1, 64), offset(run, 24, 1, 72),
            run.bossCenterPos.offset(3, -2, 3), run.bossCenterPos.offset(-3, -2, -3),
            run.bossOnLeft ? offset(run, -30, 1, 84) : offset(run, 30, 1, 84),
            run.bossOnLeft ? offset(run, -34, 1, 96) : offset(run, 34, 1, 96)
        ));
        List<BlockPos> eliteAnchors = new ArrayList<>(List.of(
            offset(run, -8, 1, 58), offset(run, 8, 1, 58),
            run.bossOnLeft ? offset(run, -22, 1, 76) : offset(run, 22, 1, 76),
            run.bossOnLeft ? offset(run, 22, 1, 76) : offset(run, -22, 1, 76)
        ));

        for (BlockPos anchor : normalAnchors) {
            spawnMobAt(level, run, normalScaled, anchor, 15.0D);
        }
        for (BlockPos anchor : eliteAnchors) {
            spawnMobAt(level, run, eliteScaled, anchor, 17.5D);
        }
        spawnMobAt(level, run, bossScaled, run.bossCenterPos.below(2), 20.0D);
    }

    private void spawnMobAt(ServerLevel level, DungeonRun run, MobTemplate template, BlockPos anchor, double leashRadius) {
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(template.entityModel()));
        if (!(entityType.create(level) instanceof Mob mob)) {
            return;
        }
        mob.moveTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        applyTemplateStats(mob, template, run.hpMultiplier, run.damageMultiplier);
        mob.setPersistenceRequired();
        mob.setCustomNameVisible(true);
        mob.getPersistentData().putBoolean(TAG_DUNGEON_ENTITY, true);
        mob.getPersistentData().putBoolean(TAG_DUNGEON_MOB, true);
        mob.getPersistentData().putString(TAG_DUNGEON_RUN, run.partyKey.toString());

        if (level.addFreshEntity(mob)) {
            UUID mobUuid = mob.getUUID();
            run.aliveMobs.add(mobUuid);
            run.anchorByMob.put(mobUuid, new MobAnchor(anchor, leashRadius));
            runKeyByMob.put(mobUuid, run.partyKey);
            templateByMob.put(mobUuid, template);
        }
    }

    private static void applyTemplateStats(Mob mob, MobTemplate template, double hpMultiplier, double damageMultiplier) {
        double maxHealth = template.baseHp() * (1.0D + 0.06D * Math.max(0, template.level() - 1))
            * template.mobType().healthModifier() * hpMultiplier;
        double damage = template.baseDamage() * (1.0D + 0.05D * Math.max(0, template.level() - 1))
            * template.mobType().damageModifier() * damageMultiplier;

        if (mob.getAttribute(Attributes.MAX_HEALTH) != null) {
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(8.0D, maxHealth));
        }
        if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(Math.max(1.0D, damage));
        }
        if (mob.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Math.max(0.18D, template.moveSpeed()));
        }
        if (mob.getAttribute(Attributes.FOLLOW_RANGE) != null) {
            mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(Math.max(10.0D, template.aggroRadius()));
        }
        mob.setHealth((float) Math.max(1.0D, maxHealth));
        int hpMax = (int) Math.ceil(maxHealth);
        mob.setCustomName(MobSpawnService.buildNameplate(template, hpMax, hpMax));
    }

    private void updateDungeonNameplate(Mob mob, MobTemplate template, long gameTime) {
        int hpMax = Math.max(1, (int) Math.ceil(mob.getMaxHealth()));
        int hpCurrent = Math.max(0, (int) Math.ceil(mob.getHealth()));
        NameplateState state = nameplateStateByMob.get(mob.getUUID());
        if (state != null
            && state.hpCurrent() == hpCurrent
            && state.hpMax() == hpMax
            && gameTime - state.lastTick() < NAMEPLATE_HEARTBEAT_TICKS) {
            return;
        }
        mob.setCustomName(MobSpawnService.buildNameplate(template, hpCurrent, hpMax));
        nameplateStateByMob.put(mob.getUUID(), new NameplateState(hpCurrent, hpMax, gameTime));
    }

    private static MobTemplate withLevel(MobTemplate template, int level) {
        int safeLevel = Math.max(1, level);
        if (template.level() == safeLevel) {
            return template;
        }
        return new MobTemplate(
            template.id(),
            template.code(),
            template.name(),
            template.mobType(),
            safeLevel,
            template.entityModel(),
            template.hostilityType(),
            template.baseDamage(),
            template.baseHp(),
            template.moveSpeed(),
            template.attackSpeed(),
            template.aggroRadius(),
            template.leashRadius(),
            template.lootTableId(),
            template.currencyMin(),
            template.currencyMax(),
            template.xpOverride(),
            template.enabled()
        );
    }

    private void spawnRandomChests(ServerLevel level, DungeonRun run) {
        ContentService content = VeyloriaServerRuntime.instance().contentService();
        if (content == null) {
            return;
        }
        var table = content.lootTable(DUNGEON_LOOT_TABLE);
        if (table == null) {
            LOOT_LOGGER.warn("Missing loot table '{}' for dungeon chests", DUNGEON_LOOT_TABLE);
            return;
        }

        List<BlockPos> candidates = new ArrayList<>(List.of(
            offset(run, -12, 1, 62),
            offset(run, 12, 1, 62),
            offset(run, -28, 1, 86),
            offset(run, 28, 1, 86),
            run.bossCenterPos.offset(0, -2, -6)
        ));
        Collections.shuffle(candidates, new Random(run.seed ^ 0x51BADC0FFEL));
        int count = 2 + (int) Math.floorMod(run.seed, 2L);
        for (int index = 0; index < Math.min(count, candidates.size()); index++) {
            BlockPos pos = candidates.get(index);
            placeChest(level, pos, table.id());
            run.chestPositions.add(pos);
        }
    }

    private void placeChest(ServerLevel level, BlockPos pos, long lootTableId) {
        level.setBlock(pos.below(), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        BlockState chestState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
        level.setBlock(pos, chestState, 3);
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
            return;
        }
        chest.clearContent();

        List<dev.laakirun.veyloria.server.game.LootRoll> rolls = VeyloriaServerRuntime.instance().lootService().roll(
            lootTableId,
            VeyloriaServerRuntime.instance().ratesConfig()
        );
        if (rolls.isEmpty()) {
            return;
        }
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            slots.add(slot);
        }
        Collections.shuffle(slots, random);

        int itemIndex = 0;
        for (dev.laakirun.veyloria.server.game.LootRoll roll : rolls) {
            if (itemIndex >= slots.size()) {
                break;
            }
            ItemStack stack = VeyloriaServerRuntime.instance().itemFactory().create(roll.itemTemplate(), roll.quantity());
            chest.setItem(slots.get(itemIndex), stack);
            itemIndex++;
        }
        chest.setChanged();
    }

    private void applyScaling(DungeonRun run, int targetPlayers, ServerLevel level) {
        if (targetPlayers <= run.scalingPlayers) {
            return;
        }
        double[] oldScale = scaleFor(run.scalingPlayers);
        double[] newScale = scaleFor(targetPlayers);
        double hpFactor = newScale[0] / oldScale[0];
        double damageFactor = newScale[1] / oldScale[1];

        for (UUID mobUuid : run.aliveMobs) {
            Entity raw = level.getEntity(mobUuid);
            if (!(raw instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            if (mob.getAttribute(Attributes.MAX_HEALTH) != null) {
                double oldHp = mob.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
                double newHp = Math.max(1.0D, oldHp * hpFactor);
                double ratio = oldHp <= 0.001D ? 1.0D : mob.getHealth() / (float) oldHp;
                mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(newHp);
                mob.setHealth((float) Math.max(1.0D, newHp * ratio));
            }
            if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                double oldDamage = mob.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue();
                mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(Math.max(0.1D, oldDamage * damageFactor));
            }
        }

        run.scalingPlayers = targetPlayers;
        run.hpMultiplier = newScale[0];
        run.damageMultiplier = newScale[1];
        COMBAT_LOGGER.info("Dungeon run {} scaled to {} players (hp x{}, damage x{})",
            run.partyKey, run.scalingPlayers,
            String.format(Locale.ROOT, "%.2f", run.hpMultiplier),
            String.format(Locale.ROOT, "%.2f", run.damageMultiplier));
    }

    private static double[] scaleFor(int players) {
        int capped = Math.max(1, Math.min(MAX_SCALING_PLAYERS, players));
        return switch (capped) {
            case 2 -> new double[] {1.38D, 1.20D};
            case 3 -> new double[] {1.71D, 1.39D};
            case 4 -> new double[] {2.02D, 1.57D};
            case 5 -> new double[] {2.32D, 1.76D};
            default -> new double[] {1.00D, 1.00D};
        };
    }

    private boolean isRunInCombat(DungeonRun run, long gameTime) {
        return run.combatUntilTick >= gameTime;
    }

    private void teleportPlayerOut(ServerPlayer player, DungeonRun run) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel returnLevel = findLevel(server, run.returnDimensionId);
        if (returnLevel == null) {
            return;
        }
        player.teleportTo(returnLevel,
            run.returnPos.getX() + 0.5D,
            run.returnPos.getY() + 1.0D,
            run.returnPos.getZ() + 0.5D,
            player.getYRot(),
            player.getXRot());
        runKeyByPlayer.remove(player.getUUID());
        run.activeMembers.remove(player.getUUID());
        player.sendSystemMessage(Component.literal("Вы покинули данж."));
    }

    private static ServerLevel findLevel(MinecraftServer server, String dimensionId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (Objects.equals(level.dimension().location().toString(), dimensionId)) {
                return level;
            }
        }
        return null;
    }

    private void clearRunEntities(DungeonRun run, ServerLevel dungeonLevel) {
        for (UUID mobUuid : run.aliveMobs) {
            Entity raw = dungeonLevel.getEntity(mobUuid);
            if (raw != null) {
                raw.discard();
            }
            runKeyByMob.remove(mobUuid);
            templateByMob.remove(mobUuid);
            blockedAttackSinceTickByMob.remove(mobUuid);
            nameplateStateByMob.remove(mobUuid);
        }
        for (UUID npcUuid : run.npcUuids) {
            Entity raw = dungeonLevel.getEntity(npcUuid);
            if (raw != null) {
                raw.discard();
            }
            runKeyByNpc.remove(npcUuid);
        }
        run.aliveMobs.clear();
        run.npcUuids.clear();
        run.anchorByMob.clear();
    }

    private static BlockPos offset(DungeonRun run, int dx, int dy, int dz) {
        return BlockPos.containing(run.originX + dx, run.floorY + dy, run.originZ + dz);
    }

    private static void carvePolyline(ServerLevel level, List<BlockPos> points, int radiusXz, int radiusY) {
        for (int index = 0; index + 1 < points.size(); index++) {
            carveTunnel(level, points.get(index), points.get(index + 1), radiusXz, radiusY);
        }
    }

    private static void carveTunnel(ServerLevel level, BlockPos from, BlockPos to, int radiusXz, int radiusY) {
        int steps = Math.max(4, from.distManhattan(to));
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            double x = lerp(from.getX(), to.getX(), t);
            double y = lerp(from.getY(), to.getY(), t);
            double z = lerp(from.getZ(), to.getZ(), t);
            carveChamber(level, BlockPos.containing(x, y, z), radiusXz, radiusY, radiusXz);
        }
    }

    private static void carveChamber(ServerLevel level, BlockPos center, int radiusX, int radiusY, int radiusZ) {
        int minX = center.getX() - radiusX;
        int maxX = center.getX() + radiusX;
        int minY = center.getY() - radiusY;
        int maxY = center.getY() + radiusY;
        int minZ = center.getZ() - radiusZ;
        int maxZ = center.getZ() + radiusZ;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double dx = (x - center.getX()) / (double) Math.max(1, radiusX);
                    double dy = (y - center.getY()) / (double) Math.max(1, radiusY);
                    double dz = (z - center.getZ()) / (double) Math.max(1, radiusZ);
                    if ((dx * dx + dy * dy + dz * dz) <= 1.0D) {
                        level.setBlock(BlockPos.containing(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static void fillFloor(ServerLevel level, BlockPos center, int radius, BlockState state) {
        int y = center.getY() - 3;
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                if (Math.hypot(x - center.getX(), z - center.getZ()) <= radius + 0.25D) {
                    level.setBlock(BlockPos.containing(x, y, z), state, 3);
                }
            }
        }
    }

    private static void placePathFloor(ServerLevel level, List<BlockPos> points, int radius, BlockState state) {
        for (int index = 0; index + 1 < points.size(); index++) {
            BlockPos from = points.get(index);
            BlockPos to = points.get(index + 1);
            int steps = Math.max(2, from.distManhattan(to));
            for (int step = 0; step <= steps; step++) {
                double t = step / (double) steps;
                int x = (int) Math.round(lerp(from.getX(), to.getX(), t));
                int z = (int) Math.round(lerp(from.getZ(), to.getZ(), t));
                int y = (int) Math.round(lerp(from.getY(), to.getY(), t)) - 3;
                for (int fx = x - radius; fx <= x + radius; fx++) {
                    for (int fz = z - radius; fz <= z + radius; fz++) {
                        if (Math.hypot(fx - x, fz - z) <= radius + 0.25D) {
                            level.setBlock(BlockPos.containing(fx, y, fz), state, 3);
                        }
                    }
                }
            }
        }
    }

    private static void placeTorchesOnPath(ServerLevel level, List<BlockPos> points) {
        for (int index = 0; index + 1 < points.size(); index++) {
            BlockPos from = points.get(index);
            BlockPos to = points.get(index + 1);
            int steps = Math.max(2, from.distManhattan(to));
            for (int step = 0; step <= steps; step += 7) {
                double t = step / (double) steps;
                int x = (int) Math.round(lerp(from.getX(), to.getX(), t));
                int z = (int) Math.round(lerp(from.getZ(), to.getZ(), t));
                int floorY = (int) Math.round(lerp(from.getY(), to.getY(), t)) - 2;
                BlockPos torchPos = BlockPos.containing(x, floorY, z);
                if (!level.getBlockState(torchPos.below()).isAir()) {
                    level.setBlock(torchPos, Blocks.TORCH.defaultBlockState(), 3);
                }
            }
        }
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private static final class DungeonRun {
        private final UUID partyKey;
        private final String displayName;
        private final int instanceIndex;
        private final long seed;
        private final int originX;
        private final int floorY;
        private final int originZ;
        private final String returnDimensionId;
        private final BlockPos returnPos;
        private final int sourceZoneIndex;
        private final int mobLevel;
        private final boolean bossOnLeft;
        private final Set<UUID> activeMembers = ConcurrentHashMap.newKeySet();
        private final Set<UUID> scalingMembers = ConcurrentHashMap.newKeySet();
        private final Set<UUID> aliveMobs = ConcurrentHashMap.newKeySet();
        private final Set<UUID> npcUuids = ConcurrentHashMap.newKeySet();
        private final Set<BlockPos> chestPositions = ConcurrentHashMap.newKeySet();
        private final Map<UUID, MobAnchor> anchorByMob = new ConcurrentHashMap<>();
        private volatile boolean completed;
        private volatile boolean restartPending;
        private volatile long combatUntilTick;
        private volatile long lastTouchedTick;
        private volatile int scalingPlayers = 1;
        private volatile double hpMultiplier = 1.0D;
        private volatile double damageMultiplier = 1.0D;
        private volatile BlockPos lobbySpawnPos;
        private volatile BlockPos bossCenterPos;
        private volatile BlockPos exitMarkerPos;

        private DungeonRun(UUID partyKey,
                           String displayName,
                           int instanceIndex,
                           long seed,
                           int originX,
                           int floorY,
                           int originZ,
                           String returnDimensionId,
                           BlockPos returnPos,
                           int sourceZoneIndex,
                           int mobLevel,
                           boolean bossOnLeft) {
            this.partyKey = partyKey;
            this.displayName = displayName;
            this.instanceIndex = instanceIndex;
            this.seed = seed;
            this.originX = originX;
            this.floorY = floorY;
            this.originZ = originZ;
            this.returnDimensionId = returnDimensionId;
            this.returnPos = returnPos;
            this.sourceZoneIndex = sourceZoneIndex;
            this.mobLevel = mobLevel;
            this.bossOnLeft = bossOnLeft;
        }

        private boolean contains(BlockPos pos) {
            if (pos == null) {
                return false;
            }
            return pos.getX() >= originX - INSTANCE_BOUNDS_RADIUS
                && pos.getX() <= originX + INSTANCE_BOUNDS_RADIUS
                && pos.getZ() >= originZ - INSTANCE_BOUNDS_RADIUS
                && pos.getZ() <= originZ + INSTANCE_BOUNDS_RADIUS
                && pos.getY() >= INSTANCE_MIN_Y
                && pos.getY() <= INSTANCE_MAX_Y;
        }
    }

    private record MobAnchor(BlockPos anchor, double leashRadius) {
    }

    private record NameplateState(int hpCurrent, int hpMax, long lastTick) {
    }
}
