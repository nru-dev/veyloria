package dev.laakirun.veyloria.server.auth;

import java.util.UUID;

public record AccountRecord(long id, UUID minecraftUuid, String nickname, String passwordHash) {
}
