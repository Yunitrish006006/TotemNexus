package dev.totem.nexus.space;

import java.util.Locale;
import java.util.Optional;

/** Stable persisted/UI identifiers for Space Unit teleport interfaces. */
public enum TeleportInterfaceType {
    COMPASS("compass", false), RECOVERY_COMPASS("recovery_compass", false), BOOK("book", false), FILLED_MAP("filled_map", true);
    private final String id;
    private final boolean mapVisualization;
    TeleportInterfaceType(String id, boolean mapVisualization) { this.id = id; this.mapVisualization = mapVisualization; }
    public String id() { return id; }
    public boolean canBind() { return true; }
    public boolean canDiscover() { return true; }
    public boolean canManage() { return true; }
    public boolean canManageFriends() { return true; }
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
