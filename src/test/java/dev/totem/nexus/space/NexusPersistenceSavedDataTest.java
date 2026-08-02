package dev.totem.nexus.space;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusPersistenceSavedDataTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void favoriteRequiresDiscoveryAndIsRemovedWithIt() {
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID unit = UUID.fromString("00000000-0000-0000-0000-000000000012");
        NexusSpaceDiscoverySavedData data = new NexusSpaceDiscoverySavedData();
        assertFalse(data.setFavorite(player, unit, true));
        assertTrue(data.markDiscovered(player, unit));
        assertTrue(data.setFavorite(player, unit, true));
        assertTrue(data.removeDiscovered(player, unit));
        assertFalse(data.isFavorite(player, unit));
    }

    @Test
    void legacyDiscoveryJsonLoadsAndReencodesItsStableFieldsWithDefaultVersionSemantics() {
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000021");
        UUID unit = UUID.fromString("00000000-0000-0000-0000-000000000022");
        // UUIDUtil.CODEC stores UUIDs in legacy SavedData as four integer components.
        JsonObject legacy = JsonParser.parseString("""
                {"data_version":2,"players":[{"player":[0,0,0,33],"units":[[0,0,0,34]],"favorites":[[0,0,0,34]]}]}
                """).getAsJsonObject();
        NexusSpaceDiscoverySavedData restored = NexusSpaceDiscoverySavedData.CODEC.parse(JsonOps.INSTANCE, legacy)
                .getOrThrow(IllegalArgumentException::new);
        assertTrue(restored.hasDiscovered(player, unit));
        assertTrue(restored.isFavorite(player, unit));
        JsonObject reencoded = NexusSpaceDiscoverySavedData.CODEC.encodeStart(JsonOps.INSTANCE, restored)
                .getOrThrow(IllegalArgumentException::new).getAsJsonObject();
        assertTrue(!reencoded.has("data_version") || reencoded.get("data_version").getAsInt() == 2);
        assertTrue(reencoded.has("players") && reencoded.getAsJsonArray("players").size() == 1);
    }

    @Test
    void soulboundTeleportTokenSurvivesCodecRoundTripAndReplacesThePreviousToken() {
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000023");
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000024");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000025");
        NexusSpaceDiscoverySavedData data = new NexusSpaceDiscoverySavedData();

        data.setSoulboundTeleportToken(player, first);
        data.setSoulboundTeleportToken(player, second);

        JsonObject encoded = NexusSpaceDiscoverySavedData.CODEC.encodeStart(JsonOps.INSTANCE, data)
                .getOrThrow(IllegalArgumentException::new).getAsJsonObject();
        NexusSpaceDiscoverySavedData restored = NexusSpaceDiscoverySavedData.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(IllegalArgumentException::new);

        assertEquals(Optional.of(second), restored.soulboundTeleportToken(player));
    }

    @Test
    void reverseBackpackBindingSurvivesCodecRoundTripAndDrivesDuplicateDiagnostics() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000031");
        UUID firstNode = UUID.fromString("00000000-0000-0000-0000-000000000032");
        UUID secondNode = UUID.fromString("00000000-0000-0000-0000-000000000033");
        UUID backpack = UUID.fromString("00000000-0000-0000-0000-000000000034");
        NexusSpaceUnitSavedData data = new NexusSpaceUnitSavedData();
        data.put(deathNode(firstNode, owner, new BlockPos(4, 70, 4), backpack));
        data.put(deathNode(secondNode, owner, new BlockPos(12, 70, 12), backpack));

        JsonObject encoded = NexusSpaceUnitSavedData.CODEC.encodeStart(JsonOps.INSTANCE, data)
                .getOrThrow(IllegalArgumentException::new).getAsJsonObject();
        NexusSpaceUnitSavedData restored = NexusSpaceUnitSavedData.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(IllegalArgumentException::new);

        assertEquals(Optional.of(backpack), restored.get(firstNode).orElseThrow().backpackId());
        assertEquals(Optional.of(backpack), restored.get(secondNode).orElseThrow().backpackId());
        Map<UUID, NexusDeathNodeAdminService.DeathNodeDiagnostics> diagnostics =
                NexusDeathNodeAdminService.diagnoseDeathNodes(
                        java.util.List.of(restored.get(firstNode).orElseThrow(), restored.get(secondNode).orElseThrow()),
                        Map.of(owner, Set.of(firstNode, secondNode))
                );
        assertTrue(diagnostics.get(firstNode).flags()
                .contains(NexusDeathNodeAdminService.DiagnosticFlag.DUPLICATE_BACKPACK_BINDING));
        assertTrue(diagnostics.get(secondNode).flags()
                .contains(NexusDeathNodeAdminService.DiagnosticFlag.DUPLICATE_BACKPACK_BINDING));
    }

    @Test
    void legacySpaceUnitWithoutBackpackIdLoadsWithEmptyReverseBinding() {
        JsonObject legacy = JsonParser.parseString("""
                {"units":[{"id":[0,0,0,65],"type":"death","dimension":"minecraft:overworld",
                "pos":[8,72,8],"owner":[0,0,0,66],"status":"active"}]}
                """).getAsJsonObject();

        NexusSpaceUnitSavedData restored = NexusSpaceUnitSavedData.CODEC.parse(JsonOps.INSTANCE, legacy)
                .getOrThrow(IllegalArgumentException::new);

        NexusSpaceUnitRecord restoredRecord = restored.get(
                UUID.fromString("00000000-0000-0000-0000-000000000041")).orElseThrow();
        assertEquals(Optional.empty(), restoredRecord.backpackId());
        assertTrue(restoredRecord.structure().materialSnapshotStale());
    }

    private static NexusSpaceUnitRecord deathNode(UUID id, UUID owner, BlockPos pos, UUID backpackId) {
        return new NexusSpaceUnitRecord(
                id,
                SpaceUnitType.DEATH,
                Level.OVERWORLD,
                pos,
                owner,
                "",
                SpaceUnitVisibility.PRIVATE,
                SpaceUnitStatus.ACTIVE,
                Set.of(),
                Set.of(),
                SpaceStructureSnapshot.EMPTY,
                10L,
                20L,
                Optional.of(backpackId)
        );
    }
}
