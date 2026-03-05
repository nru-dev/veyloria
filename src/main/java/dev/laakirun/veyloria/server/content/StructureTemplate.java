package dev.laakirun.veyloria.server.content;

public record StructureTemplate(
    long id,
    String code,
    String name,
    String structureType,
    String schematicFile,
    int sizeX,
    int sizeY,
    int sizeZ,
    boolean enabled
) {
}
