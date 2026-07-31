package dev.totem.nexus.client;

import dev.totem.nexus.network.SpaceUnitFriendsPayload;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.List;
import java.util.UUID;

/** Captures the external Space Unit friends screen with empty and populated authoritative state. */
@SuppressWarnings("UnstableApiUsage")
public final class NexusFriendsVisualGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();

            context.setScreen(() -> new NexusSpaceUnitFriendsScreen(null, new SpaceUnitFriendsPayload(List.of())));
            context.waitForScreen(NexusSpaceUnitFriendsScreen.class);
            context.waitTicks(2);
            context.takeScreenshot("safe-multi-repo-modularization-nexus-friends-empty");

            context.setScreen(() -> new NexusSpaceUnitFriendsScreen(null, populatedPayload()));
            context.waitForScreen(NexusSpaceUnitFriendsScreen.class);
            context.waitTicks(2);
            context.takeScreenshot("safe-multi-repo-modularization-nexus-friends-populated");
            context.setScreen(() -> null);
        }
    }

    private static SpaceUnitFriendsPayload populatedPayload() {
        return new SpaceUnitFriendsPayload(List.of(
                new SpaceUnitFriendsPayload.Entry(
                        UUID.fromString("00000000-0000-0000-0000-000000000101"),
                        "Alex", true, "friend"),
                new SpaceUnitFriendsPayload.Entry(
                        UUID.fromString("00000000-0000-0000-0000-000000000102"),
                        "Morgan", false, "incoming"),
                new SpaceUnitFriendsPayload.Entry(
                        UUID.fromString("00000000-0000-0000-0000-000000000103"),
                        "Quinn", true, "outgoing")));
    }
}
