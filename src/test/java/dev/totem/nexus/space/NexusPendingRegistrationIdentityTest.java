package dev.totem.nexus.space;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusPendingRegistrationIdentityTest {
    @Test
    void pendingIdentityRequiresExactPlayerSideTypeMapBindingPositionAndExpiry() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        BlockPos pos = new BlockPos(24, 70, -40);
        var expected = new TeleportInterfaceItemResolver.RegistrationInput(
                TeleportInterfaceItemResolver.RegistrationInputType.COMPASS, null, null);
        var pending = new NexusSpaceUnitAuthority.PendingLodestoneRegistration(
                playerId, Level.OVERWORLD, pos, InteractionHand.OFF_HAND,
                expected.type(), null, null, 120L);

        assertTrue(pending.matches(Level.OVERWORLD, pos, InteractionHand.OFF_HAND, expected, 120L));
        assertFalse(pending.matches(Level.OVERWORLD, pos, InteractionHand.MAIN_HAND, expected, 120L));
        assertFalse(pending.matches(Level.OVERWORLD, pos, InteractionHand.OFF_HAND,
                new TeleportInterfaceItemResolver.RegistrationInput(
                        TeleportInterfaceItemResolver.RegistrationInputType.BOOK, null, null), 120L));
        assertFalse(pending.matches(Level.NETHER, pos, InteractionHand.OFF_HAND, expected, 120L));
        assertFalse(pending.matches(Level.OVERWORLD, pos.offset(1, 0, 0), InteractionHand.OFF_HAND, expected, 120L));
        assertFalse(pending.matches(Level.OVERWORLD, pos, InteractionHand.OFF_HAND, expected, 121L));
        assertTrue(pending.matchesDimensionId("minecraft:overworld"));
        assertFalse(pending.matchesDimensionId("minecraft:the_nether"));
    }
}
