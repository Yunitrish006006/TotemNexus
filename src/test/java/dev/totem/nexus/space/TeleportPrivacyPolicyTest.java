package dev.totem.nexus.space;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportPrivacyPolicyTest {
    @Test
    void relationshipSessionIsBoundToItsTwoParticipants() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000031");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000032");
        UUID outsider = UUID.fromString("00000000-0000-0000-0000-000000000033");
        assertTrue(FriendTeleportSessionPolicy.belongsToRelationship(first, second, first, second));
        assertTrue(FriendTeleportSessionPolicy.belongsToRelationship(second, first, first, second));
        assertFalse(FriendTeleportSessionPolicy.belongsToRelationship(outsider, second, first, second));
    }

    @Test
    void targetClassificationDoesNotRevealUnavailableFriendAsAvailable() {
        assertEquals(PlayerTeleportTargetPolicy.State.OFFLINE,
                PlayerTeleportTargetPolicy.classify(false, true, false, true));
        assertEquals(PlayerTeleportTargetPolicy.State.UNAVAILABLE,
                PlayerTeleportTargetPolicy.classify(true, false, false, true));
        assertEquals(PlayerTeleportTargetPolicy.State.NOT_FRIENDS,
                PlayerTeleportTargetPolicy.classify(true, true, false, false));
        assertEquals("message.deadrecall.space_unit.teleport_cancelled.target_friendship",
                PlayerTeleportTargetPolicy.cancellationMessageKey(PlayerTeleportTargetPolicy.State.NOT_FRIENDS));
    }
}
