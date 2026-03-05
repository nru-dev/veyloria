package dev.laakirun.veyloria.server.location;

import java.util.Random;

public record LocationLevelRange(int minLevel, int maxLevel) {
    public LocationLevelRange {
        minLevel = Math.max(1, minLevel);
        maxLevel = Math.max(minLevel, maxLevel);
    }

    public int randomLevel(long seed) {
        if (minLevel == maxLevel) {
            return minLevel;
        }
        Random random = new Random(seed);
        return minLevel + random.nextInt(maxLevel - minLevel + 1);
    }
}
