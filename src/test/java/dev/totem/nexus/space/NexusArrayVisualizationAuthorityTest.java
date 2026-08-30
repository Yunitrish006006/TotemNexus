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
    void rateLimitAcceptsAtMostOneEnablePerSecond() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000702");
        NexusArrayVisualizationAuthority.disconnect(playerId);

        assertTrue(NexusArrayVisualizationAuthority.claimEnable(playerId, 100L));
        assertFalse(NexusArrayVisualizationAuthority.claimEnable(playerId, 119L));
        assertTrue(NexusArrayVisualizationAuthority.claimEnable(playerId, 120L));

        NexusArrayVisualizationAuthority.disconnect(playerId);
        assertTrue(NexusArrayVisualizationAuthority.claimEnable(playerId, 120L));
        NexusArrayVisualizationAuthority.disconnect(playerId);
    }

    @Test
    void relativeBlocksAreDeterministicAndOnlyMarkScanEmitters() {
        BlockPos origin = new BlockPos(10, 64, -4);
        BlockPos low = origin.offset(1, -1, 0);
        BlockPos emitter = origin.offset(-2, 0, 1);
        BlockPos high = origin.offset(0, 1, -3);
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
                Set.of(high, emitter, low)
        );

        assertEquals(List.of(
                new TeleportArrayVisualizationPayload.RelativeBlock(1, -1, 0, false),
                new TeleportArrayVisualizationPayload.RelativeBlock(-2, 0, 1, true),
                new TeleportArrayVisualizationPayload.RelativeBlock(0, 1, -3, false)
        ), NexusArrayVisualizationAuthority.relativeBlocks(origin, scan));
    }
}
