package com.adaptor.deadrecall.space;

import java.util.Locale;

public enum SpaceUnitType {
    LODESTONE("lodestone"),
    PLAYER("player"),
    DEATH("death"),
    TEMPORARY("temporary"),
    SYSTEM("system");

    private final String id;

    SpaceUnitType(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public static SpaceUnitType fromId(String id) {
        if (id == null || id.isBlank()) {
            return LODESTONE;
        }

        String normalized = id.toLowerCase(Locale.ROOT);
        for (SpaceUnitType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return LODESTONE;
    }
}
