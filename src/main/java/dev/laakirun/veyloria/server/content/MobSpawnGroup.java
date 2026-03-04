package dev.laakirun.veyloria.server.content;

public record MobSpawnGroup(
    long id,
    long mobTemplateId,
    String dimension,
    double centerX,
    double centerY,
    double centerZ,
    double radiusX,
    double radiusZ,
    int minAlive,
    int maxAlive,
    int respawnSeconds,
    int packSizeMin,
    int packSizeMax,
    double packSpreadMin,
    double packSpreadMax,
    boolean enabled
) {
}
