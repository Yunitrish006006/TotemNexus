package dev.totem.nexus.space;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusMapOpenPreflightTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void successfulBootstrapPublishesSessionAfterRecord() {
        var playerId = UUID.randomUUID();
        var id = new MapId(201);
        var nativeData = map(128, -256, (byte) 2);
        var records = new NexusMapRecordSavedData();
        var sessions = new TeleportInterfaceSessionStore();

        var context = NexusMapOpenPreflight.establish(
                playerId,
                InteractionHand.MAIN_HAND,
                100L,
                new NexusHeldMapResolver.ResolvedMap(id, nativeData),
                () -> records,
                sessions,
                600L).orElseThrow();

        assertEquals(id, context.mapId());
        assertEquals(playerId, context.sourceId());
        assertEquals("player", context.sourceType());
        assertTrue(records.resolve(id, nativeData).isPresent());
        assertEquals(context, sessions.require(playerId, "player", playerId, 100L).orElseThrow());
    }

    @Test
    void compatibleExistingExtensionIsPreservedBeforeSessionPublication() {
        var playerId = UUID.randomUUID();
        var id = new MapId(202);
        var nativeData = map(0, 0, (byte) 4);
        var extended = new NexusMapRecordSavedData.Entry(
                id.id(), Level.OVERWORLD, nativeData.centerX, nativeData.centerZ,
                4, false, 6, List.of(200, 201), Optional.of("1".repeat(64)), Optional.empty(), 4L);
        var records = new NexusMapRecordSavedData();
        assertEquals(NexusMapRecordSavedData.RegisterResult.INSERTED, records.register(extended));

        assertTrue(NexusMapOpenPreflight.establish(
                playerId, InteractionHand.OFF_HAND, 20L,
                new NexusHeldMapResolver.ResolvedMap(id, nativeData),
                () -> records, new TeleportInterfaceSessionStore(), 600L).isPresent());
        assertEquals(extended, records.get(id).orElseThrow());
    }

    @Test
    void contradictoryRecordCreatesNoSessionAndIsNotReplaced() {
        var playerId = UUID.randomUUID();
        var id = new MapId(203);
        var nativeData = map(0, 0, (byte) 1);
        var conflicting = new NexusMapRecordSavedData.Entry(
                id.id(), Level.NETHER, nativeData.centerX, nativeData.centerZ,
                1, false, 1, List.of(), Optional.empty(), Optional.empty(), 0L);
        var records = new NexusMapRecordSavedData();
        assertEquals(NexusMapRecordSavedData.RegisterResult.INSERTED, records.register(conflicting));
        var sessions = new TeleportInterfaceSessionStore();

        assertTrue(NexusMapOpenPreflight.establish(
                playerId, InteractionHand.MAIN_HAND, 30L,
                new NexusHeldMapResolver.ResolvedMap(id, nativeData),
                () -> records, sessions, 600L).isEmpty());

        assertEquals(conflicting, records.get(id).orElseThrow());
        assertTrue(sessions.require(playerId, "player", playerId, 30L).isEmpty());
    }

    @Test
    void unavailableStorageCreatesNeitherRecordNorSession() {
        var playerId = UUID.randomUUID();
        var id = new MapId(204);
        var nativeData = map(0, 0, (byte) 0);
        var sessions = new TeleportInterfaceSessionStore();

        assertTrue(NexusMapOpenPreflight.establish(
                playerId, InteractionHand.MAIN_HAND, 40L,
                new NexusHeldMapResolver.ResolvedMap(id, nativeData),
                () -> { throw new IllegalStateException("storage unavailable"); },
                sessions, 600L).isEmpty());
        assertTrue(sessions.require(playerId, "player", playerId, 40L).isEmpty());
    }

    @Test
    void invalidPreflightInputsFailWithoutPublishingSession() {
        var playerId = UUID.randomUUID();
        var held = new NexusHeldMapResolver.ResolvedMap(new MapId(205), map(0, 0, (byte) 0));
        var records = new NexusMapRecordSavedData();
        var sessions = new TeleportInterfaceSessionStore();

        assertFalse(NexusMapOpenPreflight.establish(
                playerId, InteractionHand.MAIN_HAND, Long.MAX_VALUE, held,
                () -> records, sessions, 1L).isPresent());
        assertTrue(records.get(held.mapId()).isEmpty());
        assertTrue(sessions.require(playerId, "player", playerId, 0L).isEmpty());
    }

    private static MapItemSavedData map(int x, int z, byte scale) {
        return MapItemSavedData.createFresh(x, z, scale, false, false, Level.OVERWORLD);
    }
}
