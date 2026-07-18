package com.adaptor.deadrecall.space;

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
}
