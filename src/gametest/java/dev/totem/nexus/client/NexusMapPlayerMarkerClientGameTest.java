package dev.totem.nexus.client;

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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Runtime proof that player position is owner-local presentation, never Observer-local substitution. */
@SuppressWarnings("UnstableApiUsage")
public final class NexusMapPlayerMarkerClientGameTest implements FabricClientGameTest {
    private static final int MAP_ID = 7410;
    private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000741");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            AtomicReference<SpaceUnitMapPayload> payload = new AtomicReference<>();
            context.runOnClient(client -> {
                int centerX = Mth.floor(client.player.getX());
                int centerZ = Mth.floor(client.player.getZ());
                var mapData = NexusMapItemSavedDataInvoker.totem$createExact(
                        centerX, centerZ, (byte) 0, false, false, false, Level.OVERWORLD);
                java.util.Arrays.fill(mapData.colors, MapColor.GRASS.getPackedId(MapColor.Brightness.NORMAL));
                client.level.overrideMapData(new MapId(MAP_ID), mapData);
                payload.set(mapPayload(centerX, centerZ));
            });

            // Real first-person ItemInHandRenderer, including both vanilla hand poses.
            for (int mode=0;mode<3;mode++) {
                final int pose=mode;
                world.getServer().runOnServer(server -> {
                    var player=server.getPlayerList().getPlayers().getFirst();
                    var data=NexusMapItemSavedDataInvoker.totem$createExact(Mth.floor(player.getX()),Mth.floor(player.getZ()),
                            (byte)0,false,false,false,Level.OVERWORLD);
                    java.util.Arrays.fill(data.colors,MapColor.GRASS.getPackedId(MapColor.Brightness.NORMAL));
                    player.level().setMapData(new MapId(MAP_ID),data);
                    var map=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FILLED_MAP);
                    map.set(net.minecraft.core.component.DataComponents.MAP_ID,new MapId(MAP_ID));
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                            pose==2?new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE):map);
                    player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                            pose==2?map:pose==1?new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE):net.minecraft.world.item.ItemStack.EMPTY);
                    player.setXRot(45);
                });
                context.waitTicks(8);
                context.runOnClient(client -> {
                    client.player.setXRot(65);
                    client.player.xRotO = 65;
                    NexusMapDetailClientState.setForVisualTest(MAP_ID,List.of());
                    ((NexusHeldMapVisualTestAccess)client.gameRenderer.itemInHandRenderer).totem$resetHeldProbe();
                });
                context.waitFor(client -> ((NexusHeldMapVisualTestAccess)client.gameRenderer.itemInHandRenderer).totem$lastHeldMap()==MAP_ID);
                context.runOnClient(client -> {
                    if(((NexusHeldMapVisualTestAccess)client.gameRenderer.itemInHandRenderer).totem$heldMarkerCount()!=1)
                        throw new AssertionError("Held map must contain exactly one transient player marker");
                });
                context.takeScreenshot("totem-nexus-held-player-marker-"+mode);
            }

            context.setScreen(() -> new NexusSpaceUnitMapScreen(payload.get()));
            context.waitForScreen(NexusSpaceUnitMapScreen.class);
            context.waitFor(client -> ((NexusMapDetailVisualTestAccess) client.gui.screen())
                    .totem$localPlayerMarkerRenderedForVisualTest());
            context.waitTicks(2);
            context.takeScreenshot("totem-nexus-map-local-player-marker");
            context.setScreen(() -> null);
            context.waitForScreen(null);

            context.setScreen(() -> new NexusSpaceUnitMapScreen(payload.get(), true, () -> { }));
            context.waitForScreen(NexusSpaceUnitMapScreen.class);
            context.waitTicks(2);
            context.runOnClient(client -> {
                NexusSpaceUnitMapScreen screen = (NexusSpaceUnitMapScreen) client.gui.screen();
                if (((NexusMapDetailVisualTestAccess) screen).totem$localPlayerMarkerRenderedForVisualTest()) {
                    throw new AssertionError("Observer map substituted the observer client's local player marker");
                }
            });
            context.takeScreenshot("totem-nexus-map-observer-no-local-player-marker");
            context.setScreen(() -> null);
            context.waitForScreen(null);
        }
    }

    private static SpaceUnitMapPayload mapPayload(int x, int z) {
        SpaceUnitMapPayload.Entry source = new SpaceUnitMapPayload.Entry(
                SOURCE_ID, "lodestone", "Marker Home", "private", false,
                "minecraft:overworld", x, 64, z,
                1.0D, 1, 0,
                0, 0, 0, 0, 0, 20,
                0, 0, 20, 20, 4, 4, 0, 0, 0,
                true, "message.totem.space_unit.interface_bonus.filled_map.active",
                false, true, true, 0, 0, false,
                "message.totem.space_unit.teleport_blocked.same_source");
        return new SpaceUnitMapPayload(
                SOURCE_ID, "lodestone", "Marker Home", "minecraft:overworld",
                x, 64, z, TeleportInterfaceType.FILLED_MAP, MAP_ID, List.of(source));
    }
}
