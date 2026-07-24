package dev.totem.nexus.network;

import dev.totem.nexus.space.NexusTeleportAuthority;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NexusAuthorityPayloadHandlerTest {
    @Test void forwardsWireFieldsToTheSingleAuthorityBoundary() {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<Object[]> arguments = new AtomicReference<>();
        NexusTeleportAuthority authority = (NexusTeleportAuthority) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{NexusTeleportAuthority.class},
                (proxy, invoked, values) -> {
                    method.set(invoked.getName());
                    arguments.set(values);
                    return null;
                });
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        new NexusAuthorityPayloadHandler(authority).toggleFavorite(null,
                new ToggleSpaceUnitFavoritePayload("compass", source, target, true));
        assertEquals("setFavorite", method.get());
        assertEquals("compass", arguments.get()[1]);
        assertEquals(source, arguments.get()[2]);
        assertEquals(target, arguments.get()[3]);
        assertEquals(true, arguments.get()[4]);
    }
}
