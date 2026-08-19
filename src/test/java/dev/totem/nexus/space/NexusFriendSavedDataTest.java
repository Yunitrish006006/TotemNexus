package dev.totem.nexus.space;

import dev.totem.core.api.v1.social.FriendActionResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NexusFriendSavedDataTest {
    @Test
    void deprecatedResultNamesRemainCompatibleWithCore() {
        String[] core = Arrays.stream(FriendActionResult.values()).map(Enum::name).toArray(String[]::new);
        String[] nexus = Arrays.stream(NexusFriendSavedData.FriendActionResult.values())
                .map(Enum::name)
                .toArray(String[]::new);
        assertArrayEquals(core, nexus);
    }

    @Test
    void persistedEnumFallbacksRemainLegacySafe() {
        assertEquals(SpaceUnitType.LODESTONE, SpaceUnitType.fromId("unknown"));
        assertEquals(SpaceUnitVisibility.PRIVATE, SpaceUnitVisibility.fromId(""));
        assertEquals(SpaceUnitStatus.ACTIVE, SpaceUnitStatus.fromId(null));
    }
}
