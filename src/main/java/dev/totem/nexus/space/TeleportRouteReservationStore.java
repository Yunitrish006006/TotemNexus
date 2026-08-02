package dev.totem.nexus.space;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Thread-confined, non-persistent load reservations for lodestone route endpoints.
 * A reservation stays occupied during preparation and landing, then expires through
 * its endpoint-specific cooldown after a completed teleport.
 */
public final class TeleportRouteReservationStore {
    private static final long ACTIVE_UNTIL_RELEASED = Long.MAX_VALUE;

    private final Map<UUID, Map<UUID, Long>> reservationsByEndpoint = new HashMap<>();
    private final Map<UUID, Map<UUID, Endpoint>> endpointsByReservation = new HashMap<>();

    /** Endpoint identity is a registered lodestone Space Unit ID. */
    public record Endpoint(UUID unitId, int slots, int recoveryTicks) {
        public Endpoint {
            if (unitId == null) {
                throw new IllegalArgumentException("Route endpoint ID cannot be null");
            }
            slots = TeleportArrayMaterialAttributes.clamp(
                    slots,
                    TeleportRouteLoadPolicy.MIN_SLOTS,
                    TeleportRouteLoadPolicy.MAX_SLOTS
            );
            recoveryTicks = TeleportArrayMaterialAttributes.clamp(
                    recoveryTicks,
                    TeleportRouteLoadPolicy.MIN_RECOVERY_TICKS,
                    TeleportRouteLoadPolicy.MAX_RECOVERY_TICKS
            );
        }
    }

    /** Atomically claims one slot on every distinct endpoint, or claims none. */
    public boolean reserve(UUID reservationId, Collection<Endpoint> requested, long gameTime) {
        if (reservationId == null || requested == null || requested.isEmpty()) {
            return requested != null && requested.isEmpty();
        }
        expire(gameTime);
        if (this.endpointsByReservation.containsKey(reservationId)) {
            return false;
        }

        Map<UUID, Endpoint> endpoints = new LinkedHashMap<>();
        for (Endpoint endpoint : requested) {
            if (endpoint == null) {
                return false;
            }
            Endpoint existing = endpoints.putIfAbsent(endpoint.unitId(), endpoint);
            if (existing != null && (existing.slots() != endpoint.slots()
                    || existing.recoveryTicks() != endpoint.recoveryTicks())) {
                throw new IllegalArgumentException("Duplicate endpoint has conflicting route settings");
            }
        }
        for (Endpoint endpoint : endpoints.values()) {
            if (reservedSlotCount(endpoint.unitId()) >= endpoint.slots()) {
                return false;
            }
        }

        this.endpointsByReservation.put(reservationId, Map.copyOf(endpoints));
        for (UUID endpointId : endpoints.keySet()) {
            this.reservationsByEndpoint
                    .computeIfAbsent(endpointId, ignored -> new HashMap<>())
                    .put(reservationId, ACTIVE_UNTIL_RELEASED);
        }
        return true;
    }

    /** Releases an interrupted preparation or landing search without cooldown. */
    public boolean release(UUID reservationId) {
        Map<UUID, Endpoint> endpoints = this.endpointsByReservation.remove(reservationId);
        if (endpoints == null) {
            return false;
        }
        for (UUID endpointId : endpoints.keySet()) {
            removeEndpointReservation(endpointId, reservationId);
        }
        return true;
    }

    /** Keeps successful-route slots reserved until their material-calculated recovery times elapse. */
    public boolean scheduleRecovery(UUID reservationId, long gameTime) {
        Map<UUID, Endpoint> endpoints = this.endpointsByReservation.get(reservationId);
        if (endpoints == null) {
            return false;
        }
        for (Endpoint endpoint : endpoints.values()) {
            Map<UUID, Long> reservations = this.reservationsByEndpoint.get(endpoint.unitId());
            if (reservations != null && reservations.containsKey(reservationId)) {
                reservations.put(reservationId, safeReleaseTime(gameTime, endpoint.recoveryTicks()));
            }
        }
        return true;
    }

    /** Removes completed cooldowns. This is safe to call every server tick. */
    public void expire(long gameTime) {
        Set<UUID> finished = new LinkedHashSet<>();
        for (Map.Entry<UUID, Map<UUID, Long>> endpointEntry : this.reservationsByEndpoint.entrySet()) {
            endpointEntry.getValue().entrySet().removeIf(reservation -> {
                boolean expired = reservation.getValue() != ACTIVE_UNTIL_RELEASED
                        && reservation.getValue() <= gameTime;
                if (expired) {
                    finished.add(reservation.getKey());
                }
                return expired;
            });
        }
        this.reservationsByEndpoint.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        for (UUID reservationId : finished) {
            Map<UUID, Endpoint> endpoints = this.endpointsByReservation.get(reservationId);
            if (endpoints != null && endpoints.keySet().stream()
                    .noneMatch(endpointId -> hasEndpointReservation(endpointId, reservationId))) {
                this.endpointsByReservation.remove(reservationId);
            }
        }
    }

    public int reservedSlotCount(UUID endpointId) {
        Map<UUID, Long> reservations = this.reservationsByEndpoint.get(endpointId);
        return reservations == null ? 0 : reservations.size();
    }

    public boolean contains(UUID reservationId) {
        return this.endpointsByReservation.containsKey(reservationId);
    }

    /** Rechecks an in-flight reservation against freshly scanned endpoint limits. */
    public boolean isStillValid(UUID reservationId, Collection<Endpoint> requested, long gameTime) {
        if (requested == null) {
            return false;
        }
        expire(gameTime);
        if (requested.isEmpty()) {
            return !contains(reservationId);
        }
        Map<UUID, Endpoint> reserved = this.endpointsByReservation.get(reservationId);
        if (reserved == null) {
            return false;
        }
        Map<UUID, Endpoint> current = new LinkedHashMap<>();
        for (Endpoint endpoint : requested) {
            if (endpoint == null || current.putIfAbsent(endpoint.unitId(), endpoint) != null) {
                return false;
            }
        }
        if (!reserved.keySet().equals(current.keySet())) {
            return false;
        }
        return current.values().stream()
                .allMatch(endpoint -> reservedSlotCount(endpoint.unitId()) <= endpoint.slots());
    }

    public boolean isEmpty() {
        return this.endpointsByReservation.isEmpty();
    }

    public Optional<Long> releaseTime(UUID reservationId, UUID endpointId) {
        Map<UUID, Long> reservations = this.reservationsByEndpoint.get(endpointId);
        if (reservations == null) {
            return Optional.empty();
        }
        Long releaseTime = reservations.get(reservationId);
        return releaseTime == null || releaseTime == ACTIVE_UNTIL_RELEASED
                ? Optional.empty()
                : Optional.of(releaseTime);
    }

    /** Reservation state is intentionally ephemeral and must not survive a restart. */
    public void clear() {
        this.reservationsByEndpoint.clear();
        this.endpointsByReservation.clear();
    }

    private void removeEndpointReservation(UUID endpointId, UUID reservationId) {
        Map<UUID, Long> reservations = this.reservationsByEndpoint.get(endpointId);
        if (reservations == null) {
            return;
        }
        reservations.remove(reservationId);
        if (reservations.isEmpty()) {
            this.reservationsByEndpoint.remove(endpointId);
        }
    }

    private boolean hasEndpointReservation(UUID endpointId, UUID reservationId) {
        Map<UUID, Long> reservations = this.reservationsByEndpoint.get(endpointId);
        return reservations != null && reservations.containsKey(reservationId);
    }

    private static long safeReleaseTime(long gameTime, int ticks) {
        if (gameTime >= Long.MAX_VALUE - ticks) {
            return Long.MAX_VALUE - 1;
        }
        return gameTime + ticks;
    }
}
