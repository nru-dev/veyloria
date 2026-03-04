package dev.laakirun.veyloria.server.profile;

public record ExperienceGainResult(int gainedXp, int previousLevel, int newLevel, boolean leveledUp) {
}
