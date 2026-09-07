package dev.totem.nexus.space;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that Nexus writes only canonical Totem IDs while preserving old worlds on first load. */
class NexusCanonicalSavedDataMigrationTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-000000000102");

    @BeforeAll
    static void initializeSavedDataVersion() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void exposesOnlyCanonicalStorageIdentifiers() {
        assertEquals("totem:space_units", NexusSpaceUnitSavedData.TYPE.id().toString());
        assertEquals("totem:space_discovery", NexusSpaceDiscoverySavedData.TYPE.id().toString());
        assertEquals("totem:distributed_spawns", NexusDistributedSpawnSavedData.TYPE.id().toString());
        assertEquals("totem:nexus_map_bindings", NexusMapBindingSavedData.TYPE.id().toString());
    }

    @Test
    void legacyDiscoveryMigratesToCanonicalWithoutMutatingTheLegacyRecord(@TempDir Path directory) {
        try (SavedDataStorage legacyStorage = storage(directory)) {
            NexusSpaceDiscoverySavedData legacy = new NexusSpaceDiscoverySavedData();
            legacy.markDiscovered(PLAYER, UNIT);
            legacy.setFavorite(PLAYER, UNIT, true);
            legacyStorage.set(NexusSpaceDiscoverySavedData.LEGACY_COMPATIBILITY_TYPE, legacy);
            legacyStorage.saveAndJoin();
        }

        try (SavedDataStorage migratingStorage = storage(directory)) {
            NexusSpaceDiscoverySavedData migrated = NexusSpaceDiscoverySavedData.loadCanonical(migratingStorage);
            assertTrue(migrated.hasDiscovered(PLAYER, UNIT));
            assertTrue(migrated.isFavorite(PLAYER, UNIT));
            assertSame(migrated, migratingStorage.get(NexusSpaceDiscoverySavedData.TYPE));
            migratingStorage.saveAndJoin();
        }

        try (SavedDataStorage reloadedStorage = storage(directory)) {
            NexusSpaceDiscoverySavedData canonical = reloadedStorage.get(NexusSpaceDiscoverySavedData.TYPE);
            NexusSpaceDiscoverySavedData legacy = reloadedStorage.get(
                    NexusSpaceDiscoverySavedData.LEGACY_COMPATIBILITY_TYPE);
            assertNotNull(canonical);
            assertNotNull(legacy);
            assertTrue(canonical.hasDiscovered(PLAYER, UNIT));
            assertTrue(canonical.isFavorite(PLAYER, UNIT));
            assertTrue(legacy.hasDiscovered(PLAYER, UNIT));
            assertTrue(legacy.isFavorite(PLAYER, UNIT));
        }
    }

    @Test
    void canonicalDiscoveryWinsWhenBothRecordsExist(@TempDir Path directory) {
        try (SavedDataStorage storage = storage(directory)) {
            NexusSpaceDiscoverySavedData legacy = new NexusSpaceDiscoverySavedData();
            legacy.markDiscovered(PLAYER, UNIT);
            storage.set(NexusSpaceDiscoverySavedData.LEGACY_COMPATIBILITY_TYPE, legacy);

            NexusSpaceDiscoverySavedData canonical = new NexusSpaceDiscoverySavedData();
            UUID canonicalUnit = UUID.fromString("00000000-0000-0000-0000-000000000103");
            canonical.markDiscovered(PLAYER, canonicalUnit);
            storage.set(NexusSpaceDiscoverySavedData.TYPE, canonical);
            storage.saveAndJoin();
        }

        try (SavedDataStorage storage = storage(directory)) {
            NexusSpaceDiscoverySavedData selected = NexusSpaceDiscoverySavedData.loadCanonical(storage);
            assertFalse(selected.hasDiscovered(PLAYER, UNIT));
            assertTrue(selected.hasDiscovered(PLAYER,
                    UUID.fromString("00000000-0000-0000-0000-000000000103")));
        }
    }

    @Test
    void emptyLegacyRecordsProduceDistinctCanonicalInstances(@TempDir Path directory) {
        try (SavedDataStorage storage = storage(directory.resolve("units"))) {
            NexusSpaceUnitSavedData legacy = new NexusSpaceUnitSavedData();
            storage.set(NexusSpaceUnitSavedData.LEGACY_COMPATIBILITY_TYPE, legacy);
            NexusSpaceUnitSavedData canonical = NexusSpaceUnitSavedData.loadCanonical(storage);
            assertFalse(canonical == legacy);
            assertSame(canonical, storage.get(NexusSpaceUnitSavedData.TYPE));
        }
        try (SavedDataStorage storage = storage(directory.resolve("spawns"))) {
            NexusDistributedSpawnSavedData legacy = new NexusDistributedSpawnSavedData();
            legacy.put(PLAYER, Level.OVERWORLD, net.minecraft.core.BlockPos.ZERO, 0.0F, 1L);
            storage.set(NexusDistributedSpawnSavedData.LEGACY_COMPATIBILITY_TYPE, legacy);
            NexusDistributedSpawnSavedData canonical = NexusDistributedSpawnSavedData.loadCanonical(storage);
            assertEquals(1, canonical.spawns().size());
            assertEquals(1, legacy.spawns().size());
        }
        try (SavedDataStorage storage = storage(directory.resolve("maps"))) {
            NexusMapBindingSavedData legacy = new NexusMapBindingSavedData();
            storage.set(NexusMapBindingSavedData.LEGACY_COMPATIBILITY_TYPE, legacy);
            NexusMapBindingSavedData canonical = NexusMapBindingSavedData.loadCanonical(storage);
            assertFalse(canonical == legacy);
            assertSame(legacy, storage.get(NexusMapBindingSavedData.LEGACY_COMPATIBILITY_TYPE));
        }
    }

    private static SavedDataStorage storage(Path directory) {
        return new SavedDataStorage(directory, DataFixers.getDataFixer(), HolderLookup.Provider.create(Stream.empty()));
    }
}
