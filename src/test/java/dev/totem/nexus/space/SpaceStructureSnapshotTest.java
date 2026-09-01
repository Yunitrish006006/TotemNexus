package dev.totem.nexus.space;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaceStructureSnapshotTest {
    @Test
    void legacyMaterialMapsDecodeAsStaleUntilTheServerRescansThem() {
        SpaceStructureSnapshot snapshot = new SpaceStructureSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0,
                new SpaceStructureSnapshot.MaterialState(Map.of("profile_revision", 1), Map.of(), Map.of()));

        assertTrue(snapshot.materialSnapshotStale());
    }

    @Test
    void currentMaterialSnapshotRetainsExpansionDiagnostics() {
        SpaceStructureSnapshot snapshot = new SpaceStructureSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0,
                new SpaceStructureSnapshot.MaterialState(
                        SpaceStructureSnapshot.MaterialState.CURRENT_SCHEMA_VERSION,
                        Map.of("profile_revision", 3),
                        Map.of("iron", 2),
                        Map.of("iron", Map.of("structure_capacity", 4)),
                        Map.of(),
                        Map.of("minecraft:iron_block", 2),
                        false));

        assertEquals(false, snapshot.materialSnapshotStale());
        assertEquals(2, snapshot.localExpansionPathCounts().get("minecraft:iron_block"));
        assertEquals(4, snapshot.materialFamilyContributions().get("iron").get("structure_capacity"));
        assertFalse(snapshot.teleportArrayExpansionModeKnown());
    }

    @Test
    void expansionModeMustBeExplicitEvenThoughLegacyMissingValuesDecodeAsZero() {
        SpaceStructureSnapshot local = new SpaceStructureSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0,
                new SpaceStructureSnapshot.MaterialState(
                        SpaceStructureSnapshot.MaterialState.CURRENT_SCHEMA_VERSION,
                        Map.of("profile_revision", 3, "expansion_mode", 0),
                        Map.of(), Map.of(), Map.of(), Map.of(), false));
        SpaceStructureSnapshot missing = new SpaceStructureSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0,
                new SpaceStructureSnapshot.MaterialState(
                        SpaceStructureSnapshot.MaterialState.CURRENT_SCHEMA_VERSION,
                        Map.of("profile_revision", 3),
                        Map.of(), Map.of(), Map.of(), Map.of(), false));

        assertTrue(local.teleportArrayExpansionModeKnown());
        assertEquals(0, local.teleportArrayExpansionModeCode());
        assertFalse(missing.teleportArrayExpansionModeKnown());
        assertEquals(0, missing.teleportArrayExpansionModeCode());
    }
}
