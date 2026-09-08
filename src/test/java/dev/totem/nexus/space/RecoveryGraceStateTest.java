package dev.totem.nexus.space;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryGraceStateTest {
    @Test void correctBackpackGetsThreeSecondsWrongOrRepeatedRecoveryCannotRefresh() {
        UUID player = UUID.randomUUID(), node = UUID.randomUUID(), backpack = UUID.randomUUID();
        var state = RecoveryGraceState.arrive(player, node, backpack, 100);
        assertSame(state, state.recover(player, UUID.randomUUID(), backpack, 200));
        assertSame(state, state.recover(player, node, UUID.randomUUID(), 200));
        assertSame(state, state.recover(UUID.randomUUID(), node, backpack, 200));
        var recovered = state.recover(player, node, backpack, 200);
        assertFalse(recovered.expired(259));
        assertTrue(recovered.expired(260));
        assertSame(recovered, recovered.recover(player, node, backpack, 250));
        assertEquals(1300, state.recover(player, node, backpack, 1290).deadlineTick());
        assertFalse(state.expired(1299));
        assertTrue(state.expired(1300));
    }
    @Test void consumedGrantsSurviveSaveReloadAndArePerPlayerAndNode() {
        UUID player = UUID.randomUUID(), node = UUID.randomUUID();
        var saved = new NexusRecoveryGraceSavedData();
        assertTrue(saved.consume(player, node));
        var json = NexusRecoveryGraceSavedData.CODEC.encodeStart(JsonOps.INSTANCE, saved).getOrThrow();
        var restored = NexusRecoveryGraceSavedData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertFalse(restored.consume(player, node));
        assertTrue(restored.consume(player, UUID.randomUUID()));
        assertTrue(restored.consume(UUID.randomUUID(), node));
    }
}
