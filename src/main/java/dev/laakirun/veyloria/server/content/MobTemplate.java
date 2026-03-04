package dev.laakirun.veyloria.server.content;

import dev.laakirun.veyloria.common.model.HostilityType;
import dev.laakirun.veyloria.common.model.MobType;

public record MobTemplate(
    long id,
    String code,
    String name,
    MobType mobType,
    int level,
    String entityModel,
    HostilityType hostilityType,
    double baseDamage,
    double baseHp,
    double moveSpeed,
    double attackSpeed,
    double aggroRadius,
    double leashRadius,
    Long lootTableId,
    int currencyMin,
    int currencyMax,
    Integer xpOverride,
    boolean enabled
) {
}
