package dev.totem.nexus.space;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Signed, server-owned material values contributed by one array block or by a
 * complete scanned array.  Individual datapack values are deliberately kept
 * small; quote calculations apply their own final bounds after aggregation.
 */
public record TeleportArrayMaterialAttributes(
        int structureCapacity,
        int scanExpansionRadius,
        int stability,
        int arrivalAccuracy,
        int targetLock,
        int arrivalSafety,
        int wearResistance,
        int maintenanceEfficiency,
        int interferenceResistance,
        int foodEfficiency,
        int phaseSpeed,
        int cooldownRecovery,
        int routeLoadCapacity,
        int crossDimensionCatalystUnits,
        Map<String, Integer> dimensionAffinity) {
    public static final int PROFILE_VALUE_LIMIT = 8;
    public static final TeleportArrayMaterialAttributes ZERO = new TeleportArrayMaterialAttributes(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of());

    public TeleportArrayMaterialAttributes {
        dimensionAffinity = Map.copyOf(dimensionAffinity == null ? Map.of() : dimensionAffinity);
    }

    public TeleportArrayMaterialAttributes plus(TeleportArrayMaterialAttributes other) {
        if (other == null || other == ZERO) {
            return this;
        }
        Map<String, Integer> affinities = new LinkedHashMap<>(this.dimensionAffinity);
        other.dimensionAffinity.forEach((dimension, value) -> affinities.merge(dimension, value, Integer::sum));
        return new TeleportArrayMaterialAttributes(
                this.structureCapacity + other.structureCapacity,
                this.scanExpansionRadius + other.scanExpansionRadius,
                this.stability + other.stability,
                this.arrivalAccuracy + other.arrivalAccuracy,
                this.targetLock + other.targetLock,
                this.arrivalSafety + other.arrivalSafety,
                this.wearResistance + other.wearResistance,
                this.maintenanceEfficiency + other.maintenanceEfficiency,
                this.interferenceResistance + other.interferenceResistance,
                this.foodEfficiency + other.foodEfficiency,
                this.phaseSpeed + other.phaseSpeed,
                this.cooldownRecovery + other.cooldownRecovery,
                this.routeLoadCapacity + other.routeLoadCapacity,
                this.crossDimensionCatalystUnits + other.crossDimensionCatalystUnits,
                affinities
        );
    }

    public int localScanExpansionRadius() {
        return clamp(this.scanExpansionRadius, 0, 2);
    }

    public int affinityFor(String dimensionId) {
        return this.dimensionAffinity.getOrDefault(dimensionId, 0);
    }

    public boolean isZero() {
        return this.equals(ZERO);
    }

    /** Compact, named scalar diagnostics suitable for persistence and the map payload. */
    public Map<String, Integer> scalarValues() {
        Map<String, Integer> values = new LinkedHashMap<>();
        putNonZero(values, "structure_capacity", structureCapacity);
        putNonZero(values, "scan_expansion_radius", scanExpansionRadius);
        putNonZero(values, "stability", stability);
        putNonZero(values, "arrival_accuracy", arrivalAccuracy);
        putNonZero(values, "target_lock", targetLock);
        putNonZero(values, "arrival_safety", arrivalSafety);
        putNonZero(values, "wear_resistance", wearResistance);
        putNonZero(values, "maintenance_efficiency", maintenanceEfficiency);
        putNonZero(values, "interference_resistance", interferenceResistance);
        putNonZero(values, "food_efficiency", foodEfficiency);
        putNonZero(values, "phase_speed", phaseSpeed);
        putNonZero(values, "cooldown_recovery", cooldownRecovery);
        putNonZero(values, "route_load_capacity", routeLoadCapacity);
        putNonZero(values, "cross_dimension_catalyst_units", crossDimensionCatalystUnits);
        return Map.copyOf(values);
    }

    private static void putNonZero(Map<String, Integer> values, String key, int value) {
        if (value != 0) {
            values.put(key, value);
        }
    }

    public static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
