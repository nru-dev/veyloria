package dev.laakirun.veyloria.server.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.laakirun.veyloria.common.model.BaseStats;
import dev.laakirun.veyloria.common.model.CharacterProfile;
import dev.laakirun.veyloria.common.model.MobType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LevelServiceTest {
    @Test
    void usesExpectedFormulaCheckpoints() {
        LevelService service = new LevelService();

        assertEquals(110, service.xpToNextLevel(1));
        assertEquals(1052, service.xpToNextLevel(10));
        assertEquals(3223, service.xpToNextLevel(20));
        assertEquals(9947, service.xpToNextLevel(40));
        assertEquals(19187, service.xpToNextLevel(60));
        assertEquals(29930, service.xpToNextLevel(79));
    }

    @Test
    void appliesMobLevelDifferenceModifier() {
        LevelService service = new LevelService();

        assertEquals(0, service.computeMobExperience(20, 14, MobType.NORMAL, null, 1.0D));
        assertEquals(24, service.computeMobExperience(10, 8, MobType.NORMAL, null, 1.0D));
        assertEquals(108, service.computeMobExperience(10, 12, MobType.ELITE, null, 1.0D));
        assertEquals(594, service.computeMobExperience(10, 12, MobType.BOSS, null, 1.375D));
    }

    @Test
    void supportsMultipleLevelUpsInSingleGrant() {
        LevelService service = new LevelService();
        CharacterProfile profile = new CharacterProfile(1L, UUID.randomUUID(), "Tester", 1, 0, 0, 0, BaseStats.ZERO);

        ExperienceGainResult result = service.grantExperience(profile, 5000);

        assertTrue(result.leveledUp());
        assertTrue(profile.level() > 1);
        assertTrue(profile.xpTotal() == 5000);
    }
}
