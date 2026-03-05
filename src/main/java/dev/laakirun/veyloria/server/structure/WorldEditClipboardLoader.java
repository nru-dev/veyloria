package dev.laakirun.veyloria.server.structure;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.regions.Region;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;

final class WorldEditClipboardLoader implements StructureClipboardLoader {
    @Override
    public LoadedSchematic load(Path path) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(path.toFile());
        if (format == null) {
            throw new IOException("Unsupported clipboard format: " + path);
        }
        try (InputStream input = Files.newInputStream(path);
             ClipboardReader reader = format.getReader(input)) {
            Clipboard clipboard = reader.read();
            Region region = clipboard.getRegion();
            BlockVector3 min = region.getMinimumPoint();
            List<PlacedBlock> blocks = new ArrayList<>();
            for (BlockVector3 point : region) {
                com.sk89q.worldedit.world.block.BlockState state = clipboard.getBlock(point);
                if (state.getBlockType().getMaterial().isAir()) {
                    continue;
                }
                BlockState minecraftState = NeoForgeAdapter.adapt(state);
                blocks.add(new PlacedBlock(
                    point.x() - min.x(),
                    point.y() - min.y(),
                    point.z() - min.z(),
                    minecraftState
                ));
            }
            return new LoadedSchematic(region.getWidth(), region.getHeight(), region.getLength(), List.copyOf(blocks));
        }
    }
}
