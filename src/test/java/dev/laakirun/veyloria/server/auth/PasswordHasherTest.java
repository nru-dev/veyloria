package dev.laakirun.veyloria.server.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {
    @Test
    void hashesAndVerifiesPassword() {
        PasswordHasher hasher = new PasswordHasher();
        String hash = hasher.hash("hunter2");

        assertTrue(hasher.verify("hunter2", hash));
        assertFalse(hasher.verify("wrong", hash));
    }
}
