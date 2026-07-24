package dev.totem.nexus.space;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportInterfaceSessionStoreTest {
    @Test void onlyTheBoundSourceIsAcceptedBeforeExpiry() {
        UUID player = UUID.randomUUID(), source = UUID.randomUUID();
        TeleportInterfaceSessionStore store = new TeleportInterfaceSessionStore();
        store.put(new TeleportInterfaceContext(player, TeleportInterfaceType.COMPASS, "compass", source, 10, 20));
        assertTrue(store.require(player, "compass", source, 20).isPresent());
        assertTrue(store.require(player, "compass", UUID.randomUUID(), 20).isEmpty());
        store.expire(21);
        assertTrue(store.require(player, "compass", source, 21).isEmpty());
        store.put(new TeleportInterfaceContext(player, TeleportInterfaceType.COMPASS, "compass", source, 21, 30));
        store.remove(player);
        assertTrue(store.require(player, "compass", source, 21).isEmpty());
    }
}
