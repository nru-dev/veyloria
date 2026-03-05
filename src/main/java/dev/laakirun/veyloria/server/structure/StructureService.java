package dev.laakirun.veyloria.server.structure;

import dev.laakirun.veyloria.common.config.VeyloriaPaths;
import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import dev.laakirun.veyloria.server.content.ContentService;
import dev.laakirun.veyloria.server.content.StructureSpawnRule;
import dev.laakirun.veyloria.server.content.StructureTemplate;
import dev.laakirun.veyloria.server.db.DatabaseManager;
import dev.laakirun.veyloria.server.game.TestWorldLayoutService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StructureService {
    private static final Logger LOGGER = LoggerFactory.getLogger("veyloria.spawn");
    private static final int EDGE_MARGIN = 10;
    private static final int MAX_ATTEMPTS_PER_STRUCTURE = 220;
    private static final int MAX_PLACEMENTS_PER_TICK = 3;
    private static final long PLACEMENT_INTERVAL_TICKS = 10L;
    private static final String DUNGEON_ENTRANCE_CODE = "dungeon_cave";

    private final DatabaseManager databaseManager;
    private final Map<Long, StructureInstanceState> instancesById = new LinkedHashMap<>();
    private final Map<String, StructureClipboardLoader.LoadedSchematic> schematicCacheByTemplateCode = new ConcurrentHashMap<>();
    private final Set<String> missingSchematicsLogged = ConcurrentHashMap.newKeySet();
    private final StructureClipboardLoader clipboardLoader;

    private long activeWorldSeed = Long.MIN_VALUE;
    private long lastPlacementTick = Long.MIN_VALUE;
    private boolean initialized;

    public StructureService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        VeyloriaPaths.ensure(VeyloriaPaths.dataDir().resolve("structures"));
        this.clipboardLoader = initClipboardLoader();
    }

    public void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        ensureInitialized(overworld, false);
        if (!initialized) {
            return;
        }
        long gameTime = overworld.getGameTime();
        if (lastPlacementTick != Long.MIN_VALUE && gameTime - lastPlacementTick < PLACEMENT_INTERVAL_TICKS) {
            return;
        }
        placePendingStructures(overworld);
        lastPlacementTick = gameTime;
    }

    public void forceReload(MinecraftServer server) {
        instancesById.clear();
        schematicCacheByTemplateCode.clear();
        missingSchematicsLogged.clear();
        initialized = false;
        activeWorldSeed = Long.MIN_VALUE;
        if (server != null && server.overworld() != null) {
            ensureInitialized(server.overworld(), true);
        }
    }

    public int templateCount() {
        ContentService contentService = VeyloriaServerRuntime.instance().contentService();
        return contentService == null ? 0 : contentService.structureTemplates().size();
    }

    public int ruleCount() {
        ContentService contentService = VeyloriaServerRuntime.instance().contentService();
        return contentService == null ? 0 : contentService.structureSpawnRules().size();
    }

    public int placedCount() {
        int placed = 0;
        for (StructureInstanceState state : instancesById.values()) {
            if (state.placed) {
                placed++;
            }
        }
        return placed;
    }

    public int pendingCount() {
        int pending = 0;
        for (StructureInstanceState state : instancesById.values()) {
            if (!state.placed) {
                pending++;
            }
        }
        return pending;
    }

    public int forcePlaceAll(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            return 0;
        }
        ServerLevel overworld = server.overworld();
        ensureInitialized(overworld, false);
        if (!initialized || clipboardLoader == null) {
            return 0;
        }

        ContentService contentService = VeyloriaServerRuntime.instance().contentService();
        if (contentService == null) {
            return 0;
        }

        String dimension = overworld.dimension().location().toString();
        int placed = 0;
        for (StructureInstanceState state : instancesById.values()) {
            if (state.placed || !Objects.equals(state.dimension, dimension)) {
                continue;
            }
            StructureTemplate template = contentService.structureTemplate(state.structureTemplateId);
            if (template == null) {
                continue;
            }
            StructureClipboardLoader.LoadedSchematic schematic = loadSchematic(template);
            if (schematic == null) {
                continue;
            }
            if (!ensureChunksLoaded(overworld, state, schematic, true)) {
                continue;
            }
            prepareLayoutArea(overworld, state, schematic);
            paste(overworld, state, schematic);
            state.placed = true;
            markPlaced(state.id);
            placed++;
            LOGGER.info("Placed structure '{}' in zone {} at ({}, {}, {}) via force place",
                template.code(), state.zoneIndex, state.originX, state.originY, state.originZ);
        }
        return placed;
    }

    public List<String> locateStructureIds() {
        ContentService contentService = VeyloriaServerRuntime.instance().contentService();
        if (contentService == null) {
            return List.of();
        }
        return contentService.structureTemplates().stream()
            .filter(StructureTemplate::enabled)
            .map(template -> "veyloria:" + template.code())
            .sorted(Comparator.naturalOrder())
            .toList();
    }

    public Map<String, String> structureMetadata() {
        ContentService contentService = VeyloriaServerRuntime.instance().contentService();
        if (contentService == null) {
            return Map.of();
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        for (StructureTemplate template : contentService.structureTemplates()) {
            if (!template.enabled()) {
                continue;
            }
            metadata.put(template.code(), template.name());
        }
        return Map.copyOf(metadata);
    }

    public StructurePresence structureAt(ServerLevel level, double x, double y, double z) {
        if (level == null) {
            return null;
        }
        ensureInitialized(level, false);
        if (!initialized) {
            return null;
        }
        ContentService contentService = VeyloriaServerRuntime.instance().contentService();
        if (contentService == null) {
            return null;
        }

        String dimensionId = level.dimension().location().toString();
        BlockPos pos = BlockPos.containing(x, y, z);
        StructurePresence nearest = null;
        double nearestDistanceSqr = Double.MAX_VALUE;

        for (StructureInstanceState state : instancesById.values()) {
            if (!state.placed || !Objects.equals(state.dimension, dimensionId)) {
                continue;
            }
            StructureTemplate template = contentService.structureTemplate(state.structureTemplateId);
            if (template == null || !template.enabled()) {
                continue;
            }
            RotationFootprint footprint = rotationFootprint(template.sizeX(), template.sizeZ(), state.rotationQuadrants);
            int minX = state.originX;
            int maxX = state.originX + footprint.width() - 1;
            int minZ = state.originZ;
            int maxZ = state.originZ + footprint.length() - 1;
            int minY = state.originY;
            int maxY = state.originY + Math.max(1, template.sizeY()) - 1;
            if (pos.getX() < minX || pos.getX() > maxX
                || pos.getY() < minY || pos.getY() > maxY
                || pos.getZ() < minZ || pos.getZ() > maxZ) {
                continue;
            }

            double centerX = (minX + maxX) * 0.5D;
            double centerY = (minY + maxY) * 0.5D;
            double centerZ = (minZ + maxZ) * 0.5D;
            double distanceSqr = distanceSqr(pos.getX(), pos.getY(), pos.getZ(), centerX, centerY, centerZ);
            if (nearest == null || distanceSqr < nearestDistanceSqr) {
                nearestDistanceSqr = distanceSqr;
                nearest = new StructurePresence(
                    template.code(),
                    template.code(),
                    template.name()
                );
            }
        }
        return nearest;
    }

    public LocateResult locateAndEnsurePlaced(ServerLevel level, String structureId, double originX, double originZ) {
        if (level == null || structureId == null || structureId.isBlank()) {
            return null;
        }
        ensureInitialized(level, false);
        if (!initialized) {
            return null;
        }
        ContentService contentService = VeyloriaServerRuntime.instance().contentService();
        if (contentService == null) {
            return null;
        }

        String code = structureId.trim();
        if (code.regionMatches(true, 0, "veyloria:", 0, "veyloria:".length())) {
            code = code.substring("veyloria:".length());
        }
        if (code.isBlank()) {
            return null;
        }

        StructureTemplate template = contentService.structureTemplate(code);
        if (template == null || !template.enabled()) {
            return null;
        }
        StructureClipboardLoader.LoadedSchematic schematic = loadSchematic(template);

        String dimension = level.dimension().location().toString();
        StructureInstanceState nearest = null;
        double nearestDistanceSqr = Double.MAX_VALUE;
        for (StructureInstanceState state : instancesById.values()) {
            if (state.structureTemplateId != template.id() || !Objects.equals(state.dimension, dimension)) {
                continue;
            }
            double dx = (state.originX + 0.5D) - originX;
            double dz = (state.originZ + 0.5D) - originZ;
            double distanceSqr = dx * dx + dz * dz;
            if (distanceSqr < nearestDistanceSqr) {
                nearestDistanceSqr = distanceSqr;
                nearest = state;
            }
        }
        if (nearest == null) {
            return null;
        }

        boolean shouldPlace = !nearest.placed;
        if (!shouldPlace && schematic != null && !isStructurePresent(level, nearest, schematic)) {
            LOGGER.warn("Structure '{}' marked as placed but missing in world at ({}, {}, {}), restoring",
                template.code(), nearest.originX, nearest.originY, nearest.originZ);
            shouldPlace = true;
        }

        boolean spawnedNow = false;
        if (shouldPlace) {
            if (schematic == null || !ensureChunksLoaded(level, nearest, schematic, true)) {
                return null;
            }
            prepareLayoutArea(level, nearest, schematic);
            paste(level, nearest, schematic);
            nearest.placed = true;
            markPlaced(nearest.id);
            spawnedNow = true;
            LOGGER.info("Placed structure '{}' in zone {} at ({}, {}, {}) via locate",
                template.code(), nearest.zoneIndex, nearest.originX, nearest.originY, nearest.originZ);
        }

        LocatorPoint locator = locatorPoint(nearest, schematic);
        return new LocateResult(
            "veyloria:" + template.code(),
            locator.x(),
            locator.y(),
            locator.z(),
            Math.sqrt(nearestDistanceSqr),
            spawnedNow
        );
    }

    public LocateResult nearestPlacedStructure(ServerLevel level,
                                               String structureId,
                                               double originX,
                                               double originZ,
                                               double maxDistance) {
        if (level == null || structureId == null || structureId.isBlank()) {
            return null;
        }
        ensureInitialized(level, false);
        if (!initialized) {
            return null;
        }
        ContentService contentService = VeyloriaServerRuntime.instance().contentService();
        if (contentService == null) {
            return null;
        }
        String code = structureId.trim();
        if (code.regionMatches(true, 0, "veyloria:", 0, "veyloria:".length())) {
            code = code.substring("veyloria:".length());
        }
        if (code.isBlank()) {
            return null;
        }
        StructureTemplate template = contentService.structureTemplate(code);
        if (template == null || !template.enabled()) {
            return null;
        }
        String dimension = level.dimension().location().toString();
        StructureInstanceState nearest = null;
        double nearestDistanceSqr = Double.MAX_VALUE;
        for (StructureInstanceState state : instancesById.values()) {
            if (!state.placed) {
                continue;
            }
            if (state.structureTemplateId != template.id() || !Objects.equals(state.dimension, dimension)) {
                continue;
            }
            RotationFootprint footprint = rotationFootprint(template.sizeX(), template.sizeZ(), state.rotationQuadrants);
            double centerX = state.originX + (footprint.width() - 1) * 0.5D;
            double centerZ = state.originZ + (footprint.length() - 1) * 0.5D;
            double dx = centerX - originX;
            double dz = centerZ - originZ;
            double distanceSqr = dx * dx + dz * dz;
            if (distanceSqr < nearestDistanceSqr) {
                nearestDistanceSqr = distanceSqr;
                nearest = state;
            }
        }
        if (nearest == null) {
            return null;
        }
        if (maxDistance > 0.0D && nearestDistanceSqr > maxDistance * maxDistance) {
            return null;
        }
        RotationFootprint footprint = rotationFootprint(template.sizeX(), template.sizeZ(), nearest.rotationQuadrants);
        int x = nearest.originX + footprint.width() / 2;
        int z = nearest.originZ + footprint.length() / 2;
        int y = nearest.originY + 1;
        return new LocateResult(
            "veyloria:" + template.code(),
            x,
            y,
            z,
            Math.sqrt(nearestDistanceSqr),
            false
        );
    }

    private StructureClipboardLoader initClipboardLoader() {
        StructureClipboardLoader nbtLoader = new VanillaNbtClipboardLoader();
        try {
            return new MultiFormatClipboardLoader(nbtLoader, new WorldEditClipboardLoader());
        } catch (NoClassDefFoundError | Exception exception) {
            LOGGER.warn("WorldEdit API недоступен, вставка .schem отключена (поддержка .nbt активна): {}", exception.getMessage());
            return new MultiFormatClipboardLoader(nbtLoader, null);
        }
    }

    private void ensureInitialized(ServerLevel level, boolean forceRebuild) {
        long worldSeed = level.getSeed();
        if (initialized && !forceRebuild && activeWorldSeed == worldSeed) {
            return;
        }

        ContentService contentService = VeyloriaServerRuntime.instance().contentService();
        if (contentService == null) {
            return;
        }

        Map<Long, StructureTemplate> templatesById = new LinkedHashMap<>();
        for (StructureTemplate template : contentService.structureTemplates()) {
            templatesById.put(template.id(), template);
        }
        List<StructureSpawnRule> rules = contentService.structureSpawnRules();

        instancesById.clear();
        lastPlacementTick = Long.MIN_VALUE;

        try (Connection connection = databaseManager.connection()) {
            deleteInstancesForOtherSeeds(connection, worldSeed);
            if (forceRebuild) {
                deleteInstancesForSeed(connection, worldSeed);
            }

            List<StructureInstanceState> loaded = loadInstances(connection, worldSeed);
            if (loaded.isEmpty() && !rules.isEmpty() && !templatesById.isEmpty()) {
                generateInstances(connection, worldSeed, templatesById, rules);
                loaded = loadInstances(connection, worldSeed);
            }

            for (StructureInstanceState state : loaded) {
                instancesById.put(state.id, state);
            }
        } catch (SQLException exception) {
            LOGGER.error("Failed to initialize structure service", exception);
            return;
        }

        activeWorldSeed = worldSeed;
        initialized = true;
        LOGGER.info(
            "Structure module initialized: templates={}, rules={}, instances={}, pending={}, seed={}",
            templatesById.size(),
            rules.size(),
            instancesById.size(),
            pendingCount(),
            worldSeed
        );
    }

    private void generateInstances(Connection connection,
                                   long worldSeed,
                                   Map<Long, StructureTemplate> templatesById,
                                   List<StructureSpawnRule> rules) throws SQLException {
        List<PlacementPoint> acceptedPoints = new ArrayList<>();

        for (StructureSpawnRule rule : rules) {
            StructureTemplate template = templatesById.get(rule.structureTemplateId());
            if (template == null) {
                continue;
            }
            int zoneStart = Math.max(1, Math.min(TestWorldLayoutService.ZONE_COUNT, rule.zoneMin()));
            int zoneEnd = Math.max(1, Math.min(TestWorldLayoutService.ZONE_COUNT, rule.zoneMax()));
            if (zoneStart > zoneEnd) {
                int swap = zoneStart;
                zoneStart = zoneEnd;
                zoneEnd = swap;
            }
            for (int zoneIndex = zoneStart; zoneIndex <= zoneEnd; zoneIndex++) {
                Random random = new Random(ruleSeed(worldSeed, template, rule, zoneIndex));
                int minCount = isDungeonEntrance(template)
                    ? 1
                    : Math.max(0, Math.min(rule.countMinPerZone(), rule.countMaxPerZone()));
                int maxCount = isDungeonEntrance(template)
                    ? 1
                    : Math.max(minCount, Math.max(rule.countMinPerZone(), rule.countMaxPerZone()));
                int targetCount = minCount + (maxCount > minCount ? random.nextInt(maxCount - minCount + 1) : 0);
                int placedInZone = 0;
                for (int rollIndex = 0; rollIndex < targetCount; rollIndex++) {
                    SpawnCandidate candidate = findCandidate(rule, zoneIndex, random, acceptedPoints);
                    if (candidate == null) {
                        continue;
                    }
                    insertInstance(connection, worldSeed, template.id(), rule.id(), rule.dimension(), zoneIndex, candidate);
                    acceptedPoints.add(new PlacementPoint(rule.dimension(), candidate.x, candidate.z, rule.minDistanceBetween()));
                    placedInZone++;
                }
                if (isDungeonEntrance(template) && placedInZone < minCount) {
                    int missing = minCount - placedInZone;
                    for (int index = 0; index < missing; index++) {
                        SpawnCandidate fallback = findDungeonFallbackCandidate(rule, zoneIndex, random, acceptedPoints);
                        if (fallback == null) {
                            LOGGER.warn("Failed to guarantee dungeon entrance in zone {} for rule {}",
                                zoneIndex, rule.id());
                            break;
                        }
                        insertInstance(connection, worldSeed, template.id(), rule.id(), rule.dimension(), zoneIndex, fallback);
                        acceptedPoints.add(new PlacementPoint(rule.dimension(), fallback.x, fallback.z, rule.minDistanceBetween()));
                        placedInZone++;
                    }
                }
            }
        }
    }

    private SpawnCandidate findCandidate(StructureSpawnRule rule,
                                         int zoneIndex,
                                         Random random,
                                         List<PlacementPoint> acceptedPoints) {
        if (!TestWorldLayoutService.OVERWORLD_DIMENSION.equals(rule.dimension())) {
            return null;
        }

        int southBoundary = TestWorldLayoutService.FIRST_ZONE_SOUTH_Z - (zoneIndex - 1) * TestWorldLayoutService.ZONE_LENGTH;
        int northBoundary = southBoundary - TestWorldLayoutService.ZONE_LENGTH + 1;
        int xMin = -TestWorldLayoutService.ZONE_HALF_WIDTH + EDGE_MARGIN;
        int xMax = TestWorldLayoutService.ZONE_HALF_WIDTH - EDGE_MARGIN;
        int zMin = northBoundary + EDGE_MARGIN;
        int zMax = southBoundary - EDGE_MARGIN;
        if (xMin > xMax || zMin > zMax) {
            return null;
        }

        for (int attempt = 0; attempt < MAX_ATTEMPTS_PER_STRUCTURE; attempt++) {
            int x = xMin + random.nextInt(xMax - xMin + 1);
            int z = zMin + random.nextInt(zMax - zMin + 1);
            if (!isValidByRoad(rule, x, z)) {
                continue;
            }
            if (!isSpacedEnough(rule.dimension(), x, z, rule.minDistanceBetween(), acceptedPoints)) {
                continue;
            }
            int rotation = random.nextInt(4);
            return new SpawnCandidate(x, TestWorldLayoutService.FLAT_GRASS_Y + 1, z, rotation);
        }
        return null;
    }

    private SpawnCandidate findDungeonFallbackCandidate(StructureSpawnRule rule,
                                                        int zoneIndex,
                                                        Random random,
                                                        List<PlacementPoint> acceptedPoints) {
        if (!TestWorldLayoutService.OVERWORLD_DIMENSION.equals(rule.dimension())) {
            return null;
        }
        int southBoundary = TestWorldLayoutService.FIRST_ZONE_SOUTH_Z - (zoneIndex - 1) * TestWorldLayoutService.ZONE_LENGTH;
        int northBoundary = southBoundary - TestWorldLayoutService.ZONE_LENGTH + 1;
        int xMin = -TestWorldLayoutService.ZONE_HALF_WIDTH + EDGE_MARGIN;
        int xMax = TestWorldLayoutService.ZONE_HALF_WIDTH - EDGE_MARGIN;
        int zMin = northBoundary + EDGE_MARGIN;
        int zMax = southBoundary - EDGE_MARGIN;
        if (xMin > xMax || zMin > zMax) {
            return null;
        }

        int y = TestWorldLayoutService.FLAT_GRASS_Y + 1;
        int baseRoadOffset = Math.max((int) Math.ceil(Math.max(0.0D, rule.roadDistanceMin())) + 8, 36);
        int centerZ = (zMin + zMax) / 2;

        for (int zDelta = 0; zDelta <= (zMax - zMin); zDelta += 28) {
            for (int direction : new int[] {0, 1, -1}) {
                int z = clamp(centerZ + direction * zDelta, zMin, zMax);
                for (int xAbs = baseRoadOffset; xAbs <= xMax; xAbs += 24) {
                    int[] xs = new int[] {-xAbs, xAbs};
                    for (int x : xs) {
                        if (x < xMin || x > xMax) {
                            continue;
                        }
                        if (!isValidByRoad(rule, x, z)) {
                            continue;
                        }
                        if (!isSpacedEnough(rule.dimension(), x, z, rule.minDistanceBetween(), acceptedPoints)) {
                            continue;
                        }
                        return new SpawnCandidate(x, y, z, random.nextInt(4));
                    }
                }
            }
        }
        return null;
    }

    private static boolean isDungeonEntrance(StructureTemplate template) {
        if (template == null || template.code() == null) {
            return false;
        }
        return DUNGEON_ENTRANCE_CODE.equalsIgnoreCase(template.code().trim());
    }

    private static boolean isValidByRoad(StructureSpawnRule rule, int x, int z) {
        if (TestWorldLayoutService.isInSafeCorridor(rule.dimension(), x, z)) {
            return false;
        }
        if (TestWorldLayoutService.isSeparatorLine(rule.dimension(), z)) {
            return false;
        }
        double distanceToRoad = Math.abs(x - TestWorldLayoutService.ROAD_CENTER_X);
        if (distanceToRoad < rule.roadDistanceMin()) {
            return false;
        }
        if (rule.roadDistanceMax() > 0.0D && distanceToRoad > rule.roadDistanceMax()) {
            return false;
        }
        return true;
    }

    private static boolean isSpacedEnough(String dimension,
                                          int x,
                                          int z,
                                          double minDistance,
                                          List<PlacementPoint> acceptedPoints) {
        double required = Math.max(1.0D, minDistance);
        for (PlacementPoint point : acceptedPoints) {
            if (!Objects.equals(point.dimension(), dimension)) {
                continue;
            }
            double distance = Math.hypot(point.x() - x, point.z() - z);
            if (distance < Math.max(required, point.minDistance())) {
                return false;
            }
        }
        return true;
    }

    private static double distanceSqr(double x, double y, double z, double centerX, double centerY, double centerZ) {
        double dx = x - centerX;
        double dy = y - centerY;
        double dz = z - centerZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private void placePendingStructures(ServerLevel level) {
        if (clipboardLoader == null || instancesById.isEmpty()) {
            return;
        }

        ContentService contentService = VeyloriaServerRuntime.instance().contentService();
        if (contentService == null) {
            return;
        }

        String dimension = level.dimension().location().toString();
        int placements = 0;

        for (StructureInstanceState state : instancesById.values()) {
            if (placements >= MAX_PLACEMENTS_PER_TICK) {
                break;
            }
            if (state.placed || !Objects.equals(state.dimension, dimension)) {
                continue;
            }
            StructureTemplate template = contentService.structureTemplate(state.structureTemplateId);
            if (template == null) {
                continue;
            }
            StructureClipboardLoader.LoadedSchematic schematic = loadSchematic(template);
            if (schematic == null) {
                continue;
            }
            if (!ensureChunksLoaded(level, state, schematic, false)) {
                continue;
            }
            prepareLayoutArea(level, state, schematic);
            paste(level, state, schematic);
            state.placed = true;
            placements++;
            markPlaced(state.id);
            LOGGER.info("Placed structure '{}' in zone {} at ({}, {}, {})",
                template.code(), state.zoneIndex, state.originX, state.originY, state.originZ);
        }
    }

    private boolean ensureChunksLoaded(ServerLevel level,
                                       StructureInstanceState state,
                                       StructureClipboardLoader.LoadedSchematic schematic,
                                       boolean forceLoad) {
        PlacementBounds bounds = placementBounds(state, schematic);
        int y = state.originY;
        BlockPos[] corners = new BlockPos[] {
            BlockPos.containing(bounds.minX(), y, bounds.minZ()),
            BlockPos.containing(bounds.minX(), y, bounds.maxZ()),
            BlockPos.containing(bounds.maxX(), y, bounds.minZ()),
            BlockPos.containing(bounds.maxX(), y, bounds.maxZ())
        };
        for (BlockPos corner : corners) {
            if (!level.hasChunkAt(corner)) {
                if (!forceLoad) {
                    return false;
                }
                level.getChunk(corner.getX() >> 4, corner.getZ() >> 4);
            }
            if (!level.hasChunkAt(corner)) {
                return false;
            }
        }
        return true;
    }

    private void prepareLayoutArea(ServerLevel level,
                                   StructureInstanceState state,
                                   StructureClipboardLoader.LoadedSchematic schematic) {
        if (level == null || schematic == null) {
            return;
        }
        if (!TestWorldLayoutService.OVERWORLD_DIMENSION.equals(level.dimension().location().toString())) {
            return;
        }
        var runtime = VeyloriaServerRuntime.instance();
        if (runtime == null || runtime.testWorldLayoutService() == null) {
            return;
        }
        PlacementBounds bounds = placementBounds(state, schematic);
        runtime.testWorldLayoutService().prepareStructureArea(level, bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ());
    }

    private void paste(ServerLevel level,
                       StructureInstanceState state,
                       StructureClipboardLoader.LoadedSchematic schematic) {
        for (StructureClipboardLoader.PlacedBlock block : schematic.blocks()) {
            RotatedOffset rotated = rotateOffset(block.x(), block.z(), schematic.width(), schematic.length(), state.rotationQuadrants);
            BlockPos pos = BlockPos.containing(
                state.originX + rotated.x(),
                state.originY + block.y(),
                state.originZ + rotated.z()
            );
            level.setBlock(pos, block.state(), 3);
        }
    }

    private StructureClipboardLoader.LoadedSchematic loadSchematic(StructureTemplate template) {
        StructureClipboardLoader.LoadedSchematic cached = schematicCacheByTemplateCode.get(template.code());
        if (cached != null) {
            return cached;
        }
        StructureClipboardLoader.LoadedSchematic generated = loadGeneratedSchematic(template);
        if (generated != null) {
            schematicCacheByTemplateCode.put(template.code(), generated);
            return generated;
        }
        Path schematicPath = resolveSchematicPath(template.schematicFile());
        if (schematicPath == null || !Files.exists(schematicPath)) {
            String key = template.code() + "::" + template.schematicFile();
            if (missingSchematicsLogged.add(key)) {
                LOGGER.warn("Missing schematic for structure '{}' ({})", template.code(), template.schematicFile());
            }
            return null;
        }
        try {
            StructureClipboardLoader.LoadedSchematic loaded = clipboardLoader.load(schematicPath);
            schematicCacheByTemplateCode.put(template.code(), loaded);
            return loaded;
        } catch (Exception exception) {
            String key = template.code() + "::" + schematicPath;
            if (missingSchematicsLogged.add(key)) {
                LOGGER.warn("Failed to read schematic {} for {}: {}", schematicPath, template.code(), exception.getMessage());
            }
            return null;
        }
    }

    private static StructureClipboardLoader.LoadedSchematic loadGeneratedSchematic(StructureTemplate template) {
        String code = template.code() == null ? "" : template.code().trim().toLowerCase(Locale.ROOT);
        String schematicFile = template.schematicFile() == null ? "" : template.schematicFile().trim().toLowerCase(Locale.ROOT);
        if ("town".equals(code) || "generated:town".equals(schematicFile)) {
            return GeneratedTownSchematic.create();
        }
        if ("dungeon_cave".equals(code) || "generated:dungeon_cave".equals(schematicFile)) {
            return GeneratedDungeonCaveSchematic.create();
        }
        return null;
    }

    private static RotationFootprint rotationFootprint(int width, int length, int rotationQuadrants) {
        int normalized = Math.floorMod(rotationQuadrants, 4);
        if (normalized == 1 || normalized == 3) {
            return new RotationFootprint(Math.max(1, length), Math.max(1, width));
        }
        return new RotationFootprint(Math.max(1, width), Math.max(1, length));
    }

    private static RotatedOffset rotateOffset(int x, int z, int width, int length, int rotationQuadrants) {
        return switch (Math.floorMod(rotationQuadrants, 4)) {
            case 1 -> new RotatedOffset(length - 1 - z, x);
            case 2 -> new RotatedOffset(width - 1 - x, length - 1 - z);
            case 3 -> new RotatedOffset(z, width - 1 - x);
            default -> new RotatedOffset(x, z);
        };
    }

    private static PlacementBounds placementBounds(StructureInstanceState state, StructureClipboardLoader.LoadedSchematic schematic) {
        RotationFootprint footprint = rotationFootprint(schematic.width(), schematic.length(), state.rotationQuadrants);
        int minX = state.originX;
        int maxX = state.originX + footprint.width() - 1;
        int minZ = state.originZ;
        int maxZ = state.originZ + footprint.length() - 1;
        return new PlacementBounds(minX, maxX, minZ, maxZ);
    }

    private static LocatorPoint locatorPoint(StructureInstanceState state, StructureClipboardLoader.LoadedSchematic schematic) {
        if (schematic == null || schematic.blocks().isEmpty()) {
            return new LocatorPoint(state.originX, state.originY, state.originZ);
        }
        RotationFootprint footprint = rotationFootprint(schematic.width(), schematic.length(), state.rotationQuadrants);
        double centerX = (footprint.width() - 1) * 0.5D;
        double centerZ = (footprint.length() - 1) * 0.5D;
        double bestDistance = Double.MAX_VALUE;
        RotatedOffset bestOffset = null;
        int bestY = 0;
        for (StructureClipboardLoader.PlacedBlock block : schematic.blocks()) {
            RotatedOffset rotated = rotateOffset(block.x(), block.z(), schematic.width(), schematic.length(), state.rotationQuadrants);
            double dx = rotated.x() - centerX;
            double dz = rotated.z() - centerZ;
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestOffset = rotated;
                bestY = block.y();
            }
        }
        if (bestOffset == null) {
            return new LocatorPoint(state.originX, state.originY, state.originZ);
        }
        return new LocatorPoint(state.originX + bestOffset.x(), state.originY + bestY, state.originZ + bestOffset.z());
    }

    private static boolean isStructurePresent(ServerLevel level,
                                              StructureInstanceState state,
                                              StructureClipboardLoader.LoadedSchematic schematic) {
        if (level == null || schematic == null || schematic.blocks().isEmpty()) {
            return false;
        }
        StructureClipboardLoader.PlacedBlock reference = highestReferenceBlock(schematic);
        if (reference == null) {
            return false;
        }
        RotatedOffset rotated = rotateOffset(reference.x(), reference.z(), schematic.width(), schematic.length(), state.rotationQuadrants);
        BlockPos pos = BlockPos.containing(
            state.originX + rotated.x(),
            state.originY + reference.y(),
            state.originZ + rotated.z()
        );
        return level.getBlockState(pos).equals(reference.state());
    }

    private static StructureClipboardLoader.PlacedBlock highestReferenceBlock(StructureClipboardLoader.LoadedSchematic schematic) {
        StructureClipboardLoader.PlacedBlock best = null;
        for (StructureClipboardLoader.PlacedBlock block : schematic.blocks()) {
            if (best == null || block.y() > best.y()) {
                best = block;
            }
        }
        return best;
    }

    private static long ruleSeed(long worldSeed, StructureTemplate template, StructureSpawnRule rule, int zoneIndex) {
        long seed = worldSeed;
        seed ^= template.code().toLowerCase(Locale.ROOT).hashCode() * 31L;
        seed ^= rule.dimension().toLowerCase(Locale.ROOT).hashCode() * 131L;
        seed ^= (long) zoneIndex * 17_171L;
        seed ^= (long) rule.zoneMin() * 7_919L;
        seed ^= (long) rule.zoneMax() * 1_157L;
        seed ^= (long) rule.countMinPerZone() * 337L;
        seed ^= (long) rule.countMaxPerZone() * 977L;
        return seed;
    }

    private static void insertInstance(Connection connection,
                                       long worldSeed,
                                       long templateId,
                                       long ruleId,
                                       String dimension,
                                       int zoneIndex,
                                       SpawnCandidate candidate) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO structure_instances(structure_template_id, structure_spawn_rule_id, world_seed, dimension, zone_index, origin_x, origin_y, origin_z, rotation_quadrants, placed, created_at)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, templateId);
            statement.setLong(2, ruleId);
            statement.setLong(3, worldSeed);
            statement.setString(4, dimension);
            statement.setInt(5, zoneIndex);
            statement.setInt(6, candidate.x);
            statement.setInt(7, candidate.y);
            statement.setInt(8, candidate.z);
            statement.setInt(9, candidate.rotationQuadrants);
            statement.setInt(10, 0);
            statement.setString(11, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    private void markPlaced(long id) {
        try (Connection connection = databaseManager.connection();
             PreparedStatement statement = connection.prepareStatement("UPDATE structure_instances SET placed = 1 WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            LOGGER.warn("Failed to mark structure instance {} as placed", id, exception);
        }
    }

    private static List<StructureInstanceState> loadInstances(Connection connection, long worldSeed) throws SQLException {
        List<StructureInstanceState> instances = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT id, structure_template_id, structure_spawn_rule_id, dimension, zone_index, origin_x, origin_y, origin_z, rotation_quadrants, placed FROM structure_instances WHERE world_seed = ?")) {
            statement.setLong(1, worldSeed);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    instances.add(new StructureInstanceState(
                        resultSet.getLong("id"),
                        resultSet.getLong("structure_template_id"),
                        resultSet.getLong("structure_spawn_rule_id"),
                        resultSet.getString("dimension"),
                        resultSet.getInt("zone_index"),
                        resultSet.getInt("origin_x"),
                        resultSet.getInt("origin_y"),
                        resultSet.getInt("origin_z"),
                        Math.floorMod(resultSet.getInt("rotation_quadrants"), 4),
                        resultSet.getInt("placed") == 1
                    ));
                }
            }
        }
        return instances;
    }

    private static void deleteInstancesForOtherSeeds(Connection connection, long worldSeed) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM structure_instances WHERE world_seed <> ?")) {
            statement.setLong(1, worldSeed);
            statement.executeUpdate();
        }
    }

    private static void deleteInstancesForSeed(Connection connection, long worldSeed) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM structure_instances WHERE world_seed = ?")) {
            statement.setLong(1, worldSeed);
            statement.executeUpdate();
        }
    }

    private static Path resolveSchematicPath(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        Path raw = Path.of(fileName);
        if (raw.isAbsolute()) {
            return raw;
        }
        Path veyloriaStructures = VeyloriaPaths.dataDir().resolve("structures").resolve(fileName);
        Path gameDir = VeyloriaPaths.gameDir();
        List<Path> candidates = List.of(
            veyloriaStructures,
            gameDir.resolve("schematics").resolve(fileName),
            gameDir.resolve("worldedit").resolve("schematics").resolve(fileName),
            gameDir.resolve("config").resolve("worldedit").resolve("schematics").resolve(fileName),
            gameDir.resolve(fileName)
        );
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return veyloriaStructures;
    }

    private record PlacementPoint(String dimension, int x, int z, double minDistance) {
    }

    private record SpawnCandidate(int x, int y, int z, int rotationQuadrants) {
    }

    private record RotationFootprint(int width, int length) {
    }

    private record RotatedOffset(int x, int z) {
    }

    private record PlacementBounds(int minX, int maxX, int minZ, int maxZ) {
    }

    private record LocatorPoint(int x, int y, int z) {
    }

    private static final class StructureInstanceState {
        private final long id;
        private final long structureTemplateId;
        @SuppressWarnings("unused")
        private final long structureSpawnRuleId;
        private final String dimension;
        private final int zoneIndex;
        private final int originX;
        private final int originY;
        private final int originZ;
        private final int rotationQuadrants;
        private boolean placed;

        private StructureInstanceState(long id,
                                       long structureTemplateId,
                                       long structureSpawnRuleId,
                                       String dimension,
                                       int zoneIndex,
                                       int originX,
                                       int originY,
                                       int originZ,
                                       int rotationQuadrants,
                                       boolean placed) {
            this.id = id;
            this.structureTemplateId = structureTemplateId;
            this.structureSpawnRuleId = structureSpawnRuleId;
            this.dimension = dimension;
            this.zoneIndex = zoneIndex;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.rotationQuadrants = rotationQuadrants;
            this.placed = placed;
        }
    }

    public record LocateResult(
        String structureId,
        int x,
        int y,
        int z,
        double distance,
        boolean spawnedNow
    ) {
    }

    public record StructurePresence(
        String structureId,
        String displayName,
        String localizedName
    ) {
    }
}
