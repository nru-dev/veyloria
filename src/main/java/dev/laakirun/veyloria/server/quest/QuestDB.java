package dev.laakirun.veyloria.server.quest;

import dev.laakirun.veyloria.common.VeyloriaConstants;
import dev.laakirun.veyloria.server.npc.XpReward;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class QuestDB {
    private final Map<ResourceLocation, QuestDefinition> definitions = new LinkedHashMap<>();

    public void registerBuiltins() {
        definitions.clear();
        ResourceLocation npcId = ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "test_npc");

        ResourceLocation quest1Id = ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "test_chain_1_kill_neutral");
        ResourceLocation quest2Id = ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "test_chain_2_kill_hostile");
        ResourceLocation quest3Id = ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "test_chain_3_combat_10s");

        QuestDefinition quest1 = QuestDefinition.builder(quest1Id)
            .title("Испытание: Нейтралы")
            .description("Устраните 5 нейтральных существ")
            .locationId(ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "zone_1"))
            .repeatPolicy(QuestRepeatPolicy.once())
            .offerable(true)
            .nextQuestId(quest2Id)
            .chainId("test_chain")
            .giverNpcDefinitionId(npcId)
            .turnInNpcDefinitionId(npcId)
            .levelPolicy(QuestLevelPolicy.scaledToLocation())
            .addObjective(new QuestObjectiveDefinition(
                QuestObjectiveHandlersRegistry.KILL_BY_DISPOSITION,
                killParams("NEUTRAL", 5, ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "zone_1")),
                QuestShareMode.NEARBY_PARTY,
                "Убейте 5 нейтральных",
                "",
                5
            ))
            .addReward(new CurrencyReward("copper", 100))
            .build();

        QuestDefinition quest2 = QuestDefinition.builder(quest2Id)
            .title("Испытание: Враждебные")
            .description("Устраните 5 враждебных существ")
            .locationId(ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "zone_1"))
            .repeatPolicy(QuestRepeatPolicy.once())
            .offerable(false)
            .parentQuestId(quest1Id)
            .nextQuestId(quest3Id)
            .chainId("test_chain")
            .giverNpcDefinitionId(npcId)
            .turnInNpcDefinitionId(npcId)
            .levelPolicy(QuestLevelPolicy.scaledToLocation())
            .addObjective(new QuestObjectiveDefinition(
                QuestObjectiveHandlersRegistry.KILL_BY_DISPOSITION,
                killParams("HOSTILE", 5, ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "zone_1")),
                QuestShareMode.NEARBY_PARTY,
                "Убейте 5 враждебных",
                "",
                5
            ))
            .addReward(new XpReward(1000))
            .build();

        QuestDefinition quest3 = QuestDefinition.builder(quest3Id)
            .title("Испытание: Бой 10 секунд")
            .description("Проведите 10 секунд в непрерывном бою")
            .locationId(ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "zone_1"))
            .repeatPolicy(QuestRepeatPolicy.once())
            .offerable(false)
            .parentQuestId(quest2Id)
            .chainId("test_chain")
            .giverNpcDefinitionId(npcId)
            .turnInNpcDefinitionId(npcId)
            .levelPolicy(QuestLevelPolicy.scaledToLocation())
            .addObjective(new QuestObjectiveDefinition(
                QuestObjectiveHandlersRegistry.COMBAT_TIME,
                combatParams(10, true, true, ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "zone_1")),
                QuestShareMode.PERSONAL,
                "Продержитесь в бою 10 секунд",
                "",
                10
            ))
            .addReward(new ItemReward("minecraft:diamond_sword", 1))
            .build();

        definitions.put(quest1.id(), quest1);
        definitions.put(quest2.id(), quest2);
        definitions.put(quest3.id(), quest3);
    }

    public QuestDefinition get(ResourceLocation id) {
        if (id == null) {
            return null;
        }
        return definitions.get(id);
    }

    public List<QuestDefinition> getAll() {
        return List.copyOf(definitions.values());
    }

    private static CompoundTag killParams(String disposition, int count, ResourceLocation locationId) {
        CompoundTag tag = new CompoundTag();
        tag.putString("disposition", disposition);
        tag.putInt("count", Math.max(1, count));
        if (locationId != null) {
            tag.putString("locationId", locationId.toString());
        }
        return tag;
    }

    private static CompoundTag combatParams(int seconds, boolean continuous, boolean requireAggro, ResourceLocation locationId) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("seconds", Math.max(1, seconds));
        tag.putBoolean("continuous", continuous);
        tag.putBoolean("requireAggro", requireAggro);
        if (locationId != null) {
            tag.putString("locationId", locationId.toString());
        }
        return tag;
    }
}
