package dev.laakirun.veyloria.server.game;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import dev.laakirun.veyloria.common.item.RpgItemData;
import dev.laakirun.veyloria.common.model.BaseStats;
import dev.laakirun.veyloria.common.model.CharacterProfile;
import dev.laakirun.veyloria.common.model.HostilityType;
import dev.laakirun.veyloria.common.config.RatesConfig;
import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import dev.laakirun.veyloria.server.auth.AccountRecord;
import dev.laakirun.veyloria.server.auth.AuthService;
import dev.laakirun.veyloria.server.content.MobSpawnGroup;
import dev.laakirun.veyloria.server.content.MobTemplate;
import dev.laakirun.veyloria.server.profile.ExperienceGainResult;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VeyloriaServerEvents {
    private static final Logger COMBAT_LOGGER = LoggerFactory.getLogger("veyloria.combat");
    private static final int ATTACK_COOLDOWN_TICKS = 20;
    private final java.util.Map<UUID, Long> lastPlayerAttackTick = new ConcurrentHashMap<>();
    private final java.util.Map<UUID, Long> lastSkillUseTick = new ConcurrentHashMap<>();
    private final java.util.Map<UUID, Double> manaByPlayer = new ConcurrentHashMap<>();
    private final java.util.Map<UUID, HealingPool> activeHealingPools = new ConcurrentHashMap<>();

    private long lastSpawnTick;
    private long lastProfileTick;

    public static void register() {
        NeoForge.EVENT_BUS.register(new VeyloriaServerEvents());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("veyloria")
                .then(Commands.literal("register")
                    .then(Commands.argument("password", StringArgumentType.word())
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String password = StringArgumentType.getString(context, "password");
                            AuthService.AuthResult result = VeyloriaServerRuntime.instance().authService()
                                .register(player.getUUID(), player.getGameProfile().getName(), password);
                            return handleAuthResult(player, result);
                        })))
                .then(Commands.literal("login")
                    .then(Commands.argument("password", StringArgumentType.word())
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String password = StringArgumentType.getString(context, "password");
                            AuthService.AuthResult result = VeyloriaServerRuntime.instance().authService()
                                .login(player.getUUID(), player.getGameProfile().getName(), password);
                            return handleAuthResult(player, result);
                        })))
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

        event.getDispatcher().register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("party")
                .then(Commands.argument("nickname", StringArgumentType.word())
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        String nickname = StringArgumentType.getString(context, "nickname");
                        return invitePartyMember(player, nickname);
                    }))
        );
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        VeyloriaServerRuntime.instance().authLockService().lock(player);
        enforceBuildMode(player);
        boolean registered = VeyloriaServerRuntime.instance().authService().findAccount(player.getUUID()).isPresent();
        ServerMarkers.sendAuthRequired(player, registered);
        player.sendSystemMessage(Component.literal(registered
            ? "Откройте окно авторизации Veyloria или используйте /veyloria login <password>"
            : "Откройте окно авторизации Veyloria или используйте /veyloria register <password>"));
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        VeyloriaServerRuntime.instance().partyService().removeMember(player.getUUID());
        VeyloriaServerRuntime.instance().authService().logout(player.getUUID());
        VeyloriaServerRuntime.instance().characterService().unload(player.getUUID());
        VeyloriaServerRuntime.instance().authLockService().unlock(player);
        lastPlayerAttackTick.remove(player.getUUID());
        lastSkillUseTick.remove(player.getUUID());
        manaByPlayer.remove(player.getUUID());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            VeyloriaServerRuntime.instance().partyService().removeMember(player.getUUID());
            VeyloriaServerRuntime.instance().characterService().unload(player.getUUID());
            VeyloriaServerRuntime.instance().authService().logout(player.getUUID());
        }
        lastPlayerAttackTick.clear();
        lastSkillUseTick.clear();
        manaByPlayer.clear();
        activeHealingPools.clear();
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        long gameTime = event.getServer().overworld().getGameTime();
        VeyloriaServerRuntime.instance().testWorldLayoutService().tick(event.getServer());
        VeyloriaServerRuntime.instance().gearDropService().tick(event.getServer());
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            disableHunger(player);
            enforceBuildMode(player);
            enforceRequiredLevel(player);
            enforceTwoHandedRule(player);
            VeyloriaServerRuntime.instance().authLockService().enforce(player);
            if (gameTime - lastProfileTick >= 20 && VeyloriaServerRuntime.instance().authService().sessionManager().isAuthenticated(player.getUUID())) {
                CharacterProfile profile = VeyloriaServerRuntime.instance().characterService().loadedProfile(player.getUUID());
                if (profile != null) {
                    syncPlayerHud(player, profile);
                }
            }
        }
        tickManaAndHealingPools(event.getServer(), gameTime);
        if (gameTime - lastProfileTick >= 20) {
            broadcastPlayerBars(event.getServer());
            lastProfileTick = gameTime;
        }
        if (gameTime - lastSpawnTick >= VeyloriaServerRuntime.instance().serverConfig().spawnTickInterval()) {
            VeyloriaServerRuntime.instance().mobSpawnService().tick(event.getServer());
            lastSpawnTick = gameTime;
        }
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        MobSpawnService spawnService = VeyloriaServerRuntime.instance().mobSpawnService();
        if (spawnService.isManagedMob(mob)) {
            spawnService.registerManagedMob(mob);
            return;
        }
        event.setCanceled(true);
        mob.discard();
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (VeyloriaServerRuntime.instance().authLockService().isLocked(player)) {
                event.setCanceled(true);
                return;
            }
            if (!canModifyWorld(player)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (VeyloriaServerRuntime.instance().authLockService().isLocked(player)) {
                event.setCanceled(true);
                return;
            }
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
        if (VeyloriaServerRuntime.instance().authLockService().isLocked(player)) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        MobTemplate template = VeyloriaServerRuntime.instance().mobSpawnService().template(target.getUUID());
        if (template == null) {
            return;
        }
        long gameTime = player.level().getGameTime();
        long previousHitTick = lastPlayerAttackTick.getOrDefault(player.getUUID(), Long.MIN_VALUE / 4);
        if (gameTime - previousHitTick < ATTACK_COOLDOWN_TICKS) {
            return;
        }
        if (template.hostilityType() == HostilityType.FRIENDLY) {
            ServerMarkers.sendError(player, "Дружелюбных существ атаковать нельзя");
            return;
        }
        CharacterProfile profile = VeyloriaServerRuntime.instance().characterService().loadedProfile(player.getUUID());
        if (profile == null) {
            return;
        }
        BaseStatsSnapshot stats = snapshotStats(player, profile);
        RpgItemData weapon = RpgItemUtils.read(player.getMainHandItem());
        double damage = computePlayerDamageByWeapon(profile.level(), stats, weapon);
        DamageSource damageSource = player.damageSources().playerAttack(player);
        Vec3 velocityBeforeHit = target.getDeltaMovement();
        boolean applied = target.hurt(damageSource, (float) damage);
        target.setDeltaMovement(velocityBeforeHit);
        if (!applied) {
            return;
        }
        lastPlayerAttackTick.put(player.getUUID(), gameTime);
        if (player.level() instanceof ServerLevel level) {
            double threat = damage * threatModifier(weapon);
            VeyloriaServerRuntime.instance().mobSpawnService().recordHit(level, target.getUUID(), player.getUUID(), gameTime, threat);
            applyMeleeSpecials(level, player, target, weapon, damageSource, damage, gameTime);
        }
        if (template.hostilityType() == HostilityType.NEUTRAL) {
            VeyloriaServerRuntime.instance().mobSpawnService().markNeutralAggro(target.getUUID(), player.getUUID(), gameTime);
            if (target instanceof Mob mob) {
                mob.setTarget(player);
            }
        }
        COMBAT_LOGGER.debug("Player {} dealt {} to mob {} ({})", player.getGameProfile().getName(),
            Math.round(damage * 100.0D) / 100.0D, target.getUUID(), template.code());
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

        if (sourceTemplate != null && targetTemplate != null
            && sourceTemplate.hostilityType() == HostilityType.HOSTILE
            && targetTemplate.hostilityType() == HostilityType.HOSTILE) {
            event.setCanceled(true);
            return;
        }

        if (sourceEntity instanceof ServerPlayer playerSource && event.getEntity() instanceof LivingEntity target) {
            targetTemplate = VeyloriaServerRuntime.instance().mobSpawnService().template(target.getUUID());
            if (targetTemplate != null) {
                if (targetTemplate.hostilityType() == HostilityType.FRIENDLY) {
                    event.setCanceled(true);
                    ServerMarkers.sendError(playerSource, "Дружелюбных существ атаковать нельзя");
                    return;
                }
                if (targetTemplate.hostilityType() == HostilityType.NEUTRAL) {
                    long gameTime = target.level().getGameTime();
                    VeyloriaServerRuntime.instance().mobSpawnService().markNeutralAggro(target.getUUID(), playerSource.getUUID(), gameTime);
                    if (target instanceof Mob mob) {
                        mob.setTarget(playerSource);
                    }
                }
            }
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            if (VeyloriaServerRuntime.instance().authLockService().isLocked(player)) {
                event.setCanceled(true);
                return;
            }
            if (sourceEntity != null && sourceTemplate != null) {
                if (sourceTemplate.hostilityType() == HostilityType.FRIENDLY) {
                    event.setCanceled(true);
                    return;
                }
                if (sourceTemplate.hostilityType() == HostilityType.NEUTRAL
                    && !VeyloriaServerRuntime.instance().mobSpawnService().canNeutralDamage(sourceEntity.getUUID(), player.getUUID(), player.level().getGameTime())) {
                    event.setCanceled(true);
                    return;
                }
                CharacterProfile profile = VeyloriaServerRuntime.instance().characterService().loadedProfile(player.getUUID());
                if (profile != null) {
                    float original = event.getAmount();
                    double mitigated = VeyloriaServerRuntime.instance().playerStatService().mitigateIncomingDamage(player, profile, original);
                    event.setAmount((float) mitigated);
                    COMBAT_LOGGER.debug("Incoming damage to {} from {}: {} -> {}",
                        player.getGameProfile().getName(), sourceEntity.getUUID(), original, mitigated);
                }
            }
        }
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
        if (VeyloriaServerRuntime.instance().authLockService().isLocked(player)) {
            event.setCanceled(true);
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!VeyloriaServerRuntime.instance().authService().sessionManager().isAuthenticated(player.getUUID())) {
            return;
        }
        RpgItemData weapon = RpgItemUtils.read(player.getMainHandItem());
        if (weapon == null || weapon.weaponType().isBlank()) {
            return;
        }
        long gameTime = player.level().getGameTime();
        long lastUse = lastSkillUseTick.getOrDefault(player.getUUID(), Long.MIN_VALUE / 4);
        if (gameTime - lastUse < 12) {
            event.setCanceled(true);
            return;
        }
        boolean casted = switch (weapon.weaponType()) {
            case "bow" -> castBowSkill(player, weapon, gameTime);
            case "wand" -> castWandSkill(player, weapon, gameTime);
            default -> false;
        };
        if (casted) {
            event.setCanceled(true);
            lastSkillUseTick.put(player.getUUID(), gameTime);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && VeyloriaServerRuntime.instance().authLockService().isLocked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player && VeyloriaServerRuntime.instance().authLockService().isLocked(player)) {
            player.closeContainer();
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

    private int invitePartyMember(ServerPlayer owner, String nickname) {
        if (!VeyloriaServerRuntime.instance().authService().sessionManager().isAuthenticated(owner.getUUID())) {
            return 0;
        }
        ServerPlayer target = owner.getServer().getPlayerList().getPlayerByName(nickname);
        if (target == null || target.getUUID().equals(owner.getUUID())) {
            return 0;
        }
        if (!VeyloriaServerRuntime.instance().authService().sessionManager().isAuthenticated(target.getUUID())) {
            return 0;
        }
        PartyService.PartyUpdateResult result = VeyloriaServerRuntime.instance().partyService().addMember(owner.getUUID(), target.getUUID());
        owner.sendSystemMessage(Component.literal("Группа обновлена: участников=" + result.memberCount()));
        target.sendSystemMessage(Component.literal("Вы вступили в группу с " + owner.getGameProfile().getName()));
        return 1;
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

    private static void disableHunger(ServerPlayer player) {
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
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
        double damage = computePlayerDamageByWeapon(profile.level(), stats, weapon);
        if (!target.hurt(source, (float) damage)) {
            return false;
        }
        VeyloriaServerRuntime.instance().mobSpawnService().recordHit(level, target.getUUID(), player.getUUID(), gameTime, damage);
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
                double splash = damage * 0.45D;
                if (mob.hurt(source, (float) splash)) {
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
                double chainDamage = damage * 0.30D;
                if (chainedTarget.hurt(source, (float) chainDamage)) {
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
        return level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(20.0D),
                mob -> mob.isAlive() && isAttackableByPlayers(mob))
            .stream()
            .min(java.util.Comparator.comparingDouble(player::distanceToSqr))
            .orElse(null);
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
        for (Map.Entry<UUID, HealingPool> entry : new java.util.ArrayList<>(activeHealingPools.entrySet())) {
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
            activeHealingPools.put(entry.getKey(), pool.withNextTick(gameTime + 20L));
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

    private void broadcastPlayerBars(MinecraftServer server) {
        List<ServerPlayer> allPlayers = server.getPlayerList().getPlayers();
        for (ServerPlayer viewer : allPlayers) {
            if (!VeyloriaServerRuntime.instance().authService().sessionManager().isAuthenticated(viewer.getUUID())) {
                continue;
            }
            for (ServerPlayer subject : allPlayers) {
                if (!subject.level().dimension().equals(viewer.level().dimension())) {
                    continue;
                }
                if (!subject.getUUID().equals(viewer.getUUID()) && viewer.distanceToSqr(subject) > 96.0D * 96.0D) {
                    continue;
                }
                CharacterProfile profile = VeyloriaServerRuntime.instance().characterService().loadedProfile(subject.getUUID());
                if (profile == null) {
                    continue;
                }
                boolean manaEnabled = hasManaWeaponEquipped(subject);
                int manaCurrent = 0;
                int manaMax = 0;
                if (manaEnabled) {
                    BaseStatsSnapshot stats = snapshotStats(subject, profile);
                    manaMax = (int) Math.round(maxMana(profile.level(), stats));
                    manaCurrent = (int) Math.round(Math.min(manaMax, manaByPlayer.getOrDefault(subject.getUUID(), (double) manaMax)));
                }
                int hpCurrent = (int) Math.ceil(Math.max(0.0D, subject.getHealth()));
                int hpMax = (int) Math.ceil(Math.max(1.0D, subject.getMaxHealth()));
                ServerMarkers.sendBars(viewer, subject.getUUID(), hpCurrent, hpMax, manaCurrent, manaMax);
            }
        }
    }

    private boolean hasManaWeaponEquipped(ServerPlayer player) {
        return isManaWeapon(player.getMainHandItem()) || isManaWeapon(player.getOffhandItem());
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

    private void enforceRequiredLevel(ServerPlayer player) {
        CharacterProfile profile = VeyloriaServerRuntime.instance().characterService().loadedProfile(player.getUUID());
        if (profile == null) {
            return;
        }
        stripIfTooHighLevel(player, player.getMainHandItem(), profile.level(), () -> player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        stripIfTooHighLevel(player, player.getOffhandItem(), profile.level(), () -> player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY));
        stripIfTooHighLevel(player, player.getItemBySlot(EquipmentSlot.HEAD), profile.level(), () -> player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY));
        stripIfTooHighLevel(player, player.getItemBySlot(EquipmentSlot.CHEST), profile.level(), () -> player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY));
        stripIfTooHighLevel(player, player.getItemBySlot(EquipmentSlot.LEGS), profile.level(), () -> player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY));
        stripIfTooHighLevel(player, player.getItemBySlot(EquipmentSlot.FEET), profile.level(), () -> player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY));
    }

    private void enforceTwoHandedRule(ServerPlayer player) {
        RpgItemData main = RpgItemUtils.read(player.getMainHandItem());
        if (main == null || !main.twoHanded()) {
            return;
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.isEmpty()) {
            return;
        }
        ItemStack copy = offhand.copy();
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        if (!player.getInventory().add(copy)) {
            player.drop(copy, false);
        }
    }

    private void stripIfTooHighLevel(ServerPlayer player, ItemStack stack, int playerLevel, Runnable clearSlot) {
        RpgItemData item = RpgItemUtils.read(stack);
        if (item == null || item.requiredLevel() <= playerLevel) {
            return;
        }
        ItemStack copy = stack.copy();
        clearSlot.run();
        if (!player.getInventory().add(copy)) {
            player.drop(copy, false);
        }
    }

    private record BaseStatsSnapshot(int strength, int stamina, int armor, int agility, int intellect) {
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

    private int handleAuthResult(ServerPlayer player, AuthService.AuthResult result) {
        if (!result.success()) {
            ServerMarkers.sendError(player, result.message());
            return 0;
        }
        AccountRecord account = result.account();
        CharacterProfile profile = VeyloriaServerRuntime.instance().characterService().loadOrCreate(account);
        VeyloriaServerRuntime.instance().authLockService().unlock(player);
        syncPlayerHud(player, profile);
        ServerMarkers.sendAuthOk(player);
        return 1;
    }

    private void syncPlayerHud(ServerPlayer player, CharacterProfile profile) {
        int xpToNext = Math.max(1, VeyloriaServerRuntime.instance().levelService().xpToNextLevel(profile.level()));
        player.experienceLevel = profile.level();
        player.experienceProgress = Math.min(1.0F, profile.xpCurrent() / (float) xpToNext);
        double maxHealth = VeyloriaServerRuntime.instance().playerStatService().computePlayerMaxHealth(player, profile);
        if (player.getAttribute(Attributes.MAX_HEALTH) != null) {
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
            if (player.getHealth() > maxHealth) {
                player.setHealth((float) maxHealth);
            }
        }
        int maxMana = 0;
        int currentMana = 0;
        if (hasManaWeaponEquipped(player)) {
            BaseStatsSnapshot stats = snapshotStats(player, profile);
            maxMana = (int) Math.round(maxMana(profile.level(), stats));
            currentMana = (int) Math.round(Math.min(maxMana, manaByPlayer.getOrDefault(player.getUUID(), (double) maxMana)));
            manaByPlayer.put(player.getUUID(), (double) currentMana);
        } else {
            manaByPlayer.remove(player.getUUID());
        }
        ServerMarkers.sendProfile(player, profile, xpToNext, currentMana, maxMana);
    }
}
