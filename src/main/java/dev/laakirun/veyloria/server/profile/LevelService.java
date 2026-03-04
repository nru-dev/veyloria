package dev.laakirun.veyloria.server.profile;

import dev.laakirun.veyloria.common.VeyloriaConstants;
import dev.laakirun.veyloria.common.model.CharacterProfile;
import dev.laakirun.veyloria.common.model.MobType;

public final class LevelService {
    public int xpToNextLevel(int level) {
        if (level >= VeyloriaConstants.MAX_LEVEL) {
            return 0;
        }
        return (int) Math.round(110 + 28 * Math.pow(level - 1, 1.6));
    }

    public int computeMobExperience(int playerLevel, int mobLevel, MobType mobType, Integer xpOverride, double xpRate) {
        double levelModifier = levelDiffModifier(mobLevel - playerLevel);
        if (levelModifier <= 0.0D) {
            return 0;
        }
        int baseXp = xpOverride != null ? xpOverride : mobType.baseXp();
        return (int) Math.round(baseXp * mobLevel * levelModifier * xpRate);
    }

    public ExperienceGainResult grantExperience(CharacterProfile profile, int amount) {
        int previousLevel = profile.level();
        if (amount <= 0 || profile.level() >= VeyloriaConstants.MAX_LEVEL) {
            return new ExperienceGainResult(0, previousLevel, profile.level(), false);
        }
        profile.setXpCurrent(profile.xpCurrent() + amount);
        profile.setXpTotal(profile.xpTotal() + amount);
        while (profile.level() < VeyloriaConstants.MAX_LEVEL) {
            int required = xpToNextLevel(profile.level());
            if (profile.xpCurrent() < required) {
                break;
            }
            profile.setXpCurrent(profile.xpCurrent() - required);
            profile.setLevel(profile.level() + 1);
        }
        if (profile.level() >= VeyloriaConstants.MAX_LEVEL) {
            profile.setXpCurrent(0);
        }
        return new ExperienceGainResult(amount, previousLevel, profile.level(), previousLevel != profile.level());
    }

    public double levelDiffModifier(int delta) {
        if (delta >= 5) {
            return Math.min(1.5D, 1.0D + 0.1D * (delta - 4));
        }
        if (delta >= -2) {
            return 1.0D;
        }
        if (delta >= -5) {
            return 0.2D;
        }
        return 0.0D;
    }
}
