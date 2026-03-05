package dev.laakirun.veyloria.server.structure;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

final class MultiFormatClipboardLoader implements StructureClipboardLoader {
    private final StructureClipboardLoader nbtLoader;
    private final StructureClipboardLoader schemLoader;

    MultiFormatClipboardLoader(StructureClipboardLoader nbtLoader, StructureClipboardLoader schemLoader) {
        this.nbtLoader = nbtLoader;
        this.schemLoader = schemLoader;
    }

    @Override
    public LoadedSchematic load(Path path) throws IOException {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".nbt")) {
            return nbtLoader.load(path);
        }
        if (fileName.endsWith(".schem") || fileName.endsWith(".schematic")) {
            if (schemLoader == null) {
                throw new IOException("WorldEdit loader is unavailable for " + path);
            }
            return schemLoader.load(path);
        }
        if (schemLoader != null) {
            try {
                return schemLoader.load(path);
            } catch (IOException ignored) {
                // Fall through to NBT loader.
            }
        }
        return nbtLoader.load(path);
    }
}
