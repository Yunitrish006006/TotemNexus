package dev.totem.nexus.space;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportRouteReservationStoreTest {
    @Test
    void claimsBothEndpointsAtomicallyAndReleasesCancelledRoutesImmediately() {
        TeleportRouteReservationStore store = new TeleportRouteReservationStore();
        UUID firstEndpoint = UUID.randomUUID();
        UUID secondEndpoint = UUID.randomUUID();
        UUID firstRoute = UUID.randomUUID();
        UUID secondRoute = UUID.randomUUID();
        List<TeleportRouteReservationStore.Endpoint> endpoints = List.of(
                new TeleportRouteReservationStore.Endpoint(firstEndpoint, 1, 100),
                new TeleportRouteReservationStore.Endpoint(secondEndpoint, 1, 100));

        assertTrue(store.reserve(firstRoute, endpoints, 0));
        assertFalse(store.reserve(secondRoute, endpoints, 0));
        assertEquals(1, store.reservedSlotCount(firstEndpoint));
        assertEquals(1, store.reservedSlotCount(secondEndpoint));

        assertTrue(store.release(firstRoute));
        assertTrue(store.reserve(secondRoute, endpoints, 0));
    }

    @Test
    void completedRoutesHoldEachEndpointThroughItsOwnCooldown() {
        TeleportRouteReservationStore store = new TeleportRouteReservationStore();
        UUID fastEndpoint = UUID.randomUUID();
        UUID slowEndpoint = UUID.randomUUID();
        UUID route = UUID.randomUUID();
        assertTrue(store.reserve(route, List.of(
                new TeleportRouteReservationStore.Endpoint(fastEndpoint, 2, 20),
                new TeleportRouteReservationStore.Endpoint(slowEndpoint, 2, 60)), 100));

        assertTrue(store.scheduleRecovery(route, 100));
        assertEquals(120L, store.releaseTime(route, fastEndpoint).orElseThrow());
        assertEquals(160L, store.releaseTime(route, slowEndpoint).orElseThrow());
        store.expire(120);
        assertEquals(0, store.reservedSlotCount(fastEndpoint));
        assertEquals(1, store.reservedSlotCount(slowEndpoint));
        assertTrue(store.contains(route));

        store.expire(160);
        assertTrue(store.isEmpty());
    }

    @Test
    void materialLoadAndRecoveryTotalsUseTheDocumentedClamps() {
        assertEquals(1, TeleportRouteLoadPolicy.slotCapacity(-100));
        assertEquals(3, TeleportRouteLoadPolicy.slotCapacity(2));
        assertEquals(8, TeleportRouteLoadPolicy.slotCapacity(100));
        assertEquals(600, TeleportRouteLoadPolicy.recoveryTicks(-100));
        assertEquals(200, TeleportRouteLoadPolicy.recoveryTicks(0));
        assertEquals(100, TeleportRouteLoadPolicy.recoveryTicks(100));
        assertEquals(16, TeleportRouteLoadPolicy.maintenanceItemCost(-100));
        assertEquals(4, TeleportRouteLoadPolicy.maintenanceItemCost(0));
        assertEquals(2, TeleportRouteLoadPolicy.maintenanceItemCost(100));
    }

    @Test
    void rejectsAnInFlightRouteWhenFreshMaterialCapacityNoLongerFits() {
        TeleportRouteReservationStore store = new TeleportRouteReservationStore();
        UUID endpoint = UUID.randomUUID();
        UUID firstRoute = UUID.randomUUID();
        UUID secondRoute = UUID.randomUUID();
        assertTrue(store.reserve(firstRoute,
                List.of(new TeleportRouteReservationStore.Endpoint(endpoint, 2, 100)), 0));
        assertTrue(store.reserve(secondRoute,
                List.of(new TeleportRouteReservationStore.Endpoint(endpoint, 2, 100)), 0));

        assertFalse(store.isStillValid(firstRoute,
                List.of(new TeleportRouteReservationStore.Endpoint(endpoint, 1, 100)), 1));
        assertTrue(store.release(firstRoute));
        assertTrue(store.isStillValid(secondRoute,
                List.of(new TeleportRouteReservationStore.Endpoint(endpoint, 1, 100)), 1));
    }
}
