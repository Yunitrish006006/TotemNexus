package com.adaptor.deadrecall.space;

import java.util.UUID;

public final class FriendTeleportSessionPolicy {
    private FriendTeleportSessionPolicy() {
    }

    public static boolean belongsToRelationship(
            UUID requesterId,
            UUID targetId,
            UUID firstPlayerId,
            UUID secondPlayerId) {
        if (requesterId == null || targetId == null || firstPlayerId == null || secondPlayerId == null) {
            return false;
        }

        return requesterId.equals(firstPlayerId) && targetId.equals(secondPlayerId)
                || requesterId.equals(secondPlayerId) && targetId.equals(firstPlayerId);
    }
}
