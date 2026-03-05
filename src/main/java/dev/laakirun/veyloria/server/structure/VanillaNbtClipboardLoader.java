package dev.laakirun.veyloria.server.structure;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

final class VanillaNbtClipboardLoader implements StructureClipboardLoader {
    @Override
    public LoadedSchematic load(Path path) throws IOException {
        CompoundTag root = readNbt(path);
        int[] declaredSize = readSize(root);
        List<BlockState> palette = readPalette(root);
        ListTag blocksTag = root.getList("blocks", Tag.TAG_COMPOUND);

        List<RawBlock> rawBlocks = new ArrayList<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (int index = 0; index < blocksTag.size(); index++) {
            CompoundTag blockTag = blocksTag.getCompound(index);
            ListTag posTag = blockTag.getList("pos", Tag.TAG_INT);
            if (posTag.size() < 3) {
                continue;
            }
            int paletteIndex = blockTag.getInt("state");
            if (paletteIndex < 0 || paletteIndex >= palette.size()) {
                continue;
            }
            BlockState state = palette.get(paletteIndex);
            if (state.isAir()) {
                continue;
            }
            int x = posTag.getInt(0);
            int y = posTag.getInt(1);
            int z = posTag.getInt(2);
            rawBlocks.add(new RawBlock(x, y, z, state));

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        if (rawBlocks.isEmpty()) {
            return new LoadedSchematic(
                Math.max(1, declaredSize[0]),
                Math.max(1, declaredSize[1]),
                Math.max(1, declaredSize[2]),
                List.of()
            );
        }

        int actualWidth = maxX - minX + 1;
        int actualHeight = maxY - minY + 1;
        int actualLength = maxZ - minZ + 1;
        int width = Math.max(Math.max(1, declaredSize[0]), actualWidth);
        int height = Math.max(Math.max(1, declaredSize[1]), actualHeight);
        int length = Math.max(Math.max(1, declaredSize[2]), actualLength);

        List<PlacedBlock> placedBlocks = new ArrayList<>(rawBlocks.size());
        for (RawBlock raw : rawBlocks) {
            placedBlocks.add(new PlacedBlock(
                raw.x() - minX,
                raw.y() - minY,
                raw.z() - minZ,
                raw.state()
            ));
        }
        return new LoadedSchematic(width, height, length, List.copyOf(placedBlocks));
    }

    private static CompoundTag readNbt(Path path) throws IOException {
        try {
            return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        } catch (IOException compressedReadException) {
            return NbtIo.read(path);
        }
    }

    private static int[] readSize(CompoundTag root) {
        ListTag sizeTag = root.getList("size", Tag.TAG_INT);
        if (sizeTag.size() >= 3) {
            return new int[] { sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2) };
        }
        return new int[] { 0, 0, 0 };
    }

    private static List<BlockState> readPalette(CompoundTag root) throws IOException {
        ListTag paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
        if (paletteTag.isEmpty()) {
            ListTag palettes = root.getList("palettes", Tag.TAG_LIST);
            if (!palettes.isEmpty()) {
                paletteTag = palettes.getList(0);
            }
        }
        if (paletteTag.isEmpty()) {
            throw new IOException("NBT structure palette is empty");
        }

        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int index = 0; index < paletteTag.size(); index++) {
            CompoundTag stateTag = paletteTag.getCompound(index);
            palette.add(parseState(stateTag));
        }
        return palette;
    }

    private static BlockState parseState(CompoundTag stateTag) throws IOException {
        String blockName = stateTag.getString("Name");
        ResourceLocation location = ResourceLocation.tryParse(blockName);
        if (location == null) {
            throw new IOException("Invalid block id in NBT structure palette: " + blockName);
        }
        Block block = BuiltInRegistries.BLOCK.get(location);
        BlockState blockState = block.defaultBlockState();
        if (!stateTag.contains("Properties", Tag.TAG_COMPOUND)) {
            return blockState;
        }
        CompoundTag propertiesTag = stateTag.getCompound("Properties");
        for (String key : propertiesTag.getAllKeys()) {
            Property<?> property = blockState.getBlock().getStateDefinition().getProperty(key);
            if (property == null) {
                continue;
            }
            blockState = applyProperty(blockState, property, propertiesTag.getString(key));
        }
        return blockState;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static BlockState applyProperty(BlockState state, Property property, String valueRaw) {
        Optional<? extends Comparable> parsed = property.getValue(valueRaw);
        if (parsed.isEmpty()) {
            return state;
        }
        return state.setValue(property, parsed.get());
    }

    private record RawBlock(int x, int y, int z, BlockState state) {
    }
}
