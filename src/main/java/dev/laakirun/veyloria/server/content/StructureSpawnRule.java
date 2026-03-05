package dev.laakirun.veyloria.server.content;

public record StructureSpawnRule(
    long id,
    long structureTemplateId,
    String dimension,
    int zoneMin,
    int zoneMax,
    int countMinPerZone,
    int countMaxPerZone,
    double roadDistanceMin,
    double roadDistanceMax,
    double minDistanceBetween,
    String nearSpawnRulesJson,
    String insideSpawnRulesJson,
    boolean enabled
) {
}
