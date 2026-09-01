package dev.totem.nexus.space;

import dev.totem.nexus.network.TeleportArrayVisualizationPayload;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusArrayVisualizationAuthorityTest {
    @Test
    void rateLimitAcceptsAtMostOneRefreshPerSecond() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000702");
        NexusArrayVisualizationAuthority.disconnect(playerId);

        assertTrue(NexusArrayVisualizationAuthority.claimRefresh(playerId, 100L));
        assertFalse(NexusArrayVisualizationAuthority.claimRefresh(playerId, 119L));
        assertTrue(NexusArrayVisualizationAuthority.claimRefresh(playerId, 120L));

        NexusArrayVisualizationAuthority.disconnect(playerId);
        assertTrue(NexusArrayVisualizationAuthority.claimRefresh(playerId, 120L));
        NexusArrayVisualizationAuthority.disconnect(playerId);
    }

    @Test
    void unchangedSnapshotsAreSuppressedUntilContentOrModeChanges() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000704");
        TeleportArrayVisualizationPayload first = snapshot(true, false, List.of(
                new TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 0, false, false)));
        TeleportArrayVisualizationPayload changed = snapshot(true, false, List.of(
                new TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 0, true, false)));
        TeleportArrayVisualizationPayload modeChanged = snapshot(true, true, List.of(
                new TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 0, true, false)));
        NexusArrayVisualizationAuthority.disconnect(playerId);

        assertTrue(NexusArrayVisualizationAuthority.recordSnapshotIfChanged(playerId, first));
        assertFalse(NexusArrayVisualizationAuthority.recordSnapshotIfChanged(playerId, first));
        assertTrue(NexusArrayVisualizationAuthority.recordSnapshotIfChanged(playerId, changed));
        assertTrue(NexusArrayVisualizationAuthority.recordSnapshotIfChanged(playerId, modeChanged));
        NexusArrayVisualizationAuthority.disconnect(playerId);
    }

    @Test
    void serverLifecycleCleanupDropsEveryNonPersistentVisualizationSession() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000706");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000707");
        UUID source = UUID.fromString("00000000-0000-0000-0000-000000000708");
        NexusArrayVisualizationAuthority.establishSession(first, "lodestone", source, true, false);
        NexusArrayVisualizationAuthority.establishSession(second, "lodestone", source, false, true);

        assertTrue(NexusArrayVisualizationAuthority.hasSession(first));
        assertTrue(NexusArrayVisualizationAuthority.hasSession(second));
        NexusArrayVisualizationAuthority.shutdown();
        assertFalse(NexusArrayVisualizationAuthority.hasSession(first));
        assertFalse(NexusArrayVisualizationAuthority.hasSession(second));
    }

    @Test
    void relativeBlocksAreDeterministicAndClassifyCountedAndBuildableExactly() {
        BlockPos origin = new BlockPos(10, 64, -4);
        BlockPos low = origin.offset(1, -1, 0);
        BlockPos emitter = origin.offset(-2, 0, 1);
        BlockPos high = origin.offset(0, 1, -3);
        BlockPos buildable = origin.offset(1, 0, 2);
        TeleportArrayMaterialScan.Result scan = new TeleportArrayMaterialScan.Result(
                3,
                0,
                0,
                0,
                3,
                TeleportArrayMaterialAttributes.ZERO,
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of(high, emitter, low),
                Set.of(emitter),
                Set.of(buildable),
                Set.of(high, emitter, low, buildable)
        );

        assertEquals(List.of(
                new TeleportArrayVisualizationPayload.RelativeBlock(1, -1, 0, false, false),
                new TeleportArrayVisualizationPayload.RelativeBlock(-2, 0, 1, true, false),
                new TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 2, false, true),
                new TeleportArrayVisualizationPayload.RelativeBlock(0, 1, -3, false, false)
        ), NexusArrayVisualizationAuthority.relativeBlocks(origin, scan, true, true));
        assertEquals(List.of(
                new TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 2, false, true)
        ), NexusArrayVisualizationAuthority.relativeBlocks(origin, scan, false, true));
    }

    private static TeleportArrayVisualizationPayload snapshot(
            boolean showArray,
            boolean showBuildSites,
            List<TeleportArrayVisualizationPayload.RelativeBlock> blocks) {
        return new TeleportArrayVisualizationPayload(
                UUID.fromString("00000000-0000-0000-0000-000000000705"),
                "minecraft:overworld", 0, 64, 0, showArray, showBuildSites, blocks);
    }
}
