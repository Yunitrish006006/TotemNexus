package dev.totem.nexus.client;

import dev.totem.core.api.v1.client.observer.ObserverScreenContext;
import dev.totem.core.api.v1.client.observer.ObserverScreenHandle;
import dev.totem.core.api.v1.client.observer.ObserverScreenSnapshot;
import dev.totem.nexus.mixin.NexusMapItemSavedDataInvoker;
import dev.totem.nexus.network.SpaceUnitMapPayload;
import dev.totem.nexus.space.TeleportInterfaceType;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Proves Observer zoom restores real finer map layers and renders only the relayed target marker. */
@SuppressWarnings("UnstableApiUsage")
public final class NexusObserverMapDetailClientGameTest implements FabricClientGameTest {
    private static final int BASE_ID = 8891;
    private static final int SCALE_ONE_ID = 8892;
    private static final int SCALE_ZERO_ID = 8893;
    private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000008891");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            AtomicReference<SpaceUnitMapPayload> payload = new AtomicReference<>();
            context.runOnClient(client -> {
                int centerX = Mth.floor(client.player.getX());
                int centerZ = Mth.floor(client.player.getZ());
                var coarse = NexusMapItemSavedDataInvoker.totem$createExact(centerX, centerZ, (byte) 2,
                        false, false, false, Level.OVERWORLD);
                var middle = NexusMapItemSavedDataInvoker.totem$createExact(centerX, centerZ, (byte) 1,
                        false, false, false, Level.OVERWORLD);
                var finest = NexusMapItemSavedDataInvoker.totem$createExact(centerX, centerZ, (byte) 0,
                        false, false, false, Level.OVERWORLD);
                java.util.Arrays.fill(coarse.colors, MapColor.GRASS.getPackedId(MapColor.Brightness.NORMAL));
                java.util.Arrays.fill(middle.colors, MapColor.SAND.getPackedId(MapColor.Brightness.NORMAL));
                java.util.Arrays.fill(finest.colors, MapColor.WATER.getPackedId(MapColor.Brightness.NORMAL));
                client.level.overrideMapData(new MapId(BASE_ID), coarse);
                client.level.overrideMapData(new MapId(SCALE_ONE_ID), middle);
                client.level.overrideMapData(new MapId(SCALE_ZERO_ID), finest);
                NexusMapDetailClientState.setForVisualTest(BASE_ID, List.of(SCALE_ZERO_ID, SCALE_ONE_ID));
                payload.set(mapPayload(centerX, centerZ));
            });

            NexusObserverScreenProvider provider = new NexusObserverScreenProvider();
            ObserverScreenSnapshot captured = context.computeOnClient(client -> provider.capture(
                    new NexusSpaceUnitMapScreen(payload.get()), 1).orElseThrow());
            require(captured.metadata().containsKey("player_marker_x"),
                    "Owner capture omitted the bounded map-local player marker");

            ObserverScreenSnapshot zoom2 = withZoom(captured, 1, 2, true);
            ObserverScreenHandle handle = context.computeOnClient(client -> provider.create(
                    new ObserverScreenContext(UUID.randomUUID(), "Target", () -> { }), zoom2));
            context.runOnClient(client -> client.setScreenAndShow(handle.screen()));
            context.waitFor(client -> {
                var probe = (NexusMapDetailVisualTestAccess) client.gui.screen();
                List<Integer> rendered = probe.totem$detailLayersRenderedForVisualTest();
                return probe.totem$observedPlayerMarkerRenderedForVisualTest()
                        && !probe.totem$localPlayerMarkerRenderedForVisualTest()
                        && rendered.contains(SCALE_ONE_ID) && !rendered.contains(SCALE_ZERO_ID);
            }, 100);

            ObserverScreenSnapshot zoom4 = withZoom(captured, 2, 4, true);
            context.runOnClient(client -> handle.applySnapshot(zoom4));
            context.waitFor(client -> {
                List<Integer> rendered = ((NexusMapDetailVisualTestAccess) client.gui.screen())
                        .totem$detailLayersRenderedForVisualTest();
                return rendered.contains(SCALE_ONE_ID) && rendered.contains(SCALE_ZERO_ID);
            }, 100);

            ObserverScreenSnapshot noMarker = withZoom(captured, 3, 4, false);
            context.runOnClient(client -> handle.applySnapshot(noMarker));
            context.waitFor(client -> {
                var probe = (NexusMapDetailVisualTestAccess) client.gui.screen();
                return !probe.totem$observedPlayerMarkerRenderedForVisualTest()
                        && !probe.totem$localPlayerMarkerRenderedForVisualTest();
            }, 100);
            context.takeScreenshot("totem-nexus-observer-restored-detail-no-local-substitution");
        }
    }

    private static ObserverScreenSnapshot withZoom(
            ObserverScreenSnapshot source, long sequence, int zoom, boolean retainMarker) {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>(source.metadata());
        metadata.put("map_zoom", Integer.toString(zoom));
        if (!retainMarker) {
            metadata.remove("player_marker_off_map");
            metadata.remove("player_marker_x");
            metadata.remove("player_marker_y");
            metadata.remove("player_marker_rotation");
        }
        return new ObserverScreenSnapshot(source.familyId(), source.variant(), source.protocolVersion(), sequence,
                source.title(), source.slots(), source.data(), metadata, source.ownerPayload());
    }

    private static SpaceUnitMapPayload mapPayload(int x, int z) {
        SpaceUnitMapPayload.Entry source = new SpaceUnitMapPayload.Entry(
                SOURCE_ID, "lodestone", "Observer Detail Home", "private", false,
                "minecraft:overworld", x, 64, z,
                1.0D, 1, 0,
                0, 0, 0, 0, 0, 20,
                0, 0, 20, 20, 4, 4, 0, 0, 0,
                true, "message.totem.space_unit.interface_bonus.filled_map.active",
                false, true, true, 0, 0, false,
                "message.totem.space_unit.teleport_blocked.same_source");
        return new SpaceUnitMapPayload(
                SOURCE_ID, "lodestone", "Observer Detail Home", "minecraft:overworld",
                x, 64, z, TeleportInterfaceType.FILLED_MAP, BASE_ID, List.of(source));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
