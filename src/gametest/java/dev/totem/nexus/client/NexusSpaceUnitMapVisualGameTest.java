package dev.totem.nexus.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.totem.nexus.mixin.NexusMapItemSavedDataInvoker;
import dev.totem.nexus.network.SpaceUnitMapPayload;
import dev.totem.nexus.space.TeleportInterfaceType;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.components.Button;
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
    private static final int MAP_SCALE_ONE_ID = 7402;
    private static final int MAP_SCALE_ZERO_ID = 7403;

    @Override
    public void runTest(ClientGameTestContext context) {
        selectLanguage(context, "en_us", "Nexus Compass");
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getConnection().waitForChunksRender();
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

            exerciseRecoveryList(context, "totem-nexus-recovery-teleport-list-en-us");
            exerciseFriendsAndPortablePreview(context, "en_us");

            installMapDetailFixture(context);
            context.setScreen(() -> new NexusSpaceUnitMapScreen(filledMapPayload()));
            context.waitForScreen(NexusSpaceUnitMapScreen.class);
            context.waitFor(client -> ((NexusSpaceUnitMapScreen) client.gui.screen())
                    .renderedMapLabelsForVisualTest(
                            List.of("Home Nexus", "East Archive", "Unnamed Nexus")));
            context.waitTicks(2);
            context.takeScreenshot("totem-nexus-map-detail-base");
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

            exerciseRecoveryList(context, "totem-nexus-recovery-teleport-list-zh-tw");
            exerciseFriendsAndPortablePreview(context, "zh_tw");

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

            // A newly surveyed page outside the original 128-block footprint, with no ancestor maps.
            context.runOnClient(client -> {
                var detail = NexusMapItemSavedDataInvoker.totem$createExact(
                        192, 64, (byte)0, false, false, true, Level.OVERWORLD);
                for(int z=0;z<128;z++) for(int x=0;x<128;x++)
                    detail.colors[x+z*128] = ((x/8+z/8)%2==0 ? MapColor.WATER : MapColor.SAND)
                            .getPackedId(MapColor.Brightness.NORMAL);
                client.level.overrideMapData(new MapId(7404), detail);
                NexusMapDetailClientState.setForVisualTest(MAP_ID, List.of(7404));
            });
            context.setScreen(() -> new NexusSpaceUnitMapScreen(filledMapPayload()));
            context.waitForScreen(NexusSpaceUnitMapScreen.class);
            context.runOnClient(client -> {
                var screen = (NexusSpaceUnitMapScreen)client.gui.screen();
                int[] center = screen.mapViewportCenterForVisualTest();
                screen.mouseScrolled(center[0], center[1], 0, 1);
                screen.mouseScrolled(center[0], center[1], 0, 1);
                screen.mouseClicked(new MouseButtonEvent(center[0], center[1], new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                screen.mouseDragged(new MouseButtonEvent(center[0]-192, center[1]-64, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), -192, -64);
                screen.mouseReleased(new MouseButtonEvent(center[0]-192, center[1]-64, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)));
                if(screen.mapViewForVisualTest()[0]!=4) throw new AssertionError("New region without ancestors cannot reach scale zero");
            });
            context.waitTicks(3);
            context.takeScreenshot("totem-nexus-new-outer-region-detail");
            context.setScreen(() -> null);
            context.waitForScreen(null);
            selectLanguage(context, "en_us", "Nexus Compass");
            exerciseMapPointerBounds(context);
        }
    }

    private static void installMapDetailFixture(ClientGameTestContext context) {
        context.runOnClient(client -> {
            MapItemSavedData coarse = NexusMapItemSavedDataInvoker.totem$createExact(
                    0, 0, (byte) 2, false, false, false, Level.OVERWORLD);
            MapItemSavedData middle = NexusMapItemSavedDataInvoker.totem$createExact(
                    0, 0, (byte) 1, false, false, false, Level.OVERWORLD);
            MapItemSavedData finest = NexusMapItemSavedDataInvoker.totem$createExact(
                    0, 0, (byte) 0, false, false, false, Level.OVERWORLD);
            fillVanillaMapColors(coarse, 0);
            fillVanillaMapColors(middle, 1);
            fillVanillaMapColors(finest, 2);
            client.level.overrideMapData(new MapId(MAP_ID), coarse);
            client.level.overrideMapData(new MapId(MAP_SCALE_ONE_ID), middle);
            client.level.overrideMapData(new MapId(MAP_SCALE_ZERO_ID), finest);
            NexusMapDetailClientState.setForVisualTest(
                    MAP_ID, List.of(MAP_SCALE_ZERO_ID, MAP_SCALE_ONE_ID));
        });
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
            if (!SOURCE_ID.equals(screen.selectedUnitIdForVisualTest())) {
                throw new AssertionError("Nexus map did not begin with its source selected");
            }

            int[] center = screen.mapViewportCenterForVisualTest();
            int[] viewport = screen.mapViewportForVisualTest();
            int firstAnchorX = center[0];
            int limitYAt2x = Math.max(0, (128 * viewport[4] * 2 - (viewport[3] - 2)) / 2);
            int firstAnchorY = center[1] - Math.min(16, limitYAt2x / 2);
            double[] beforeZoom = screen.mapPixelAtForVisualTest(firstAnchorX, firstAnchorY);
            if (!screen.mouseScrolled(firstAnchorX, firstAnchorY, 0.0D, 1.0D)
                    || screen.mapViewForVisualTest()[0] != 2) {
                throw new AssertionError("Nexus map mouse wheel did not reveal scale-1 detail at 2x");
            }
            double[] at2x = screen.mapPixelAtForVisualTest(firstAnchorX, firstAnchorY);
            assertMapAnchorStable(beforeZoom, at2x, viewport[4] * 2);

            int limitAt4x = Math.max(0, (128 * viewport[4] * 4 - (viewport[2] - 2)) / 2);
            int secondAnchorX = center[0] + Math.min(32, limitAt4x / 2);
            int secondAnchorY = center[1] - 16;
            double[] before4x = screen.mapPixelAtForVisualTest(secondAnchorX, secondAnchorY);
            if (!screen.mouseScrolled(secondAnchorX, secondAnchorY, 0.0D, 1.0D)
                    || screen.mapViewForVisualTest()[0] != 4) {
                throw new AssertionError("Nexus map mouse wheel did not reveal scale-0 detail at 4x");
            }
            double[] at4x = screen.mapPixelAtForVisualTest(secondAnchorX, secondAnchorY);
            assertMapAnchorStable(before4x, at4x, viewport[4] * 4);

            if (!screen.mouseScrolled(secondAnchorX, secondAnchorY, 0.0D, 1.0D)
                    || screen.mapViewForVisualTest()[0] != 4) {
                throw new AssertionError("Nexus map zoom exceeded its finest proven scale-0 detail");
            }

            int[] point = screen.mapEntryCenterForVisualTest(MAP_TARGET_ID);
            if (point.length != 2) {
                throw new AssertionError("Nexus map target marker was unavailable after zoom");
            }
            int[] beforeDrag = screen.mapViewForVisualTest();
            if (!screen.mouseClicked(new MouseButtonEvent(
                    point[0], point[1], new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false)
                    || !screen.mouseDragged(new MouseButtonEvent(
                    point[0], point[1] - 28, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), 0.0D, -28.0D)
                    || !screen.mouseReleased(new MouseButtonEvent(
                    point[0], point[1] - 28, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)))) {
                throw new AssertionError("Nexus map left-drag gesture was not consumed");
            }
            int[] afterDrag = screen.mapViewForVisualTest();
            if (afterDrag[0] != 4
                    || (afterDrag[1] == beforeDrag[1] && afterDrag[2] == beforeDrag[2])) {
                throw new AssertionError("Nexus map left-drag did not preserve zoom and update pan");
            }
            if (!SOURCE_ID.equals(screen.selectedUnitIdForVisualTest())) {
                throw new AssertionError("Dragging from an unselected Nexus marker incorrectly selected it");
            }

            point = screen.mapEntryCenterForVisualTest(MAP_TARGET_ID);
            if (point.length != 2 || !screen.mouseClicked(new MouseButtonEvent(
                    point[0], point[1], new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false)) {
                throw new AssertionError("Nexus map left click was not consumed for destination selection");
            }
            if (!screen.mouseReleased(new MouseButtonEvent(
                    point[0], point[1], new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)))) {
                throw new AssertionError("Nexus map destination left click was not released");
            }
            if (!MAP_TARGET_ID.equals(screen.selectedUnitIdForVisualTest())) {
                throw new AssertionError("Nexus map left click selected a different destination");
            }
        });
    }

    private static void assertMapAnchorStable(double[] before, double[] after, int pixelScale) {
        // Integer pan rounding permits at most half a screen pixel, not an
        // arbitrary map-pixel tolerance that gets looser at higher zoom.
        double tolerance = 0.5D / pixelScale + 1.0e-9D;
        if (before.length != 2 || after.length != 2
                || Math.abs(before[0] - after[0]) > tolerance
                || Math.abs(before[1] - after[1]) > tolerance) {
            throw new AssertionError("Nexus map cursor anchor drifted at pixel scale " + pixelScale
                    + ": " + java.util.Arrays.toString(before) + " -> " + java.util.Arrays.toString(after));
        }
    }

    private static void exerciseMapPointerBounds(ClientGameTestContext context) {
        int previousScale = context.computeOnClient(client -> client.options.guiScale().get());
        try {
            for (int guiScale : new int[]{2, 3}) {
                context.runOnClient(client -> client.options.guiScale().set(guiScale));
                context.setScreen(() -> new NexusSpaceUnitMapScreen(filledMapPayload()));
                context.waitForScreen(NexusSpaceUnitMapScreen.class);
                context.runOnClient(client -> {
                    var screen = (NexusSpaceUnitMapScreen) client.gui.screen();
                    int[] viewport = screen.mapViewportForVisualTest();
                    var controls = screen.children().stream().filter(child -> child instanceof Button)
                            .map(child -> (Button) child)
                            .filter(button -> button.visible && button.active
                                    && button.getY() >= viewport[1]
                                    && button.getY() < viewport[1] + viewport[3])
                            .toList();
                    if (controls.size() != 2) throw new AssertionError("Expected favorite and visibility map controls");
                    for (Button button : controls) {
                        int x = button.getX() + button.getWidth() / 2;
                        int y = button.getY() + button.getHeight() / 2;
                        ObserverPacketProbe.reset();
                        if (!screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false)
                                || ObserverPacketProbe.sends() != 1) {
                            throw new AssertionError("Actionable map button did not receive its click: " + button.getMessage());
                        }
                        int[] before = screen.mapViewForVisualTest();
                        screen.mouseDragged(new MouseButtonEvent(x + 20, y + 20, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), 20, 20);
                        screen.mouseReleased(new MouseButtonEvent(x + 20, y + 20, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)));
                        if (!java.util.Arrays.equals(before, screen.mapViewForVisualTest())) {
                            throw new AssertionError("Button click incorrectly started map panning");
                        }
                    }
                });
                selectMapDestination(context);
                context.waitTicks(2);
                context.takeScreenshot("nexus-pointer-interior-scale-" + guiScale);
                for (int sign : new int[]{-1, 1}) {
                    context.setScreen(() -> new NexusSpaceUnitMapScreen(filledMapPayload()));
                    context.waitForScreen(NexusSpaceUnitMapScreen.class);
                    context.runOnClient(client -> {
                        var screen = (NexusSpaceUnitMapScreen) client.gui.screen();
                        int[] center = screen.mapViewportCenterForVisualTest();
                        int[] v = screen.mapViewportForVisualTest();
                        screen.mouseScrolled(center[0], center[1], 0, 1);
                        screen.mouseScrolled(center[0], center[1], 0, 1);
                        screen.mouseClicked(new MouseButtonEvent(center[0], center[1], new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                        screen.mouseDragged(new MouseButtonEvent(center[0], center[1], new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), sign * 100000, sign * 100000);
                        screen.mouseReleased(new MouseButtonEvent(center[0], center[1], new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)));
                        double x = center[0] + sign * (v[2] / 2 - 5);
                        double y = center[1] + sign * (v[3] / 2 - 5);
                        for (int expectedZoom : new int[]{2, 1}) {
                            screen.mouseScrolled(x, y, 0, -1);
                            int[] view = screen.mapViewForVisualTest();
                            int size = 128 * v[4] * expectedZoom;
                            int limitX = Math.max(0, (size - (v[2] - 2)) / 2);
                            int limitY = Math.max(0, (size - (v[3] - 2)) / 2);
                            if (view[0] != expectedZoom || view[1] != sign * limitX || view[2] != sign * limitY) {
                                throw new AssertionError("Zoom must clamp exactly to pan bounds: " + java.util.Arrays.toString(view));
                            }
                        }
                    });
                }
            }
        } finally {
            context.setScreen(() -> null);
            context.runOnClient(client -> client.options.guiScale().set(previousScale));
        }
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
            String title = I18n.get("container.totem.space_unit.compass");
            if (!expectedCompassTitle.equals(title)) {
                throw new AssertionError(language + " Nexus compass resources were not loaded: " + title);
            }
        });
    }

    private static void exerciseFriendsAndPortablePreview(ClientGameTestContext context, String language) {
        for (int scale : new int[]{2, 3}) {
            context.runOnClient(client -> client.options.guiScale().set(scale));
            context.setScreen(() -> new NexusSpaceUnitMapScreen(friendPayload()));
            context.waitForScreen(NexusSpaceUnitMapScreen.class);
            selectCompassDestination(context);
            context.waitFor(client -> ((NexusSpaceUnitMapScreen) client.gui.screen()).teleportButtonActiveForVisualTest());
            context.waitTicks(2);
            context.takeScreenshot("nexus-friend-target-" + language + "-scale-" + scale);
            context.setScreen(() -> new NexusSpaceUnitMapScreen(friendPayload()));
            context.runOnClient(client -> {
                var screen = (NexusSpaceUnitMapScreen) client.gui.screen();
                screen.setFocused(null);
                for (int i = 0; i < 4 && !SOURCE_ID.equals(screen.selectedUnitIdForVisualTest()); i++)
                    screen.keyPressed(new KeyEvent(264, 0, 0));
                screen.showMaterialDiagnosticsForVisualTest();
            });
            context.waitFor(client -> ((NexusSpaceUnitMapScreen) client.gui.screen()).arrayPreviewButtonsActiveForVisualTest());
            context.waitTicks(2);
            context.takeScreenshot("nexus-player-source-array-preview-" + language + "-scale-" + scale);
            context.setScreen(() -> null);
        }
    }

    static SpaceUnitMapPayload friendPayload() {
        var friend = new SpaceUnitMapPayload.Entry(
                COMPASS_TARGET_ID, "player", "Online Friend", "friends", true, "minecraft:overworld", 32, 72, 32,
                .6, 0, 64, 2, 2, 2, 0, 0, 20, 0, 0, 40, 40, 4, 4, 0, 0, 0,
                false, "message.totem.space_unit.interface_bonus.compass", false, false, false, 0, 0, true, "");
        return new SpaceUnitMapPayload(UUID.fromString("00000000-0000-0000-0000-000000000488"),
                "player", "Player", "minecraft:overworld", 1, 65, 1,
                TeleportInterfaceType.COMPASS, SpaceUnitMapPayload.NO_MAP_ID,
                List.of(entry(SOURCE_ID, "Home Nexus", 0, 0, "message.totem.space_unit.interface_bonus.compass", true), friend));
    }

    private static SpaceUnitMapPayload managementPayload() {
        return new SpaceUnitMapPayload(
                SOURCE_ID, "lodestone", "Home Nexus", "minecraft:overworld", 0, 64, 0,
                TeleportInterfaceType.BOOK, SpaceUnitMapPayload.NO_MAP_ID,
                List.of(
                        entry(SOURCE_ID, "Home Nexus", 0, 0,
                                "message.totem.space_unit.interface_bonus.book.active", false),
                        entry(UUID.fromString("00000000-0000-0000-0000-000000000499"),
                                "Hidden Remote Nexus", 24, 24,
                                "message.totem.space_unit.interface_bonus.book.active", true)));
    }

    private static void exerciseRecoveryList(ClientGameTestContext context, String screenshot) {
        context.setScreen(() -> new NexusSpaceUnitMapScreen(compassPayload(TeleportInterfaceType.RECOVERY_COMPASS)));
        context.waitForScreen(NexusSpaceUnitMapScreen.class);
        context.waitFor(client -> ((NexusSpaceUnitMapScreen) client.gui.screen()).compassTeleportPresentationForVisualTest());
        selectCompassDestination(context);
        context.waitFor(client -> ((NexusSpaceUnitMapScreen) client.gui.screen()).teleportButtonActiveForVisualTest());
        context.waitTicks(2);
        context.takeScreenshot(screenshot);
        context.setScreen(() -> null);
        context.waitForScreen(null);
    }

    private static SpaceUnitMapPayload compassPayload() { return compassPayload(TeleportInterfaceType.COMPASS); }

    private static SpaceUnitMapPayload compassPayload(TeleportInterfaceType type) {
        List<SpaceUnitMapPayload.Entry> entries = new ArrayList<>();
        entries.add(entry(SOURCE_ID, "Home Nexus", 0, 0,
                "message.totem.space_unit.interface_bonus.compass", false));
        entries.add(entry(COMPASS_TARGET_ID, "Archive Relay", 28, -20,
                "message.totem.space_unit.interface_bonus.compass", true));
        for (int index = 0; index < 9; index++) {
            entries.add(entry(UUID.fromString(String.format(
                            "00000000-0000-0000-0000-%012d", 420 + index)),
                    "Relay " + (index + 1), 40 + index * 6, 12 + index * 4,
                    "message.totem.space_unit.interface_bonus.compass", true));
        }
        return new SpaceUnitMapPayload(
                SOURCE_ID, "lodestone", "Home Nexus", "minecraft:overworld", 0, 64, 0,
                type, SpaceUnitMapPayload.NO_MAP_ID, entries);
    }

    private static SpaceUnitMapPayload filledMapPayload() {
        return new SpaceUnitMapPayload(
                SOURCE_ID, "lodestone", "Home Nexus", "minecraft:overworld", 0, 64, 0,
                TeleportInterfaceType.FILLED_MAP, MAP_ID,
                List.of(
                        entry(SOURCE_ID, "Home Nexus", 0, 0,
                                "message.totem.space_unit.interface_bonus.filled_map.active", false),
                        entry(MAP_TARGET_ID,
                                "East Archive", 28, -20,
                                "message.totem.space_unit.interface_bonus.filled_map.active", true),
                        entry(UUID.fromString("00000000-0000-0000-0000-000000000403"),
                                "", -32, 26,
                                "message.totem.space_unit.interface_bonus.filled_map.active", true)));
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
                canTeleport ? "" : "message.totem.space_unit.teleport_blocked.same_source");
    }

    private static void fillVanillaMapColors(MapItemSavedData data, int detailLevel) {
        for (int z = 0; z < 128; z++) {
            for (int x = 0; x < 128; x++) {
                int shiftedX = (x + detailLevel * 13) & 127;
                int shiftedZ = (z + detailLevel * 9) & 127;
                MapColor color;
                if (shiftedX < 24 || (shiftedX < 46 && shiftedZ > 76)) {
                    color = MapColor.WATER;
                } else if (shiftedZ > 92) {
                    color = MapColor.SAND;
                } else if ((shiftedX - 82) * (shiftedX - 82) + (shiftedZ - 42) * (shiftedZ - 42) < 380) {
                    color = MapColor.STONE;
                } else {
                    color = MapColor.GRASS;
                }
                MapColor.Brightness brightness = ((x / 9) + (z / 13) + detailLevel) % 3 == 0
                        ? MapColor.Brightness.HIGH
                        : ((x / 11) + (z / 7) + detailLevel) % 3 == 0
                        ? MapColor.Brightness.LOW
                        : MapColor.Brightness.NORMAL;
                data.colors[x + z * 128] = color.getPackedId(brightness);
            }
        }
    }
}
