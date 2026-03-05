package dev.laakirun.veyloria.server.game;

import net.minecraft.ChatFormatting;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import dev.laakirun.veyloria.common.item.RpgItemData;
import dev.laakirun.veyloria.common.model.BaseStats;
import dev.laakirun.veyloria.common.model.CharacterProfile;
import dev.laakirun.veyloria.common.model.EquipSlot;
import dev.laakirun.veyloria.common.model.HostilityType;
import dev.laakirun.veyloria.common.model.ItemCategory;
import dev.laakirun.veyloria.common.model.Rarity;
import dev.laakirun.veyloria.common.config.RatesConfig;
import dev.laakirun.veyloria.common.entity.HomingArrowEntity;
import dev.laakirun.veyloria.common.registry.VeyloriaAttachments;
import dev.laakirun.veyloria.common.registry.VeyloriaEntityTypes;
import dev.laakirun.veyloria.common.targeting.PlayerTargetState;
import dev.laakirun.veyloria.common.targeting.TargetingProfile;
import dev.laakirun.veyloria.common.targeting.TargetingService;
import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import dev.laakirun.veyloria.server.content.MobSpawnGroup;
import dev.laakirun.veyloria.server.content.MobTemplate;
import dev.laakirun.veyloria.server.profile.ExperienceGainResult;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VeyloriaServerEvents {
    private static final Logger COMBAT_LOGGER = LoggerFactory.getLogger("veyloria.combat");
    private static VeyloriaServerEvents INSTANCE;
    private static final int ATTACK_COOLDOWN_TICKS = 10;
    private static final double MELEE_TARGET_RANGE = 4.25D;
    private static final double MELEE_TARGET_RANGE_SQR = MELEE_TARGET_RANGE * MELEE_TARGET_RANGE;
    private static final double MELEE_TARGET_RADIUS_SQR = 0.90D * 0.90D;
    private static final double STARTER_BOW_TARGET_RANGE = 50.0D;
    private static final String STARTER_BOW_TEMPLATE_CODE = "test_best_bow";
    private static final long PROFILE_SYNC_INTERVAL_TICKS = 20L;
    private static final long TARGET_MARKER_HEARTBEAT_TICKS = 15L;
    private static final double BARS_VIEW_DISTANCE_SQR = 96.0D * 96.0D;
    private static final long BARS_HEARTBEAT_TICKS = 40L;
    private static final String TAG_TEST_SWORD_GRANTED = "veyloria_test_sword_granted";
    private static final String TAG_TEST_BOW_GRANTED = "veyloria_test_bow_granted";
    private static final int STARTER_ARROW_STACKS = 4;
    private static final int ARROWS_PER_STACK = 64;
    private static final long DAMAGE_TEXT_LIFETIME_TICKS = 20L;
    private static final long PLAYER_COMBAT_TIMEOUT_TICKS = 20L * 12L;
    private static final double CRIT_BASE_CHANCE = 0.05D;
    private static final double CRIT_PER_AGILITY = 0.0035D;
    private static final double CRIT_MAX_CHANCE = 0.55D;
    private static final double CRIT_MULTIPLIER = 1.80D;
    private static final double CRIT_SWORD_BONUS = 0.10D;
    private static final double DEFAULT_PLAYER_MAX_HEALTH = 20.0D;
    private final java.util.Map<UUID, Long> lastPlayerAttackTick = new ConcurrentHashMap<>();
    private final java.util.Map<UUID, Long> lastSkillUseTick = new ConcurrentHashMap<>();
    private final java.util.Map<UUID, Double> manaByPlayer = new ConcurrentHashMap<>();
    private final java.util.Map<UUID, HealingPool> activeHealingPools = new ConcurrentHashMap<>();
    private final java.util.Map<BarsPairKey, BarsCacheEntry> barCacheByViewerSubject = new ConcurrentHashMap<>();
    private final java.util.Map<UUID, TargetMarkerCacheEntry> targetMarkerByPlayer = new ConcurrentHashMap<>();
    private final java.util.Map<UUID, DamageTextState> damageTextById = new ConcurrentHashMap<>();
    private final java.util.Map<UUID, Long> suppressKnockbackUntilTickByPlayer = new ConcurrentHashMap<>();
    private final java.util.Map<UUID, Long> playerCombatUntilTickByPlayer = new ConcurrentHashMap<>();
    private final java.util.Map<UUID, Integer> lastZoneByPlayer = new ConcurrentHashMap<>();

    private long lastSpawnTick;
    private long lastProfileTick;

    public static void register() {
        INSTANCE = new VeyloriaServerEvents();
        NeoForge.EVENT_BUS.register(INSTANCE);
    }

    public static void handleMeleeIntent(ServerPlayer player) {
        if (INSTANCE == null || player == null || !player.isAlive()) {
            return;
        }
        INSTANCE.performMeleeAttack(player, null);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("veyloria")
                .then(Commands.literal("rates")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("show")
                        .executes(context -> {
                            RatesConfig rates = VeyloriaServerRuntime.instance().ratesConfig();
                            context.getSource().sendSuccess(() -> Component.literal("Рейты: xp=" + rates.xpRate()
                                + ", currency=" + rates.currencyRate()
                                + ", resource=" + rates.resourceDropRate()
                                + ", equipment=" + rates.equipmentDropRate()
                                + ", consumable=" + rates.consumableDropRate()
                                + ", boss_respawn=" + rates.bossRespawnRate()), false);
                            return 1;
                        }))
                    .then(Commands.literal("reset")
                        .executes(context -> {
                            VeyloriaServerRuntime.instance().resetRatesOverrides();
                            context.getSource().sendSuccess(() -> Component.literal("Рейты сброшены к значениям из конфига"), false);
                            return 1;
                        }))
                    .then(Commands.literal("set")
                        .then(Commands.argument("type", StringArgumentType.word())
                            .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.01D))
                                .executes(context -> {
                                    String type = StringArgumentType.getString(context, "type");
                                    double value = DoubleArgumentType.getDouble(context, "value");
                                    return applyRateOverride(context.getSource(), type, value);
                                })))))
        );

        registerPartyAlias(event, "party");
        registerPartyAlias(event, "p");
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        VeyloriaServerRuntime runtime = VeyloriaServerRuntime.instance();
        enforceBuildMode(player);
        var account = runtime.authService().ensureAuthenticated(player.getUUID(), player.getGameProfile().getName());
        CharacterProfile profile = runtime.characterService().loadedProfile(player.getUUID());
        if (profile == null) {
            profile = runtime.characterService().loadOrCreate(account);
        }
        runtime.testWorldLayoutService().ensureStarterSpawn(player);
        grantBestTestSword(player);
        grantBestTestBow(player);
        runtime.playerLoadoutService().initializePlayer(player);
        lastZoneByPlayer.remove(player.getUUID());
        syncPlayerHud(player, profile);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        VeyloriaServerRuntime.instance().partyService().removeMember(player.getUUID());
        VeyloriaServerRuntime.instance().authService().logout(player.getUUID());
        VeyloriaServerRuntime.instance().characterService().unload(player.getUUID());
        VeyloriaServerRuntime.instance().playerLoadoutService().unload(player.getUUID());
        lastPlayerAttackTick.remove(player.getUUID());
        lastSkillUseTick.remove(player.getUUID());
        manaByPlayer.remove(player.getUUID());
        targetMarkerByPlayer.remove(player.getUUID());
        suppressKnockbackUntilTickByPlayer.remove(player.getUUID());
        playerCombatUntilTickByPlayer.remove(player.getUUID());
        lastZoneByPlayer.remove(player.getUUID());
        invalidateBarsCache(player.getUUID());
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            VeyloriaServerRuntime.instance().playerLoadoutService().initializePlayer(player);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        VeyloriaServerRuntime runtime = VeyloriaServerRuntime.instance();
        var partyService = runtime.partyService();
        var characterService = runtime.characterService();
        var authService = runtime.authService();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            partyService.removeMember(player.getUUID());
            characterService.unload(player.getUUID());
            authService.logout(player.getUUID());
        }
        lastPlayerAttackTick.clear();
        lastSkillUseTick.clear();
        manaByPlayer.clear();
        activeHealingPools.clear();
        barCacheByViewerSubject.clear();
        targetMarkerByPlayer.clear();
        damageTextById.clear();
        suppressKnockbackUntilTickByPlayer.clear();
        playerCombatUntilTickByPlayer.clear();
        lastZoneByPlayer.clear();
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        VeyloriaServerRuntime runtime = VeyloriaServerRuntime.instance();
        var serverConfig = runtime.serverConfig();
        var authService = runtime.authService();
        var characterService = runtime.characterService();
        var mobSpawnService = runtime.mobSpawnService();
        var testWorldLayoutService = runtime.testWorldLayoutService();
        var gearDropService = runtime.gearDropService();
        var playerLoadoutService = runtime.playerLoadoutService();
        if (serverConfig == null
            || authService == null
            || characterService == null
            || mobSpawnService == null
            || testWorldLayoutService == null
            || gearDropService == null
            || playerLoadoutService == null) {
            return;
        }
        MinecraftServer server = event.getServer();
        long gameTime = server.overworld().getGameTime();
        boolean shouldSyncProfile = gameTime - lastProfileTick >= PROFILE_SYNC_INTERVAL_TICKS;
        testWorldLayoutService.tick(server);
        gearDropService.tick(server);
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            disableHunger(player);
            enforceBuildMode(player);
            updateServerTargetState(player, gameTime);
            if (!authService.sessionManager().isAuthenticated(player.getUUID())) {
                lastZoneByPlayer.remove(player.getUUID());
                continue;
            }
            syncZoneAnnouncement(player);
            CharacterProfile profile = characterService.loadedProfile(player.getUUID());
            if (profile != null) {
                playerLoadoutService.tick(player, profile.level());
                if (shouldSyncProfile && authService.sessionManager().isAuthenticated(player.getUUID())) {
                    syncPlayerHud(player, profile);
                }
            }
        }
        tickManaAndHealingPools(server, gameTime);
        tickDamageTexts(server, gameTime);
        suppressKnockbackUntilTickByPlayer.entrySet().removeIf(entry -> entry.getValue() < gameTime);
        playerCombatUntilTickByPlayer.entrySet().removeIf(entry -> entry.getValue() < gameTime);
        if (shouldSyncProfile) {
            broadcastPlayerBars(server, gameTime);
            lastProfileTick = gameTime;
        }
        if (gameTime - lastSpawnTick >= serverConfig.spawnTickInterval()) {
            mobSpawnService.tick(server);
            lastSpawnTick = gameTime;
        }
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Arrow arrow) {
            tryReplaceArrowWithHoming(event, arrow);
            return;
        }
        if (event.getEntity() instanceof ExperienceOrb orb) {
            event.setCanceled(true);
            orb.discard();
            return;
        }
        if (event.getEntity() instanceof ItemEntity drop) {
            if (shouldDiscardNonRpgDrop(event, drop)) {
                event.setCanceled(true);
                drop.discard();
            }
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        MobSpawnService spawnService = VeyloriaServerRuntime.instance().mobSpawnService();
        if (spawnService == null) {
            event.setCanceled(true);
            mob.discard();
            return;
        }
        if (spawnService.isManagedMob(mob)) {
            spawnService.registerManagedMob(mob);
            return;
        }
        event.setCanceled(true);
        mob.discard();
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel)) {
            return;
        }
        MobSpawnService spawnService = VeyloriaServerRuntime.instance().mobSpawnService();
        if (spawnService == null) {
            return;
        }
        MobTemplate template = spawnService.template(event.getEntity().getUUID());
        if (template == null) {
            return;
        }
        event.getDrops().clear();
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!canModifyWorld(player)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!canModifyWorld(player)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && !canModifyWorld(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !canModifyWorld(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        event.setCanceled(true);
        performMeleeAttack(player, event.getTarget());
    }

    private void performMeleeAttack(ServerPlayer player, Entity rawTarget) {
        if (player == null || !player.isAlive()) {
            return;
        }
        VeyloriaServerRuntime runtime = VeyloriaServerRuntime.instance();
        if (runtime.playerLoadoutService() == null || runtime.characterService() == null || runtime.mobSpawnService() == null) {
            return;
        }
        runtime.playerLoadoutService().initializePlayer(player);
        long gameTime = player.level().getGameTime();
        ResolvedMeleeTarget resolvedTarget = resolveMeleeTarget(player, rawTarget, gameTime);
        if (resolvedTarget == null) {
            return;
        }
        LivingEntity target = resolvedTarget.target();
        MobTemplate template = resolvedTarget.template();
        long previousHitTick = lastPlayerAttackTick.getOrDefault(player.getUUID(), Long.MIN_VALUE / 4);
        if (gameTime - previousHitTick < ATTACK_COOLDOWN_TICKS) {
            return;
        }
        if (template.hostilityType() == HostilityType.FRIENDLY) {
            ServerMarkers.sendError(player, "Дружелюбных существ атаковать нельзя");
            return;
        }
        CharacterProfile profile = runtime.characterService().loadedProfile(player.getUUID());
        if (profile == null) {
            return;
        }
        BaseStatsSnapshot stats = snapshotStats(player, profile);
        RpgItemData weapon = RpgItemUtils.read(runtime.playerLoadoutService().currentWeapon(player));
        double baseDamage = computePlayerDamageByWeapon(profile.level(), stats, weapon);
        DamageRoll roll = rollDamage(stats, weapon, baseDamage);
        DamageSource damageSource = player.damageSources().playerAttack(player);
        Vec3 velocityBeforeHit = target.getDeltaMovement();
        boolean applied = target.hurt(damageSource, (float) roll.damage());
        target.setDeltaMovement(velocityBeforeHit);
        if (!applied) {
            return;
        }
        lastPlayerAttackTick.put(player.getUUID(), gameTime);
        if (player.level() instanceof ServerLevel level) {
            showDamageNumber(level, target, roll.damage(), roll.critical());
            double threat = roll.damage() * threatModifier(weapon);
            runtime.mobSpawnService().recordHit(level, target.getUUID(), player.getUUID(), gameTime, threat);
            applyMeleeSpecials(level, player, target, weapon, damageSource, roll.damage(), gameTime);
        }
        if (template.hostilityType() == HostilityType.NEUTRAL) {
            runtime.mobSpawnService().markNeutralAggro(target.getUUID(), player.getUUID(), gameTime);
            if (target instanceof Mob mob) {
                mob.setTarget(player);
            }
        }
        COMBAT_LOGGER.debug("Player {} dealt {}{} to mob {} ({})", player.getGameProfile().getName(),
            Math.round(roll.damage() * 100.0D) / 100.0D, roll.critical() ? " (crit)" : "", target.getUUID(), template.code());
    }

    private ResolvedMeleeTarget resolveMeleeTarget(ServerPlayer player, Entity rawTarget, long gameTime) {
        VeyloriaServerRuntime runtime = VeyloriaServerRuntime.instance();
        MobSpawnService spawnService = runtime.mobSpawnService();
        if (spawnService == null) {
            return null;
        }

        ResolvedMeleeTarget directTarget = toManagedMeleeTarget(player, rawTarget, spawnService);
        if (directTarget != null) {
            return directTarget;
        }

        TargetingService targetingService = runtime.targetingService();
        TargetingProfile targetingProfile = resolveWeaponTargetingProfile(player, runtime.targetingProfile());
        if (targetingService != null && targetingProfile != null && player.level() instanceof ServerLevel level) {
            PlayerTargetState targetState = player.getData(VeyloriaAttachments.PLAYER_TARGET);
            UUID lockedTargetUuid = targetState.currentTargetUuid();
            if (lockedTargetUuid != null) {
                Entity lockedEntity = level.getEntity(lockedTargetUuid);
                if (lockedEntity instanceof LivingEntity lockedTarget
                    && isWithinMeleeReach(player, lockedTarget)
                    && targetingService.isLockCandidate(player, lockedTarget, targetingProfile)
                    && (!targetingProfile.requireLosForLock()
                    || targetingService.hasLineOfSight(player, lockedTarget, TargetingService.defaultTargetPoint(lockedTarget)))) {
                    MobTemplate lockedTemplate = spawnService.template(lockedTarget.getUUID());
                    if (lockedTemplate != null && (!(lockedTarget instanceof Mob lockedMob) || isAttackableByPlayers(lockedMob))) {
                        targetState.update(lockedTarget.getUUID(), gameTime);
                        return new ResolvedMeleeTarget(lockedTarget, lockedTemplate);
                    }
                }
            }
        }

        return findRayMeleeTarget(player, spawnService);
    }

    private ResolvedMeleeTarget toManagedMeleeTarget(ServerPlayer player, Entity rawTarget, MobSpawnService spawnService) {
        if (!(rawTarget instanceof LivingEntity livingTarget)) {
            return null;
        }
        if (!isWithinMeleeReach(player, livingTarget)) {
            return null;
        }
        MobTemplate template = spawnService.template(livingTarget.getUUID());
        if (template == null) {
            return null;
        }
        if (template.hostilityType() == HostilityType.FRIENDLY) {
            return null;
        }
        if (livingTarget instanceof Mob mob && !isAttackableByPlayers(mob)) {
            return null;
        }
        return new ResolvedMeleeTarget(livingTarget, template);
    }

    private ResolvedMeleeTarget findRayMeleeTarget(ServerPlayer player, MobSpawnService spawnService) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Vec3 look = player.getLookAngle();
        if (look.lengthSqr() < 0.0001D) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(look.normalize().scale(MELEE_TARGET_RANGE));
        AABB scan = player.getBoundingBox().expandTowards(look.scale(MELEE_TARGET_RANGE)).inflate(1.2D);
        ResolvedMeleeTarget best = null;
        double bestScore = Double.MAX_VALUE;

        for (Mob mob : level.getEntitiesOfClass(Mob.class, scan, Mob::isAlive)) {
            MobTemplate template = spawnService.template(mob.getUUID());
            if (template == null || template.hostilityType() == HostilityType.FRIENDLY) {
                continue;
            }
            if (!isAttackableByPlayers(mob)) {
                continue;
            }
            if (!isWithinMeleeReach(player, mob)) {
                continue;
            }
            double distanceSqr = player.distanceToSqr(mob);
            if (!player.hasLineOfSight(mob)) {
                continue;
            }

            boolean intersectsHitbox = mob.getBoundingBox().inflate(0.25D).clip(eye, end).isPresent();
            double aimOffsetSqr = distancePointToSegmentSqr(TargetingService.defaultTargetPoint(mob), eye, end);
            if (!intersectsHitbox && aimOffsetSqr > MELEE_TARGET_RADIUS_SQR) {
                continue;
            }

            double score = distanceSqr + aimOffsetSqr * 2.4D;
            if (score < bestScore) {
                bestScore = score;
                best = new ResolvedMeleeTarget(mob, template);
            }
        }
        return best;
    }

    private static double distancePointToSegmentSqr(Vec3 point, Vec3 segmentStart, Vec3 segmentEnd) {
        Vec3 segment = segmentEnd.subtract(segmentStart);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 0.000001D) {
            return point.distanceToSqr(segmentStart);
        }
        double t = point.subtract(segmentStart).dot(segment) / lengthSqr;
        double clampedT = Math.max(0.0D, Math.min(1.0D, t));
        Vec3 closest = segmentStart.add(segment.scale(clampedT));
        return point.distanceToSqr(closest);
    }

    private static boolean isWithinMeleeReach(ServerPlayer player, LivingEntity target) {
        if (player == null || target == null) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        AABB hitbox = target.getBoundingBox().inflate(0.20D);
        return hitbox.distanceToSqr(eye) <= MELEE_TARGET_RANGE_SQR;
    }

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        Entity sourceEntity = event.getSource().getEntity();
        MobTemplate sourceTemplate = sourceEntity == null ? null : VeyloriaServerRuntime.instance().mobSpawnService().template(sourceEntity.getUUID());
        MobTemplate targetTemplate = VeyloriaServerRuntime.instance().mobSpawnService().template(event.getEntity().getUUID());
        if (targetTemplate != null && event.getEntity().level() instanceof ServerLevel level
            && VeyloriaServerRuntime.instance().mobSpawnService().isEvading(event.getEntity().getUUID(), level.getGameTime())) {
            event.setCanceled(true);
            return;
        }

        if (targetTemplate != null
            && targetTemplate.hostilityType() == HostilityType.HOSTILE
            && sourceEntity == null
            && VeyloriaServerRuntime.instance().mobSpawnService().isDaylightBurnScenario(event.getEntity())
            && isFireTickDamage(event.getSource().getMsgId())) {
            if (event.getEntity() instanceof Mob mob && mob.getRemainingFireTicks() > 0) {
                mob.setRemainingFireTicks(0);
            }
            event.setCanceled(true);
            return;
        }

        if (sourceTemplate != null && targetTemplate != null
            && !isManagedMobCombatAllowed(sourceTemplate.hostilityType(), targetTemplate.hostilityType())) {
            event.setCanceled(true);
            return;
        }

        if (sourceEntity instanceof ServerPlayer playerSource && event.getEntity() instanceof LivingEntity target) {
            targetTemplate = VeyloriaServerRuntime.instance().mobSpawnService().template(target.getUUID());
            if (targetTemplate != null) {
                long gameTime = target.level().getGameTime();
                markPlayerInCombat(playerSource.getUUID(), gameTime);
                if (targetTemplate.hostilityType() == HostilityType.FRIENDLY) {
                    event.setCanceled(true);
                    ServerMarkers.sendError(playerSource, "Дружелюбных существ атаковать нельзя");
                    return;
                }
                if (targetTemplate.hostilityType() == HostilityType.NEUTRAL) {
                    VeyloriaServerRuntime.instance().mobSpawnService().markNeutralAggro(target.getUUID(), playerSource.getUUID(), gameTime);
                    if (target instanceof Mob mob) {
                        mob.setTarget(playerSource);
                    }
                }
            }
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            if (sourceEntity != null && sourceTemplate != null) {
                long gameTime = player.level().getGameTime();
                markPlayerInCombat(player.getUUID(), gameTime);
                if (sourceTemplate.hostilityType() == HostilityType.FRIENDLY) {
                    event.setCanceled(true);
                    return;
                }
                if (sourceTemplate.hostilityType() == HostilityType.NEUTRAL
                    && sourceEntity != null) {
                    boolean canNeutralDamage = VeyloriaServerRuntime.instance().mobSpawnService()
                        .canNeutralDamage(sourceEntity.getUUID(), player.getUUID(), gameTime);
                    if (!canNeutralDamage && sourceEntity instanceof Mob neutralMob && neutralMob.getTarget() != null
                        && neutralMob.getTarget().getUUID().equals(player.getUUID())) {
                        VeyloriaServerRuntime.instance().mobSpawnService().markNeutralAggro(sourceEntity.getUUID(), player.getUUID(), gameTime);
                        canNeutralDamage = true;
                    }
                    if (!canNeutralDamage) {
                        event.setCanceled(true);
                        return;
                    }
                }
                suppressKnockbackUntilTickByPlayer.put(player.getUUID(), player.level().getGameTime() + 4L);
                Vec3 velocity = player.getDeltaMovement();
                player.setDeltaMovement(0.0D, velocity.y, 0.0D);
                CharacterProfile profile = VeyloriaServerRuntime.instance().characterService().loadedProfile(player.getUUID());
                if (profile != null) {
                    float original = event.getAmount();
                    int attackerLevel = sourceTemplate == null ? profile.level() : sourceTemplate.level();
                    double mitigated = VeyloriaServerRuntime.instance().playerStatService()
                        .mitigateIncomingDamage(player, profile, original, attackerLevel);
                    if (sourceTemplate != null) {
                        mitigated *= sourceTemplate.hostilityType() == HostilityType.HOSTILE ? 1.55D : 1.30D;
                    }
                    float compensated = compensateVanillaArmorReduction(player, event.getSource(), (float) mitigated);
                    event.setAmount(compensated);
                    COMBAT_LOGGER.debug("Incoming damage to {} from {}: {} -> {} -> {}",
                        player.getGameProfile().getName(), sourceEntity.getUUID(), original, mitigated, compensated);
                }
            }
        }
    }

    @SubscribeEvent
    public void onLivingKnockback(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long gameTime = player.level().getGameTime();
        Long untilTick = suppressKnockbackUntilTickByPlayer.get(player.getUUID());
        if (untilTick != null && untilTick >= gameTime) {
            event.setStrength(0.0F);
            event.setRatioX(0.0D);
            event.setRatioZ(0.0D);
            return;
        }
        DamageSource recentSource = player.getLastDamageSource();
        Entity attacker = recentSource == null ? null : recentSource.getEntity();
        if (attacker == null) {
            return;
        }
        MobTemplate sourceTemplate = VeyloriaServerRuntime.instance().mobSpawnService().template(attacker.getUUID());
        if (sourceTemplate == null || sourceTemplate.hostilityType() == HostilityType.FRIENDLY) {
            return;
        }
        event.setStrength(0.0F);
        event.setRatioX(0.0D);
        event.setRatioZ(0.0D);
    }

    @SubscribeEvent
    public void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long gameTime = player.level().getGameTime();
        if (!isPlayerInCombat(player.getUUID(), gameTime)) {
            return;
        }
        event.setCanceled(true);
    }

    private static boolean isManagedMobCombatAllowed(HostilityType source, HostilityType target) {
        return (source == HostilityType.FRIENDLY && target == HostilityType.HOSTILE)
            || (source == HostilityType.HOSTILE && target == HostilityType.FRIENDLY);
    }

    @SubscribeEvent
    public void onPlayerPickupXp(PlayerXpEvent.PickupXp event) {
        event.setCanceled(true);
        event.getOrb().discard();
    }

    @SubscribeEvent
    public void onItemEntityPickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            VeyloriaServerRuntime runtime = VeyloriaServerRuntime.instance();
            if (runtime.playerLoadoutService() != null) {
                runtime.playerLoadoutService().sanitizePickupMirrorSlot(player);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerXpChange(PlayerXpEvent.XpChange event) {
        event.setAmount(0);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onPlayerLevelChange(PlayerXpEvent.LevelChange event) {
        event.setLevels(0);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onMobDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        MobInstance instance = VeyloriaServerRuntime.instance().mobSpawnService().remove(event.getEntity().getUUID());
        if (instance == null) {
            return;
        }
        MobTemplate template = VeyloriaServerRuntime.instance().contentService().mobTemplate(instance.templateId());
        if (template == null) {
            return;
        }
        VeyloriaServerRuntime.instance().gearDropService().rollAndDrop(level, event.getEntity(), template);
        int zoneIndex = resolveMobZone(instance, level, event.getEntity().getZ());
        List<ServerPlayer> recipients = resolveExperienceRecipients(level, instance, zoneIndex);
        int split = Math.max(1, recipients.size());

        for (ServerPlayer player : recipients) {
            CharacterProfile profile = VeyloriaServerRuntime.instance().characterService().loadedProfile(player.getUUID());
            if (profile == null) {
                continue;
            }
            int baseXp = VeyloriaServerRuntime.instance().levelService().computeMobExperience(
                profile.level(),
                template.level(),
                template.mobType(),
                template.xpOverride(),
                VeyloriaServerRuntime.instance().ratesConfig().xpRate()
            );
            int xp = (int) Math.floor(baseXp / (double) split);
            ExperienceGainResult gainResult = VeyloriaServerRuntime.instance().levelService().grantExperience(profile, xp);
            int copper = (int) Math.round((template.currencyMin() + level.getRandom().nextInt(template.currencyMax() - template.currencyMin() + 1))
                * VeyloriaServerRuntime.instance().ratesConfig().currencyRate());
            profile.addCurrency(copper);
            ServerMarkers.sendGain(player, xp, copper);
            player.sendSystemMessage(Component.literal("Опыт с моба: +" + xp));
            syncPlayerHud(player, profile);
            if (gainResult.leveledUp()) {
                player.sendSystemMessage(Component.literal("Новый уровень: " + gainResult.previousLevel() + " -> " + gainResult.newLevel()));
            }
            VeyloriaServerRuntime.instance().characterService().save(profile);
            COMBAT_LOGGER.debug("Rewards for {} from mob {}: +{} xp, +{} copper", player.getGameProfile().getName(),
                template.code(), xp, copper);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        VeyloriaServerRuntime.instance().playerLoadoutService().initializePlayer(player);
        RpgItemData weapon = RpgItemUtils.read(VeyloriaServerRuntime.instance().playerLoadoutService().currentWeapon(player));
        if (weapon == null || weapon.weaponType().isBlank()) {
            return;
        }
        if (!"wand".equals(weapon.weaponType())) {
            return;
        }
        long gameTime = player.level().getGameTime();
        long lastUse = lastSkillUseTick.getOrDefault(player.getUUID(), Long.MIN_VALUE / 4);
        if (gameTime - lastUse < 12) {
            event.setCanceled(true);
            return;
        }
        boolean casted = castWandSkill(player, weapon, gameTime);
        if (casted) {
            event.setCanceled(true);
            lastSkillUseTick.put(player.getUUID(), gameTime);
        }
    }

    @SubscribeEvent
    public void onUseItemStop(LivingEntityUseItemEvent.Stop event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!VeyloriaServerRuntime.instance().playerLoadoutService().isUsingConsumable(player)) {
            return;
        }
        VeyloriaServerRuntime.instance().playerLoadoutService().resumeConsumableUse(player);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!VeyloriaServerRuntime.instance().playerLoadoutService().isUsingConsumable(player)) {
            return;
        }
        VeyloriaServerRuntime.instance().playerLoadoutService().finishConsumableUse(player, event.getResultStack());
    }

    private void updateServerTargetState(ServerPlayer player, long gameTime) {
        VeyloriaServerRuntime runtime = VeyloriaServerRuntime.instance();
        TargetingService targetingService = runtime.targetingService();
        MobSpawnService spawnService = runtime.mobSpawnService();
        TargetingProfile profile = runtime.targetingProfile();
        if (targetingService == null || spawnService == null || profile == null) {
            PlayerTargetState targetState = player.getData(VeyloriaAttachments.PLAYER_TARGET);
            targetState.clear(gameTime);
            syncTargetMarker(player, null, gameTime);
            return;
        }
        TargetingProfile effectiveProfile = resolveWeaponTargetingProfile(player, profile);
        PlayerTargetState targetState = player.getData(VeyloriaAttachments.PLAYER_TARGET);
        TargetingProfile attackableProfile = new TargetingProfile(
            effectiveProfile.fovDegrees(),
            effectiveProfile.rangeBlocks(),
            effectiveProfile.requireLosForLock(),
            effectiveProfile.memoryTicks(),
            effectiveProfile.turnRate(),
            effectiveProfile.targetOnlyHit(),
            candidate -> isServerAttackableTarget(candidate, spawnService, gameTime),
            effectiveProfile.stickyTicks()
        );
        LivingEntity target = targetingService.findBestTarget(player, attackableProfile, targetState, gameTime);
        if (target == null) {
            targetState.clear(gameTime);
            syncTargetMarker(player, null, gameTime);
            return;
        }
        targetState.update(target.getUUID(), gameTime);
        syncTargetMarker(player, target.getUUID(), gameTime);
    }

    private boolean isServerAttackableTarget(LivingEntity candidate, MobSpawnService spawnService, long gameTime) {
        if (!(candidate instanceof Mob mob)) {
            return false;
        }
        MobTemplate template = spawnService.template(mob.getUUID());
        if (template == null || template.hostilityType() == HostilityType.FRIENDLY) {
            return false;
        }
        return !spawnService.isEvading(mob.getUUID(), gameTime);
    }

    private void syncTargetMarker(ServerPlayer player, UUID targetUuid, long gameTime) {
        UUID playerUuid = player.getUUID();
        TargetMarkerCacheEntry cached = targetMarkerByPlayer.get(playerUuid);
        if (cached != null
            && Objects.equals(cached.targetUuid(), targetUuid)
            && gameTime - cached.lastSentTick() < TARGET_MARKER_HEARTBEAT_TICKS) {
            return;
        }
        ServerMarkers.sendTarget(player, targetUuid);
        targetMarkerByPlayer.put(playerUuid, new TargetMarkerCacheEntry(targetUuid, gameTime));
    }

    private void tryReplaceArrowWithHoming(EntityJoinLevelEvent event, Arrow arrow) {
        if (arrow instanceof HomingArrowEntity || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(arrow.getOwner() instanceof ServerPlayer shooter)) {
            return;
        }

        VeyloriaServerRuntime runtime = VeyloriaServerRuntime.instance();
        if (runtime.playerLoadoutService() == null || runtime.targetingService() == null || runtime.targetingProfile() == null) {
            return;
        }

        RpgItemData weapon = RpgItemUtils.read(runtime.playerLoadoutService().currentWeapon(shooter));
        if (weapon == null || !"bow".equals(weapon.weaponType())) {
            return;
        }

        PlayerTargetState targetState = shooter.getData(VeyloriaAttachments.PLAYER_TARGET);
        UUID targetUuid = targetState.currentTargetUuid();
        if (targetUuid == null) {
            return;
        }
        Entity rawTarget = level.getEntity(targetUuid);
        if (!(rawTarget instanceof LivingEntity target)) {
            return;
        }
        TargetingProfile effectiveProfile = resolveWeaponTargetingProfile(shooter, runtime.targetingProfile());
        if (!runtime.targetingService().isLockCandidate(shooter, target, effectiveProfile)) {
            return;
        }

        HomingArrowEntity homingArrow = new HomingArrowEntity(VeyloriaEntityTypes.HOMING_ARROW.get(), level);
        CompoundTag snapshot = arrow.saveWithoutId(new CompoundTag());
        homingArrow.load(snapshot);
        homingArrow.setPos(arrow.getX(), arrow.getY(), arrow.getZ());
        homingArrow.setDeltaMovement(arrow.getDeltaMovement());
        homingArrow.setYRot(arrow.getYRot());
        homingArrow.setXRot(arrow.getXRot());
        homingArrow.setOwner(shooter);
        homingArrow.setTarget(target);

        if (level.addFreshEntity(homingArrow)) {
            event.setCanceled(true);
            arrow.discard();
        }
    }

    private int applyRateOverride(CommandSourceStack source, String rateTypeRaw, double value) {
        String rateType = rateTypeRaw.toLowerCase(Locale.ROOT);
        RatesConfig current = VeyloriaServerRuntime.instance().ratesConfig();
        RatesConfig updated = switch (rateType) {
            case "xp" -> new RatesConfig(value, current.currencyRate(), current.resourceDropRate(),
                current.equipmentDropRate(), current.consumableDropRate(), current.bossRespawnRate());
            case "currency" -> new RatesConfig(current.xpRate(), value, current.resourceDropRate(),
                current.equipmentDropRate(), current.consumableDropRate(), current.bossRespawnRate());
            case "resource" -> new RatesConfig(current.xpRate(), current.currencyRate(), value,
                current.equipmentDropRate(), current.consumableDropRate(), current.bossRespawnRate());
            case "equipment" -> new RatesConfig(current.xpRate(), current.currencyRate(), current.resourceDropRate(),
                value, current.consumableDropRate(), current.bossRespawnRate());
            case "consumable" -> new RatesConfig(current.xpRate(), current.currencyRate(), current.resourceDropRate(),
                current.equipmentDropRate(), value, current.bossRespawnRate());
            case "boss_respawn" -> new RatesConfig(current.xpRate(), current.currencyRate(), current.resourceDropRate(),
                current.equipmentDropRate(), current.consumableDropRate(), value);
            default -> null;
        };
        if (updated == null) {
            source.sendFailure(Component.literal("Неизвестный тип рейта. Используйте: xp|currency|resource|equipment|consumable|boss_respawn"));
            return 0;
        }
        VeyloriaServerRuntime.instance().overrideRates(updated);
        source.sendSuccess(() -> Component.literal("Рейт " + rateType + " установлен в " + value + " (только до рестарта)"), false);
        return 1;
    }

    private void registerPartyAlias(RegisterCommandsEvent event, String command) {
        event.getDispatcher().register(
            LiteralArgumentBuilder.<CommandSourceStack>literal(command)
                .executes(context -> partyHelp(context.getSource().getPlayerOrException()))
                .then(Commands.literal("help")
                    .executes(context -> partyHelp(context.getSource().getPlayerOrException())))
                .then(Commands.literal("leave")
                    .executes(context -> partyLeave(context.getSource().getPlayerOrException())))
                .then(Commands.literal("add")
                    .then(Commands.argument("nickname", StringArgumentType.word())
                        .executes(context -> partyAdd(
                            context.getSource().getPlayerOrException(),
                            StringArgumentType.getString(context, "nickname"),
                            true))))
                .then(Commands.literal("kick")
                    .then(Commands.argument("nickname", StringArgumentType.word())
                        .executes(context -> partyKick(
                            context.getSource().getPlayerOrException(),
                            StringArgumentType.getString(context, "nickname")))))
                .then(Commands.argument("nickname", StringArgumentType.word())
                    .executes(context -> partyAdd(
                        context.getSource().getPlayerOrException(),
                        StringArgumentType.getString(context, "nickname"),
                        false)))
        );
    }

    private int partyHelp(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Команды группы:"));
        player.sendSystemMessage(Component.literal("/party <nickname> или /p <nickname> - добавить игрока в группу"));
        player.sendSystemMessage(Component.literal("/party add <nickname> или /p add <nickname> - добавить игрока (только лидер)"));
        player.sendSystemMessage(Component.literal("/party kick <nickname> или /p kick <nickname> - исключить игрока (только лидер)"));
        player.sendSystemMessage(Component.literal("/party leave или /p leave - выйти из группы"));
        player.sendSystemMessage(Component.literal("/party help или /p help - показать эту справку"));
        return 1;
    }

    private int partyAdd(ServerPlayer requester, String nickname, boolean requireExistingParty) {
        if (!isAuthenticated(requester)) {
            return 0;
        }
        if (requireExistingParty && !VeyloriaServerRuntime.instance().partyService().isLeader(requester.getUUID())) {
            if (VeyloriaServerRuntime.instance().partyService().leaderOf(requester.getUUID()) == null) {
                requester.sendSystemMessage(Component.literal("Вы не состоите в группе. Сначала создайте группу через /party <nickname>"));
            } else {
                requester.sendSystemMessage(Component.literal("Только лидер группы может добавлять игроков"));
            }
            return 0;
        }
        ServerPlayer target = requester.getServer().getPlayerList().getPlayerByName(nickname);
        if (target == null) {
            requester.sendSystemMessage(Component.literal("Игрок не найден в онлайне"));
            return 0;
        }
        if (!isAuthenticated(target)) {
            requester.sendSystemMessage(Component.literal("Игрок не авторизован"));
            return 0;
        }
        PartyService.PartyUpdateResult result = VeyloriaServerRuntime.instance().partyService().addMember(requester.getUUID(), target.getUUID());
        switch (result.status()) {
            case CREATED -> {
                requester.sendSystemMessage(Component.literal("Создана группа. Вы лидер. Участников: " + result.memberCount()));
                target.sendSystemMessage(Component.literal("Вы вступили в группу. Лидер: " + requester.getGameProfile().getName()));
                return 1;
            }
            case ADDED -> {
                requester.sendSystemMessage(Component.literal("Игрок " + target.getGameProfile().getName()
                    + " добавлен в группу. Участников: " + result.memberCount() + "/" + PartyService.MAX_MEMBERS));
                target.sendSystemMessage(Component.literal("Вы добавлены в группу игроком " + requester.getGameProfile().getName()));
                return 1;
            }
            case NOT_IN_PARTY -> requester.sendSystemMessage(Component.literal("Сначала создайте группу через /party <nickname>"));
            case NOT_LEADER -> requester.sendSystemMessage(Component.literal("Только лидер группы может добавлять игроков"));
            case PARTY_FULL -> requester.sendSystemMessage(Component.literal("В группе уже максимум " + PartyService.MAX_MEMBERS + " игроков"));
            case TARGET_IN_OTHER_PARTY -> requester.sendSystemMessage(Component.literal("Игрок уже состоит в другой группе"));
            case TARGET_ALREADY_IN_PARTY -> requester.sendSystemMessage(Component.literal("Игрок уже в вашей группе"));
            case SELF_TARGET -> requester.sendSystemMessage(Component.literal("Нельзя добавить самого себя"));
            default -> requester.sendSystemMessage(Component.literal("Не удалось добавить игрока в группу"));
        }
        return 0;
    }

    private int partyLeave(ServerPlayer requester) {
        if (!isAuthenticated(requester)) {
            return 0;
        }
        UUID requesterUuid = requester.getUUID();
        Set<UUID> membersBeforeLeave = VeyloriaServerRuntime.instance().partyService().membersOf(requesterUuid);
        UUID leaderBeforeLeave = VeyloriaServerRuntime.instance().partyService().leaderOf(requesterUuid);
        PartyService.PartyUpdateResult result = VeyloriaServerRuntime.instance().partyService().leave(requesterUuid);
        if (result.status() == PartyService.Status.NOT_IN_PARTY) {
            requester.sendSystemMessage(Component.literal("Вы не состоите в группе"));
            return 0;
        }

        requester.sendSystemMessage(Component.literal(result.partyId() == null
            ? "Вы вышли из группы"
            : "Вы вышли из группы. Осталось участников: " + result.memberCount()));

        for (UUID memberUuid : membersBeforeLeave) {
            if (memberUuid.equals(requesterUuid)) {
                continue;
            }
            ServerPlayer member = requester.getServer().getPlayerList().getPlayer(memberUuid);
            if (member != null) {
                member.sendSystemMessage(Component.literal(requester.getGameProfile().getName() + " вышел из группы"));
            }
        }
        if (leaderBeforeLeave != null && leaderBeforeLeave.equals(requesterUuid)
            && result.partyId() != null && result.leaderUuid() != null) {
            ServerPlayer newLeader = requester.getServer().getPlayerList().getPlayer(result.leaderUuid());
            if (newLeader != null) {
                newLeader.sendSystemMessage(Component.literal("Вы назначены лидером группы"));
            }
        }
        return 1;
    }

    private int partyKick(ServerPlayer requester, String nickname) {
        if (!isAuthenticated(requester)) {
            return 0;
        }
        ServerPlayer target = requester.getServer().getPlayerList().getPlayerByName(nickname);
        if (target == null) {
            requester.sendSystemMessage(Component.literal("Игрок не найден в онлайне"));
            return 0;
        }
        PartyService.PartyUpdateResult result = VeyloriaServerRuntime.instance().partyService().kick(requester.getUUID(), target.getUUID());
        switch (result.status()) {
            case KICKED -> {
                requester.sendSystemMessage(Component.literal("Игрок " + target.getGameProfile().getName()
                    + " исключён из группы. Участников: " + result.memberCount() + "/" + PartyService.MAX_MEMBERS));
                target.sendSystemMessage(Component.literal("Вы исключены из группы игроком " + requester.getGameProfile().getName()));
                return 1;
            }
            case NOT_IN_PARTY -> requester.sendSystemMessage(Component.literal("Вы не состоите в группе"));
            case NOT_LEADER -> requester.sendSystemMessage(Component.literal("Только лидер группы может исключать игроков"));
            case TARGET_NOT_IN_PARTY -> requester.sendSystemMessage(Component.literal("Игрок не состоит в вашей группе"));
            case CANNOT_KICK_SELF -> requester.sendSystemMessage(Component.literal("Для выхода из группы используйте /party leave"));
            default -> requester.sendSystemMessage(Component.literal("Не удалось исключить игрока из группы"));
        }
        return 0;
    }

    private boolean isAuthenticated(ServerPlayer player) {
        return VeyloriaServerRuntime.instance().authService().sessionManager().isAuthenticated(player.getUUID());
    }

    private List<ServerPlayer> resolveExperienceRecipients(ServerLevel level, MobInstance instance, int zoneIndex) {
        Set<UUID> recipients = new LinkedHashSet<>();
        for (UUID participantUuid : instance.participants().keySet()) {
            ServerPlayer participant = level.getServer().getPlayerList().getPlayer(participantUuid);
            if (participant == null || !participant.isAlive()) {
                continue;
            }
            if (!VeyloriaServerRuntime.instance().authService().sessionManager().isAuthenticated(participantUuid)) {
                continue;
            }
            if (zoneIndex >= 0 && playerZone(participant) != zoneIndex) {
                continue;
            }
            recipients.add(participantUuid);
            Set<UUID> partyMembers = VeyloriaServerRuntime.instance().partyService().membersOf(participantUuid);
            for (UUID memberUuid : partyMembers) {
                ServerPlayer member = level.getServer().getPlayerList().getPlayer(memberUuid);
                if (member == null || !member.isAlive()) {
                    continue;
                }
                if (!VeyloriaServerRuntime.instance().authService().sessionManager().isAuthenticated(memberUuid)) {
                    continue;
                }
                if (zoneIndex >= 0 && playerZone(member) != zoneIndex) {
                    continue;
                }
                recipients.add(memberUuid);
            }
        }

        List<ServerPlayer> players = new java.util.ArrayList<>();
        for (UUID uuid : recipients) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    private int resolveMobZone(MobInstance instance, ServerLevel level, double fallbackZ) {
        MobSpawnGroup group = VeyloriaServerRuntime.instance().contentService().spawnGroup(instance.spawnGroupId());
        if (group != null) {
            return TestWorldLayoutService.zoneIndex(group.dimension(), group.centerZ());
        }
        return TestWorldLayoutService.zoneIndex(level.dimension().location().toString(), fallbackZ);
    }

    private int playerZone(ServerPlayer player) {
        return TestWorldLayoutService.zoneIndex(player.level().dimension().location().toString(), player.getZ());
    }

    private void syncZoneAnnouncement(ServerPlayer player) {
        int currentZone = playerZone(player);
        Integer previousZone = lastZoneByPlayer.put(player.getUUID(), currentZone);
        if (currentZone < 1) {
            return;
        }
        if (previousZone != null && previousZone == currentZone) {
            return;
        }
        ServerMarkers.sendZone(
            player,
            currentZone,
            TestWorldLayoutService.zoneName(currentZone),
            TestWorldLayoutService.zoneLevelRange(currentZone)
        );
    }

    private static void disableHunger(ServerPlayer player) {
        if (player.getFoodData().getFoodLevel() != 20) {
            player.getFoodData().setFoodLevel(20);
        }
        if (player.getFoodData().getSaturationLevel() < 20.0F) {
            player.getFoodData().setSaturation(20.0F);
        }
        player.getFoodData().setExhaustion(0.0F);
    }

    private static void enforceBuildMode(ServerPlayer player) {
        if (canModifyWorld(player)) {
            return;
        }
        if (player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
            player.setGameMode(GameType.ADVENTURE);
        }
    }

    private static boolean canModifyWorld(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    private boolean shouldDiscardNonRpgDrop(EntityJoinLevelEvent event, ItemEntity drop) {
        if (RpgItemUtils.read(drop.getItem()) != null) {
            return false;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        String dimension = level.dimension().location().toString();
        return TestWorldLayoutService.zoneIndex(dimension, drop.getZ()) >= 1;
    }

    private static boolean isFireTickDamage(String messageId) {
        if (messageId == null) {
            return false;
        }
        String normalized = messageId.toLowerCase(Locale.ROOT);
        return normalized.contains("onfire") || normalized.contains("infire") || normalized.equals("in_fire");
    }

    private static float compensateVanillaArmorReduction(ServerPlayer player, DamageSource source, float desiredDamage) {
        if (desiredDamage <= 0.0F || source == null || source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return Math.max(0.0F, desiredDamage);
        }
        float armor = Math.max(0.0F, player.getArmorValue());
        float toughness = (float) Math.max(0.0D, player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        if (armor <= 0.0F && toughness <= 0.0F) {
            return desiredDamage;
        }
        float rawGuess = desiredDamage;
        for (int iteration = 0; iteration < 4; iteration++) {
            float postArmor = CombatRules.getDamageAfterAbsorb(player, rawGuess, source, armor, toughness);
            if (postArmor <= 0.0001F) {
                break;
            }
            float scale = desiredDamage / postArmor;
            rawGuess *= scale;
            if (Math.abs(1.0F - scale) < 0.01F) {
                break;
            }
        }
        return Math.max(0.0F, rawGuess);
    }

    private BaseStatsSnapshot snapshotStats(ServerPlayer player, CharacterProfile profile) {
        BaseStats stats = VeyloriaServerRuntime.instance().playerStatService().totalStats(player, profile);
        return new BaseStatsSnapshot(stats.power(), stats.vitality(), stats.armor(), stats.crit(), stats.haste());
    }

    private double computePlayerDamageByWeapon(int playerLevel, BaseStatsSnapshot stats, RpgItemData weapon) {
        if (weapon == null || weapon.weaponType().isBlank()) {
            return 1.4D + playerLevel * 0.22D + stats.strength() * 0.35D;
        }
        return switch (weapon.weaponType()) {
            case "sword_2h" -> 2.2D + playerLevel * 0.35D + stats.strength() * 2.20D + stats.stamina() * 0.08D;
            case "axe" -> 1.5D + playerLevel * 0.20D + stats.strength() * 1.10D + stats.armor() * 0.22D;
            case "bow" -> 1.2D + playerLevel * 0.24D + stats.agility() * 1.85D + stats.strength() * 0.10D;
            case "wand" -> 0.9D + playerLevel * 0.14D + stats.intellect() * 0.72D;
            default -> 1.5D + playerLevel * 0.22D + stats.strength() * 0.40D;
        };
    }

    private double threatModifier(RpgItemData weapon) {
        if (weapon == null) {
            return 1.0D;
        }
        return "axe".equals(weapon.weaponType()) ? 2.6D : 1.0D;
    }

    private void applyMeleeSpecials(ServerLevel level, ServerPlayer player, LivingEntity primaryTarget, RpgItemData weapon,
                                    DamageSource source, double baseDamage, long gameTime) {
        if (!(primaryTarget instanceof Mob) || weapon == null || weapon.aoeChance() <= 0.0D || weapon.aoeTargets() <= 0) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() > weapon.aoeChance()) {
            return;
        }
        List<Mob> candidates = new java.util.ArrayList<>(level.getEntitiesOfClass(Mob.class, primaryTarget.getBoundingBox().inflate(5.0D),
            mob -> mob.isAlive()
                && !mob.getUUID().equals(primaryTarget.getUUID())
                && isAttackableByPlayers(mob)));
        java.util.Collections.shuffle(candidates);
        int applied = 0;
        double splashFactor = "axe".equals(weapon.weaponType()) ? 0.36D : 0.52D;
        for (Mob mob : candidates) {
            if (applied >= weapon.aoeTargets()) {
                break;
            }
            double splash = baseDamage * splashFactor;
            if (mob.hurt(source, (float) splash)) {
                showDamageNumber(level, mob, splash, false);
                VeyloriaServerRuntime.instance().mobSpawnService().recordHit(level, mob.getUUID(), player.getUUID(), gameTime, splash * threatModifier(weapon));
                applied++;
            }
        }
        if (applied > 0) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, primaryTarget.getX(), primaryTarget.getY() + 1.0D, primaryTarget.getZ(),
                8 + applied * 2, 0.7D, 0.3D, 0.7D, 0.0D);
            if (weapon.legendaryEffect()) {
                level.sendParticles(ParticleTypes.END_ROD, primaryTarget.getX(), primaryTarget.getY() + 1.2D, primaryTarget.getZ(),
                    18, 0.8D, 0.5D, 0.8D, 0.02D);
                if (primaryTarget.isAlive() && primaryTarget.hurt(source, (float) (baseDamage * 0.18D))) {
                    showDamageNumber(level, primaryTarget, baseDamage * 0.18D, false);
                    VeyloriaServerRuntime.instance().mobSpawnService().recordHit(level, primaryTarget.getUUID(), player.getUUID(),
                        gameTime, baseDamage * 0.18D * threatModifier(weapon));
                }
            }
        }
    }

    private boolean castBowSkill(ServerPlayer player, RpgItemData weapon, long gameTime) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        CharacterProfile profile = VeyloriaServerRuntime.instance().characterService().loadedProfile(player.getUUID());
        if (profile == null) {
            return false;
        }
        BaseStatsSnapshot stats = snapshotStats(player, profile);
        Mob target = findHostileTarget(player, weapon.homingChance());
        if (target == null) {
            return false;
        }
        DamageSource source = player.damageSources().playerAttack(player);
        double baseDamage = computePlayerDamageByWeapon(profile.level(), stats, weapon);
        DamageRoll roll = rollDamage(stats, weapon, baseDamage);
        if (!target.hurt(source, (float) roll.damage())) {
            return false;
        }
        showDamageNumber(level, target, roll.damage(), roll.critical());
        VeyloriaServerRuntime.instance().mobSpawnService().recordHit(level, target.getUUID(), player.getUUID(), gameTime, roll.damage());
        spawnLineParticles(level, player.getEyePosition(), target.getEyePosition(), ParticleTypes.CRIT, 0.1D);

        if (weapon.aoeTargets() > 0 && ThreadLocalRandom.current().nextDouble() <= weapon.aoeChance()) {
            List<Mob> secondary = new java.util.ArrayList<>(level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(6.0D),
                mob -> mob.isAlive()
                    && !mob.getUUID().equals(target.getUUID())
                    && isAttackableByPlayers(mob)));
            java.util.Collections.shuffle(secondary);
            int hits = 0;
            for (Mob mob : secondary) {
                if (hits >= Math.min(5, weapon.aoeTargets())) {
                    break;
                }
                double splash = roll.damage() * 0.45D;
                if (mob.hurt(source, (float) splash)) {
                    showDamageNumber(level, mob, splash, false);
                    VeyloriaServerRuntime.instance().mobSpawnService().recordHit(level, mob.getUUID(), player.getUUID(), gameTime, splash);
                    spawnLineParticles(level, target.getEyePosition(), mob.getEyePosition(), ParticleTypes.CRIT, 0.08D);
                    hits++;
                }
            }
        }
        if (weapon.legendaryEffect()) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1.0D, target.getZ(),
                22, 0.7D, 0.4D, 0.7D, 0.02D);
            List<Mob> chained = new java.util.ArrayList<>(level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(4.5D),
                mob -> mob.isAlive() && !mob.getUUID().equals(target.getUUID()) && isAttackableByPlayers(mob)));
            java.util.Collections.shuffle(chained);
            int chainedHits = 0;
            for (Mob chainedTarget : chained) {
                if (chainedHits >= 2) {
                    break;
                }
                double chainDamage = roll.damage() * 0.30D;
                if (chainedTarget.hurt(source, (float) chainDamage)) {
                    showDamageNumber(level, chainedTarget, chainDamage, false);
                    VeyloriaServerRuntime.instance().mobSpawnService().recordHit(level, chainedTarget.getUUID(), player.getUUID(), gameTime, chainDamage);
                    spawnLineParticles(level, target.getEyePosition(), chainedTarget.getEyePosition(), ParticleTypes.ELECTRIC_SPARK, 0.05D);
                    chainedHits++;
                }
            }
        }
        return true;
    }

    private boolean castWandSkill(ServerPlayer player, RpgItemData weapon, long gameTime) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        CharacterProfile profile = VeyloriaServerRuntime.instance().characterService().loadedProfile(player.getUUID());
        if (profile == null) {
            return false;
        }
        BaseStatsSnapshot stats = snapshotStats(player, profile);
        ServerPlayer target = findFriendlyTarget(player);
        if (target == null) {
            target = player;
        }
        int manaCost = Math.max(8, weapon.manaCost());
        if (!trySpendMana(player, profile, stats, manaCost)) {
            return false;
        }

        double healAmount = Math.max(4.0D, weapon.healPower() + stats.intellect() * 1.45D + profile.level() * 0.35D);
        if (weapon.legendaryEffect()) {
            healAmount *= 1.22D;
        }
        if (weapon.aoeHealing() && weapon.aoeChance() > 0.0D && ThreadLocalRandom.current().nextDouble() <= weapon.aoeChance()) {
            spawnArcParticles(level, player.getEyePosition(), target.getEyePosition(), ParticleTypes.HAPPY_VILLAGER, 20, 2.4D);
            createHealingPool(level, target.position(), healAmount * 0.42D, gameTime, player.getUUID(), weapon.legendaryEffect());
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + 0.5D, target.getZ(),
                24, 0.8D, 0.25D, 0.8D, 0.02D);
        } else {
            double healed = Math.min(target.getMaxHealth(), target.getHealth() + healAmount);
            target.setHealth((float) healed);
            spawnLineParticles(level, player.getEyePosition(), target.getEyePosition(), ParticleTypes.HAPPY_VILLAGER, 0.08D);
            if (weapon.legendaryEffect()) {
                UUID primaryTargetUuid = target.getUUID();
                AABB splashZone = target.getBoundingBox().inflate(3.0D);
                for (ServerPlayer nearby : level.getEntitiesOfClass(ServerPlayer.class, splashZone,
                    other -> other.isAlive() && !other.getUUID().equals(primaryTargetUuid))) {
                    double splashHeal = Math.min(nearby.getMaxHealth(), nearby.getHealth() + healAmount * 0.30D);
                    nearby.setHealth((float) splashHeal);
                }
            }
        }
        return true;
    }

    private DamageRoll rollDamage(BaseStatsSnapshot stats, RpgItemData weapon, double baseDamage) {
        double critChance = CRIT_BASE_CHANCE + stats.agility() * CRIT_PER_AGILITY;
        if (weapon != null && "sword_2h".equals(weapon.weaponType())) {
            critChance += CRIT_SWORD_BONUS;
        }
        critChance = Math.max(0.0D, Math.min(CRIT_MAX_CHANCE, critChance));
        boolean critical = ThreadLocalRandom.current().nextDouble() < critChance;
        double multiplier = critical ? CRIT_MULTIPLIER : 1.0D;
        if (critical && weapon != null && weapon.legendaryEffect()) {
            multiplier += 0.12D;
        }
        return new DamageRoll(baseDamage * multiplier, critical);
    }

    private void showDamageNumber(ServerLevel level, LivingEntity target, double damage, boolean critical) {
        if (!target.isAlive() || damage <= 0.0D) {
            return;
        }
        double baseX = target.getX() + ThreadLocalRandom.current().nextDouble(-0.30D, 0.30D);
        double baseY = target.getY() + Math.min(1.35D, target.getBbHeight() * 0.65D) + 0.16D;
        double baseZ = target.getZ() + ThreadLocalRandom.current().nextDouble(-0.30D, 0.30D);
        ArmorStand marker = new ArmorStand(level, baseX, baseY, baseZ);
        marker.setSilent(true);
        marker.setInvulnerable(true);
        marker.setInvisible(true);
        marker.setNoGravity(true);
        marker.setNoBasePlate(true);
        marker.setCustomNameVisible(true);
        int roundedDamage = Math.max(1, (int) Math.round(damage));
        String text = critical ? "✦ " + roundedDamage : Integer.toString(roundedDamage);
        marker.setCustomName(Component.literal(text).withStyle(critical ? ChatFormatting.GOLD : ChatFormatting.WHITE));
        if (!level.addFreshEntity(marker)) {
            return;
        }
        Vec3 velocity = new Vec3(
            ThreadLocalRandom.current().nextDouble(-0.012D, 0.012D),
            critical ? 0.058D : 0.046D,
            ThreadLocalRandom.current().nextDouble(-0.012D, 0.012D)
        );
        damageTextById.put(marker.getUUID(), new DamageTextState(
            level.dimension().location().toString(),
            level.getGameTime() + DAMAGE_TEXT_LIFETIME_TICKS,
            velocity
        ));
    }

    private void tickDamageTexts(MinecraftServer server, long gameTime) {
        for (Map.Entry<UUID, DamageTextState> entry : damageTextById.entrySet()) {
            DamageTextState state = entry.getValue();
            ServerLevel level = findLevel(server, state.dimension());
            if (level == null || gameTime >= state.expiresAtTick()) {
                removeDamageText(level, entry.getKey());
                continue;
            }
            Entity raw = level.getEntity(entry.getKey());
            if (!(raw instanceof ArmorStand marker) || !marker.isAlive()) {
                damageTextById.remove(entry.getKey());
                continue;
            }
            Vec3 velocity = state.velocity();
            Vec3 nextPos = marker.position().add(velocity);
            marker.teleportTo(nextPos.x, nextPos.y, nextPos.z);
            Vec3 damped = new Vec3(velocity.x * 0.86D, Math.max(0.018D, velocity.y * 0.88D), velocity.z * 0.86D);
            damageTextById.replace(entry.getKey(), state, state.withVelocity(damped));
        }
    }

    private void removeDamageText(ServerLevel level, UUID entityUuid) {
        if (level != null) {
            Entity raw = level.getEntity(entityUuid);
            if (raw != null) {
                raw.discard();
            }
        }
        damageTextById.remove(entityUuid);
    }

    private void grantBestTestSword(ServerPlayer player) {
        if (player.getPersistentData().getBoolean(TAG_TEST_SWORD_GRANTED)) {
            return;
        }
        ItemStack sword = createBestTestSword();
        if (!player.getInventory().add(sword)) {
            player.drop(sword, false);
        }
        player.getPersistentData().putBoolean(TAG_TEST_SWORD_GRANTED, true);
    }

    private void grantBestTestBow(ServerPlayer player) {
        if (player.getPersistentData().getBoolean(TAG_TEST_BOW_GRANTED)) {
            return;
        }
        ItemStack bow = createBestTestBow();
        if (!player.getInventory().add(bow)) {
            player.drop(bow, false);
        }
        for (int index = 0; index < STARTER_ARROW_STACKS; index++) {
            ItemStack arrows = new ItemStack(Items.ARROW, ARROWS_PER_STACK);
            if (!player.getInventory().add(arrows)) {
                player.drop(arrows, false);
            }
        }
        player.getPersistentData().putBoolean(TAG_TEST_BOW_GRANTED, true);
    }

    private TargetingProfile resolveWeaponTargetingProfile(ServerPlayer player, TargetingProfile baseProfile) {
        if (baseProfile == null) {
            return null;
        }
        double range = resolveWeaponTargetRange(player, baseProfile.clampedRangeBlocks());
        return new TargetingProfile(
            baseProfile.fovDegrees(),
            range,
            baseProfile.requireLosForLock(),
            baseProfile.memoryTicks(),
            baseProfile.turnRate(),
            baseProfile.targetOnlyHit(),
            baseProfile.targetFilter(),
            baseProfile.stickyTicks()
        );
    }

    private double resolveWeaponTargetRange(ServerPlayer player, double fallbackRange) {
        if (player == null) {
            return fallbackRange;
        }
        PlayerLoadoutService loadoutService = VeyloriaServerRuntime.instance().playerLoadoutService();
        if (loadoutService == null) {
            return fallbackRange;
        }
        RpgItemData weapon = RpgItemUtils.read(loadoutService.currentWeapon(player));
        if (weapon == null || weapon.weaponType().isBlank()) {
            return fallbackRange;
        }
        return switch (weapon.weaponType()) {
            case "sword_2h", "axe" -> MELEE_TARGET_RANGE;
            case "bow" -> STARTER_BOW_TEMPLATE_CODE.equalsIgnoreCase(weapon.templateCode())
                ? STARTER_BOW_TARGET_RANGE
                : fallbackRange;
            default -> fallbackRange;
        };
    }

    private ItemStack createBestTestSword() {
        ItemStack stack = new ItemStack(Items.NETHERITE_SWORD);
        BaseStats stats = new BaseStats(230, 165, 0, 90, 0);
        RpgItemData data = new RpgItemData(
            "test_best_sword",
            ItemCategory.EQUIPMENT,
            Rarity.LEGENDARY,
            1,
            EquipSlot.WEAPON,
            stats,
            "sword_2h",
            true,
            0.62D,
            5,
            0.0D,
            0,
            0,
            false,
            true,
            false,
            80,
            "Клинок Северного Завета"
        );
        CompoundTag root = new CompoundTag();
        root.put(RpgItemData.ROOT_KEY, data.toTag());
        CustomData.set(DataComponents.CUSTOM_DATA, stack, root);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(data.fantasyName()).withStyle(ChatFormatting.GOLD));
        return stack;
    }

    private ItemStack createBestTestBow() {
        ItemStack stack = new ItemStack(Items.BOW);
        BaseStats stats = new BaseStats(180, 120, 0, 260, 20);
        RpgItemData data = new RpgItemData(
            "test_best_bow",
            ItemCategory.EQUIPMENT,
            Rarity.LEGENDARY,
            1,
            EquipSlot.WEAPON,
            stats,
            "bow",
            true,
            0.40D,
            4,
            1.00D,
            0,
            0,
            false,
            true,
            false,
            80,
            "Skypiercer Prototype"
        );
        CompoundTag root = new CompoundTag();
        root.put(RpgItemData.ROOT_KEY, data.toTag());
        CustomData.set(DataComponents.CUSTOM_DATA, stack, root);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(data.fantasyName()).withStyle(ChatFormatting.GOLD));
        return stack;
    }

    private Mob findHostileTarget(ServerPlayer player, double homingChance) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Mob best = null;
        double bestDistance = 32.0D;
        double bestAim = 0.88D;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32.0D))) {
            if (!mob.isAlive() || !isAttackableByPlayers(mob)) {
                continue;
            }
            Vec3 to = mob.getEyePosition().subtract(eye);
            double distance = to.length();
            if (distance <= 0.2D || distance > 32.0D) {
                continue;
            }
            double aim = to.normalize().dot(look);
            if (aim < bestAim || !player.hasLineOfSight(mob)) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = mob;
            }
        }
        if (best != null) {
            return best;
        }
        if (homingChance <= 0.0D || ThreadLocalRandom.current().nextDouble() > homingChance) {
            return null;
        }
        Mob fallback = null;
        double bestDistanceSqr = 20.0D * 20.0D;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(20.0D),
            candidate -> candidate.isAlive() && isAttackableByPlayers(candidate))) {
            double distanceSqr = player.distanceToSqr(mob);
            if (distanceSqr > bestDistanceSqr) {
                continue;
            }
            bestDistanceSqr = distanceSqr;
            fallback = mob;
        }
        return fallback;
    }

    private ServerPlayer findFriendlyTarget(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        ServerPlayer best = null;
        double bestDistance = 28.0D;
        for (ServerPlayer other : level.players()) {
            if (other.getUUID().equals(player.getUUID()) || !other.isAlive()) {
                continue;
            }
            Vec3 to = other.getEyePosition().subtract(eye);
            double distance = to.length();
            if (distance <= 0.2D || distance > 28.0D) {
                continue;
            }
            double aim = to.normalize().dot(look);
            if (aim < 0.90D || !player.hasLineOfSight(other)) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    private void spawnLineParticles(ServerLevel level, Vec3 from, Vec3 to, net.minecraft.core.particles.ParticleOptions particle, double speed) {
        Vec3 delta = to.subtract(from);
        double distance = delta.length();
        if (distance <= 0.001D) {
            return;
        }
        Vec3 step = delta.normalize().scale(0.7D);
        int steps = Math.max(2, (int) (distance / 0.7D));
        Vec3 current = from;
        for (int index = 0; index < steps; index++) {
            current = current.add(step);
            level.sendParticles(particle, current.x, current.y, current.z, 1, 0.01D, 0.01D, 0.01D, speed);
        }
    }

    private void spawnArcParticles(ServerLevel level, Vec3 from, Vec3 to, net.minecraft.core.particles.ParticleOptions particle,
                                   int segments, double arcHeight) {
        int safeSegments = Math.max(8, segments);
        for (int index = 0; index <= safeSegments; index++) {
            double t = index / (double) safeSegments;
            double x = from.x + (to.x - from.x) * t;
            double z = from.z + (to.z - from.z) * t;
            double linearY = from.y + (to.y - from.y) * t;
            double y = linearY + Math.sin(Math.PI * t) * arcHeight;
            level.sendParticles(particle, x, y, z, 1, 0.03D, 0.03D, 0.03D, 0.01D);
        }
    }

    private void tickManaAndHealingPools(MinecraftServer server, long gameTime) {
        if (gameTime % 100L == 0L) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                CharacterProfile profile = VeyloriaServerRuntime.instance().characterService().loadedProfile(player.getUUID());
                if (profile == null) {
                    continue;
                }
                if (!hasManaWeaponEquipped(player)) {
                    manaByPlayer.remove(player.getUUID());
                    continue;
                }
                BaseStatsSnapshot stats = snapshotStats(player, profile);
                double maxMana = maxMana(profile.level(), stats);
                double regen = 4.0D + stats.intellect() * 0.55D;
                manaByPlayer.merge(player.getUUID(), maxMana, (oldValue, ignored) -> Math.min(maxMana, oldValue + regen));
            }
        }
        for (Map.Entry<UUID, HealingPool> entry : activeHealingPools.entrySet()) {
            HealingPool pool = entry.getValue();
            ServerLevel level = findLevel(server, pool.dimension());
            if (level == null || gameTime >= pool.expiresAtTick()) {
                activeHealingPools.remove(entry.getKey());
                continue;
            }
            if (gameTime < pool.nextTick()) {
                continue;
            }
            AABB area = new AABB(
                pool.center().x - pool.radius(), pool.center().y - 1.0D, pool.center().z - pool.radius(),
                pool.center().x + pool.radius(), pool.center().y + 2.5D, pool.center().z + pool.radius()
            );
            for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class, area, ServerPlayer::isAlive)) {
                double healed = Math.min(target.getMaxHealth(), target.getHealth() + pool.healPerTick());
                target.setHealth((float) healed);
            }
            double spread = Math.min(pool.radius(), (gameTime - (pool.expiresAtTick() - 200L)) * 0.04D + 0.8D);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pool.center().x, pool.center().y + 0.1D, pool.center().z,
                pool.legendary() ? 30 : 18, spread, 0.15D, spread, 0.02D);
            int ringPoints = pool.legendary() ? 22 : 14;
            double ringRadius = Math.min(pool.radius(), Math.max(0.6D, spread));
            for (int index = 0; index < ringPoints; index++) {
                double angle = (Math.PI * 2.0D * index) / ringPoints + gameTime * 0.06D;
                double x = pool.center().x + Math.cos(angle) * ringRadius;
                double z = pool.center().z + Math.sin(angle) * ringRadius;
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, pool.center().y + 0.05D, z,
                    1, 0.02D, 0.02D, 0.02D, 0.0D);
            }
            if (pool.legendary()) {
                level.sendParticles(ParticleTypes.END_ROD, pool.center().x, pool.center().y + 0.15D, pool.center().z,
                    10, spread * 0.7D, 0.08D, spread * 0.7D, 0.02D);
            }
            activeHealingPools.replace(entry.getKey(), pool, pool.withNextTick(gameTime + 20L));
        }
    }

    private boolean trySpendMana(ServerPlayer player, CharacterProfile profile, BaseStatsSnapshot stats, int manaCost) {
        if (!hasManaWeaponEquipped(player)) {
            manaByPlayer.remove(player.getUUID());
            return false;
        }
        double maxMana = maxMana(profile.level(), stats);
        double current = manaByPlayer.getOrDefault(player.getUUID(), maxMana);
        if (current < manaCost) {
            ServerMarkers.sendError(player, "Недостаточно маны");
            return false;
        }
        manaByPlayer.put(player.getUUID(), Math.max(0.0D, current - manaCost));
        return true;
    }

    private double maxMana(int level, BaseStatsSnapshot stats) {
        return 60.0D + level * 4.0D + stats.intellect() * 12.0D;
    }

    private void createHealingPool(ServerLevel level, Vec3 center, double healPerTick, long gameTime, UUID ownerUuid, boolean legendary) {
        HealingPool pool = new HealingPool(
            level.dimension().location().toString(),
            center,
            3.5D,
            healPerTick,
            ownerUuid,
            gameTime + 200L,
            gameTime + 20L,
            legendary
        );
        activeHealingPools.put(UUID.randomUUID(), pool);
    }

    private void broadcastPlayerBars(MinecraftServer server, long gameTime) {
        VeyloriaServerRuntime runtime = VeyloriaServerRuntime.instance();
        var characterService = runtime.characterService();
        var playerStatService = runtime.playerStatService();
        List<ServerPlayer> allPlayers = server.getPlayerList().getPlayers();
        java.util.Map<UUID, SubjectBarsSnapshot> subjects = new java.util.HashMap<>();
        for (ServerPlayer subject : allPlayers) {
            if (!isAuthenticated(subject)) {
                continue;
            }
            CharacterProfile profile = characterService.loadedProfile(subject.getUUID());
            if (profile == null) {
                continue;
            }
            int manaCurrent = 0;
            int manaMax = 0;
            if (hasManaWeaponEquipped(subject)) {
                BaseStatsSnapshot stats = snapshotStats(subject, profile);
                manaMax = (int) Math.round(maxMana(profile.level(), stats));
                manaCurrent = (int) Math.round(Math.min(manaMax, manaByPlayer.getOrDefault(subject.getUUID(), (double) manaMax)));
            }
            double effectiveHpMax = Math.max(1.0D, playerStatService.computePlayerMaxHealth(subject, profile));
            double hpRatio = Math.max(0.0D, Math.min(1.0D, subject.getHealth() / DEFAULT_PLAYER_MAX_HEALTH));
            int hpMax = (int) Math.ceil(effectiveHpMax);
            int hpCurrent = (int) Math.ceil(Math.max(0.0D, effectiveHpMax * hpRatio));
            subjects.put(subject.getUUID(), new SubjectBarsSnapshot(
                subject.getUUID(),
                subject,
                subject.level().dimension().location().toString(),
                new BarsPayload(hpCurrent, hpMax, manaCurrent, manaMax)
            ));
        }

        if (subjects.isEmpty()) {
            barCacheByViewerSubject.clear();
            return;
        }

        Set<UUID> activeViewers = new LinkedHashSet<>();
        for (ServerPlayer viewer : allPlayers) {
            if (!isAuthenticated(viewer)) {
                continue;
            }
            activeViewers.add(viewer.getUUID());
            String viewerDimension = viewer.level().dimension().location().toString();
            for (SubjectBarsSnapshot subject : subjects.values()) {
                if (!viewerDimension.equals(subject.dimensionId())) {
                    continue;
                }
                if (!subject.subjectUuid().equals(viewer.getUUID()) && viewer.distanceToSqr(subject.player()) > BARS_VIEW_DISTANCE_SQR) {
                    continue;
                }
                BarsPairKey key = new BarsPairKey(viewer.getUUID(), subject.subjectUuid());
                BarsCacheEntry cached = barCacheByViewerSubject.get(key);
                if (cached != null
                    && cached.payload().equals(subject.payload())
                    && gameTime - cached.lastSentTick() < BARS_HEARTBEAT_TICKS) {
                    continue;
                }
                BarsPayload payload = subject.payload();
                ServerMarkers.sendBars(viewer, subject.subjectUuid(), payload.hp(), payload.hpMax(), payload.mana(), payload.manaMax());
                barCacheByViewerSubject.put(key, new BarsCacheEntry(payload, gameTime));
            }
        }

        barCacheByViewerSubject.keySet().removeIf(key ->
            !activeViewers.contains(key.viewerUuid()) || !subjects.containsKey(key.subjectUuid()));
    }

    private void invalidateBarsCache(UUID playerUuid) {
        barCacheByViewerSubject.keySet().removeIf(key ->
            key.viewerUuid().equals(playerUuid) || key.subjectUuid().equals(playerUuid));
    }

    private boolean hasManaWeaponEquipped(ServerPlayer player) {
        return isManaWeapon(VeyloriaServerRuntime.instance().playerLoadoutService().currentWeapon(player));
    }

    private static boolean isManaWeapon(ItemStack stack) {
        RpgItemData data = RpgItemUtils.read(stack);
        return data != null && data.manaCost() > 0 && !data.weaponType().isBlank();
    }

    private boolean isAttackableByPlayers(Mob mob) {
        MobTemplate template = VeyloriaServerRuntime.instance().mobSpawnService().template(mob.getUUID());
        if (template == null || template.hostilityType() == HostilityType.FRIENDLY) {
            return false;
        }
        if (mob.level() instanceof ServerLevel level
            && VeyloriaServerRuntime.instance().mobSpawnService().isEvading(mob.getUUID(), level.getGameTime())) {
            return false;
        }
        return true;
    }

    private ServerLevel findLevel(MinecraftServer server, String dimensionId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimensionId)) {
                return level;
            }
        }
        return null;
    }

    private record BaseStatsSnapshot(int strength, int stamina, int armor, int agility, int intellect) {
    }

    private record DamageRoll(double damage, boolean critical) {
    }

    private record BarsPayload(int hp, int hpMax, int mana, int manaMax) {
    }

    private record SubjectBarsSnapshot(UUID subjectUuid, ServerPlayer player, String dimensionId, BarsPayload payload) {
    }

    private record BarsPairKey(UUID viewerUuid, UUID subjectUuid) {
    }

    private record BarsCacheEntry(BarsPayload payload, long lastSentTick) {
    }

    private record TargetMarkerCacheEntry(UUID targetUuid, long lastSentTick) {
    }

    private record ResolvedMeleeTarget(LivingEntity target, MobTemplate template) {
    }

    private record DamageTextState(String dimension, long expiresAtTick, Vec3 velocity) {
        DamageTextState withVelocity(Vec3 velocity) {
            return new DamageTextState(dimension, expiresAtTick, velocity);
        }
    }

    private record HealingPool(
        String dimension,
        Vec3 center,
        double radius,
        double healPerTick,
        UUID ownerUuid,
        long expiresAtTick,
        long nextTick,
        boolean legendary
    ) {
        HealingPool withNextTick(long nextTick) {
            return new HealingPool(dimension, center, radius, healPerTick, ownerUuid, expiresAtTick, nextTick, legendary);
        }
    }

    private void markPlayerInCombat(UUID playerUuid, long gameTime) {
        if (playerUuid == null) {
            return;
        }
        playerCombatUntilTickByPlayer.put(playerUuid, gameTime + PLAYER_COMBAT_TIMEOUT_TICKS);
    }

    private boolean isPlayerInCombat(UUID playerUuid, long gameTime) {
        Long untilTick = playerCombatUntilTickByPlayer.get(playerUuid);
        if (untilTick == null) {
            return false;
        }
        if (untilTick < gameTime) {
            playerCombatUntilTickByPlayer.remove(playerUuid, untilTick);
            return false;
        }
        return true;
    }

    private void syncPlayerHud(ServerPlayer player, CharacterProfile profile) {
        BaseStats totalStats = VeyloriaServerRuntime.instance().playerStatService().totalStats(player, profile);
        int xpToNext = Math.max(1, VeyloriaServerRuntime.instance().levelService().xpToNextLevel(profile.level()));
        player.experienceLevel = Math.max(1, profile.level());
        player.experienceProgress = 0.0F;
        player.totalExperience = 0;
        if (player.getAttribute(Attributes.MAX_HEALTH) != null) {
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(DEFAULT_PLAYER_MAX_HEALTH);
            if (player.getHealth() > DEFAULT_PLAYER_MAX_HEALTH) {
                player.setHealth((float) DEFAULT_PLAYER_MAX_HEALTH);
            }
        }
        int maxMana = 0;
        int currentMana = 0;
        BaseStatsSnapshot stats = snapshotStats(player, profile);
        if (hasManaWeaponEquipped(player)) {
            maxMana = (int) Math.round(maxMana(profile.level(), stats));
            currentMana = (int) Math.round(Math.min(maxMana, manaByPlayer.getOrDefault(player.getUUID(), (double) maxMana)));
            manaByPlayer.put(player.getUUID(), (double) currentMana);
        } else {
            manaByPlayer.remove(player.getUUID());
        }
        ServerMarkers.sendProfile(player, profile, xpToNext, currentMana, maxMana, totalStats);
    }
}
