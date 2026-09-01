package dev.totem.nexus.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

/** Persisted structural and material quote data embedded in {@code deadrecall:space_units}. */
public record SpaceStructureSnapshot(
        double completeness,
        double symmetry,
        double resonance,
        double interference,
        double environmentStability,
        double wear,
        int tier,
        int amethystCatalystBlocks,
        MaterialState material) {
    public SpaceStructureSnapshot(
            double completeness,
            double symmetry,
            double resonance,
            double interference,
            double environmentStability,
            double wear,
            int tier) {
        this(completeness, symmetry, resonance, interference, environmentStability, wear, tier, 0, MaterialState.EMPTY);
    }

    public SpaceStructureSnapshot(
            double completeness,
            double symmetry,
            double resonance,
            double interference,
            double environmentStability,
            double wear,
            int tier,
            int amethystCatalystBlocks) {
        this(completeness, symmetry, resonance, interference, environmentStability, wear, tier, amethystCatalystBlocks,
                MaterialState.EMPTY);
    }

    public SpaceStructureSnapshot {
        material = material == null ? MaterialState.EMPTY : material;
    }

    public static final SpaceStructureSnapshot EMPTY = new SpaceStructureSnapshot(0, 0, 0, 0, 0, 0, 0, 0);
    public static final Codec<SpaceStructureSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("completeness", 0D).forGetter(SpaceStructureSnapshot::completeness),
            Codec.DOUBLE.optionalFieldOf("symmetry", 0D).forGetter(SpaceStructureSnapshot::symmetry),
            Codec.DOUBLE.optionalFieldOf("resonance", 0D).forGetter(SpaceStructureSnapshot::resonance),
            Codec.DOUBLE.optionalFieldOf("interference", 0D).forGetter(SpaceStructureSnapshot::interference),
            Codec.DOUBLE.optionalFieldOf("environment_stability", 0D).forGetter(SpaceStructureSnapshot::environmentStability),
            Codec.DOUBLE.optionalFieldOf("wear", 0D).forGetter(SpaceStructureSnapshot::wear),
            Codec.INT.optionalFieldOf("tier", 0).forGetter(SpaceStructureSnapshot::tier),
            Codec.INT.optionalFieldOf("amethyst_catalyst_blocks", 0).forGetter(SpaceStructureSnapshot::amethystCatalystBlocks),
            MaterialState.CODEC.optionalFieldOf("material", MaterialState.EMPTY).forGetter(SpaceStructureSnapshot::material)
    ).apply(instance, SpaceStructureSnapshot::new));

    public int rawStructuralBlocks() { return material.value("raw_structural_blocks"); }
    public int effectiveStructureCapacity() { return material.value("effective_structure_capacity"); }
    public int maximumReachedDistance() { return material.value("maximum_reached_distance"); }
    public int materialProfileRevision() { return material.value("profile_revision"); }
    public boolean teleportArrayExpansionModeKnown() { return material.hasValue("expansion_mode"); }
    public int teleportArrayExpansionModeCode() { return material.value("expansion_mode"); }
    public int materialStability() { return material.value("stability"); }
    public int arrivalAccuracy() { return material.value("arrival_accuracy"); }
    public int targetLock() { return material.value("target_lock"); }
    public int arrivalSafety() { return material.value("arrival_safety"); }
    public int wearResistance() { return material.value("wear_resistance"); }
    public int maintenanceEfficiency() { return material.value("maintenance_efficiency"); }
    public int interferenceResistance() { return material.value("interference_resistance"); }
    public int foodEfficiency() { return material.value("food_efficiency"); }
    public int phaseSpeed() { return material.value("phase_speed"); }
    public int cooldownRecovery() { return material.value("cooldown_recovery"); }
    public int routeLoadCapacity() { return material.value("route_load_capacity"); }
    public int crossDimensionCatalystUnits() { return material.value("cross_dimension_catalyst_units"); }
    public Map<String, Integer> materialFamilyCounts() { return material.familyCounts(); }
    /** Signed scalar totals contributed by each material family. */
    public Map<String, Map<String, Integer>> materialFamilyContributions() {
        return material.familyContributions();
    }
    public Map<String, Integer> dimensionAffinity() { return material.dimensionAffinity(); }
    /** Legacy material data must be recalculated before it can affect a quote. */
    public boolean materialSnapshotStale() { return material.stale(); }
    /** Counts the profile blocks that actually emitted a local scan expansion. */
    public Map<String, Integer> localExpansionPathCounts() { return material.localExpansionPathCounts(); }

    public TeleportArrayMaterialAttributes materialAttributes() {
        return new TeleportArrayMaterialAttributes(
                effectiveStructureCapacity(), 0, materialStability(), arrivalAccuracy(), targetLock(), arrivalSafety(),
                wearResistance(), maintenanceEfficiency(), interferenceResistance(), foodEfficiency(), phaseSpeed(),
                cooldownRecovery(), routeLoadCapacity(), crossDimensionCatalystUnits(), dimensionAffinity()
        );
    }

    public record MaterialState(
            int schemaVersion,
            Map<String, Integer> totals,
            Map<String, Integer> familyCounts,
            Map<String, Map<String, Integer>> familyContributions,
            Map<String, Integer> dimensionAffinity,
            Map<String, Integer> localExpansionPathCounts,
            boolean stale) {
        public static final int CURRENT_SCHEMA_VERSION = 1;
        /** The absence of material data in an old SavedData entry is deliberately stale. */
        public static final MaterialState EMPTY = new MaterialState(0, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), true);
        public static final Codec<MaterialState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("schema_version", 0).forGetter(MaterialState::schemaVersion),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("totals", Map.of()).forGetter(MaterialState::totals),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("family_counts", Map.of()).forGetter(MaterialState::familyCounts),
                Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.INT)).optionalFieldOf(
                        "family_contributions", Map.of()).forGetter(MaterialState::familyContributions),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("dimension_affinity", Map.of()).forGetter(MaterialState::dimensionAffinity),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("local_expansion_paths", Map.of())
                        .forGetter(MaterialState::localExpansionPathCounts),
                Codec.BOOL.optionalFieldOf("stale", true).forGetter(MaterialState::stale)
        ).apply(instance, MaterialState::new));

        /** Compatibility surface retained for callers that only have the original material maps. */
        public MaterialState(
                Map<String, Integer> totals,
                Map<String, Integer> familyCounts,
                Map<String, Integer> dimensionAffinity) {
            this(0, totals, familyCounts, Map.of(), dimensionAffinity, Map.of(), true);
        }

        public MaterialState {
            schemaVersion = Math.max(0, schemaVersion);
            totals = Map.copyOf(totals == null ? Map.of() : totals);
            familyCounts = Map.copyOf(familyCounts == null ? Map.of() : familyCounts);
            familyContributions = copyNestedMap(familyContributions);
            dimensionAffinity = Map.copyOf(dimensionAffinity == null ? Map.of() : dimensionAffinity);
            localExpansionPathCounts = Map.copyOf(localExpansionPathCounts == null ? Map.of() : localExpansionPathCounts);
            stale = stale || schemaVersion < CURRENT_SCHEMA_VERSION;
        }

        public int value(String key) {
            return totals.getOrDefault(key, 0);
        }

        public boolean hasValue(String key) {
            return totals.containsKey(key);
        }

        private static Map<String, Map<String, Integer>> copyNestedMap(Map<String, Map<String, Integer>> values) {
            if (values == null || values.isEmpty()) {
                return Map.of();
            }
            Map<String, Map<String, Integer>> copied = new java.util.LinkedHashMap<>();
            values.forEach((family, attributes) -> copied.put(family,
                    Map.copyOf(attributes == null ? Map.of() : attributes)));
            return Map.copyOf(copied);
        }
    }
}
