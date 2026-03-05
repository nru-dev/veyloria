package dev.laakirun.veyloria.server.structure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;

final class GeneratedDungeonCaveSchematic {
    private static final int WIDTH = 21;
    private static final int HEIGHT = 12;
    private static final int LENGTH = 21;
    private static final int CENTER = 10;

    private GeneratedDungeonCaveSchematic() {
    }

    static StructureClipboardLoader.LoadedSchematic create() {
        Builder builder = new Builder(WIDTH, HEIGHT, LENGTH);
        layBase(builder);
        layCaveMouth(builder);
        layPortalMarker(builder);
        layTorches(builder);
        return builder.build();
    }

    private static void layBase(Builder builder) {
        builder.fill(0, 0, 0, WIDTH - 1, 0, LENGTH - 1, Blocks.STONE.defaultBlockState());
        builder.fill(1, 0, 1, WIDTH - 2, 0, LENGTH - 2, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        builder.fill(CENTER - 2, 0, CENTER - 7, CENTER + 2, 0, CENTER + 7, Blocks.STONE_BRICKS.defaultBlockState());
    }

    private static void layCaveMouth(Builder builder) {
        BlockState shell = Blocks.DEEPSLATE.defaultBlockState();
        BlockState trim = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        for (int y = 1; y <= 7; y++) {
            int radius = 8 - y;
            builder.fill(CENTER - radius, y, CENTER - radius,
                CENTER + radius, y, CENTER + radius, shell);
            if (y == 1 || y == 2) {
                builder.fill(CENTER - radius + 1, y, CENTER - radius + 1,
                    CENTER + radius - 1, y, CENTER + radius - 1, trim);
            }
        }

        builder.fill(CENTER - 4, 1, CENTER - 7, CENTER + 4, 6, CENTER + 1, Blocks.AIR.defaultBlockState());
        builder.fill(CENTER - 2, 1, CENTER + 1, CENTER + 2, 3, CENTER + 6, Blocks.AIR.defaultBlockState());
    }

    private static void layPortalMarker(Builder builder) {
        int frameMinX = CENTER - 1;
        int frameMaxX = CENTER + 2;
        int frameZ = CENTER + 4;
        int frameBottomY = 1;
        int frameTopY = 5;

        builder.fill(frameMinX, frameBottomY, frameZ, frameMaxX, frameBottomY, frameZ, Blocks.OBSIDIAN.defaultBlockState());
        builder.fill(frameMinX, frameTopY, frameZ, frameMaxX, frameTopY, frameZ, Blocks.OBSIDIAN.defaultBlockState());
        builder.fill(frameMinX, frameBottomY + 1, frameZ, frameMinX, frameTopY - 1, frameZ, Blocks.OBSIDIAN.defaultBlockState());
        builder.fill(frameMaxX, frameBottomY + 1, frameZ, frameMaxX, frameTopY - 1, frameZ, Blocks.OBSIDIAN.defaultBlockState());

        BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
        builder.fill(CENTER, 2, frameZ, CENTER + 1, 4, frameZ, portal);
        builder.fill(CENTER - 2, 1, CENTER + 5, CENTER + 3, 1, CENTER + 5, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        builder.set(CENTER, 1, CENTER + 7, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
    }

    private static void layTorches(Builder builder) {
        builder.set(CENTER - 3, 2, CENTER - 3, Blocks.TORCH.defaultBlockState());
        builder.set(CENTER + 3, 2, CENTER - 3, Blocks.TORCH.defaultBlockState());
        builder.set(CENTER - 3, 2, CENTER + 2, Blocks.TORCH.defaultBlockState());
        builder.set(CENTER + 3, 2, CENTER + 2, Blocks.TORCH.defaultBlockState());
    }

    private static final class Builder {
        private final int width;
        private final int height;
        private final int length;
        private final Map<Integer, BlockState> blocks = new LinkedHashMap<>();

        private Builder(int width, int height, int length) {
            this.width = width;
            this.height = height;
            this.length = length;
        }

        private void set(int x, int y, int z, BlockState state) {
            if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= length) {
                return;
            }
            int key = pack(x, y, z);
            if (state == null || state.isAir()) {
                blocks.remove(key);
                return;
            }
            blocks.put(key, state);
        }

        private void fill(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockState state) {
            int x1 = Math.min(minX, maxX);
            int y1 = Math.min(minY, maxY);
            int z1 = Math.min(minZ, maxZ);
            int x2 = Math.max(minX, maxX);
            int y2 = Math.max(minY, maxY);
            int z2 = Math.max(minZ, maxZ);
            for (int x = x1; x <= x2; x++) {
                for (int y = y1; y <= y2; y++) {
                    for (int z = z1; z <= z2; z++) {
                        set(x, y, z, state);
                    }
                }
            }
        }

        private StructureClipboardLoader.LoadedSchematic build() {
            List<StructureClipboardLoader.PlacedBlock> placedBlocks = new ArrayList<>(blocks.size());
            for (Map.Entry<Integer, BlockState> entry : blocks.entrySet()) {
                int packed = entry.getKey();
                int x = packed % width;
                int tail = packed / width;
                int z = tail % length;
                int y = tail / length;
                placedBlocks.add(new StructureClipboardLoader.PlacedBlock(x, y, z, entry.getValue()));
            }
            return new StructureClipboardLoader.LoadedSchematic(width, height, length, List.copyOf(placedBlocks));
        }

        private int pack(int x, int y, int z) {
            return (y * length + z) * width + x;
        }
    }
}
