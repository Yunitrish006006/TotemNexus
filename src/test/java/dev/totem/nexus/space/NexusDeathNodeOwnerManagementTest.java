package dev.totem.nexus.space;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NexusDeathNodeOwnerManagementTest {
    @Test
    void nonAdministratorQueryIsForcedToRequesterUuid() {
        UUID requester = UUID.fromString("00000000-0000-0000-0000-000000000101");
        NexusDeathNodeAdminService.DeathNodeQuery forged = new NexusDeathNodeAdminService.DeathNodeQuery(
                "another-player",
                "minecraft:the_nether",
                "active",
                100L,
                200L,
                new NexusDeathNodeAdminService.PageRequest(3, 25)
        );

        NexusDeathNodeAdminService.DeathNodeQuery authorized = NexusDeathNodeAdminService.authorizeQuery(
                requester,
                false,
                forged,
                ignored -> UUID.fromString("00000000-0000-0000-0000-000000000999")
        );

        assertEquals(requester.toString(), authorized.ownerQuery());
        assertEquals(forged.dimensionId(), authorized.dimensionId());
        assertEquals(forged.statusId(), authorized.statusId());
        assertEquals(forged.createdAfterGameTime(), authorized.createdAfterGameTime());
        assertEquals(forged.createdBeforeGameTime(), authorized.createdBeforeGameTime());
        assertEquals(forged.pageRequest(), authorized.pageRequest());
    }

    @Test
    void administratorQueryStillResolvesCachedPlayerName() {
        UUID resolvedOwner = UUID.fromString("00000000-0000-0000-0000-000000000102");
        NexusDeathNodeAdminService.DeathNodeQuery requested = new NexusDeathNodeAdminService.DeathNodeQuery(
                "KnownPlayer", "", "", 0L, 0L,
                new NexusDeathNodeAdminService.PageRequest(0, 25)
        );

        NexusDeathNodeAdminService.DeathNodeQuery authorized = NexusDeathNodeAdminService.authorizeQuery(
                UUID.randomUUID(),
                true,
                requested,
                name -> "KnownPlayer".equals(name) ? resolvedOwner : null
        );

        assertEquals(resolvedOwner.toString(), authorized.ownerQuery());
    }

    @Test
    void ownerActionsRejectForeignOwnersAndAdministratorActionIds() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000103");
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000104");

        assertTrue(NexusDeathNodeAdminService.canOwnerManageNode(
                owner, owner, NexusDeathNodeAdminService.ACTION_REQUEST_OWNER_PURGE));
        assertTrue(NexusDeathNodeAdminService.canOwnerManageNode(
                owner, owner, NexusDeathNodeAdminService.ACTION_OWNER_PURGE));
        assertFalse(NexusDeathNodeAdminService.canOwnerManageNode(
                other, owner, NexusDeathNodeAdminService.ACTION_OWNER_PURGE));
        assertFalse(NexusDeathNodeAdminService.canOwnerManageNode(
                owner, owner, NexusDeathNodeAdminService.ACTION_PURGE));
        assertFalse(NexusDeathNodeAdminService.canOwnerManageNode(
                owner, owner, NexusDeathNodeAdminService.ACTION_TELEPORT));
        assertFalse(NexusDeathNodeAdminService.canOwnerManageNode(
                owner, owner, NexusDeathNodeAdminService.ACTION_BATCH_PURGE));
    }

    @Test
    void ownerDeleteConfirmationIsBoundAndSingleUse() {
        NexusDeathNodeAdminService.DestructiveConfirmationStore confirmations =
                new NexusDeathNodeAdminService.DestructiveConfirmationStore();
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000105");
        UUID node = UUID.fromString("00000000-0000-0000-0000-000000000106");
        NexusDeathNodeAdminService.DestructiveConfirmation confirmation = confirmations.issue(
                owner,
                node,
                NexusDeathNodeAdminService.ACTION_OWNER_PURGE,
                1_000L,
                30_000L
        );

        assertNotEquals(
                NexusDeathNodeAdminService.ConfirmationConsumeResult.CONFIRMED,
                confirmations.consume(
                        owner,
                        UUID.randomUUID(),
                        NexusDeathNodeAdminService.ACTION_OWNER_PURGE,
                        confirmation.token(),
                        2_000L
                )
        );
        assertEquals(
                NexusDeathNodeAdminService.ConfirmationConsumeResult.CONFIRMED,
                confirmations.consume(
                        owner,
                        node,
                        NexusDeathNodeAdminService.ACTION_OWNER_PURGE,
                        confirmation.token(),
                        2_000L
                )
        );
        assertEquals(
                NexusDeathNodeAdminService.ConfirmationConsumeResult.MISSING,
                confirmations.consume(
                        owner,
                        node,
                        NexusDeathNodeAdminService.ACTION_OWNER_PURGE,
                        confirmation.token(),
                        2_001L
                )
        );
    }
}

