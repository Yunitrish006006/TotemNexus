package dev.totem.nexus.space;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class NexusMapTeleportCommitGameTest {
    private static final int HELD_MAP_ID = 1_900_000_001;
    private static final int REPLACEMENT_MAP_ID = 1_900_000_002;
    private static final int CONFLICTING_MAP_ID = 1_900_000_003;
    private static final int DESTINATION_MAP_ID = 1_900_000_004;

    @GameTest
    public void commitRequiresCurrentPlayerSourceAndSameCompatibleHeldMap(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var mapId = new MapId(HELD_MAP_ID);
        var nativeData = MapItemSavedData.createFresh(256, -256, (byte) 1, false, false, level.dimension());
        level.setMapData(mapId, nativeData);
        var bootstrap = level.getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusMapRecordSavedData.TYPE).bootstrap(mapId, nativeData);
        if (bootstrap.result() == NexusMapRecordSavedData.RegisterResult.CONFLICT)
            helper.fail("GameTest MapId record conflicted before commit validation");
        player.setItemInHand(InteractionHand.MAIN_HAND, filledMap(mapId));

        if (!NexusMapTeleportCommitAuthority.hasCurrentPlayerSource(
                player, "player", player.getUUID()))
            helper.fail("Current player position was not accepted as the map teleport source");
        if (NexusMapTeleportCommitAuthority.hasCurrentPlayerSource(
                player, "lodestone", player.getUUID()))
            helper.fail("Map center/lodestone was accepted as the map teleport source");
        if (NexusMapTeleportCommitAuthority.resolveHeldRecord(
                player, InteractionHand.MAIN_HAND, mapId).isEmpty())
            helper.fail("Compatible same-hand MapId record was rejected");
        var execution = new NexusTeleportExecutionSession(
                player.getUUID(), "player", player.getUUID(), UUID.randomUUID(), SpaceUnitType.LODESTONE,
                player.level().dimension(), player.blockPosition(), TeleportInterfaceType.FILLED_MAP,
                InteractionHand.MAIN_HAND, mapId, true, false, 2);
        var next = execution.tick();
        if (!mapId.equals(next.mapId()) || next.interactionHand() != InteractionHand.MAIN_HAND
                || next.remainingTicks() != 1) {
            helper.fail("Teleport preparation did not retain its exact held MapId identity");
        }

        var replacementId = new MapId(REPLACEMENT_MAP_ID);
        var replacementData = MapItemSavedData.createFresh(256, -256, (byte) 1, false, false, level.dimension());
        level.setMapData(replacementId, replacementData);
        level.getServer().overworld().getDataStorage().computeIfAbsent(NexusMapRecordSavedData.TYPE)
                .bootstrap(replacementId, replacementData);
        player.setItemInHand(InteractionHand.MAIN_HAND, filledMap(replacementId));
        if (NexusMapTeleportCommitAuthority.resolveHeldRecord(
                player, InteractionHand.MAIN_HAND, mapId).isPresent()) {
            helper.fail("A different held MapId authorized the pending teleport");
        }

        var conflictingId = new MapId(CONFLICTING_MAP_ID);
        var conflictingNative = MapItemSavedData.createFresh(
                384, -256, (byte) 1, false, false, level.dimension());
        level.setMapData(conflictingId, conflictingNative);
        var conflictingRecord = new NexusMapRecordSavedData.Entry(
                conflictingId.id(), level.dimension(), 512, -256,
                1, false, 1, List.of(), Optional.empty(), Optional.empty(), 0L);
        level.getServer().overworld().getDataStorage().computeIfAbsent(NexusMapRecordSavedData.TYPE)
                .register(conflictingRecord);
        player.setItemInHand(InteractionHand.MAIN_HAND, filledMap(conflictingId));
        if (NexusMapTeleportCommitAuthority.resolveHeldRecord(
                player, InteractionHand.MAIN_HAND, conflictingId).isPresent()) {
            helper.fail("Contradictory native geometry authorized teleport commit");
        }
        player.discard();
        helper.succeed();
    }

    @GameTest
    public void commitRecomputesDestinationForCurrentHolder(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var mapId = new MapId(DESTINATION_MAP_ID);
        var nativeData = MapItemSavedData.createFresh(0, 0, (byte) 1, false, false, level.dimension());
        level.setMapData(mapId, nativeData);
        var bootstrap = level.getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusMapRecordSavedData.TYPE).bootstrap(mapId, nativeData);
        if (bootstrap.result() == NexusMapRecordSavedData.RegisterResult.CONFLICT)
            helper.fail("GameTest destination MapId record conflicted before validation");
        player.setItemInHand(InteractionHand.MAIN_HAND, filledMap(mapId));
        var context = new TeleportInterfaceContext(
                player.getUUID(), TeleportInterfaceType.FILLED_MAP, "player", player.getUUID(),
                InteractionHand.MAIN_HAND, mapId, null, 0L, Long.MAX_VALUE);
        var visible = unit(1, UUID.randomUUID(), level.dimension(), nativeData, SpaceUnitVisibility.PUBLIC);
        var hidden = unit(2, UUID.randomUUID(), level.dimension(), nativeData, SpaceUnitVisibility.PRIVATE);
        NexusSpaceDiscoverySavedData.loadCanonical(level.getServer().overworld().getDataStorage())
                .markDiscovered(player.getUUID(), visible.id());

        if (!NexusMapTeleportCommitAuthority.allowsDestination(player, context, visible))
            helper.fail("Commit rejected the current holder's public discovered destination");
        if (NexusMapTeleportCommitAuthority.allowsDestination(player, context, hidden))
            helper.fail("Commit exposed a private destination to the current holder");
        player.discard();
        helper.succeed();
    }

    private static NexusSpaceUnitRecord unit(
            int id,
            UUID owner,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            MapItemSavedData map,
            SpaceUnitVisibility visibility) {
        return new NexusSpaceUnitRecord(
                new UUID(0L, id), SpaceUnitType.LODESTONE,
                dimension,
                new BlockPos(map.centerX, 64, map.centerZ), owner, "Target " + id,
                visibility, SpaceUnitStatus.ACTIVE, Set.of(), Set.of(),
                SpaceStructureSnapshot.EMPTY, 1L, 1L);
    }

    private static ItemStack filledMap(net.minecraft.world.level.saveddata.maps.MapId mapId) {
        var stack = new ItemStack(Items.FILLED_MAP);
        stack.set(DataComponents.MAP_ID, mapId);
        return stack;
    }
}
