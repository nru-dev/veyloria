package dev.laakirun.veyloria.server.npc;

import dev.laakirun.veyloria.common.VeyloriaConstants;
import dev.laakirun.veyloria.common.npc.NpcAppearance;
import dev.laakirun.veyloria.common.npc.NpcRole;
import dev.laakirun.veyloria.common.npc.NpcStats;
import dev.laakirun.veyloria.server.quest.QuestService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class NpcDB {
    private final Map<ResourceLocation, NpcDefinition> definitions = new LinkedHashMap<>();

    public void registerBuiltins() {
        definitions.clear();
        ResourceLocation testNpcId = ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "test_npc");

        NpcInteractionAction ackAction = new NpcInteractionAction(
            "ack",
            "",
            "Понял",
            "quest_hub",
            List.of(new XpReward(500))
        );
        NpcInteractionAction questListAction = new NpcInteractionAction(
            QuestService.ACTION_QUEST_LIST,
            "",
            "Показать квесты",
            "",
            List.of()
        );

        NpcInteractionNode rootNode = new NpcInteractionNode(
            "root",
            "Тестовый NPC: фундамент системы NPC готов.",
            List.of(ackAction)
        );
        NpcInteractionNode questHubNode = new NpcInteractionNode(
            "quest_hub",
            "Выберите действие:",
            List.of(questListAction)
        );

        NpcInteractionGraph graph = new NpcInteractionGraph("root", Map.of(
            rootNode.nodeId(), rootNode,
            questHubNode.nodeId(), questHubNode
        ));

        NpcDefinition testNpc = NpcDefinition.builder(testNpcId)
            .displayName("Тестовый NPC")
            .nameKey("npc.veyloria.test_npc")
            .addRole(NpcRole.GENERIC)
            .appearance(NpcAppearance.WITHER)
            .stats(new NpcStats(120.0D, 8.0D, 1.0D))
            .respawnSeconds(60)
            .interactionGraph(graph)
            .questPoolIds(List.of("test_chain"))
            .questChainIds(List.of("test_chain"))
            .tradeTableId("")
            .forgeTableId("")
            .build();
        definitions.put(testNpc.id(), testNpc);
    }

    public NpcDefinition getDefinition(ResourceLocation id) {
        if (id == null) {
            return null;
        }
        return definitions.get(id);
    }

    public List<NpcDefinition> getAllDefinitions() {
        return List.copyOf(definitions.values());
    }
}
