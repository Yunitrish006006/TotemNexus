package dev.totem.nexus.space;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusFriendSavedDataTest {
    @Test
    void reciprocalInviteCreatesCanonicalFriendship() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        NexusFriendSavedData data = new NexusFriendSavedData();

        assertEquals(NexusFriendSavedData.FriendActionResult.INVITED, data.inviteOrAccept(first, second));
        assertEquals(NexusFriendSavedData.FriendActionResult.ACCEPTED, data.inviteOrAccept(second, first));
        assertTrue(data.areFriends(first, second));
        assertTrue(data.areFriends(second, first));
        assertEquals(1, data.friendsOf(first).size());
        assertTrue(data.removeRelationship(second, first));
        assertFalse(data.areFriends(first, second));
    }

    @Test
    void persistedEnumFallbacksRemainLegacySafe() {
        assertEquals(SpaceUnitType.LODESTONE, SpaceUnitType.fromId("unknown"));
        assertEquals(SpaceUnitVisibility.PRIVATE, SpaceUnitVisibility.fromId(""));
        assertEquals(SpaceUnitStatus.ACTIVE, SpaceUnitStatus.fromId(null));
    }
}
