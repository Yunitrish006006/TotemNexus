package com.adaptor.deadrecall.space;

public final class PlayerTeleportTargetPolicy {
    // PLAYER destinations use more precise cancellation reasons than fixed Space Units.
    private static final String GENERIC_TARGET_KEY = "message.deadrecall.space_unit.teleport_cancelled.target";
    private static final String OFFLINE_TARGET_KEY = "message.deadrecall.space_unit.teleport_cancelled.target_offline";
    private static final String UNAVAILABLE_TARGET_KEY = "message.deadrecall.space_unit.teleport_cancelled.target_unavailable";
    private static final String FRIENDSHIP_TARGET_KEY = "message.deadrecall.space_unit.teleport_cancelled.target_friendship";

    private PlayerTeleportTargetPolicy() {
    }

    public static State classify(boolean online, boolean alive, boolean removed, boolean friends) {
        if (!online) {
            return State.OFFLINE;
        }
        if (!alive || removed) {
            return State.UNAVAILABLE;
        }
        if (!friends) {
            return State.NOT_FRIENDS;
        }
        return State.AVAILABLE;
    }

    public static String cancellationMessageKey(State state) {
        return switch (state) {
            case OFFLINE -> OFFLINE_TARGET_KEY;
            case UNAVAILABLE -> UNAVAILABLE_TARGET_KEY;
            case NOT_FRIENDS -> FRIENDSHIP_TARGET_KEY;
            case AVAILABLE -> GENERIC_TARGET_KEY;
        };
    }

    public enum State {
        AVAILABLE,
        OFFLINE,
        UNAVAILABLE,
        NOT_FRIENDS
    }
}
