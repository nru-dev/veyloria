package dev.laakirun.veyloria.server.game;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.laakirun.veyloria.common.model.CharacterProfile;
import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import dev.laakirun.veyloria.server.auth.AccountRecord;
import dev.laakirun.veyloria.server.auth.AuthService;
import dev.laakirun.veyloria.server.content.ItemTemplate;
import dev.laakirun.veyloria.server.content.MobTemplate;
import dev.laakirun.veyloria.server.profile.ExperienceGainResult;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class VeyloriaServerEvents {
    private long lastSpawnTick;
    private long lastProfileTick;

    public static void register() {
        NeoForge.EVENT_BUS.register(new VeyloriaServerEvents());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("veyloria")
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
        );
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        VeyloriaServerRuntime.instance().authLockService().lock(player);
        boolean registered = VeyloriaServerRuntime.instance().authService().findAccount(player.getUUID()).isPresent();
        ServerMarkers.sendAuthRequired(player, registered);
        player.sendSystemMessage(Component.literal(registered
            ? "Open the Veyloria auth window or use /veyloria login <password>"
            : "Open the Veyloria auth window or use /veyloria register <password>"));
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        VeyloriaServerRuntime.instance().authService().logout(player.getUUID());
        VeyloriaServerRuntime.instance().characterService().unload(player.getUUID());
        VeyloriaServerRuntime.instance().authLockService().unlock(player);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            VeyloriaServerRuntime.instance().characterService().unload(player.getUUID());
            VeyloriaServerRuntime.instance().authService().logout(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        long gameTime = event.getServer().overworld().getGameTime();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            VeyloriaServerRuntime.instance().authLockService().enforce(player);
            if (gameTime - lastProfileTick >= 20 && VeyloriaServerRuntime.instance().authService().sessionManager().isAuthenticated(player.getUUID())) {
                CharacterProfile profile = VeyloriaServerRuntime.instance().characterService().loadedProfile(player.getUUID());
                if (profile != null) {
                    syncPlayerHud(player, profile);
                }
            }
        }
        if (gameTime - lastProfileTick >= 20) {
            lastProfileTick = gameTime;
        }
        if (gameTime - lastSpawnTick >= VeyloriaServerRuntime.instance().serverConfig().spawnTickInterval()) {
            VeyloriaServerRuntime.instance().mobSpawnService().tick(event.getServer());
            lastSpawnTick = gameTime;
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (VeyloriaServerRuntime.instance().authLockService().isLocked(player)) {
            event.setCanceled(true);
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        MobTemplate template = VeyloriaServerRuntime.instance().mobSpawnService().template(target.getUUID());
        if (template == null) {
            return;
        }
        CharacterProfile profile = VeyloriaServerRuntime.instance().characterService().loadedProfile(player.getUUID());
        if (profile == null) {
            event.setCanceled(true);
            return;
        }
        double damage = VeyloriaServerRuntime.instance().playerStatService().computePlayerDamage(player, profile);
        DamageSource damageSource = player.damageSources().playerAttack(player);
        target.hurt(damageSource, (float) damage);
        VeyloriaServerRuntime.instance().mobSpawnService().recordHit(target.getUUID(), player.getUUID(), player.level().getGameTime());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (VeyloriaServerRuntime.instance().authLockService().isLocked(player)) {
                event.setCanceled(true);
                return;
            }
            Entity sourceEntity = event.getSource().getEntity();
            if (sourceEntity != null && VeyloriaServerRuntime.instance().mobSpawnService().template(sourceEntity.getUUID()) != null) {
                CharacterProfile profile = VeyloriaServerRuntime.instance().characterService().loadedProfile(player.getUUID());
                if (profile != null) {
                    double mitigated = VeyloriaServerRuntime.instance().playerStatService().mitigateIncomingDamage(player, profile, event.getAmount());
                    event.setAmount((float) mitigated);
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
        List<ServerPlayer> participants = VeyloriaServerRuntime.instance().mobSpawnService().eligibleParticipants(level, instance);
        for (ServerPlayer player : participants) {
            CharacterProfile profile = VeyloriaServerRuntime.instance().characterService().loadedProfile(player.getUUID());
            if (profile == null) {
                continue;
            }
            int xp = VeyloriaServerRuntime.instance().levelService().computeMobExperience(
                profile.level(),
                template.level(),
                template.mobType(),
                template.xpOverride(),
                VeyloriaServerRuntime.instance().ratesConfig().xpRate()
            );
            ExperienceGainResult gainResult = VeyloriaServerRuntime.instance().levelService().grantExperience(profile, xp);
            int copper = (int) Math.round((template.currencyMin() + level.getRandom().nextInt(template.currencyMax() - template.currencyMin() + 1))
                * VeyloriaServerRuntime.instance().ratesConfig().currencyRate());
            profile.addCurrency(copper);
            if (template.lootTableId() != null) {
                VeyloriaServerRuntime.instance().lootService().roll(template.lootTableId(), VeyloriaServerRuntime.instance().ratesConfig())
                    .forEach(roll -> {
                        ItemTemplate itemTemplate = roll.itemTemplate();
                        player.getInventory().placeItemBackInInventory(VeyloriaServerRuntime.instance().itemFactory().create(itemTemplate, roll.quantity()));
                        ServerMarkers.sendLoot(player, itemTemplate.name(), roll.quantity());
                    });
            }
            ServerMarkers.sendGain(player, xp, copper);
            syncPlayerHud(player, profile);
            if (gainResult.leveledUp()) {
                player.sendSystemMessage(Component.literal("Level up: " + gainResult.previousLevel() + " -> " + gainResult.newLevel()));
            }
            VeyloriaServerRuntime.instance().characterService().save(profile);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && VeyloriaServerRuntime.instance().authLockService().isLocked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && VeyloriaServerRuntime.instance().authLockService().isLocked(player)) {
            event.setCanceled(true);
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
        ServerMarkers.sendProfile(player, profile, xpToNext);
    }
}
