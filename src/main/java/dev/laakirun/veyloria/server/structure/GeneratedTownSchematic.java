package dev.laakirun.veyloria.server.structure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class GeneratedTownSchematic {
    private static final int WIDTH = 97;
    private static final int HEIGHT = 24;
    private static final int LENGTH = 97;
    private static final int CENTER = 48;

    private GeneratedTownSchematic() {
    }

    static StructureClipboardLoader.LoadedSchematic create() {
        Builder builder = new Builder(WIDTH, HEIGHT, LENGTH);
        layRoads(builder);
        layWalls(builder);
        layCentralPlaza(builder);
        layTownHall(builder);
        layResidentialQuarter(builder);
        layMarket(builder);
        layGreenery(builder);
        layLamps(builder);
        return builder.build();
    }

    private static void layRoads(Builder builder) {
        BlockState road = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState trim = Blocks.COBBLESTONE.defaultBlockState();
        builder.fill(0, 0, CENTER - 2, WIDTH - 1, 0, CENTER + 2, road);
        builder.fill(CENTER - 2, 0, 0, CENTER + 2, 0, LENGTH - 1, road);
        builder.fill(0, 0, CENTER - 3, WIDTH - 1, 0, CENTER - 3, trim);
        builder.fill(0, 0, CENTER + 3, WIDTH - 1, 0, CENTER + 3, trim);
        builder.fill(CENTER - 3, 0, 0, CENTER - 3, 0, LENGTH - 1, trim);
        builder.fill(CENTER + 3, 0, 0, CENTER + 3, 0, LENGTH - 1, trim);
    }

    private static void layWalls(Builder builder) {
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState pillar = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        int gateMin = CENTER - 4;
        int gateMax = CENTER + 4;
        for (int y = 0; y <= 5; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (x >= gateMin && x <= gateMax) {
                    continue;
                }
                builder.set(x, y, 0, wall);
                builder.set(x, y, LENGTH - 1, wall);
            }
            for (int z = 0; z < LENGTH; z++) {
                if (z >= gateMin && z <= gateMax) {
                    continue;
                }
                builder.set(0, y, z, wall);
                builder.set(WIDTH - 1, y, z, wall);
            }
        }

        for (int y = 0; y <= 7; y++) {
            builder.set(0, y, 0, pillar);
            builder.set(0, y, LENGTH - 1, pillar);
            builder.set(WIDTH - 1, y, 0, pillar);
            builder.set(WIDTH - 1, y, LENGTH - 1, pillar);
        }

        layGate(builder, CENTER - 4, CENTER + 4, 0, true);
        layGate(builder, CENTER - 4, CENTER + 4, LENGTH - 1, true);
        layGate(builder, CENTER - 4, CENTER + 4, 0, false);
        layGate(builder, CENTER - 4, CENTER + 4, WIDTH - 1, false);
    }

    private static void layGate(Builder builder, int min, int max, int fixed, boolean alongX) {
        BlockState frame = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState arch = Blocks.DEEPSLATE_TILES.defaultBlockState();
        for (int y = 0; y <= 7; y++) {
            if (alongX) {
                builder.set(min, y, fixed, frame);
                builder.set(max, y, fixed, frame);
            } else {
                builder.set(fixed, y, min, frame);
                builder.set(fixed, y, max, frame);
            }
        }
        for (int y = 6; y <= 7; y++) {
            for (int i = min; i <= max; i++) {
                if (alongX) {
                    builder.set(i, y, fixed, arch);
                } else {
                    builder.set(fixed, y, i, arch);
                }
            }
        }
    }

    private static void layCentralPlaza(Builder builder) {
        BlockState plaza = Blocks.POLISHED_DIORITE.defaultBlockState();
        BlockState border = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState core = Blocks.SEA_LANTERN.defaultBlockState();

        builder.fill(CENTER - 8, 0, CENTER - 8, CENTER + 8, 0, CENTER + 8, plaza);
        builder.fill(CENTER - 9, 0, CENTER - 9, CENTER + 9, 0, CENTER + 9, border);
        builder.fill(CENTER - 3, 0, CENTER - 3, CENTER + 3, 0, CENTER + 3, border);
        builder.fill(CENTER - 2, 1, CENTER - 2, CENTER + 2, 1, CENTER + 2, water);
        builder.set(CENTER, 1, CENTER, core);
        builder.fill(CENTER, 2, CENTER, CENTER, 4, CENTER, Blocks.WATER.defaultBlockState());
        builder.fill(CENTER - 1, 5, CENTER - 1, CENTER + 1, 5, CENTER + 1, water);
    }

    private static void layTownHall(Builder builder) {
        int startX = CENTER - 12;
        int startZ = 8;
        int width = 25;
        int length = 18;

        builder.fill(startX, 0, startZ, startX + width - 1, 0, startZ + length - 1, Blocks.STONE_BRICKS.defaultBlockState());
        hollowBuilding(builder, startX, 1, startZ, width, length, 6, Blocks.SMOOTH_STONE.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState());
        steppedRoof(builder, startX - 1, startZ - 1, width + 2, length + 2, 7, Blocks.DEEPSLATE_TILES.defaultBlockState());

        int doorX = CENTER;
        builder.fill(doorX - 1, 1, startZ + length - 1, doorX + 1, 3, startZ + length - 1, Blocks.AIR.defaultBlockState());
        builder.fill(doorX - 3, 0, startZ + length, doorX + 3, 0, startZ + length + 2, Blocks.STONE_BRICK_STAIRS.defaultBlockState());
        builder.fill(startX + 2, 2, startZ + 3, startX + width - 3, 2, startZ + 3, Blocks.WHITE_STAINED_GLASS_PANE.defaultBlockState());
        builder.fill(startX + 2, 2, startZ + length - 4, startX + width - 3, 2, startZ + length - 4, Blocks.WHITE_STAINED_GLASS_PANE.defaultBlockState());
        builder.fill(CENTER - 1, 1, startZ + 6, CENTER + 1, 1, startZ + 8, Blocks.RED_CARPET.defaultBlockState());
    }

    private static void layResidentialQuarter(Builder builder) {
        int[][] houseOrigins = {
            { 9, 9 }, { 27, 9 }, { 58, 9 }, { 76, 9 },
            { 9, 27 }, { 27, 27 }, { 58, 27 }, { 76, 27 },
            { 9, 58 }, { 27, 58 }, { 58, 58 }, { 76, 58 },
            { 9, 76 }, { 27, 76 }, { 58, 76 }, { 76, 76 }
        };
        for (int[] origin : houseOrigins) {
            buildHouse(builder, origin[0], origin[1], 12, 10, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState());
        }
    }

    private static void buildHouse(Builder builder,
                                   int startX,
                                   int startZ,
                                   int width,
                                   int length,
                                   BlockState wallBlock,
                                   BlockState roofBlock) {
        builder.fill(startX, 0, startZ, startX + width - 1, 0, startZ + length - 1, Blocks.COBBLESTONE.defaultBlockState());
        hollowBuilding(builder, startX, 1, startZ, width, length, 4, wallBlock, Blocks.OAK_PLANKS.defaultBlockState());
        steppedRoof(builder, startX - 1, startZ - 1, width + 2, length + 2, 5, roofBlock);

        int midX = startX + width / 2;
        int doorZ = startZ + length - 1;
        builder.fill(midX, 1, doorZ, midX, 2, doorZ, Blocks.AIR.defaultBlockState());

        builder.set(startX + 2, 2, startZ, Blocks.WHITE_STAINED_GLASS_PANE.defaultBlockState());
        builder.set(startX + width - 3, 2, startZ, Blocks.WHITE_STAINED_GLASS_PANE.defaultBlockState());
        builder.set(startX, 2, startZ + 3, Blocks.WHITE_STAINED_GLASS_PANE.defaultBlockState());
        builder.set(startX + width - 1, 2, startZ + 3, Blocks.WHITE_STAINED_GLASS_PANE.defaultBlockState());
        builder.set(startX + 1, 1, startZ + 1, Blocks.CRAFTING_TABLE.defaultBlockState());
        builder.set(startX + width - 2, 1, startZ + length - 2, Blocks.BARREL.defaultBlockState());
        builder.set(startX + width - 3, 1, startZ + 2, Blocks.BRICKS.defaultBlockState());
        builder.set(startX + width - 3, 2, startZ + 2, Blocks.BRICKS.defaultBlockState());
    }

    private static void hollowBuilding(Builder builder,
                                       int startX,
                                       int startY,
                                       int startZ,
                                       int width,
                                       int length,
                                       int height,
                                       BlockState wallBlock,
                                       BlockState floorBlock) {
        builder.fill(startX, startY - 1, startZ, startX + width - 1, startY - 1, startZ + length - 1, floorBlock);
        for (int y = startY; y <= startY + height - 1; y++) {
            for (int x = startX; x < startX + width; x++) {
                for (int z = startZ; z < startZ + length; z++) {
                    boolean border = x == startX || x == startX + width - 1 || z == startZ || z == startZ + length - 1;
                    if (border) {
                        builder.set(x, y, z, wallBlock);
                    } else {
                        builder.set(x, y, z, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        for (int y = startY; y <= startY + height - 1; y++) {
            builder.set(startX, y, startZ, Blocks.SPRUCE_LOG.defaultBlockState());
            builder.set(startX + width - 1, y, startZ, Blocks.SPRUCE_LOG.defaultBlockState());
            builder.set(startX, y, startZ + length - 1, Blocks.SPRUCE_LOG.defaultBlockState());
            builder.set(startX + width - 1, y, startZ + length - 1, Blocks.SPRUCE_LOG.defaultBlockState());
        }
    }

    private static void steppedRoof(Builder builder,
                                    int startX,
                                    int startZ,
                                    int width,
                                    int length,
                                    int startY,
                                    BlockState roofBlock) {
        int layers = Math.max(2, Math.min(width, length) / 4);
        for (int i = 0; i < layers; i++) {
            int x1 = startX + i;
            int z1 = startZ + i;
            int x2 = startX + width - 1 - i;
            int z2 = startZ + length - 1 - i;
            if (x1 > x2 || z1 > z2) {
                break;
            }
            builder.fill(x1, startY + i, z1, x2, startY + i, z2, roofBlock);
        }
    }

    private static void layMarket(Builder builder) {
        buildStall(builder, CENTER - 18, CENTER - 15, Blocks.RED_WOOL.defaultBlockState());
        buildStall(builder, CENTER + 10, CENTER - 15, Blocks.BLUE_WOOL.defaultBlockState());
        buildStall(builder, CENTER - 18, CENTER + 9, Blocks.GREEN_WOOL.defaultBlockState());
        buildStall(builder, CENTER + 10, CENTER + 9, Blocks.YELLOW_WOOL.defaultBlockState());
    }

    private static void buildStall(Builder builder, int startX, int startZ, BlockState cloth) {
        builder.fill(startX, 0, startZ, startX + 7, 0, startZ + 5, Blocks.OAK_PLANKS.defaultBlockState());
        builder.fill(startX, 1, startZ, startX, 3, startZ, Blocks.OAK_FENCE.defaultBlockState());
        builder.fill(startX + 7, 1, startZ, startX + 7, 3, startZ, Blocks.OAK_FENCE.defaultBlockState());
        builder.fill(startX, 1, startZ + 5, startX, 3, startZ + 5, Blocks.OAK_FENCE.defaultBlockState());
        builder.fill(startX + 7, 1, startZ + 5, startX + 7, 3, startZ + 5, Blocks.OAK_FENCE.defaultBlockState());
        builder.fill(startX, 4, startZ, startX + 7, 4, startZ + 5, cloth);
        builder.set(startX + 1, 1, startZ + 2, Blocks.BARREL.defaultBlockState());
        builder.set(startX + 3, 1, startZ + 2, Blocks.CHEST.defaultBlockState());
        builder.set(startX + 5, 1, startZ + 2, Blocks.CRAFTING_TABLE.defaultBlockState());
    }

    private static void layGreenery(Builder builder) {
        int[][] trees = {
            { 16, 16 }, { 37, 16 }, { 59, 16 }, { 80, 16 },
            { 16, 37 }, { 80, 37 },
            { 16, 59 }, { 80, 59 },
            { 16, 80 }, { 37, 80 }, { 59, 80 }, { 80, 80 }
        };
        for (int[] tree : trees) {
            buildTree(builder, tree[0], tree[1], 5);
        }
    }

    private static void buildTree(Builder builder, int x, int z, int height) {
        for (int y = 1; y <= height; y++) {
            builder.set(x, y, z, Blocks.OAK_LOG.defaultBlockState());
        }
        for (int y = height - 1; y <= height + 2; y++) {
            int radius = y >= height + 1 ? 1 : 2;
            builder.fill(x - radius, y, z - radius, x + radius, y, z + radius, Blocks.OAK_LEAVES.defaultBlockState());
        }
    }

    private static void layLamps(Builder builder) {
        int[][] lamps = {
            { CENTER - 20, CENTER - 20 }, { CENTER, CENTER - 20 }, { CENTER + 20, CENTER - 20 },
            { CENTER - 20, CENTER }, { CENTER + 20, CENTER },
            { CENTER - 20, CENTER + 20 }, { CENTER, CENTER + 20 }, { CENTER + 20, CENTER + 20 }
        };
        for (int[] lamp : lamps) {
            int x = lamp[0];
            int z = lamp[1];
            builder.fill(x, 1, z, x, 4, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
            builder.set(x, 5, z, Blocks.LANTERN.defaultBlockState());
        }
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
