package dev.laakirun.veyloria.common.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

public final class VeyloriaPaths {
    private static final String ROOT_DIR = "veyloria";

    private VeyloriaPaths() {
    }

    public static Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    public static Path rootDir() {
        return ensure(gameDir().resolve(ROOT_DIR));
    }

    public static Path configDir() {
        return ensure(gameDir().resolve("config").resolve(ROOT_DIR));
    }

    public static Path dataDir() {
        return ensure(gameDir().resolve("data").resolve(ROOT_DIR));
    }

    public static Path logsDir() {
        return ensure(gameDir().resolve("logs").resolve(ROOT_DIR));
    }

    public static Path ensure(Path path) {
        try {
            Files.createDirectories(path);
            return path;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create path " + path, exception);
        }
    }

    public static Path resolveGameRelative(String path) {
        Path resolved = gameDir().resolve(path).normalize();
        Path parent = resolved.getParent();
        if (parent != null) {
            ensure(parent);
        }
        return resolved;
    }
}
