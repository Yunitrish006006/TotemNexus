package com.adaptor.deadrecall.space;

import java.util.Locale;
import java.util.Optional;

public enum TeleportInterfaceType {
    COMPASS("compass", true),
    RECOVERY_COMPASS("recovery_compass", false),
    BOOK("book", false),
    FILLED_MAP("filled_map", false);

    private final String id;
    private final boolean compassCapabilities;

    TeleportInterfaceType(String id, boolean compassCapabilities) {
        this.id = id;
        this.compassCapabilities = compassCapabilities;
    }

    public String id() {
        return this.id;
    }

    public boolean hasCompassCapabilities() {
        return this.compassCapabilities;
    }

    public static Optional<TeleportInterfaceType> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (TeleportInterfaceType type : values()) {
            if (type.id.equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
