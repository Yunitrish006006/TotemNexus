package dev.totem.nexus.space;

import java.util.Locale;

/** Stable persisted lifecycle values for Space Units. */
public enum SpaceUnitStatus {
    ACTIVE("active"), DISABLED("disabled"), INVALID("invalid");

    private final String id;

    SpaceUnitStatus(String id) { this.id = id; }

    public String id() { return id; }

    public static SpaceUnitStatus fromId(String id) {
        if (id != null && !id.isBlank()) {
            String normalized = id.toLowerCase(Locale.ROOT);
            for (SpaceUnitStatus status : values()) if (status.id.equals(normalized)) return status;
        }
        return ACTIVE;
    }
}
