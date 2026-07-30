package dev.totem.nexus.space;

import dev.totem.nexus.network.RequestDeathNodeAdminPayload;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/** Proves the external Nexus authority rejects forged administration queries. */
public final class NexusDeathNodeAdminAuthorizationGameTest {
    private static final BlockPos PLAYER_POS = new BlockPos(2, 2, 2);
    private static final BlockPos NODE_POS = new BlockPos(6, 2, 2);

    @SuppressWarnings("removal")
    @GameTest(maxTicks = 40)
    public void nonAdministratorCannotReceiveASnapshotFromAForgedQuery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = createPlayer(helper);
        NexusSpaceUnitRecord node = units(level).createDeathUnit(level, helper.absolutePos(NODE_POS), player);

        try {
            boolean snapshotSent = NexusDeathNodeAdminService.sendSnapshot(
                    player,
                    new RequestDeathNodeAdminPayload(player.getUUID().toString(), "", "", 0L, 0L, 0)
            );

            require(helper, !NexusDeathNodeAdminService.canManage(player),
                    "GameTest mock player unexpectedly has death-node administration permission");
            require(helper, !snapshotSent,
                    "Unauthorized query unexpectedly received a private death-node snapshot");
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

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
