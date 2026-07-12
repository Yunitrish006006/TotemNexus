package com.adaptor.deadrecall.space;

import java.util.Locale;

public enum SpaceUnitVisibility {
    PRIVATE("private"),
    FRIENDS("friends"),
    PUBLIC("public"),
    HIDDEN("hidden");

    private final String id;

    SpaceUnitVisibility(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public static SpaceUnitVisibility fromId(String id) {
        if (id == null || id.isBlank()) {
            return PRIVATE;
        }

        String normalized = id.toLowerCase(Locale.ROOT);
        for (SpaceUnitVisibility visibility : values()) {
            if (visibility.id.equals(normalized)) {
                return visibility;
            }
        }
        return PRIVATE;
    }
}
