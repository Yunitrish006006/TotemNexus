package dev.totem.nexus.space;

import com.mojang.brigadier.tree.CommandNode;
import dev.totem.nexus.network.RequestDeathNodeAdminPayload;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/** Proves player self-service stays owner-scoped and server-authoritative. */
public final class NexusDeathNodeAdminAuthorizationGameTest {
    private static final BlockPos PLAYER_POS = new BlockPos(2, 2, 2);
    private static final BlockPos NODE_POS = new BlockPos(6, 2, 2);

    @SuppressWarnings("removal")
    @GameTest(maxTicks = 40)
    public void nonAdministratorReceivesOnlyAnOwnerScopedSnapshot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = createPlayer(helper);
        NexusSpaceUnitRecord node = units(level).createDeathUnit(level, helper.absolutePos(NODE_POS), player);

        try {
            CommandNode<CommandSourceStack> root = level.getServer().getCommands().getDispatcher()
                    .getRoot().getChild("deadrecall");
            require(helper, root != null && root.canUse(player.createCommandSourceStack()),
                    "Normal player cannot use the shared /deadrecall command root");
            CommandNode<CommandSourceStack> deathNodes = root.getChild("deathnodes");
            require(helper, deathNodes != null && deathNodes.canUse(player.createCommandSourceStack()),
                    "Normal player cannot open /deadrecall deathnodes");

            boolean snapshotSent = NexusDeathNodeAdminService.sendSnapshot(
                    player,
                    new RequestDeathNodeAdminPayload(player.getUUID().toString(), "", "", 0L, 0L, 0)
            );

            require(helper, !NexusDeathNodeAdminService.canManage(player),
                    "GameTest mock player unexpectedly has death-node administration permission");
            require(helper, snapshotSent,
                    "Owner-scoped query did not receive a private death-node snapshot");
            NexusSpaceUnitRecord current = units(level).get(node.id())
                    .orElseThrow(() -> helper.assertionException("Unauthorized query removed the death node"));
            require(helper, current.status() == SpaceUnitStatus.ACTIVE,
                    "Unauthorized query changed a death node while being denied");
            helper.succeed();
        } finally {
            units(level).get(node.id()).ifPresent(ignored -> units(level).disableDeathUnit(
                    player.getUUID(), node.id(), level.getGameTime()));
            player.discard();
        }
    }

    @SuppressWarnings("removal")
    @GameTest(maxTicks = 60)
    public void ownerCanDeleteOwnActiveNodeButCannotUseAdministratorActions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = createPlayer(helper);
        ServerPlayer other = createPlayer(helper);
        NexusSpaceUnitRecord node = units(level).createDeathUnit(level, helper.absolutePos(NODE_POS), owner);
        NexusSpaceDiscoverySavedData discovery = discovery(level);
        discovery.markDiscovered(owner.getUUID(), node.id());
        discovery.setFavorite(owner.getUUID(), node.id(), true);
        discovery.markDiscovered(other.getUUID(), node.id());
        discovery.setFavorite(other.getUUID(), node.id(), true);

        try {
            NexusDeathNodeAdminService.handleAction(
                    owner, node.id(), NexusDeathNodeAdminService.ACTION_DISABLE, null);
            require(helper, units(level).get(node.id()).orElseThrow().status() == SpaceUnitStatus.ACTIVE,
                    "Non-administrator used the administrator disable action");

            NexusDeathNodeAdminService.handleAction(
                    other, node.id(), NexusDeathNodeAdminService.ACTION_REQUEST_OWNER_PURGE, null);
            require(helper, NexusDeathNodeAdminService.pendingConfirmationFor(other.getUUID()) == null,
                    "A foreign player received an owner-delete confirmation");
            require(helper, units(level).get(node.id()).isPresent(),
                    "A foreign player removed another owner's death node");

            NexusDeathNodeAdminService.handleAction(
                    owner, node.id(), NexusDeathNodeAdminService.ACTION_REQUEST_OWNER_PURGE, null);
            NexusDeathNodeAdminService.DestructiveConfirmation confirmation =
                    NexusDeathNodeAdminService.pendingConfirmationFor(owner.getUUID());
            require(helper, confirmation != null
                            && NexusDeathNodeAdminService.ACTION_OWNER_PURGE.equals(confirmation.action()),
                    "The owner did not receive a bound delete confirmation");

            NexusDeathNodeAdminService.handleAction(
                    owner,
                    node.id(),
                    NexusDeathNodeAdminService.ACTION_OWNER_PURGE,
                    confirmation.token()
            );
            require(helper, units(level).get(node.id()).isEmpty(),
                    "Confirmed owner deletion retained the death node");
            require(helper, !discovery.hasDiscovered(owner.getUUID(), node.id())
                            && !discovery.isFavorite(owner.getUUID(), node.id())
                            && !discovery.hasDiscovered(other.getUUID(), node.id())
                            && !discovery.isFavorite(other.getUUID(), node.id()),
                    "Confirmed owner deletion retained discovery or favorite references");
            require(helper, units(level).recoverDeathUnit(node.id(), level.getGameTime()),
                    "Recovery did not treat the owner-deleted node as an idempotent success");
            helper.succeed();
        } finally {
            NexusDeathNodeAdminService.clearSession(owner.getUUID());
            NexusDeathNodeAdminService.clearSession(other.getUUID());
            owner.discard();
            other.discard();
        }
    }

    private static ServerPlayer createPlayer(GameTestHelper helper) {
        BlockPos absolutePos = helper.absolutePos(PLAYER_POS);
        helper.getLevel().setBlockAndUpdate(absolutePos.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(absolutePos, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(absolutePos.above(), Blocks.AIR.defaultBlockState());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(absolutePos.getX() + 0.5D, absolutePos.getY(), absolutePos.getZ() + 0.5D, 0.0F, 0.0F);
        return player;
    }

    private static NexusSpaceUnitSavedData units(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
    }

    private static NexusSpaceDiscoverySavedData discovery(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
