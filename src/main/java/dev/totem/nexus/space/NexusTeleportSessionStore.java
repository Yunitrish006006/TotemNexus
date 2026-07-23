package dev.totem.nexus.space;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Thread-confined server store for at most one pending teleport per player. */
public final class NexusTeleportSessionStore {
    private final Map<UUID, NexusTeleportExecutionSession> sessions = new HashMap<>();
    public void start(NexusTeleportExecutionSession session) { sessions.put(session.playerId(), session); }
    public Optional<NexusTeleportExecutionSession> get(UUID player) { return Optional.ofNullable(sessions.get(player)); }
    public Optional<NexusTeleportExecutionSession> tick(UUID player) { NexusTeleportExecutionSession current = sessions.get(player); if (current == null) return Optional.empty(); NexusTeleportExecutionSession next = current.tick(); if (next.ready()) sessions.remove(player); else sessions.put(player, next); return Optional.of(next); }
    public void cancel(UUID player) { sessions.remove(player); }
}
