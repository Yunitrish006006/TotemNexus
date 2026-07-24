package dev.totem.nexus.space;

import java.util.UUID;

/** Prevents a player-teleport session from being replayed by a third party. */
public final class FriendTeleportSessionPolicy {
    private FriendTeleportSessionPolicy() { }

    public static boolean belongsToRelationship(UUID requesterId, UUID targetId, UUID firstPlayerId, UUID secondPlayerId) {
        if (requesterId == null || targetId == null || firstPlayerId == null || secondPlayerId == null) return false;
        return requesterId.equals(firstPlayerId) && targetId.equals(secondPlayerId)
                || requesterId.equals(secondPlayerId) && targetId.equals(firstPlayerId);
    }
}
