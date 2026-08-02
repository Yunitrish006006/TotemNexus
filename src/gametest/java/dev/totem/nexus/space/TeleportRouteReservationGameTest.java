package dev.totem.nexus.space;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.List;
import java.util.UUID;

/** Server-thread checks for atomic route capacity, cancellation and restart-safe reservation state. */
public final class TeleportRouteReservationGameTest {
    @GameTest(maxTicks = 30)
    public void competingRoutesReserveBothEndpointsAtomically(GameTestHelper helper) {
        TeleportRouteReservationStore store = new TeleportRouteReservationStore();
        UUID source = UUID.fromString("00000000-0000-0000-0000-000000000211");
        UUID target = UUID.fromString("00000000-0000-0000-0000-000000000212");
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000213");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000214");
        List<TeleportRouteReservationStore.Endpoint> endpoints = List.of(
                new TeleportRouteReservationStore.Endpoint(source, 1, 20),
                new TeleportRouteReservationStore.Endpoint(target, 1, 60));

        boolean firstAccepted = store.reserve(first, endpoints, 100);
        boolean secondAccepted = store.reserve(second, endpoints, 100);
        UUID accepted = firstAccepted ? first : second;
        UUID rejected = firstAccepted ? second : first;
        if (firstAccepted == secondAccepted
                || store.reservedSlotCount(source) != 1
                || store.reservedSlotCount(target) != 1
                || !store.release(accepted)
                || !store.reserve(rejected, endpoints, 100)) {
            helper.fail("Competing routes did not reserve both endpoints atomically");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void completedReservationsRecoverPerEndpointAndClearOnRestart(GameTestHelper helper) {
        TeleportRouteReservationStore store = new TeleportRouteReservationStore();
        UUID fast = UUID.fromString("00000000-0000-0000-0000-000000000221");
        UUID slow = UUID.fromString("00000000-0000-0000-0000-000000000222");
        UUID route = UUID.fromString("00000000-0000-0000-0000-000000000223");
        if (!store.reserve(route, List.of(
                new TeleportRouteReservationStore.Endpoint(fast, 2, 20),
                new TeleportRouteReservationStore.Endpoint(slow, 2, 60)), 200)
                || !store.scheduleRecovery(route, 200)) {
            helper.fail("Completed route could not enter material-calculated cooldown");
            return;
        }
        store.expire(220);
        if (store.reservedSlotCount(fast) != 0 || store.reservedSlotCount(slow) != 1) {
            helper.fail("Endpoint cooldown did not recover independently");
            return;
        }
        store.clear();
        if (!store.isEmpty()) {
            helper.fail("Ephemeral route reservations survived simulated server restart");
            return;
        }
        helper.succeed();
    }
}
