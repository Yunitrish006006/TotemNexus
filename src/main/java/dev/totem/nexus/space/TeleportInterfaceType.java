package dev.totem.nexus.space;

import java.util.Locale;
import java.util.Optional;

/** Stable persisted/UI identifiers for Space Unit teleport interfaces. */
public enum TeleportInterfaceType {
    COMPASS("compass", true, false),
    RECOVERY_COMPASS("recovery_compass", false, false),
    BOOK("book", false, false),
    FILLED_MAP("filled_map", true, true);
    private final String id;
    private final boolean teleportDestinationSelection;
    private final boolean mapVisualization;
    TeleportInterfaceType(String id, boolean teleportDestinationSelection, boolean mapVisualization) {
        this.id = id;
        this.teleportDestinationSelection = teleportDestinationSelection;
        this.mapVisualization = mapVisualization;
    }
    public String id() { return id; }
    public boolean canBind() { return true; }
    public boolean canDiscover() { return true; }
    public boolean canManage() { return true; }
    public boolean canManageFriends() { return true; }
    public boolean canSelectTeleportDestination() { return teleportDestinationSelection; }
    public boolean hasMapVisualization() { return mapVisualization; }
    /** Compatibility surface; capabilities are no longer ordinary-compass-only. */
    @Deprecated(forRemoval = false)
    public boolean hasCompassCapabilities() { return canManage(); }
    public static Optional<TeleportInterfaceType> fromId(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (TeleportInterfaceType type : values()) if (type.id.equals(normalized)) return Optional.of(type);
        return Optional.empty();
    }
}
