package dev.totem.nexus.space;

import java.util.Locale;

/** Stable persisted values for the legacy {@code deadrecall:space_units} data. */
public enum SpaceUnitType {
    LODESTONE("lodestone"), PLAYER("player"), DEATH("death"), TEMPORARY("temporary"), SYSTEM("system");

    private final String id;

    SpaceUnitType(String id) { this.id = id; }

    public String id() { return id; }

    public static SpaceUnitType fromId(String id) {
        if (id != null && !id.isBlank()) {
            String normalized = id.toLowerCase(Locale.ROOT);
            for (SpaceUnitType type : values()) if (type.id.equals(normalized)) return type;
        }
        return LODESTONE;
    }
}
