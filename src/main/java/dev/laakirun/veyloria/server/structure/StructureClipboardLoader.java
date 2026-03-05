package dev.laakirun.veyloria.server.structure;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;

interface StructureClipboardLoader {
    LoadedSchematic load(Path path) throws IOException;

    record LoadedSchematic(int width, int height, int length, List<PlacedBlock> blocks) {
    }

    record PlacedBlock(int x, int y, int z, BlockState state) {
    }
}
