package dev.laakirun.veyloria.server.quest;

import dev.laakirun.veyloria.common.VeyloriaConstants;
import dev.laakirun.veyloria.common.model.CharacterProfile;
import dev.laakirun.veyloria.common.network.VeyloriaNetwork;
import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import dev.laakirun.veyloria.server.game.CommonMobAiService;
import dev.laakirun.veyloria.server.location.LocationLevelRange;
import dev.laakirun.veyloria.server.location.LocationService;
import dev.laakirun.veyloria.server.npc.NpcDefinition;
import dev.laakirun.veyloria.server.npc.NpcReward;
import dev.laakirun.veyloria.server.npc.NpcStoredInstance;
import dev.laakirun.veyloria.server.npc.XpReward;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.neoforge.network.PacketDistributor;

public final class QuestService {
    public static final String ACTION_QUEST_LIST = ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "quest_list").toString();
    public static final String ACTION_QUEST_ACCEPT = ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "quest_accept").toString();
    public static final String ACTION_QUEST_SUBMIT = ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "quest_submit").toString();

    private static final int OFFER_DECK_SIZE = 2;
    private static final double NEARBY_PARTY_RADIUS = 64.0D;
    private static final int COMBAT_SILENCE_TICKS = 160;
    private static final int COMBAT_AGGRO_SCAN_INTERVAL = 10;
    private static final double COMBAT_AGGRO_RADIUS = 28.0D;

    private final QuestDB questDb = new QuestDB();
    private final QuestObjectiveHandlersRegistry objectiveHandlers = new QuestObjectiveHandlersRegistry();
    private final QuestParamResolver paramResolver = new QuestParamResolver();
    private final LocationService locationService;

    public QuestService(LocationService locationService) {
        this.locationService = locationService;
    }

    public void registerDefinitions() {
        questDb.registerBuiltins();
        objectiveHandlers.registerBuiltins();
    }

    public void clear() {
    }

    public QuestDB questDb() {
        return questDb;
    }

    public LocationService locationService() {
        return locationService;
    }

    public long combatSilenceWindowTicks() {
        return COMBAT_SILENCE_TICKS;
    }

    public int combatAggroScanIntervalTicks() {
        return COMBAT_AGGRO_SCAN_INTERVAL;
    }

    public double combatAggroRadius() {
        return COMBAT_AGGRO_RADIUS;
    }

    public boolean ensureOfferDeck(ServerLevel level, NpcStoredInstance instance, NpcDefinition npcDefinition, long gameTime) {
        if (level == null || instance == null || npcDefinition == null) {
            return false;
        }
        boolean changed = false;
        ResourceLocation locationId = instance.locationId();
        if (locationId == null || locationId.equals(LocationService.NONE)) {
            ResourceLocation resolved = locationService.resolveLocationId(level, instance.spawnPos());
            if (!resolved.toString().equals(instance.locationIdRaw())) {
                instance.setLocationId(resolved);
                changed = true;
            }
            locationId = resolved;
        }

        List<QuestOffer> offers = new ArrayList<>();
        for (QuestOffer offer : instance.offers()) {
            QuestDefinition definition = questDb.get(offer.questIdLocation());
            if (definition == null) {
                changed = true;
                continue;
            }
            if (!definition.offerable() || !canOfferByNpc(definition, npcDefinition, locationId)) {
                changed = true;
                continue;
            }
            offers.add(offer);
        }

        if (offers.size() > OFFER_DECK_SIZE) {
            offers = new ArrayList<>(offers.subList(0, OFFER_DECK_SIZE));
            changed = true;
        }

        Set<String> existing = new LinkedHashSet<>();
        for (QuestOffer offer : offers) {
            existing.add(offer.questId());
        }

        for (QuestDefinition definition : questDb.getAll()) {
            if (offers.size() >= OFFER_DECK_SIZE) {
                break;
            }
            if (!definition.offerable() || !canOfferByNpc(definition, npcDefinition, locationId)) {
                continue;
            }
            if (existing.contains(definition.id().toString())) {
                continue;
            }
            long seed = mixOfferSeed(gameTime, instance.instanceId(), definition.id().toString(), offers.size());
            int levelValue = resolveOfferLevel(definition, locationId, seed);
            offers.add(new QuestOffer(definition.id().toString(), levelValue, seed, gameTime));
            existing.add(definition.id().toString());
            changed = true;
        }

        if (changed) {
            instance.setOffers(offers);
        }
        return changed;
    }

    public List<QuestNpcEntry> getAvailableOffersForNpc(ServerPlayer player, NpcStoredInstance npcInstance) {
        if (player == null || npcInstance == null || player.getServer() == null) {
            return List.of();
        }
        QuestSavedData data = QuestSavedData.get(player.getServer());
        PlayerQuestState state = data.state(player.getUUID());
        long gameTime = player.level().getGameTime();
        LinkedHashMap<String, QuestNpcEntry> entries = new LinkedHashMap<>();

        for (QuestProgress progress : state.active().values()) {
            if (!npcInstance.instanceId().equals(progress.giverNpcInstanceId())) {
                continue;
            }
            ResourceLocation questId = ResourceLocation.tryParse(progress.questId());
            QuestDefinition definition = questDb.get(questId);
            if (definition == null) {
                continue;
            }
            QuestObjectiveDefinition objective = currentObjective(definition, progress);
            String progressText = objectiveProgressText(player, progress, objective, gameTime);
            QuestNpcActionType actionType = progress.status() == QuestProgressStatus.READY_TO_TURN_IN
                ? QuestNpcActionType.SUBMIT : QuestNpcActionType.NONE;
            entries.put(progress.questId(), new QuestNpcEntry(
                progress.questId(),
                definition.title(),
                progressText,
                progress.instanceLevel(),
                actionType,
                true,
                ""
            ));
        }

        for (Map.Entry<String, QuestContinuationUnlock> entry : state.continuations().entrySet()) {
            String questIdRaw = entry.getKey();
            QuestContinuationUnlock unlock = entry.getValue();
            if (!npcInstance.instanceId().equals(unlock.npcInstanceId()) || entries.containsKey(questIdRaw)) {
                continue;
            }
            QuestDefinition definition = questDb.get(ResourceLocation.tryParse(questIdRaw));
            if (definition == null) {
                continue;
            }
            String reason = acceptDeniedReason(player, state, npcInstance, definition, gameTime);
            boolean available = reason.isBlank();
            entries.put(questIdRaw, new QuestNpcEntry(
                questIdRaw,
                definition.title(),
                "Уровень " + unlock.instanceLevel(),
                unlock.instanceLevel(),
                available ? QuestNpcActionType.ACCEPT : QuestNpcActionType.NONE,
                available,
                reason
            ));
        }

        for (QuestOffer offer : npcInstance.offers()) {
            if (entries.containsKey(offer.questId())) {
                continue;
            }
            QuestDefinition definition = questDb.get(offer.questIdLocation());
            if (definition == null) {
                continue;
            }
            String reason = acceptDeniedReason(player, state, npcInstance, definition, gameTime);
            boolean available = reason.isBlank();
            entries.put(offer.questId(), new QuestNpcEntry(
                offer.questId(),
                definition.title(),
                "Уровень " + offer.instanceLevel(),
                offer.instanceLevel(),
                available ? QuestNpcActionType.ACCEPT : QuestNpcActionType.NONE,
                available,
                reason
            ));
        }

        return List.copyOf(entries.values());
    }

    public QuestActionResult acceptQuest(ServerPlayer player, NpcStoredInstance npcInstance, String questIdRaw) {
        if (player == null || npcInstance == null || questIdRaw == null || questIdRaw.isBlank() || player.getServer() == null) {
            return QuestActionResult.fail("Квест недоступен");
        }
        ResourceLocation questId = ResourceLocation.tryParse(questIdRaw);
        QuestDefinition definition = questDb.get(questId);
        if (definition == null) {
            return QuestActionResult.fail("Квест не найден");
        }

        QuestSavedData data = QuestSavedData.get(player.getServer());
        PlayerQuestState state = data.state(player.getUUID());
        long gameTime = player.level().getGameTime();
        String denied = acceptDeniedReason(player, state, npcInstance, definition, gameTime);
        if (!denied.isBlank()) {
            return QuestActionResult.fail(denied);
        }

        OfferResolution offerResolution = resolveOfferForAccept(state, npcInstance, definition);
        if (!offerResolution.allowed()) {
            return QuestActionResult.fail(offerResolution.reason());
        }

        QuestObjectiveDefinition objective = definition.objectives().isEmpty() ? null : definition.objectives().get(0);
        if (objective == null) {
            return QuestActionResult.fail("У квеста нет objectives");
        }

        CompoundTag resolvedParams = paramResolver.resolve(objective, offerResolution.instanceLevel(), npcInstance.locationId(),
            offerResolution.rollSeed());
        QuestProgress progress = new QuestProgress(
            definition.id().toString(),
            npcInstance.instanceId(),
            offerResolution.instanceLevel(),
            0,
            new CompoundTag(),
            gameTime,
            QuestProgressStatus.ACTIVE,
            resolvedParams
        );

        QuestObjectiveHandler handler = objectiveHandlers.get(objective.type());
        if (handler != null) {
            handler.onAccept(player, progress, objective, this, gameTime);
        }
        refreshProgressStatus(player, definition, progress, gameTime);

        state.active().put(definition.id().toString(), progress);
        state.continuations().remove(definition.id().toString());
        data.setDirty();
        syncPlayer(player);
        return QuestActionResult.ok("Квест принят: " + definition.title());
    }

    public QuestActionResult submitQuest(ServerPlayer player, NpcStoredInstance npcInstance, String questIdRaw) {
        if (player == null || npcInstance == null || questIdRaw == null || questIdRaw.isBlank() || player.getServer() == null) {
            return QuestActionResult.fail("Квест недоступен");
        }
        ResourceLocation questId = ResourceLocation.tryParse(questIdRaw);
        QuestDefinition definition = questDb.get(questId);
        if (definition == null) {
            return QuestActionResult.fail("Квест не найден");
        }

        QuestSavedData data = QuestSavedData.get(player.getServer());
        PlayerQuestState state = data.state(player.getUUID());
        QuestProgress progress = state.active().get(definition.id().toString());
        if (progress == null) {
            return QuestActionResult.fail("Квест не активен");
        }
        if (!npcInstance.instanceId().equals(progress.giverNpcInstanceId())) {
            return QuestActionResult.fail("Сдать можно только тому же NPC");
        }
        if (progress.status() != QuestProgressStatus.READY_TO_TURN_IN) {
            return QuestActionResult.fail("Квест ещё не выполнен");
        }

        int playerLevel = playerLevel(player);
        double rewardFactor = rewardFactor(playerLevel, progress.instanceLevel());
        applyRewards(player, definition.rewards(), rewardFactor);

        long gameTime = player.level().getGameTime();
        state.active().remove(definition.id().toString());
        state.completed().put(definition.id().toString(), gameTime);
        if (definition.repeatPolicy().type() == QuestRepeatType.COOLDOWN) {
            state.cooldowns().put(definition.id().toString(), gameTime + definition.repeatPolicy().cooldownSeconds() * 20L);
        }

        if (definition.nextQuestId() != null) {
            state.continuations().put(definition.nextQuestId().toString(),
                new QuestContinuationUnlock(npcInstance.instanceId(), progress.instanceLevel()));
        }

        data.setDirty();
        syncPlayer(player);
        return QuestActionResult.ok("Квест сдан: " + definition.title());
    }

    public QuestActionResult handleQuestNpcAction(ServerPlayer player, NpcStoredInstance npcInstance, String actionId, String payload) {
        if (ACTION_QUEST_LIST.equals(actionId)) {
            return QuestActionResult.ok("Список квестов");
        }
        if (ACTION_QUEST_ACCEPT.equals(actionId)) {
            return acceptQuest(player, npcInstance, payload);
        }
        if (ACTION_QUEST_SUBMIT.equals(actionId)) {
            return submitQuest(player, npcInstance, payload);
        }
        return QuestActionResult.fail("Неизвестное действие");
    }

    public void onPlayerDealtDamage(ServerPlayer player, long gameTime) {
        for (QuestObjectiveHandler handler : objectiveHandlers.all()) {
            handler.onPlayerDealtDamage(player, gameTime);
        }
    }

    public void onPlayerTakenDamage(ServerPlayer player, long gameTime) {
        for (QuestObjectiveHandler handler : objectiveHandlers.all()) {
            handler.onPlayerTookDamage(player, gameTime);
        }
    }

    public void onMobKilledByPlayer(ServerPlayer killer, LivingEntity victim, long gameTime) {
        if (killer == null || victim == null || killer.getServer() == null) {
            return;
        }
        QuestSavedData data = QuestSavedData.get(killer.getServer());
        Set<UUID> party = VeyloriaServerRuntime.instance().partyService().membersOf(killer.getUUID());
        Map<UUID, ServerPlayer> onlineParty = new LinkedHashMap<>();
        for (UUID memberId : party) {
            ServerPlayer member = killer.getServer().getPlayerList().getPlayer(memberId);
            if (member != null && member.isAlive()) {
                onlineParty.put(memberId, member);
            }
        }

        boolean dirty = false;
        Set<UUID> changedPlayers = new LinkedHashSet<>();

        for (ServerPlayer member : onlineParty.values()) {
            PlayerQuestState state = data.state(member.getUUID());
            for (QuestProgress progress : new ArrayList<>(state.active().values())) {
                QuestDefinition definition = questDb.get(ResourceLocation.tryParse(progress.questId()));
                if (definition == null) {
                    continue;
                }
                QuestObjectiveDefinition objective = currentObjective(definition, progress);
                if (objective == null || !QuestObjectiveHandlersRegistry.KILL_BY_DISPOSITION.equals(objective.type())) {
                    continue;
                }
                QuestShareMode shareMode = effectiveShareMode(objective);
                if (shareMode == QuestShareMode.PERSONAL && !member.getUUID().equals(killer.getUUID())) {
                    continue;
                }
                if (shareMode == QuestShareMode.NEARBY_PARTY && !isNearby(member, victim, NEARBY_PARTY_RADIUS)) {
                    continue;
                }
                QuestObjectiveHandler handler = objectiveHandlers.get(objective.type());
                if (handler == null) {
                    continue;
                }
                boolean changed = handler.onPlayerKilled(member, victim, progress, objective, this, gameTime);
                if (!changed) {
                    continue;
                }
                if (refreshProgressStatus(member, definition, progress, gameTime)) {
                    changed = true;
                }
                if (changed) {
                    dirty = true;
                    changedPlayers.add(member.getUUID());
                }
            }
        }

        if (dirty) {
            data.setDirty();
            for (UUID changedId : changedPlayers) {
                ServerPlayer changedPlayer = killer.getServer().getPlayerList().getPlayer(changedId);
                if (changedPlayer != null) {
                    syncPlayer(changedPlayer);
                }
            }
        }
    }

    public void tick(ServerLevel level) {
        if (level == null || level.getServer() == null) {
            return;
        }
        QuestSavedData data = QuestSavedData.get(level.getServer());
        long gameTime = level.getGameTime();
        boolean dirty = false;
        for (ServerPlayer player : level.players()) {
            PlayerQuestState state = data.state(player.getUUID());
            boolean changed = tickPlayerObjectives(player, state, gameTime);
            if (changed) {
                dirty = true;
                syncPlayer(player);
            }
        }
        if (dirty) {
            data.setDirty();
        }
    }

    public void syncPlayer(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        QuestSavedData data = QuestSavedData.get(player.getServer());
        PlayerQuestState state = data.state(player.getUUID());
        CompoundTag payload = buildSyncTag(player, state);
        PacketDistributor.sendToPlayer(player, VeyloriaNetwork.questStateSync(payload));
    }

    public String resolveDispositionId(LivingEntity victim) {
        if (victim instanceof ServerPlayer) {
            return "ALLIED";
        }
        if (!(victim instanceof Mob mob)) {
            return "NEUTRAL";
        }
        CommonMobAiService commonMobAiService = VeyloriaServerRuntime.instance().commonMobAiService();
        if (commonMobAiService != null) {
            String id = commonMobAiService.dispositionId(mob);
            if (!id.isBlank()) {
                return id.toUpperCase(java.util.Locale.ROOT);
            }
        }
        if (mob instanceof Monster) {
            return "HOSTILE";
        }
        return "NEUTRAL";
    }

    private boolean tickPlayerObjectives(ServerPlayer player, PlayerQuestState state, long gameTime) {
        boolean changed = false;
        for (QuestProgress progress : new ArrayList<>(state.active().values())) {
            QuestDefinition definition = questDb.get(ResourceLocation.tryParse(progress.questId()));
            if (definition == null) {
                continue;
            }
            QuestObjectiveDefinition objective = currentObjective(definition, progress);
            if (objective == null) {
                continue;
            }
            QuestObjectiveHandler handler = objectiveHandlers.get(objective.type());
            if (handler != null && handler.onTick(player, progress, objective, this, gameTime)) {
                changed = true;
            }
            if (refreshProgressStatus(player, definition, progress, gameTime)) {
                changed = true;
            }
        }
        return changed;
    }

    private boolean refreshProgressStatus(ServerPlayer player, QuestDefinition definition, QuestProgress progress, long gameTime) {
        QuestObjectiveDefinition objective = currentObjective(definition, progress);
        if (objective == null) {
            return false;
        }
        QuestObjectiveHandler handler = objectiveHandlers.get(objective.type());
        boolean complete = handler != null && handler.isComplete(player, progress, objective, this, gameTime);
        if (complete) {
            if (progress.objectiveIndex() + 1 < definition.objectives().size()) {
                int nextIndex = progress.objectiveIndex() + 1;
                QuestObjectiveDefinition nextObjective = definition.objectives().get(nextIndex);
                progress.setObjectiveIndex(nextIndex);
                ResourceLocation locationId = ResourceLocation.tryParse(progress.resolvedParams().getString("resolvedLocationId"));
                long seed = progress.resolvedParams().getLong("rollSeed");
                CompoundTag resolved = paramResolver.resolve(nextObjective, progress.instanceLevel(), locationId, seed);
                progress.setResolvedParams(resolved);
                progress.setStatus(QuestProgressStatus.ACTIVE);
                QuestObjectiveHandler nextHandler = objectiveHandlers.get(nextObjective.type());
                if (nextHandler != null) {
                    nextHandler.onAccept(player, progress, nextObjective, this, gameTime);
                }
                return true;
            }
            if (progress.status() != QuestProgressStatus.READY_TO_TURN_IN) {
                progress.setStatus(QuestProgressStatus.READY_TO_TURN_IN);
                return true;
            }
            return false;
        }
        if (progress.status() != QuestProgressStatus.ACTIVE) {
            progress.setStatus(QuestProgressStatus.ACTIVE);
            return true;
        }
        return false;
    }

    private OfferResolution resolveOfferForAccept(PlayerQuestState state, NpcStoredInstance npcInstance, QuestDefinition definition) {
        if (definition.offerable()) {
            for (QuestOffer offer : npcInstance.offers()) {
                if (offer.questId().equals(definition.id().toString())) {
                    return OfferResolution.allowed(offer.instanceLevel(), offer.rollSeed());
                }
            }
            return OfferResolution.denied("Квест не в оффере NPC");
        }
        QuestContinuationUnlock unlock = state.continuations().get(definition.id().toString());
        if (unlock == null) {
            return OfferResolution.denied("Квест пока недоступен");
        }
        if (!npcInstance.instanceId().equals(unlock.npcInstanceId())) {
            return OfferResolution.denied("Продолжение доступно у другого NPC");
        }
        long seed = mixOfferSeed(System.nanoTime(), npcInstance.instanceId(), definition.id().toString(), unlock.instanceLevel());
        return OfferResolution.allowed(unlock.instanceLevel(), seed);
    }

    private String acceptDeniedReason(ServerPlayer player, PlayerQuestState state, NpcStoredInstance npcInstance, QuestDefinition definition, long gameTime) {
        if (state.active().containsKey(definition.id().toString())) {
            return "Квест уже активен";
        }
        Long cooldownUntil = state.cooldowns().get(definition.id().toString());
        if (cooldownUntil != null && cooldownUntil > gameTime) {
            long leftSeconds = Math.max(1L, (cooldownUntil - gameTime) / 20L);
            return "Кулдаун: " + leftSeconds + "с";
        }
        if (definition.repeatPolicy().type() == QuestRepeatType.ONCE && state.completed().containsKey(definition.id().toString())) {
            return "Квест уже завершён";
        }
        if (definition.giverNpcDefinitionId() != null) {
            ResourceLocation npcDefId = npcInstance.definitionId();
            if (npcDefId == null || !definition.giverNpcDefinitionId().equals(npcDefId)) {
                return "Этот NPC не выдаёт данный квест";
            }
        }
        if (!locationService.matches(definition.locationId(), npcInstance.locationId())) {
            return "Квест недоступен в этой локации";
        }
        return "";
    }

    private void applyRewards(ServerPlayer player, List<NpcReward> rewards, double factor) {
        if (player == null || rewards == null || rewards.isEmpty()) {
            return;
        }
        for (NpcReward reward : rewards) {
            if (reward instanceof XpReward xpReward) {
                int scaled = scaledValue(xpReward.amount(), factor);
                if (scaled > 0) {
                    new XpReward(scaled).apply(player);
                }
                continue;
            }
            if (reward instanceof CurrencyReward currencyReward) {
                int scaled = scaledValue(currencyReward.amount(), factor);
                if (scaled > 0) {
                    new CurrencyReward(currencyReward.currencyId(), scaled).apply(player);
                }
                continue;
            }
            reward.apply(player);
        }
    }

    private int scaledValue(int base, double factor) {
        if (base <= 0) {
            return 0;
        }
        return Math.max(0, (int) Math.round(base * factor));
    }

    private double rewardFactor(int playerLevel, int questLevel) {
        int delta = playerLevel - questLevel;
        if (delta <= 5) {
            return 1.0D;
        }
        double reduced = 1.0D - (delta - 5) * 0.08D;
        return Math.max(0.10D, reduced);
    }

    private int playerLevel(ServerPlayer player) {
        CharacterProfile profile = VeyloriaServerRuntime.instance().characterService() == null
            ? null
            : VeyloriaServerRuntime.instance().characterService().loadedProfile(player.getUUID());
        return profile == null ? 1 : profile.level();
    }

    private int resolveOfferLevel(QuestDefinition definition, ResourceLocation locationId, long seed) {
        QuestLevelPolicy policy = definition.levelPolicy();
        Random random = new Random(seed);
        return switch (policy.type()) {
            case FIXED -> policy.fixedLevel();
            case RANGE -> policy.minLevel() + random.nextInt(policy.maxLevel() - policy.minLevel() + 1);
            case SCALED_TO_LOCATION -> locationService.levelRange(locationId).randomLevel(seed);
            case SCALED_TO_LOCATION_WITH_OFFSET -> {
                LocationLevelRange range = locationService.levelRange(locationId);
                int base = range.randomLevel(seed);
                int offset = policy.offsetMin() + random.nextInt(policy.offsetMax() - policy.offsetMin() + 1);
                yield Math.max(1, base + offset);
            }
        };
    }

    private boolean canOfferByNpc(QuestDefinition definition, NpcDefinition npcDefinition, ResourceLocation locationId) {
        if (!definition.offerable()) {
            return false;
        }
        if (definition.parentQuestId() != null) {
            return false;
        }
        if (definition.giverNpcDefinitionId() != null && !definition.giverNpcDefinitionId().equals(npcDefinition.id())) {
            return false;
        }
        return locationService.matches(definition.locationId(), locationId);
    }

    private long mixOfferSeed(long base, String a, String b, int c) {
        long seed = base;
        seed ^= (a == null ? 0L : a.hashCode()) * 31L;
        seed ^= (b == null ? 0L : b.hashCode()) * 17L;
        seed ^= c * 13L;
        return seed;
    }

    private QuestObjectiveDefinition currentObjective(QuestDefinition definition, QuestProgress progress) {
        if (definition == null || progress == null || definition.objectives().isEmpty()) {
            return null;
        }
        int idx = Math.max(0, Math.min(progress.objectiveIndex(), definition.objectives().size() - 1));
        return definition.objectives().get(idx);
    }

    private String objectiveProgressText(ServerPlayer player, QuestProgress progress, QuestObjectiveDefinition objective, long gameTime) {
        if (objective == null) {
            return "";
        }
        QuestObjectiveHandler handler = objectiveHandlers.get(objective.type());
        if (handler == null) {
            return "";
        }
        return handler.progressText(player, progress, objective, this, gameTime);
    }

    private QuestShareMode effectiveShareMode(QuestObjectiveDefinition objective) {
        if (objective == null) {
            return QuestShareMode.PERSONAL;
        }
        if (QuestObjectiveHandlersRegistry.COMBAT_TIME.equals(objective.type())) {
            return QuestShareMode.PERSONAL;
        }
        return objective.shareMode();
    }

    private boolean isNearby(ServerPlayer player, LivingEntity victim, double radius) {
        if (player == null || victim == null) {
            return false;
        }
        if (player.level() != victim.level()) {
            return false;
        }
        return player.distanceToSqr(victim) <= radius * radius;
    }

    private CompoundTag buildSyncTag(ServerPlayer player, PlayerQuestState state) {
        CompoundTag root = new CompoundTag();
        ListTag activeList = new ListTag();
        long gameTime = player.level().getGameTime();
        for (QuestProgress progress : state.active().values()) {
            QuestDefinition definition = questDb.get(ResourceLocation.tryParse(progress.questId()));
            if (definition == null) {
                continue;
            }
            QuestObjectiveDefinition objective = currentObjective(definition, progress);
            String objectiveText = objective == null
                ? definition.description()
                : (objective.displayText().isBlank() ? definition.description() : objective.displayText());
            String progressText = objectiveProgressText(player, progress, objective, gameTime);

            CompoundTag tag = new CompoundTag();
            tag.putString("questId", definition.id().toString());
            tag.putString("title", definition.title());
            tag.putString("objective", objectiveText);
            tag.putString("progress", progressText);
            tag.putBoolean("readyToTurnIn", progress.status() == QuestProgressStatus.READY_TO_TURN_IN);
            tag.putInt("level", progress.instanceLevel());
            activeList.add(tag);
        }
        root.put("active", activeList);
        return root;
    }

    private record OfferResolution(boolean allowed, int instanceLevel, long rollSeed, String reason) {
        private static OfferResolution allowed(int instanceLevel, long rollSeed) {
            return new OfferResolution(true, instanceLevel, rollSeed, "");
        }

        private static OfferResolution denied(String reason) {
            return new OfferResolution(false, 1, 0L, reason == null ? "" : reason);
        }
    }
}
