package dev.laakirun.veyloria.server.game;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TestWorldLayoutService {
    private static final Logger LOGGER = LoggerFactory.getLogger("veyloria.spawn");

    public static final String OVERWORLD_DIMENSION = "minecraft:overworld";
    public static final int ZONE_COUNT = 7;
    public static final int ZONE_HALF_WIDTH = 240;
    public static final int ZONE_LENGTH = 640;
    public static final int FIRST_ZONE_SOUTH_Z = 320;
    public static final int ROAD_CENTER_X = 0;
    public static final int ROAD_HALF_WIDTH = 2;
    public static final int SAFE_HALF_WIDTH = 7;
    public static final int SPAWN_Z = FIRST_ZONE_SOUTH_Z - 60;
    public static final int FLAT_BEDROCK_Y = 0;
    public static final int FLAT_DIRT_MIN_Y = 1;
    public static final int FLAT_DIRT_MAX_Y = 2;
    public static final int FLAT_GRASS_Y = 3;
    public static final int FLAT_SPAWN_Y = FLAT_GRASS_Y + 1;

    private static final String[] ZONE_LABELS = {
        "1-10", "10-25", "25-35", "35-45", "45-60", "60-70", "70-80"
    };

    private static final int DECORATION_CHUNK_RADIUS = 6;
    private static final int MAX_CHUNKS_PER_TICK = 12;
    private static final int SEPARATOR_HALF_THICKNESS = 1;
    private static final int SIGN_OFFSET_X = SAFE_HALF_WIDTH + 2;
    private static final BlockState ROAD_BLOCK = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState SEPARATOR_BLOCK = Blocks.WHITE_WOOL.defaultBlockState();
    private static final BlockState FENCE_BLOCK = Blocks.OAK_FENCE.defaultBlockState();
    private static final String TAG_STARTER_SPAWNED = "veyloria_starter_spawned";

    private final Set<Long> decoratedOverworldChunks = ConcurrentHashMap.newKeySet();
    private boolean worldConfigured;

    public void onServerStarting(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        configureWorld(overworld, server);
        decorateAroundSpawn(overworld);
    }

    public void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        configureWorld(overworld, server);
        decorateActiveChunks(overworld);
    }

    public static boolean isInSafeCorridor(String dimension, double x, double z) {
        if (!OVERWORLD_DIMENSION.equals(dimension)) {
            return false;
        }
        if (!isManagedNorthSouthBand(z)) {
            return false;
        }
        return Math.abs(x - ROAD_CENTER_X) <= SAFE_HALF_WIDTH;
    }

    public static boolean isSeparatorLine(String dimension, double z) {
        if (!OVERWORLD_DIMENSION.equals(dimension)) {
            return false;
        }
        return isSeparatorLineZ((int) Math.round(z));
    }

    public static int zoneIndex(String dimension, double z) {
        if (!OVERWORLD_DIMENSION.equals(dimension) || !isManagedNorthSouthBand(z)) {
            return -1;
        }
        int index = (int) Math.floor((FIRST_ZONE_SOUTH_Z - z) / (double) ZONE_LENGTH) + 1;
        if (index < 1 || index > ZONE_COUNT) {
            return -1;
        }
        return index;
    }

    public static String zoneLabel(int zoneIndex) {
        if (zoneIndex < 1 || zoneIndex > ZONE_COUNT) {
            return "Неизвестно";
        }
        return ZONE_LABELS[zoneIndex - 1];
    }

    public void ensureStarterSpawn(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!OVERWORLD_DIMENSION.equals(level.dimension().location().toString())) {
            return;
        }
        boolean firstJoin = !player.getPersistentData().getBoolean(TAG_STARTER_SPAWNED);
        boolean belowSurface = player.getY() < FLAT_SPAWN_Y - 0.5D;
        if (!firstJoin && !belowSurface) {
            return;
        }
        decorateAroundSpawn(level);
        BlockPos spawnPos = starterSpawnPos();
        player.teleportTo(level, spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 180.0F, 0.0F);
        player.setRespawnPosition(level.dimension(), spawnPos, 180.0F, true, false);
        player.getPersistentData().putBoolean(TAG_STARTER_SPAWNED, true);
    }

    private void configureWorld(ServerLevel overworld, MinecraftServer server) {
        if (worldConfigured) {
            return;
        }
        overworld.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        BlockPos spawnPos = starterSpawnPos();
        overworld.setDefaultSpawnPos(spawnPos, 180.0F);
        worldConfigured = true;
        LOGGER.info("Configured test layout in overworld: zones={}, zone_length={}, zone_width={}, safe_band={}, spawn=({}, {}, {})",
            ZONE_COUNT, ZONE_LENGTH, ZONE_HALF_WIDTH * 2, SAFE_HALF_WIDTH * 2 + 1, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
    }

    private void decorateAroundSpawn(ServerLevel level) {
        ChunkPos spawnChunk = new ChunkPos(BlockPos.containing(ROAD_CENTER_X, 0, SPAWN_Z));
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int chunkX = spawnChunk.x + dx;
                int chunkZ = spawnChunk.z + dz;
                decorateChunk(level, chunkX, chunkZ);
                decoratedOverworldChunks.add(chunkKey(chunkX, chunkZ));
            }
        }
    }

    private void decorateActiveChunks(ServerLevel level) {
        Set<Long> candidates = new LinkedHashSet<>();
        ChunkPos spawnChunk = new ChunkPos(BlockPos.containing(ROAD_CENTER_X, 0, SPAWN_Z));
        candidates.add(chunkKey(spawnChunk.x, spawnChunk.z));

        for (ServerPlayer player : level.players()) {
            ChunkPos playerChunk = player.chunkPosition();
            for (int dx = -DECORATION_CHUNK_RADIUS; dx <= DECORATION_CHUNK_RADIUS; dx++) {
                for (int dz = -DECORATION_CHUNK_RADIUS; dz <= DECORATION_CHUNK_RADIUS; dz++) {
                    candidates.add(chunkKey(playerChunk.x + dx, playerChunk.z + dz));
                }
            }
        }

        int decorated = 0;
        for (long key : candidates) {
            if (decorated >= MAX_CHUNKS_PER_TICK) {
                break;
            }
            if (decoratedOverworldChunks.contains(key)) {
                continue;
            }
            int chunkX = chunkX(key);
            int chunkZ = chunkZ(key);
            decorateChunk(level, chunkX, chunkZ);
            decoratedOverworldChunks.add(key);
            decorated++;
        }
    }

    private void decorateChunk(ServerLevel level, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                normalizeFlatColumn(level, x, z);
                if (Math.abs(x) > ZONE_HALF_WIDTH || !isManagedNorthSouthBand(z)) {
                    continue;
                }
                boolean separator = isSeparatorLineZ(z);
                boolean road = isRoadColumn(x);
                boolean fence = isPerimeterFence(x, z);
                if (!separator && !road && !fence) {
                    continue;
                }
                BlockPos ground = BlockPos.containing(x, FLAT_GRASS_Y, z);
                if (fence) {
                    level.setBlock(ground, FENCE_BLOCK, 3);
                    continue;
                }
                if (separator) {
                    level.setBlock(ground, SEPARATOR_BLOCK, 3);
                } else if (road) {
                    level.setBlock(ground, ROAD_BLOCK, 3);
                }
            }
        }
        decorateZoneSigns(level, chunkX, chunkZ);
    }

    private void decorateZoneSigns(ServerLevel level, int chunkX, int chunkZ) {
        for (int zone = 1; zone <= ZONE_COUNT; zone++) {
            int signX = ROAD_CENTER_X - SIGN_OFFSET_X;
            int signZ = zoneSouthBoundary(zone) - 2;
            if (!isInChunk(signX, signZ, chunkX, chunkZ)) {
                continue;
            }
            placeZoneSign(level, signX, signZ, "Зона " + zoneLabel(zone));
        }
    }

    private static void placeZoneSign(ServerLevel level, int x, int z, String text) {
        BlockPos ground = BlockPos.containing(x, FLAT_GRASS_Y, z);
        BlockPos signPos = ground.above();
        level.setBlock(ground, ROAD_BLOCK, 3);
        level.setBlock(signPos, Blocks.OAK_SIGN.defaultBlockState(), 3);
        if (level.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
            sign.setText(sign.getFrontText().setMessage(0, Component.literal(text)), true);
            sign.setText(sign.getBackText().setMessage(0, Component.literal(text)), false);
            sign.setChanged();
        }
    }

    private static boolean isPerimeterFence(int x, int z) {
        if (Math.abs(x) == ZONE_HALF_WIDTH && isManagedNorthSouthBand(z)) {
            return true;
        }
        if (isZoneBoundaryZ(z) && !isRoadColumn(x)) {
            return true;
        }
        return false;
    }

    private static boolean isZoneBoundaryZ(int z) {
        if (z == FIRST_ZONE_SOUTH_Z || z == northEdge()) {
            return true;
        }
        for (int boundaryIndex = 1; boundaryIndex < ZONE_COUNT; boundaryIndex++) {
            int separatorZ = FIRST_ZONE_SOUTH_Z - boundaryIndex * ZONE_LENGTH;
            if (z == separatorZ) {
                return true;
            }
        }
        return false;
    }

    private static boolean isManagedNorthSouthBand(double z) {
        return z <= FIRST_ZONE_SOUTH_Z && z >= northEdge();
    }

    private static boolean isSeparatorLineZ(int z) {
        for (int boundaryIndex = 1; boundaryIndex < ZONE_COUNT; boundaryIndex++) {
            int separatorZ = FIRST_ZONE_SOUTH_Z - boundaryIndex * ZONE_LENGTH;
            if (Math.abs(z - separatorZ) <= SEPARATOR_HALF_THICKNESS) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRoadColumn(int x) {
        return Math.abs(x - ROAD_CENTER_X) <= ROAD_HALF_WIDTH;
    }

    private static int zoneSouthBoundary(int zoneIndex) {
        return FIRST_ZONE_SOUTH_Z - (zoneIndex - 1) * ZONE_LENGTH;
    }

    private static int northEdge() {
        return FIRST_ZONE_SOUTH_Z - ZONE_COUNT * ZONE_LENGTH + 1;
    }

    private static boolean isInChunk(int x, int z, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        return x >= minX && x <= minX + 15 && z >= minZ && z <= minZ + 15;
    }

    private static BlockPos starterSpawnPos() {
        return BlockPos.containing(ROAD_CENTER_X, FLAT_SPAWN_Y, SPAWN_Z);
    }

    private static void normalizeFlatColumn(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int topY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, 0, z)).getY();
        for (int y = FLAT_GRASS_Y + 1; y <= topY + 1; y++) {
            pos.set(x, y, z);
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        pos.set(x, FLAT_BEDROCK_Y, z);
        level.setBlock(pos, Blocks.BEDROCK.defaultBlockState(), 3);
        for (int y = FLAT_DIRT_MIN_Y; y <= FLAT_DIRT_MAX_Y; y++) {
            pos.set(x, y, z);
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
        }
        pos.set(x, FLAT_GRASS_Y, z);
        level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static int chunkX(long key) {
        return (int) (key >> 32);
    }

    private static int chunkZ(long key) {
        return (int) key;
    }
}
