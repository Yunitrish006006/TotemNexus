package com.adaptor.deadrecall.space;

import java.util.Locale;

public enum SpaceUnitStatus {
    ACTIVE("active"),
    DISABLED("disabled"),
    INVALID("invalid");

    private final String id;

    SpaceUnitStatus(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public static SpaceUnitStatus fromId(String id) {
        if (id == null || id.isBlank()) {
            return ACTIVE;
        }

        String normalized = id.toLowerCase(Locale.ROOT);
        for (SpaceUnitStatus status : values()) {
            if (status.id.equals(normalized)) {
                return status;
            }
        }
        return ACTIVE;
    }
}
