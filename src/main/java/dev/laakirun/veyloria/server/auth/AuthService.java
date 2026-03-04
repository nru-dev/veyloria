package dev.laakirun.veyloria.server.auth;

import dev.laakirun.veyloria.server.db.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger("veyloria.auth");
    private static final String AUTO_AUTH_PASSWORD = "__veyloria_auto_auth_disabled__";

    private final DatabaseManager databaseManager;
    private final PasswordHasher passwordHasher;
    private final SessionManager sessionManager;

    public AuthService(DatabaseManager databaseManager, PasswordHasher passwordHasher, SessionManager sessionManager) {
        this.databaseManager = databaseManager;
        this.passwordHasher = passwordHasher;
        this.sessionManager = sessionManager;
    }

    public Optional<AccountRecord> findAccount(UUID minecraftUuid) {
        try (Connection connection = databaseManager.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM accounts WHERE minecraft_uuid = ?")) {
            statement.setString(1, minecraftUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new AccountRecord(
                        resultSet.getLong("id"),
                        UUID.fromString(resultSet.getString("minecraft_uuid")),
                        resultSet.getString("nickname"),
                        resultSet.getString("password_hash")
                    ));
                }
            }
            return Optional.empty();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load account", exception);
        }
    }

    public AuthResult register(UUID minecraftUuid, String nickname, String password) {
        if (password == null || password.length() < 4) {
            return AuthResult.failure("Пароль должен быть не короче 4 символов");
        }
        if (findAccount(minecraftUuid).isPresent()) {
            return AuthResult.failure("Аккаунт уже существует");
        }
        try (Connection connection = databaseManager.connection();
             PreparedStatement statement = connection.prepareStatement("""
                 INSERT INTO accounts(minecraft_uuid, nickname, password_hash, created_at, updated_at)
                 VALUES(?, ?, ?, ?, ?)
                 """)) {
            statement.setString(1, minecraftUuid.toString());
            statement.setString(2, nickname);
            statement.setString(3, passwordHasher.hash(password));
            statement.setString(4, Instant.now().toString());
            statement.setString(5, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to register account", exception);
        }
        LOGGER.info("Registered account for {}", nickname);
        return login(minecraftUuid, password, nickname);
    }

    public AccountRecord ensureAuthenticated(UUID minecraftUuid, String nickname) {
        AccountRecord account = findAccount(minecraftUuid)
            .map(existing -> withNickname(existing, nickname))
            .orElseGet(() -> createAutoAccount(minecraftUuid, nickname));
        sessionManager.register(account.id(), minecraftUuid);
        return account;
    }

    public AuthResult login(UUID minecraftUuid, String password, String nickname) {
        Optional<AccountRecord> optional = findAccount(minecraftUuid);
        if (optional.isEmpty()) {
            return AuthResult.failure("Аккаунт не найден");
        }
        AccountRecord record = optional.get();
        if (!passwordHasher.verify(password, record.passwordHash())) {
            return AuthResult.failure("Неверный пароль");
        }
        if (sessionManager.hasActiveSession(record.id(), minecraftUuid)) {
            return AuthResult.failure("Аккаунт уже находится в игре");
        }
        updateNickname(record.id(), nickname);
        AccountRecord updated = new AccountRecord(record.id(), record.minecraftUuid(), nickname, record.passwordHash());
        sessionManager.register(updated.id(), minecraftUuid);
        LOGGER.info("Login success for {}", nickname);
        return AuthResult.success(updated);
    }

    public void logout(UUID playerUuid) {
        sessionManager.unregister(playerUuid);
    }

    public SessionManager sessionManager() {
        return sessionManager;
    }

    private AccountRecord createAutoAccount(UUID minecraftUuid, String nickname) {
        try (Connection connection = databaseManager.connection();
             PreparedStatement statement = connection.prepareStatement("""
                 INSERT INTO accounts(minecraft_uuid, nickname, password_hash, created_at, updated_at)
                 VALUES(?, ?, ?, ?, ?)
                 """)) {
            statement.setString(1, minecraftUuid.toString());
            statement.setString(2, nickname);
            statement.setString(3, passwordHasher.hash(AUTO_AUTH_PASSWORD));
            statement.setString(4, Instant.now().toString());
            statement.setString(5, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to create auto account", exception);
        }
        LOGGER.info("Auto-created account for {}", nickname);
        return findAccount(minecraftUuid)
            .orElseThrow(() -> new IllegalStateException("Auto-created account is missing for " + minecraftUuid));
    }

    private AccountRecord withNickname(AccountRecord record, String nickname) {
        if (record.nickname().equals(nickname)) {
            return record;
        }
        updateNickname(record.id(), nickname);
        return new AccountRecord(record.id(), record.minecraftUuid(), nickname, record.passwordHash());
    }

    private void updateNickname(long accountId, String nickname) {
        try (Connection connection = databaseManager.connection();
             PreparedStatement statement = connection.prepareStatement("UPDATE accounts SET nickname = ?, updated_at = ? WHERE id = ?")) {
            statement.setString(1, nickname);
            statement.setString(2, Instant.now().toString());
            statement.setLong(3, accountId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update nickname", exception);
        }
    }

    public record AuthResult(boolean success, String message, AccountRecord account) {
        public static AuthResult success(AccountRecord account) {
            return new AuthResult(true, "OK", account);
        }

        public static AuthResult failure(String message) {
            return new AuthResult(false, message, null);
        }
    }
}
