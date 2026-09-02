package dev.totem.nexus.client;

import dev.totem.nexus.network.SpaceUnitMapPayload;
import dev.totem.nexus.space.TeleportInterfaceType;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Native-scale visual coverage for list-only compass, marker-only map, and management presentations. */
@SuppressWarnings("UnstableApiUsage")
public final class NexusSpaceUnitMapVisualGameTest implements FabricClientGameTest {
    private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final UUID COMPASS_TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000411");
    private static final UUID MAP_TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000402");
    private static final int MAP_ID = 7401;

    @Override
    public void runTest(ClientGameTestContext context) {
        selectLanguage(context, "en_us", "Nexus Compass");
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();
            context.getInput().resizeWindow(1280, 720);

            context.setScreen(() -> new NexusSpaceUnitMapScreen(managementPayload()));
            context.waitForScreen(NexusSpaceUnitMapScreen.class);
            context.waitFor(client -> ((NexusSpaceUnitMapScreen) client.gui.screen())
                    .managementOnlyPresentationForVisualTest());
            context.waitTicks(2);
            context.takeScreenshot("totem-nexus-management-only-book");
            context.setScreen(() -> null);
            context.waitForScreen(null);

            context.setScreen(() -> new NexusSpaceUnitMapScreen(compassPayload()));
            context.waitForScreen(NexusSpaceUnitMapScreen.class);
            context.waitFor(client -> ((NexusSpaceUnitMapScreen) client.gui.screen())
                    .compassTeleportPresentationForVisualTest());
            selectCompassDestination(context);
            context.waitFor(client -> ((NexusSpaceUnitMapScreen) client.gui.screen())
                    .teleportButtonActiveForVisualTest());
            context.waitTicks(2);
            context.takeScreenshot("totem-nexus-compass-teleport-list");
            context.setScreen(() -> null);
            context.waitForScreen(null);

            context.runOnClient(client -> {
                MapItemSavedData data = MapItemSavedData.createFresh(
                        0, 0, (byte) 0, false, false, Level.OVERWORLD);
                fillVanillaMapColors(data);
                client.level.overrideMapData(new MapId(MAP_ID), data);
            });
            context.setScreen(() -> new NexusSpaceUnitMapScreen(filledMapPayload()));
            context.waitForScreen(NexusSpaceUnitMapScreen.class);
            context.waitFor(client -> ((NexusSpaceUnitMapScreen) client.gui.screen())
                    .renderedMapLabelsForVisualTest(
                            List.of("Home Nexus", "East Archive", "Unnamed Nexus")));
            selectMapDestination(context);
            context.waitFor(client -> ((NexusSpaceUnitMapScreen) client.gui.screen())
                    .teleportButtonActiveForVisualTest());
            context.waitTicks(2);
            context.takeScreenshot("totem-nexus-map-coordinate-teleport");
            context.setScreen(() -> null);
            context.waitForScreen(null);

            selectLanguage(context, "zh_tw", "Nexus 羅盤");
            context.setScreen(() -> new NexusSpaceUnitMapScreen(compassPayload()));
            context.waitForScreen(NexusSpaceUnitMapScreen.class);
            context.waitFor(client -> ((NexusSpaceUnitMapScreen) client.gui.screen())
                    .compassTeleportPresentationForVisualTest());
            selectCompassDestination(context);
            context.waitFor(client -> ((NexusSpaceUnitMapScreen) client.gui.screen())
                    .teleportButtonActiveForVisualTest());
            context.waitTicks(2);
            context.takeScreenshot("totem-nexus-compass-teleport-list-zh-tw");
            context.setScreen(() -> null);
            context.waitForScreen(null);

            context.setScreen(() -> new NexusSpaceUnitMapScreen(filledMapPayload()));
            context.waitForScreen(NexusSpaceUnitMapScreen.class);
            context.waitFor(client -> ((NexusSpaceUnitMapScreen) client.gui.screen())
                    .renderedMapLabelsForVisualTest(
                            List.of("Home Nexus", "East Archive", "未命名 Nexus")));
            selectMapDestination(context);
            context.waitFor(client -> ((NexusSpaceUnitMapScreen) client.gui.screen())
                    .teleportButtonActiveForVisualTest());
            context.waitTicks(2);
            context.takeScreenshot("totem-nexus-map-coordinate-teleport-zh-tw");
            context.setScreen(() -> null);
            context.waitForScreen(null);

            selectLanguage(context, "en_us", "Nexus Compass");
        }
    }

    private static void selectCompassDestination(ClientGameTestContext context) {
        context.runOnClient(client -> {
            NexusSpaceUnitMapScreen screen = (NexusSpaceUnitMapScreen) client.gui.screen();
            screen.setFocused(null);
            for (int attempt = 0; attempt < 20
                    && !COMPASS_TARGET_ID.equals(screen.selectedUnitIdForVisualTest()); attempt++) {
                if (!screen.keyPressed(new KeyEvent(264, 0, 0))) {
                    throw new AssertionError("Compass destination keyboard selection was not consumed");
                }
            }
            if (!COMPASS_TARGET_ID.equals(screen.selectedUnitIdForVisualTest())) {
                throw new AssertionError("Compass keyboard navigation did not reach the expected destination");
            }
        });
    }

    private static void selectMapDestination(ClientGameTestContext context) {
        context.runOnClient(client -> {
            NexusSpaceUnitMapScreen screen = (NexusSpaceUnitMapScreen) client.gui.screen();
            if (!screen.mapOnlyTeleportPresentationForVisualTest()) {
                throw new AssertionError("Nexus map rendered a destination list");
            }
            int[] point = screen.mapEntryCenterForVisualTest(MAP_TARGET_ID);
            if (point.length != 2 || !screen.mouseClicked(new MouseButtonEvent(
                    point[0], point[1], new MouseButtonInfo(0, 0)), false)) {
                throw new AssertionError("Nexus map coordinate did not select its destination marker");
            }
            if (!screen.mouseReleased(new MouseButtonEvent(
                    point[0], point[1], new MouseButtonInfo(0, 0)))) {
                throw new AssertionError("Nexus map coordinate click was not released");
            }
            if (!MAP_TARGET_ID.equals(screen.selectedUnitIdForVisualTest())) {
                throw new AssertionError("Nexus map selected a different destination than the clicked marker");
            }

            int[] center = screen.mapViewportCenterForVisualTest();
            if (!screen.mouseScrolled(center[0], center[1], 0.0D, 1.0D)
                    || screen.mapViewForVisualTest()[0] != 2) {
                throw new AssertionError("Nexus map mouse wheel did not zoom to 200%");
            }
            if (!screen.mouseClicked(new MouseButtonEvent(
                    center[0], center[1], new MouseButtonInfo(0, 0)), false)
                    || !screen.mouseDragged(new MouseButtonEvent(
                    center[0], center[1] - 28, new MouseButtonInfo(0, 0)), 0.0D, -28.0D)
                    || !screen.mouseReleased(new MouseButtonEvent(
                    center[0], center[1] - 28, new MouseButtonInfo(0, 0)))) {
                throw new AssertionError("Nexus map drag-to-pan gesture was not consumed");
            }
            int[] view = screen.mapViewForVisualTest();
            if (view[0] != 2 || view[2] >= 0) {
                throw new AssertionError("Nexus map drag did not update the visible pan state");
            }
            if (!MAP_TARGET_ID.equals(screen.selectedUnitIdForVisualTest())) {
                throw new AssertionError("Dragging the Nexus map changed the selected destination");
            }
        });
    }

    private static void selectLanguage(ClientGameTestContext context, String language, String expectedCompassTitle) {
        AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
        context.runOnClient(client -> {
            client.options.languageCode = language;
            client.getLanguageManager().setSelected(language);
            reload.set(client.reloadResourcePacks());
        });
        context.waitFor(client -> reload.get() != null && reload.get().isDone());
        context.waitFor(client -> client.gui.overlay() == null);
        context.runOnClient(client -> {
            String title = I18n.get("container.deadrecall.space_unit.compass");
            if (!expectedCompassTitle.equals(title)) {
                throw new AssertionError(language + " Nexus compass resources were not loaded: " + title);
            }
        });
    }

    private static SpaceUnitMapPayload managementPayload() {
        return new SpaceUnitMapPayload(
                SOURCE_ID, "lodestone", "Home Nexus", "minecraft:overworld", 0, 64, 0,
                TeleportInterfaceType.BOOK, SpaceUnitMapPayload.NO_MAP_ID,
                List.of(
                        entry(SOURCE_ID, "Home Nexus", 0, 0,
                                "message.deadrecall.space_unit.interface_bonus.book.active", false),
                        entry(UUID.fromString("00000000-0000-0000-0000-000000000499"),
                                "Hidden Remote Nexus", 24, 24,
                                "message.deadrecall.space_unit.interface_bonus.book.active", true)));
    }

    private static SpaceUnitMapPayload compassPayload() {
        List<SpaceUnitMapPayload.Entry> entries = new ArrayList<>();
        entries.add(entry(SOURCE_ID, "Home Nexus", 0, 0,
                "message.deadrecall.space_unit.interface_bonus.compass", false));
        entries.add(entry(COMPASS_TARGET_ID, "Archive Relay", 28, -20,
                "message.deadrecall.space_unit.interface_bonus.compass", true));
        for (int index = 0; index < 9; index++) {
            entries.add(entry(UUID.fromString(String.format(
                            "00000000-0000-0000-0000-%012d", 420 + index)),
                    "Relay " + (index + 1), 40 + index * 6, 12 + index * 4,
                    "message.deadrecall.space_unit.interface_bonus.compass", true));
        }
        return new SpaceUnitMapPayload(
                SOURCE_ID, "lodestone", "Home Nexus", "minecraft:overworld", 0, 64, 0,
                TeleportInterfaceType.COMPASS, SpaceUnitMapPayload.NO_MAP_ID, entries);
    }

    private static SpaceUnitMapPayload filledMapPayload() {
        return new SpaceUnitMapPayload(
                SOURCE_ID, "lodestone", "Home Nexus", "minecraft:overworld", 0, 64, 0,
                TeleportInterfaceType.FILLED_MAP, MAP_ID,
                List.of(
                        entry(SOURCE_ID, "Home Nexus", 0, 0,
                                "message.deadrecall.space_unit.interface_bonus.filled_map.active", false),
                        entry(MAP_TARGET_ID,
                                "East Archive", 28, -20,
                                "message.deadrecall.space_unit.interface_bonus.filled_map.active", true),
                        entry(UUID.fromString("00000000-0000-0000-0000-000000000403"),
                                "", -32, 26,
                                "message.deadrecall.space_unit.interface_bonus.filled_map.active", true)));
    }

    private static SpaceUnitMapPayload.Entry entry(
            UUID id, String name, int x, int z, String interfaceBonusMessageKey, boolean canTeleport) {
        return new SpaceUnitMapPayload.Entry(
                id, "lodestone", name, "private", false, "minecraft:overworld", x, 64, z,
                0.92D, 2, Math.max(Math.abs(x), Math.abs(z)),
                0, 0, 0, 0, 0, 20,
                0, 0,
                20, 16,
                4, 3,
                0,
                0, 0,
                true, interfaceBonusMessageKey,
                false, true, true, 1, 2, canTeleport,
                canTeleport ? "" : "message.deadrecall.space_unit.teleport_blocked.same_source");
    }

    private static void fillVanillaMapColors(MapItemSavedData data) {
        for (int z = 0; z < 128; z++) {
            for (int x = 0; x < 128; x++) {
                MapColor color;
                if (x < 24 || (x < 46 && z > 76)) {
                    color = MapColor.WATER;
                } else if (z > 92) {
                    color = MapColor.SAND;
                } else if ((x - 82) * (x - 82) + (z - 42) * (z - 42) < 380) {
                    color = MapColor.STONE;
                } else {
                    color = MapColor.GRASS;
                }
                MapColor.Brightness brightness = ((x / 9) + (z / 13)) % 3 == 0
                        ? MapColor.Brightness.HIGH
                        : ((x / 11) + (z / 7)) % 3 == 0
                        ? MapColor.Brightness.LOW
                        : MapColor.Brightness.NORMAL;
                data.colors[x + z * 128] = color.getPackedId(brightness);
            }
        }
    }
}
