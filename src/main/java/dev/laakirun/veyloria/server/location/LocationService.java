package dev.laakirun.veyloria.server.location;

import dev.laakirun.veyloria.common.VeyloriaConstants;
import dev.laakirun.veyloria.server.game.TestWorldLayoutService;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public final class LocationService {
    public static final ResourceLocation NONE = ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "none");

    private final Map<ResourceLocation, LocationLevelRange> rangesByLocation = new LinkedHashMap<>();

    public LocationService() {
        registerZone("zone_1", 1, 10);
        registerZone("zone_2", 10, 25);
        registerZone("zone_3", 25, 35);
        registerZone("zone_4", 35, 45);
        registerZone("zone_5", 45, 60);
        registerZone("zone_6", 60, 70);
        registerZone("zone_7", 70, 80);
        rangesByLocation.put(NONE, new LocationLevelRange(1, 1));
    }

    public ResourceLocation resolveLocationId(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return NONE;
        }
        int zone = TestWorldLayoutService.zoneIndex(level.dimension().location().toString(), pos.getZ() + 0.5D);
        if (zone < 1 || zone > TestWorldLayoutService.ZONE_COUNT) {
            return NONE;
        }
        return ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, "zone_" + zone);
    }

    public LocationLevelRange levelRange(ResourceLocation locationId) {
        if (locationId == null) {
            return rangesByLocation.get(NONE);
        }
        return rangesByLocation.getOrDefault(locationId, rangesByLocation.get(NONE));
    }

    public boolean matches(ResourceLocation expectedLocationId, ResourceLocation actualLocationId) {
        if (expectedLocationId == null || expectedLocationId.equals(NONE)) {
            return true;
        }
        return expectedLocationId.equals(actualLocationId);
    }

    private void registerZone(String path, int minLevel, int maxLevel) {
        rangesByLocation.put(ResourceLocation.fromNamespaceAndPath(VeyloriaConstants.MOD_ID, path), new LocationLevelRange(minLevel, maxLevel));
    }
}
