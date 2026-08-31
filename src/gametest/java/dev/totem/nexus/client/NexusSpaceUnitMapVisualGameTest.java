package dev.totem.nexus.client;

import dev.totem.nexus.network.SpaceUnitMapPayload;
import dev.totem.nexus.space.TeleportInterfaceType;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.List;
import java.util.UUID;

/** Native-scale visual coverage for management-only and vanilla-map Nexus presentations. */
@SuppressWarnings("UnstableApiUsage")
public final class NexusSpaceUnitMapVisualGameTest implements FabricClientGameTest {
    private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final int MAP_ID = 7401;

    @Override
    public void runTest(ClientGameTestContext context) {
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
            context.waitTicks(2);
            context.takeScreenshot("totem-nexus-filled-map-native-names");
            context.setScreen(() -> null);
            context.waitForScreen(null);
        }
    }

    private static SpaceUnitMapPayload managementPayload() {
        return new SpaceUnitMapPayload(
                SOURCE_ID, "lodestone", "Home Nexus", "minecraft:overworld", 0, 64, 0,
                TeleportInterfaceType.BOOK, SpaceUnitMapPayload.NO_MAP_ID,
                List.of(
                        entry(SOURCE_ID, "Home Nexus", 0, 0,
                                "message.deadrecall.space_unit.interface_bonus.book.active"),
                        entry(UUID.fromString("00000000-0000-0000-0000-000000000499"),
                                "Hidden Remote Nexus", 24, 24,
                                "message.deadrecall.space_unit.interface_bonus.book.active")));
    }

    private static SpaceUnitMapPayload filledMapPayload() {
        return new SpaceUnitMapPayload(
                SOURCE_ID, "lodestone", "Home Nexus", "minecraft:overworld", 0, 64, 0,
                TeleportInterfaceType.FILLED_MAP, MAP_ID,
                List.of(
                        entry(SOURCE_ID, "Home Nexus", 0, 0,
                                "message.deadrecall.space_unit.interface_bonus.filled_map.active"),
                        entry(UUID.fromString("00000000-0000-0000-0000-000000000402"),
                                "East Archive", 28, -20,
                                "message.deadrecall.space_unit.interface_bonus.filled_map.active"),
                        entry(UUID.fromString("00000000-0000-0000-0000-000000000403"),
                                "", -32, 26,
                                "message.deadrecall.space_unit.interface_bonus.filled_map.active")));
    }

    private static SpaceUnitMapPayload.Entry entry(
            UUID id, String name, int x, int z, String interfaceBonusMessageKey) {
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
                false, true, true, 1, 2, false, "");
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
