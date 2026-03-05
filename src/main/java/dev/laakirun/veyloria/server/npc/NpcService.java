package dev.laakirun.veyloria.server.npc;

import dev.laakirun.veyloria.common.VeyloriaConstants;
import dev.laakirun.veyloria.common.entity.NpcEntity;
import dev.laakirun.veyloria.common.npc.NpcAppearance;
import dev.laakirun.veyloria.common.registry.VeyloriaEntityTypes;
import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import dev.laakirun.veyloria.server.quest.QuestActionResult;
import dev.laakirun.veyloria.server.quest.QuestNpcActionType;
import dev.laakirun.veyloria.server.quest.QuestNpcEntry;
import dev.laakirun.veyloria.server.quest.QuestService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public final class NpcService {
    public static final String ACTION_URL_PREFIX = "veyloria://npc/";

    private static final ResourceLocation TEST_NPC_DEFINITION_ID =
        ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "test_npc");
    private static final String TEST_NPC_INSTANCE_ID = "veyloria:test_npc_spawn";
    private static final String TEST_NPC_GROUP_KEY = "spawn_area";
    private static final long SESSION_TTL_TICKS = 20L * 10L;
    private static final double INTERACTION_DISTANCE = 6.0D;

    private final NpcDB npcDb = new NpcDB();
    private final NpcActionHandlers actionHandlers = new NpcActionHandlers();
    private final Map<UUID, InteractionSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, RecentInteract> recentInteracts = new ConcurrentHashMap<>();

    public void registerDefinitions() {
        npcDb.registerBuiltins();
    }

    public void clear() {
        sessions.clear();
        recentInteracts.clear();
    }

    public NpcDB npcDb() {
        return npcDb;
    }

    public void ensureSpawnedOnLoad(ServerLevel level) {
        if (level == null || level.getServer() == null || level.dimension() != Level.OVERWORLD) {
            return;
        }
        NpcSavedData data = NpcSavedData.get(level.getServer());
        for (NpcSpawnPlan plan : spawnPlans(level)) {
            ensurePlanSpawned(level, data, plan);
        }
    }

    public void tick(ServerLevel level) {
        if (level == null || level.getServer() == null) {
            return;
        }
        ensureSpawnedOnLoad(level);
        long gameTime = level.getGameTime();
        pruneExpiredSessions(gameTime);
        pruneRecentInteracts(gameTime);
        NpcSavedData data = NpcSavedData.get(level.getServer());
        for (NpcStoredInstance instance : data.instances().values()) {
            if (!level.dimension().location().toString().equals(instance.dimensionId())) {
                continue;
            }
            if (instance.entityUuid() != null) {
                Entity raw = level.getEntity(instance.entityUuid());
                if (raw instanceof NpcEntity npc && npc.isAlive()) {
                    continue;
                }
                instance.setEntityUuid(null);
                data.setDirty();
            }
            if (instance.entityUuid() == null) {
                NpcEntity existing = findLoadedNpcByInstanceId(level, instance.instanceId(), instance.spawnPos());
                if (existing != null) {
                    onEntityJoin(level, existing);
                    continue;
                }
            }
            if (instance.entityUuid() == null && gameTime >= instance.nextRespawnTick()) {
                spawnNpc(level, data, instance);
            }
        }
    }

    public void onEntityJoin(ServerLevel level, NpcEntity npc) {
        if (level == null || npc == null || level.getServer() == null) {
            return;
        }
        String instanceId = npc.instanceId();
        ResourceLocation definitionId = npc.definitionId();
        if (instanceId.isBlank() || definitionId == null) {
            return;
        }
        NpcSavedData data = NpcSavedData.get(level.getServer());
        NpcStoredInstance instance = data.get(instanceId);
        if (instance == null) {
            instance = new NpcStoredInstance(
                instanceId,
                definitionId.toString(),
                level.dimension().location().toString(),
                npc.blockPosition(),
                npc.getUUID(),
                0L,
                ""
            );
            data.put(instance);
        } else {
            boolean changed = false;
            if (!definitionId.toString().equals(instance.definitionIdRaw())) {
                instance.setDefinitionId(definitionId);
                changed = true;
            }
            String dimensionId = level.dimension().location().toString();
            if (!dimensionId.equals(instance.dimensionId())) {
                instance.setDimensionId(dimensionId);
                changed = true;
            }
            if (!npc.getUUID().equals(instance.entityUuid())) {
                instance.setEntityUuid(npc.getUUID());
                changed = true;
            }
            if (instance.nextRespawnTick() != 0L) {
                instance.setNextRespawnTick(0L);
                changed = true;
            }
            if (changed) {
                data.setDirty();
            }
        }
        NpcDefinition definition = npcDb.getDefinition(definitionId);
        if (definition != null) {
            npc.setNpcData(definition.id().toString(), instanceId, definition.appearance());
            applyDefinition(npc, definition);
            if (ensureOfferDeck(level, data, instance, definition)) {
                data.setDirty();
            }
        } else {
            npc.setNpcData(definitionId.toString(), instanceId, NpcAppearance.WITHER);
            npc.getPersistentData().putBoolean("veyloria_allied", true);
        }
    }

    public void onNpcDeath(NpcEntity npc) {
        if (npc == null || !(npc.level() instanceof ServerLevel level) || level.getServer() == null) {
            return;
        }
        String instanceId = npc.instanceId();
        if (instanceId.isBlank()) {
            return;
        }
        NpcSavedData data = NpcSavedData.get(level.getServer());
        NpcStoredInstance instance = data.get(instanceId);
        if (instance == null) {
            return;
        }
        NpcDefinition definition = npcDb.getDefinition(instance.definitionId());
        int respawnSeconds = definition == null ? 60 : definition.respawnSeconds();
        instance.setEntityUuid(null);
        instance.setNextRespawnTick(level.getGameTime() + respawnSeconds * 20L);
        data.setDirty();
        sessions.entrySet().removeIf(entry -> entry.getValue().instanceId().equals(instanceId));
    }

    public boolean onInteract(ServerPlayer player, NpcEntity npc) {
        if (player == null || npc == null || !npc.canBeInteractedBy(player) || !(npc.level() instanceof ServerLevel level)) {
            return false;
        }
        if (player.level() != level) {
            return false;
        }
        String instanceId = npc.instanceId();
        if (instanceId.isBlank()) {
            return false;
        }
        if (isDuplicateInteract(player.getUUID(), npc.getUUID(), level.getGameTime())) {
            return true;
        }
        NpcSavedData data = NpcSavedData.get(level.getServer());
        NpcStoredInstance instance = data.get(instanceId);
        if (instance == null) {
            return false;
        }
        NpcDefinition definition = npcDb.getDefinition(instance.definitionId());
        if (definition == null) {
            return false;
        }
        if (ensureOfferDeck(level, data, instance, definition)) {
            data.setDirty();
        }
        NpcInteractionNode node = definition.startNode();
        if (node == null) {
            return false;
        }
        long nonce = nextNonce();
        InteractionSession session = new InteractionSession(instanceId, node.nodeId(), nonce, level.getGameTime() + SESSION_TTL_TICKS);
        sessions.put(player.getUUID(), session);
        sendNodeMessage(player, definition, node, session);
        return true;
    }

    public void handleAction(ServerPlayer player, String instanceId, String actionId, String payload, long nonce) {
        if (player == null || instanceId == null || actionId == null || instanceId.isBlank() || actionId.isBlank()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level) || level.getServer() == null) {
            sessions.remove(player.getUUID());
            return;
        }
        long gameTime = level.getGameTime();
        InteractionSession session = sessions.get(player.getUUID());
        if (session == null || gameTime > session.expiresAtTick() || session.nonce() != nonce || !session.instanceId().equals(instanceId)) {
            sessions.remove(player.getUUID());
            return;
        }
        NpcSavedData data = NpcSavedData.get(level.getServer());
        NpcStoredInstance instance = data.get(instanceId);
        if (instance == null || !level.dimension().location().toString().equals(instance.dimensionId())) {
            sessions.remove(player.getUUID());
            return;
        }
        Entity raw = instance.entityUuid() == null ? null : level.getEntity(instance.entityUuid());
        if (!(raw instanceof NpcEntity npc) || !npc.isAlive()) {
            sessions.remove(player.getUUID());
            return;
        }
        if (player.distanceToSqr(npc) > INTERACTION_DISTANCE * INTERACTION_DISTANCE) {
            sessions.remove(player.getUUID());
            return;
        }
        if (isQuestAction(actionId)) {
            handleQuestAction(player, level, data, instance, session, actionId, payload == null ? "" : payload);
            return;
        }

        NpcDefinition definition = npcDb.getDefinition(instance.definitionId());
        if (definition == null) {
            sessions.remove(player.getUUID());
            return;
        }
        NpcInteractionNode node = definition.node(session.nodeId());
        if (node == null) {
            sessions.remove(player.getUUID());
            return;
        }
        NpcInteractionAction action = node.action(actionId, payload == null ? "" : payload);
        if (action == null) {
            sessions.remove(player.getUUID());
            return;
        }
        ActionContext context = new ActionContext(instance, definition, node, action, session);
        for (NpcReward reward : action.rewards()) {
            reward.apply(player);
        }
        NpcActionHandler handler = actionHandlers.get(action.actionId());
        if (handler != null) {
            handler.handle(player, context);
        }
        String nextNodeId = action.nextNodeId();
        if (!nextNodeId.isBlank()) {
            NpcInteractionNode nextNode = definition.node(nextNodeId);
            if (nextNode != null) {
                long nextNonce = nextNonce();
                InteractionSession nextSession = new InteractionSession(
                    session.instanceId(),
                    nextNode.nodeId(),
                    nextNonce,
                    gameTime + SESSION_TTL_TICKS
                );
                sessions.put(player.getUUID(), nextSession);
                sendNodeMessage(player, definition, nextNode, nextSession);
                return;
            }
        }
        sessions.remove(player.getUUID());
    }

    public static String buildActionUrl(String actionId, String payload, String instanceId, long nonce) {
        String safePayload = payload == null || payload.isBlank() ? "-" : payload;
        return ACTION_URL_PREFIX + actionId + "/" + safePayload + "/" + instanceId + "/" + nonce;
    }

    private void pruneExpiredSessions(long gameTime) {
        sessions.entrySet().removeIf(entry -> gameTime > entry.getValue().expiresAtTick());
    }

    private void pruneRecentInteracts(long gameTime) {
        recentInteracts.entrySet().removeIf(entry -> gameTime - entry.getValue().tick() > 5L);
    }

    private boolean isDuplicateInteract(UUID playerId, UUID npcId, long gameTime) {
        RecentInteract recent = recentInteracts.get(playerId);
        if (recent != null && recent.npcId().equals(npcId) && gameTime - recent.tick() <= 1L) {
            return true;
        }
        recentInteracts.put(playerId, new RecentInteract(npcId, gameTime));
        return false;
    }

    private List<NpcSpawnPlan> spawnPlans(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        ResourceKey<Level> dimension = level.dimension();
        if (dimension != Level.OVERWORLD) {
            return List.of();
        }
        return List.of(buildTestSpawnPlan(level));
    }

    private void ensurePlanSpawned(ServerLevel level, NpcSavedData data, NpcSpawnPlan plan) {
        NpcStoredInstance instance = data.get(plan.instanceId());
        if (instance == null) {
            instance = new NpcStoredInstance(
                plan.instanceId(),
                plan.definitionId().toString(),
                plan.dimension().location().toString(),
                plan.spawnPos(),
                null,
                0L,
                plan.groupKey()
            );
            data.put(instance);
        } else {
            boolean changed = false;
            if (!plan.definitionId().toString().equals(instance.definitionIdRaw())) {
                instance.setDefinitionId(plan.definitionId());
                changed = true;
            }
            String dimensionId = plan.dimension().location().toString();
            if (!dimensionId.equals(instance.dimensionId())) {
                instance.setDimensionId(dimensionId);
                changed = true;
            }
            if (!plan.spawnPos().equals(instance.spawnPos())) {
                instance.setSpawnPos(plan.spawnPos());
                changed = true;
            }
            if (!plan.groupKey().equals(instance.groupKey())) {
                instance.setGroupKey(plan.groupKey());
                changed = true;
            }
            if (changed) {
                data.setDirty();
            }
        }
        if (instance.entityUuid() != null) {
            Entity raw = level.getEntity(instance.entityUuid());
            if (raw instanceof NpcEntity npc && npc.isAlive()) {
                onEntityJoin(level, npc);
                return;
            }
            instance.setEntityUuid(null);
            data.setDirty();
        }
        NpcEntity existing = findLoadedNpcByInstanceId(level, instance.instanceId(), instance.spawnPos());
        if (existing != null) {
            onEntityJoin(level, existing);
            return;
        }
        if (instance.nextRespawnTick() <= level.getGameTime()) {
            spawnNpc(level, data, instance);
        }
    }

    private void spawnNpc(ServerLevel level, NpcSavedData data, NpcStoredInstance instance) {
        if (instance == null || instance.definitionId() == null) {
            return;
        }
        NpcDefinition definition = npcDb.getDefinition(instance.definitionId());
        if (definition == null) {
            return;
        }
        NpcEntity npc = new NpcEntity(VeyloriaEntityTypes.NPC.get(), level);
        BlockPos pos = instance.spawnPos();
        npc.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 180.0F, 0.0F);
        npc.setNpcData(definition.id().toString(), instance.instanceId(), definition.appearance());
        applyDefinition(npc, definition);
        if (level.addFreshEntity(npc)) {
            instance.setEntityUuid(npc.getUUID());
            instance.setNextRespawnTick(0L);
            if (ensureOfferDeck(level, data, instance, definition)) {
                data.setDirty();
            }
            data.setDirty();
        }
    }

    private void applyDefinition(NpcEntity npc, NpcDefinition definition) {
        npc.getPersistentData().putBoolean("veyloria_allied", true);
        npc.setPersistenceRequired();
        if (!definition.displayName().isBlank()) {
            npc.setCustomName(Component.literal(definition.displayName()));
            npc.setCustomNameVisible(true);
        }
        if (npc.getAttribute(Attributes.MAX_HEALTH) != null) {
            npc.getAttribute(Attributes.MAX_HEALTH).setBaseValue(definition.stats().maxHealth());
        }
        if (npc.getAttribute(Attributes.ARMOR) != null) {
            npc.getAttribute(Attributes.ARMOR).setBaseValue(definition.stats().armor());
        }
        if (npc.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            npc.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(definition.stats().knockbackResistance());
        }
        npc.setHealth((float) definition.stats().maxHealth());
    }

    private NpcSpawnPlan buildTestSpawnPlan(ServerLevel level) {
        BlockPos sharedSpawn = level.getSharedSpawnPos().offset(2, 0, 2);
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sharedSpawn).above();
        return new NpcSpawnPlan(TEST_NPC_INSTANCE_ID, TEST_NPC_DEFINITION_ID, level.dimension(), top, TEST_NPC_GROUP_KEY);
    }

    private NpcEntity findLoadedNpcByInstanceId(ServerLevel level, String instanceId, BlockPos around) {
        if (instanceId == null || instanceId.isBlank()) {
            return null;
        }
        AABB area = new AABB(around).inflate(64.0D);
        for (NpcEntity npc : level.getEntitiesOfClass(NpcEntity.class, area, Entity::isAlive)) {
            if (instanceId.equals(npc.instanceId())) {
                return npc;
            }
        }
        return null;
    }

    private void sendNodeMessage(ServerPlayer player, NpcDefinition definition, NpcInteractionNode node, InteractionSession session) {
        MutableComponent message = Component.literal(node.message());
        for (NpcInteractionAction action : node.actions()) {
            String url = buildActionUrl(action.actionId(), action.payload(), session.instanceId(), session.nonce());
            MutableComponent clickable = Component.literal(action.label())
                .withStyle(style -> style
                    .withColor(ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
            message.append(Component.literal(" ")).append(clickable);
        }
        player.sendSystemMessage(message);
    }

    private boolean ensureOfferDeck(ServerLevel level, NpcSavedData data, NpcStoredInstance instance, NpcDefinition definition) {
        QuestService questService = VeyloriaServerRuntime.instance().questService();
        if (questService == null) {
            return false;
        }
        boolean changed = questService.ensureOfferDeck(level, instance, definition, level.getGameTime());
        if (changed && data != null) {
            data.setDirty();
        }
        return changed;
    }

    private boolean isQuestAction(String actionId) {
        return QuestService.ACTION_QUEST_LIST.equals(actionId)
            || QuestService.ACTION_QUEST_ACCEPT.equals(actionId)
            || QuestService.ACTION_QUEST_SUBMIT.equals(actionId);
    }

    private void handleQuestAction(ServerPlayer player, ServerLevel level, NpcSavedData data, NpcStoredInstance instance,
                                   InteractionSession session, String actionId, String payload) {
        QuestService questService = VeyloriaServerRuntime.instance().questService();
        if (questService == null) {
            sessions.remove(player.getUUID());
            return;
        }

        QuestActionResult result = questService.handleQuestNpcAction(player, instance, actionId, payload);
        if (!result.message().isBlank()) {
            player.sendSystemMessage(Component.literal(result.message()));
        }

        NpcDefinition definition = npcDb.getDefinition(instance.definitionId());
        if (definition != null) {
            ensureOfferDeck(level, data, instance, definition);
        }

        long nextNonce = nextNonce();
        InteractionSession nextSession = new InteractionSession(
            session.instanceId(),
            session.nodeId(),
            nextNonce,
            level.getGameTime() + SESSION_TTL_TICKS
        );
        sessions.put(player.getUUID(), nextSession);
        sendQuestList(player, instance, questService, nextSession);
    }

    private void sendQuestList(ServerPlayer player, NpcStoredInstance instance, QuestService questService, InteractionSession session) {
        List<QuestNpcEntry> entries = questService.getAvailableOffersForNpc(player, instance);
        if (entries.isEmpty()) {
            player.sendSystemMessage(Component.literal("Квестов пока нет."));
            return;
        }
        player.sendSystemMessage(Component.literal("Квесты NPC:"));
        for (QuestNpcEntry entry : entries) {
            MutableComponent line = Component.literal("- " + entry.title() + " (ур. " + entry.instanceLevel() + ")");
            if (!entry.progressText().isBlank()) {
                line.append(Component.literal(" " + entry.progressText()).withStyle(ChatFormatting.GRAY));
            }
            if (entry.actionType() == QuestNpcActionType.ACCEPT && entry.available()) {
                String url = buildActionUrl(QuestService.ACTION_QUEST_ACCEPT, entry.questId(), session.instanceId(), session.nonce());
                MutableComponent action = Component.literal(" [Принять]")
                    .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
                line.append(action);
            } else if (entry.actionType() == QuestNpcActionType.SUBMIT) {
                String url = buildActionUrl(QuestService.ACTION_QUEST_SUBMIT, entry.questId(), session.instanceId(), session.nonce());
                MutableComponent action = Component.literal(" [Сдать]")
                    .withStyle(style -> style
                        .withColor(ChatFormatting.GOLD)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
                line.append(action);
            } else if (!entry.reason().isBlank()) {
                line.append(Component.literal(" " + entry.reason()).withStyle(ChatFormatting.DARK_RED));
            }
            player.sendSystemMessage(line);
        }
    }

    private static long nextNonce() {
        long nonce = ThreadLocalRandom.current().nextLong();
        return nonce == 0L ? 1L : nonce;
    }

    public record ActionContext(
        NpcStoredInstance instance,
        NpcDefinition definition,
        NpcInteractionNode node,
        NpcInteractionAction action,
        InteractionSession session
    ) {
    }

    private record InteractionSession(String instanceId, String nodeId, long nonce, long expiresAtTick) {
    }

    private record RecentInteract(UUID npcId, long tick) {
    }
}
