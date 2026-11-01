package dev.totem.nexus.space;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.List;
import java.util.Optional;

public final class NexusMapOpenPreflightGameTest {
    @GameTest
    public void ordinaryFilledMapBootstrapsBeforeSessionPublication(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var mapId = unusedMapId(level);
        var nativeData = MapItemSavedData.createFresh(333, -447, (byte) 2, false, false, level.dimension());
        level.setMapData(mapId, nativeData);
        ItemStack held = filledMap(mapId);
        ItemStack before = held.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        var sessions = new TeleportInterfaceSessionStore();
        var authority = new NexusTeleportInterfaceAuthority(sessions);

        var context = authority.establishPlayerAnchor(player, InteractionHand.MAIN_HAND).orElse(null);
        var records = level.getServer().overworld().getDataStorage().get(NexusMapRecordSavedData.TYPE);
        var record = records == null ? null : records.resolve(mapId, nativeData).orElse(null);
        if (context == null || record == null
                || sessions.require(player.getUUID(), "player", player.getUUID(),
                level.getServer().overworld().getGameTime()).isEmpty()
                || nativeData.centerX != 192 || nativeData.centerZ != -320
                || record.centerX() != nativeData.centerX || record.centerZ() != nativeData.centerZ
                || record.nativeScale() != 2 || record.logicalScale() != 2
                || !ItemStack.isSameItemSameComponents(before, player.getMainHandItem())) {
            helper.fail("Ordinary held map session was published without a compatible MapId record");
        }
        player.discard();
        helper.succeed();
    }

    @GameTest
    public void contradictoryMapIdRecordPublishesNoSession(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var mapId = unusedMapId(level);
        var nativeData = MapItemSavedData.createFresh(640, 640, (byte) 1, false, false, level.dimension());
        level.setMapData(mapId, nativeData);
        player.setItemInHand(InteractionHand.MAIN_HAND, filledMap(mapId));
        var records = level.getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusMapRecordSavedData.TYPE);
        var conflicting = new NexusMapRecordSavedData.Entry(
                mapId.id(), Level.NETHER, nativeData.centerX, nativeData.centerZ,
                1, false, 1, List.of(), Optional.empty(), Optional.empty(), 0L);
        if (records.register(conflicting) != NexusMapRecordSavedData.RegisterResult.INSERTED) {
            helper.fail("GameTest MapId unexpectedly occupied");
        }
        var sessions = new TeleportInterfaceSessionStore();
        var authority = new NexusTeleportInterfaceAuthority(sessions);

        if (authority.establishPlayerAnchor(player, InteractionHand.MAIN_HAND).isPresent()
                || sessions.require(player.getUUID(), "player", player.getUUID(),
                level.getServer().overworld().getGameTime()).isPresent()
                || !conflicting.equals(records.get(mapId).orElse(null))) {
            helper.fail("Contradictory MapId record opened or was replaced");
        }
        player.discard();
        helper.succeed();
    }

    @GameTest
    public void missingNativeMapDataPublishesNeitherRecordNorSession(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var mapId = unusedMapId(level);
        ItemStack held = filledMap(mapId);
        ItemStack before = held.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        var sessions = new TeleportInterfaceSessionStore();

        if (new NexusTeleportInterfaceAuthority(sessions)
                .establishPlayerAnchor(player, InteractionHand.MAIN_HAND).isPresent()
                || sessions.require(player.getUUID(), "player", player.getUUID(),
                level.getServer().overworld().getGameTime()).isPresent()
                || !ItemStack.isSameItemSameComponents(before, player.getMainHandItem())) {
            helper.fail("Missing native map data created a record/session or mutated the held item");
        }
        var records = level.getServer().overworld().getDataStorage().get(NexusMapRecordSavedData.TYPE);
        if (records != null && records.get(mapId).isPresent()) {
            helper.fail("Missing native map data published a partial MapId record");
        }
        player.discard();
        helper.succeed();
    }

    private static ItemStack filledMap(net.minecraft.world.level.saveddata.maps.MapId mapId) {
        var stack = new ItemStack(Items.FILLED_MAP);
        stack.set(DataComponents.MAP_ID, mapId);
        return stack;
    }

    private static net.minecraft.world.level.saveddata.maps.MapId unusedMapId(
            net.minecraft.server.level.ServerLevel level) {
        var storage = level.getServer().overworld().getDataStorage();
        var records = storage.get(NexusMapRecordSavedData.TYPE);
        var legacy = NexusMapBindingSavedData.loadCanonical(storage);
        for (int attempt = 0; attempt < 1024; attempt++) {
            var candidate = level.getFreeMapId();
            if (level.getMapData(candidate) == null
                    && (records == null || records.get(candidate).isEmpty())
                    && legacy.get(candidate).isEmpty()) return candidate;
        }
        throw new IllegalStateException("Could not allocate an unused GameTest MapId");
    }
}
