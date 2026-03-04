package dev.laakirun.veyloria.common.model;

import java.util.UUID;

public final class CharacterProfile {
    private final long accountId;
    private final UUID minecraftUuid;
    private final String nickname;
    private int level;
    private int xpCurrent;
    private int xpTotal;
    private int currencyCopper;
    private BaseStats baseStats;

    public CharacterProfile(
        long accountId,
        UUID minecraftUuid,
        String nickname,
        int level,
        int xpCurrent,
        int xpTotal,
        int currencyCopper,
        BaseStats baseStats
    ) {
        this.accountId = accountId;
        this.minecraftUuid = minecraftUuid;
        this.nickname = nickname;
        this.level = level;
        this.xpCurrent = xpCurrent;
        this.xpTotal = xpTotal;
        this.currencyCopper = currencyCopper;
        this.baseStats = baseStats;
    }

    public long accountId() {
        return accountId;
    }

    public UUID minecraftUuid() {
        return minecraftUuid;
    }

    public String nickname() {
        return nickname;
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int xpCurrent() {
        return xpCurrent;
    }

    public void setXpCurrent(int xpCurrent) {
        this.xpCurrent = xpCurrent;
    }

    public int xpTotal() {
        return xpTotal;
    }

    public void setXpTotal(int xpTotal) {
        this.xpTotal = xpTotal;
    }

    public int currencyCopper() {
        return currencyCopper;
    }

    public void addCurrency(int amount) {
        currencyCopper += amount;
    }

    public BaseStats baseStats() {
        return baseStats;
    }

    public void setBaseStats(BaseStats baseStats) {
        this.baseStats = baseStats;
    }
}
