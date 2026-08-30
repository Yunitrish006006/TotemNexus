package dev.totem.nexus.client;

import dev.totem.nexus.network.TeleportArrayVisualizationPayload;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.UUID;

/** Client proof for bounded lifetime and intentional through-wall gizmo submission. */
@SuppressWarnings("UnstableApiUsage")
public final class NexusArrayVisualizationClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        UUID source = UUID.fromString("00000000-0000-0000-0000-000000000703");
        TeleportArrayVisualizationPayload payload = new TeleportArrayVisualizationPayload(
                source,
                "minecraft:overworld",
                0,
                64,
                0,
                600,
                List.of(
                        new TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 0, true),
                        new TeleportArrayVisualizationPayload.RelativeBlock(2, 0, 0, false)
                )
        );

        NexusArrayVisualizationClient.accept(payload, 1_000L);
        if (!NexusArrayVisualizationClient.isActiveFor(source, 1_000L)) {
            throw new AssertionError("Fresh Nexus array preview was not active");
        }
        if (NexusArrayVisualizationClient.isActiveFor(source, 1_000L + 600L * 50_000_000L)) {
            throw new AssertionError("Nexus array preview did not expire at its bounded lifetime");
        }

        SimpleGizmoCollector collector = new SimpleGizmoCollector();
        try (var ignored = Gizmos.withCollector(collector)) {
            NexusArrayVisualizationClient.submit(payload);
        }
        if (collector.getGizmos().size() != 3
                || collector.getGizmos().stream().anyMatch(gizmo -> !gizmo.isAlwaysOnTop())) {
            throw new AssertionError("Nexus origin and array blocks were not all submitted through walls");
        }
        NexusArrayVisualizationClient.clear();

        captureThroughWallPreview(context, source);
    }

    private static void captureThroughWallPreview(ClientGameTestContext context, UUID source) {
        BlockPos origin = new BlockPos(0, 81, -6);
        TeleportArrayVisualizationPayload payload = new TeleportArrayVisualizationPayload(
                source,
                "minecraft:overworld",
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                600,
                List.of(
                        new TeleportArrayVisualizationPayload.RelativeBlock(-1, 0, 0, false),
                        new TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 0, true),
                        new TeleportArrayVisualizationPayload.RelativeBlock(2, 0, 4, false)
                )
        );

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();
            singleplayer.getServer().runCommand("fill -3 79 -1 3 79 -10 minecraft:stone");
            singleplayer.getServer().runCommand("fill -2 80 -4 2 84 -4 minecraft:stone_bricks");
            singleplayer.getServer().runCommand("setblock 0 81 -6 minecraft:lodestone");
            singleplayer.getServer().runCommand("setblock -1 81 -6 minecraft:gold_block");
            singleplayer.getServer().runCommand("setblock 1 81 -6 minecraft:iron_block");
            singleplayer.getServer().runCommand("setblock 2 81 -2 minecraft:diamond_block");
            singleplayer.getServer().runCommand("tp @a 0 80 0");
            context.waitFor(client -> client.level != null
                    && client.level.getBlockState(origin).is(Blocks.LODESTONE));
            context.getInput().lookAt(origin);
            context.runOnClient(client -> NexusArrayVisualizationClient.accept(payload));
            context.waitFor(client -> NexusArrayVisualizationClient.isActiveFor(source));
            context.waitFor(client -> NexusArrayVisualizationClient.hasRendered(source));
            context.waitTicks(2);
            // Fabric's screenshot helper invokes GameRenderer directly instead of
            // Minecraft's outer render-frame method, so prime the same render-thread
            // collector that production BEFORE_GIZMOS callbacks normally receive.
            context.runOnClient(client -> {
                try (var ignored = client.levelRenderer.collectPerFrameRenderThreadGizmos()) {
                    NexusArrayVisualizationClient.submit(payload);
                }
            });
            context.takeScreenshot("totem-nexus-array-through-wall");
        } finally {
            NexusArrayVisualizationClient.clear();
        }
    }
}
