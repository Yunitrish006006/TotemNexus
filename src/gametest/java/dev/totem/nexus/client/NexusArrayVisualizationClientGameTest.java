package dev.totem.nexus.client;

import dev.totem.nexus.network.TeleportArrayVisualizationPayload;
import dev.totem.nexus.network.TeleportArrayVisualizationStatusPayload;
import dev.totem.nexus.space.TeleportArrayVisualizationGameTestSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.LineGizmo;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client proof for cached outer-only union lines, dual-mode state and mixed occlusion. */
@SuppressWarnings("UnstableApiUsage")
public final class NexusArrayVisualizationClientGameTest implements FabricClientGameTest {
    private static final Map<BlockPos, net.minecraft.world.level.block.Block> COMPLEX_EMITTERS = Map.ofEntries(
            Map.entry(new BlockPos(-1, 0, 0), Blocks.IRON_BLOCK),
            Map.entry(new BlockPos(-2, 0, 0), Blocks.REDSTONE_BLOCK),
            Map.entry(new BlockPos(-3, 0, 0), Blocks.AMETHYST_BLOCK),
            Map.entry(new BlockPos(-4, 0, 0), Blocks.DIAMOND_BLOCK),
            Map.entry(new BlockPos(1, 0, 0), Blocks.IRON_BLOCK),
            Map.entry(new BlockPos(2, 0, 0), Blocks.REDSTONE_BLOCK),
            Map.entry(new BlockPos(3, 0, 0), Blocks.AMETHYST_BLOCK),
            Map.entry(new BlockPos(4, 0, 0), Blocks.DIAMOND_BLOCK),
            Map.entry(new BlockPos(0, 1, 0), Blocks.AMETHYST_BLOCK),
            Map.entry(new BlockPos(0, 2, 0), Blocks.IRON_BLOCK),
            Map.entry(new BlockPos(0, 3, 0), Blocks.REDSTONE_BLOCK),
            Map.entry(new BlockPos(0, 4, 0), Blocks.DIAMOND_BLOCK)
    );
    private static final Map<BlockPos, net.minecraft.world.level.block.Block> COMPLEX_ORDINARY_MATERIALS = Map.ofEntries(
            Map.entry(new BlockPos(-5, 0, 0), Blocks.TUFF),
            Map.entry(new BlockPos(-4, 1, 0), Blocks.OBSIDIAN),
            Map.entry(new BlockPos(-4, 0, -1), Blocks.CRYING_OBSIDIAN),
            Map.entry(new BlockPos(5, 0, 0), Blocks.TUFF),
            Map.entry(new BlockPos(4, 1, 0), Blocks.OBSIDIAN),
            Map.entry(new BlockPos(4, 0, -1), Blocks.CRYING_OBSIDIAN),
            Map.entry(new BlockPos(0, 5, 0), Blocks.TUFF),
            Map.entry(new BlockPos(-1, 4, 0), Blocks.OBSIDIAN),
            Map.entry(new BlockPos(1, 4, 0), Blocks.CRYING_OBSIDIAN)
    );

    @Override
    public void runTest(ClientGameTestContext context) {
        UUID source = UUID.fromString("00000000-0000-0000-0000-000000000703");
        TeleportArrayVisualizationPayload payload = payload(source, new BlockPos(0, 64, 0));

        NexusArrayVisualizationClient.clear();
        NexusArrayVisualizationClient.enableForVisualTest("lodestone", source, true, true);
        NexusArrayVisualizationClient.accept(payload);
        NexusArrayVisualizationClient.acceptStatus(
                new TeleportArrayVisualizationStatusPayload(source, true, true, true));
        if (!NexusArrayVisualizationClient.isActiveFor(source)
                || !NexusArrayVisualizationClient.isArrayEnabledFor(source)
                || !NexusArrayVisualizationClient.isBuildSitesEnabledFor(source)
                || !NexusArrayVisualizationClient.hasSnapshotFor(source)) {
            throw new AssertionError("Persistent dual-mode Nexus preview was not active");
        }
        if (NexusArrayVisualizationClient.acceptedOutlinePlanDerivationsForTest() != 1
                || NexusArrayVisualizationClient.cachedArraySegmentCountForTest() != 12
                || NexusArrayVisualizationClient.cachedBuildSiteSegmentCountForTest() != 12) {
            throw new AssertionError("Accepted payload did not cache one independent outline plan per class");
        }
        for (int tick = 1; tick < NexusArrayVisualizationClient.REFRESH_INTERVAL_TICKS; tick++) {
            if (NexusArrayVisualizationClient.advanceRefreshCadence()) {
                throw new AssertionError("Nexus client requested a refresh before 20 ticks");
            }
        }
        if (!NexusArrayVisualizationClient.advanceRefreshCadence()) {
            throw new AssertionError("Nexus client did not request a refresh at 20 ticks");
        }

        SimpleGizmoCollector collector = new SimpleGizmoCollector();
        try (var ignored = Gizmos.withCollector(collector)) {
            NexusArrayVisualizationClient.submit(payload);
        }
        long throughWalls = collector.getGizmos().stream().filter(gizmo -> gizmo.isAlwaysOnTop()).count();
        long depthTested = collector.getGizmos().size() - throughWalls;
        long cyan = collector.getGizmos().stream()
                .map(entry -> requireLine(entry.gizmo()))
                .filter(line -> line.color() == 0xFF4FC3F7)
                .count();
        long green = collector.getGizmos().stream()
                .map(entry -> requireLine(entry.gizmo()))
                .filter(line -> line.color() == 0xFF66BB6A)
                .count();
        if (collector.getGizmos().size() != 24
                || throughWalls != 12
                || depthTested != 12
                || cyan != 12
                || green != 12) {
            throw new AssertionError("Nexus array/build-site outer lines did not preserve semantic style and occlusion");
        }
        assertThreeBlockRowHasOnlyTwelveOuterSegments(collector);
        if (collector.getGizmos().size() >= 4 * 12) {
            throw new AssertionError("Merged outline did not materially reduce four per-block wireframes");
        }
        collect(payload);
        collect(payload);
        if (NexusArrayVisualizationClient.acceptedOutlinePlanDerivationsForTest() != 1) {
            throw new AssertionError("Render submission rebuilt an already accepted outline plan");
        }

        assertDisconnectedArrayComponentsRemainSeparate(source);

        NexusArrayVisualizationClient.acceptStatus(
                new TeleportArrayVisualizationStatusPayload(source, false, false, false));
        if (NexusArrayVisualizationClient.isActiveFor(source)) {
            throw new AssertionError("Server invalidation did not clear the persistent Nexus preview");
        }
        if (NexusArrayVisualizationClient.acceptedOutlinePlanDerivationsForTest() != 0
                || NexusArrayVisualizationClient.cachedArraySegmentCountForTest() != 0
                || NexusArrayVisualizationClient.cachedBuildSiteSegmentCountForTest() != 0) {
            throw new AssertionError("Server invalidation did not clear cached outline geometry");
        }
        NexusArrayVisualizationClient.clear();

        captureComplexCountedArrayPreview(context, source);
        captureComplexBuildSitesPreview(context, source);
    }

    private static void captureComplexCountedArrayPreview(ClientGameTestContext context, UUID source) {
        BlockPos origin = new BlockPos(0, 81, -4);

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();
            buildComplexArrayScene(singleplayer);
            awaitComplexArrayScene(context, origin);
            TeleportArrayVisualizationPayload payload = productionScenePayload(
                    singleplayer, source, origin, true, false);
            assertSceneSemantics(singleplayer, origin, payload);
            context.getInput().lookAt(origin.above());
            context.runOnClient(client -> {
                NexusArrayVisualizationClient.clear();
                NexusArrayVisualizationClient.enableForVisualTest("lodestone", source, true, false);
                NexusArrayVisualizationClient.accept(payload);
            });
            context.waitFor(client -> NexusArrayVisualizationClient.isActiveFor(source)
                    && NexusArrayVisualizationClient.isArrayEnabledFor(source)
                    && !NexusArrayVisualizationClient.isBuildSitesEnabledFor(source));
            assertCountedArrayOnlyGizmos(payload);
            context.waitFor(client -> NexusArrayVisualizationClient.hasRendered(source));
            context.waitTicks(2);
            primeProductionGizmos(context, payload);
            context.takeScreenshot("totem-nexus-complex-counted-array");
        } finally {
            NexusArrayVisualizationClient.clear();
        }
    }

    private static void captureComplexBuildSitesPreview(ClientGameTestContext context, UUID source) {
        BlockPos origin = new BlockPos(0, 81, -4);

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();
            buildComplexArrayScene(singleplayer);
            awaitComplexArrayScene(context, origin);
            TeleportArrayVisualizationPayload payload = productionScenePayload(
                    singleplayer, source, origin, false, true);
            assertSceneSemantics(singleplayer, origin, payload);
            context.getInput().lookAt(origin.above());
            context.runOnClient(client -> {
                NexusArrayVisualizationClient.clear();
                NexusArrayVisualizationClient.enableForVisualTest("lodestone", source, false, true);
                NexusArrayVisualizationClient.accept(payload);
            });
            context.waitFor(client -> NexusArrayVisualizationClient.isActiveFor(source)
                    && !NexusArrayVisualizationClient.isArrayEnabledFor(source)
                    && NexusArrayVisualizationClient.isBuildSitesEnabledFor(source));
            assertBuildSitesOnlyGizmos(payload);
            context.waitFor(client -> NexusArrayVisualizationClient.hasRendered(source));
            context.waitTicks(2);
            primeProductionGizmos(context, payload);
            context.takeScreenshot("totem-nexus-complex-build-sites");
        } finally {
            NexusArrayVisualizationClient.clear();
        }
    }

    private static void buildComplexArrayScene(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runCommand("time set noon");
        singleplayer.getServer().runCommand("weather clear");
        singleplayer.getServer().runCommand("fill -7 79 4 7 79 -11 minecraft:gray_concrete");

        // A partial opaque wall hides the origin and right branch from the
        // camera while leaving the stepped left branch readable in-world.
        singleplayer.getServer().runCommand("fill 0 80 -2 3 85 -2 minecraft:black_concrete");
        singleplayer.getServer().runCommand("fill 1 80 -1 2 82 -1 minecraft:polished_basalt");
        singleplayer.getServer().runCommand("setblock 0 85 -1 minecraft:cut_copper_stairs");
        singleplayer.getServer().runCommand("setblock 3 85 -1 minecraft:cut_copper_stairs");

        singleplayer.getServer().runCommand("setblock 0 81 -4 minecraft:lodestone");
        setRelativeArrayBlock(singleplayer, -1, 0, 0, "minecraft:iron_block");
        setRelativeArrayBlock(singleplayer, -2, 0, 0, "minecraft:redstone_block");
        setRelativeArrayBlock(singleplayer, -3, 0, 0, "minecraft:amethyst_block");
        setRelativeArrayBlock(singleplayer, -4, 0, 0, "minecraft:diamond_block");
        setRelativeArrayBlock(singleplayer, -5, 0, 0, "minecraft:tuff");
        setRelativeArrayBlock(singleplayer, -4, 1, 0, "minecraft:obsidian");
        setRelativeArrayBlock(singleplayer, -4, 0, -1, "minecraft:crying_obsidian");
        setRelativeArrayBlock(singleplayer, -5, 1, -1, "minecraft:gold_block");

        setRelativeArrayBlock(singleplayer, 1, 0, 0, "minecraft:iron_block");
        setRelativeArrayBlock(singleplayer, 2, 0, 0, "minecraft:redstone_block");
        setRelativeArrayBlock(singleplayer, 3, 0, 0, "minecraft:amethyst_block");
        setRelativeArrayBlock(singleplayer, 4, 0, 0, "minecraft:diamond_block");
        setRelativeArrayBlock(singleplayer, 5, 0, 0, "minecraft:tuff");
        setRelativeArrayBlock(singleplayer, 4, 1, 0, "minecraft:obsidian");
        setRelativeArrayBlock(singleplayer, 4, 0, -1, "minecraft:crying_obsidian");
        setRelativeArrayBlock(singleplayer, 5, 1, -1, "minecraft:gold_block");

        setRelativeArrayBlock(singleplayer, 0, 1, 0, "minecraft:amethyst_block");
        setRelativeArrayBlock(singleplayer, 0, 2, 0, "minecraft:iron_block");
        setRelativeArrayBlock(singleplayer, 0, 3, 0, "minecraft:redstone_block");
        setRelativeArrayBlock(singleplayer, 0, 4, 0, "minecraft:diamond_block");
        setRelativeArrayBlock(singleplayer, 0, 5, 0, "minecraft:tuff");
        setRelativeArrayBlock(singleplayer, -1, 4, 0, "minecraft:obsidian");
        setRelativeArrayBlock(singleplayer, 1, 4, 0, "minecraft:crying_obsidian");
        setRelativeArrayBlock(singleplayer, 0, 5, -1, "minecraft:gold_block");

        singleplayer.getServer().runCommand("clear @a");
        singleplayer.getServer().runCommand("tp @a 0 80 3");
    }

    private static void setRelativeArrayBlock(
            TestSingleplayerContext singleplayer,
            int dx,
            int dy,
            int dz,
            String block) {
        setArrayBlock(singleplayer, dx, 81 + dy, -4 + dz, block);
    }

    private static void setArrayBlock(
            TestSingleplayerContext singleplayer,
            int x,
            int y,
            int z,
            String block) {
        singleplayer.getServer().runCommand("setblock " + x + " " + y + " " + z + " " + block);
    }

    private static void awaitComplexArrayScene(ClientGameTestContext context, BlockPos origin) {
        context.waitFor(client -> client.level != null
                && client.level.getBlockState(origin).is(Blocks.LODESTONE)
                && client.level.getBlockState(origin.offset(-4, 0, 0)).is(Blocks.DIAMOND_BLOCK)
                && client.level.getBlockState(origin.offset(4, 0, 0)).is(Blocks.DIAMOND_BLOCK)
                && client.level.getBlockState(origin.offset(0, 4, 0)).is(Blocks.DIAMOND_BLOCK)
                && !client.level.getBlockState(new BlockPos(1, 81, -2)).isAir());
        context.waitTicks(5);
    }

    private static TeleportArrayVisualizationPayload productionScenePayload(
            TestSingleplayerContext singleplayer,
            UUID source,
            BlockPos origin,
            boolean showArray,
            boolean showBuildSites) {
        return singleplayer.getServer().computeOnServer(server ->
                TeleportArrayVisualizationGameTestSupport.snapshot(
                        server.overworld(), source, origin, showArray, showBuildSites));
    }

    private static void assertSceneSemantics(
            TestSingleplayerContext singleplayer,
            BlockPos origin,
            TeleportArrayVisualizationPayload payload) {
        singleplayer.getServer().runOnServer(server ->
                TeleportArrayVisualizationGameTestSupport.assertSceneSemantics(
                        server.overworld(), origin, payload, COMPLEX_EMITTERS, COMPLEX_ORDINARY_MATERIALS));
    }

    private static void primeProductionGizmos(
            ClientGameTestContext context,
            TeleportArrayVisualizationPayload payload) {
        // Fabric's screenshot helper invokes GameRenderer directly instead of
        // Minecraft's outer render-frame method, so prime production gizmos.
        context.runOnClient(client -> {
            client.gui.hud.getChat().clearMessages(false);
            try (var ignored = client.levelRenderer.collectPerFrameRenderThreadGizmos()) {
                NexusArrayVisualizationClient.submit(payload);
            }
        });
    }

    private static void assertCountedArrayOnlyGizmos(TeleportArrayVisualizationPayload payload) {
        if (!payload.showArray() || payload.showBuildSites()
                || payload.blocks().stream().anyMatch(TeleportArrayVisualizationPayload.RelativeBlock::buildable)
                || payload.blocks().stream().noneMatch(TeleportArrayVisualizationPayload.RelativeBlock::expansionEmitter)
                || payload.blocks().stream().noneMatch(block -> !block.expansionEmitter())) {
            throw new AssertionError("Complex counted-array fixture does not cover origin, emitter and ordinary blocks");
        }
        SimpleGizmoCollector collector = collect(payload);
        if (collector.getGizmos().isEmpty()
                || collector.getGizmos().size() >= (payload.blocks().size() + 1) * 12
                || collector.getGizmos().stream().anyMatch(gizmo -> !gizmo.isAlwaysOnTop())
                || collector.getGizmos().stream()
                        .map(entry -> requireLine(entry.gizmo()))
                        .anyMatch(line -> line.color() != 0xFF4FC3F7)) {
            throw new AssertionError("Counted-array fixture did not use one reduced cyan outer union through walls");
        }
    }

    private static void assertBuildSitesOnlyGizmos(TeleportArrayVisualizationPayload payload) {
        if (payload.showArray() || !payload.showBuildSites() || payload.blocks().isEmpty()
                || payload.blocks().stream().anyMatch(block -> !block.buildable())) {
            throw new AssertionError("Complex build-site fixture does not contain only reached replaceable positions");
        }
        SimpleGizmoCollector collector = collect(payload);
        if (collector.getGizmos().isEmpty()
                || collector.getGizmos().size() >= payload.blocks().size() * 12
                || collector.getGizmos().stream().anyMatch(gizmo -> gizmo.isAlwaysOnTop())
                || collector.getGizmos().stream()
                        .map(entry -> requireLine(entry.gizmo()))
                        .anyMatch(line -> line.color() != 0xFF66BB6A)) {
            throw new AssertionError("Build-site fixture did not use one reduced green depth-tested outer union");
        }
    }

    private static void assertThreeBlockRowHasOnlyTwelveOuterSegments(SimpleGizmoCollector collector) {
        List<LineGizmo> cyan = collector.getGizmos().stream()
                .map(entry -> requireLine(entry.gizmo()))
                .filter(line -> line.color() == 0xFF4FC3F7)
                .toList();
        long lengthThree = cyan.stream().filter(line -> line.start().distanceTo(line.end()) == 3.0D).count();
        boolean hasInternalSeam = cyan.stream().anyMatch(line -> {
            boolean variesY = line.start().x == line.end().x && line.start().y != line.end().y;
            boolean variesZ = line.start().x == line.end().x && line.start().z != line.end().z;
            return (variesY || variesZ) && (line.start().x == 0.0D || line.start().x == 1.0D);
        });
        if (cyan.size() != 12 || lengthThree != 4 || hasInternalSeam) {
            throw new AssertionError("Adjacent array voxels retained a shared face or coplanar grid seam");
        }
    }

    private static void assertDisconnectedArrayComponentsRemainSeparate(UUID source) {
        TeleportArrayVisualizationPayload disconnected = new TeleportArrayVisualizationPayload(
                source,
                "minecraft:overworld",
                0,
                64,
                0,
                true,
                false,
                List.of(new TeleportArrayVisualizationPayload.RelativeBlock(4, 0, 0, false, false))
        );
        SimpleGizmoCollector collector = collect(disconnected);
        if (collector.getGizmos().size() != 24
                || collector.getGizmos().stream()
                        .map(entry -> requireLine(entry.gizmo()))
                        .anyMatch(line -> line.start().distanceTo(line.end()) > 1.0D)) {
            throw new AssertionError("Disconnected array components were wrapped or bridged by one inaccurate box");
        }
    }

    private static LineGizmo requireLine(net.minecraft.gizmos.Gizmo gizmo) {
        if (!(gizmo instanceof LineGizmo line)) {
            throw new AssertionError("Merged Nexus outline submitted a non-line gizmo: " + gizmo);
        }
        return line;
    }

    private static SimpleGizmoCollector collect(TeleportArrayVisualizationPayload payload) {
        SimpleGizmoCollector collector = new SimpleGizmoCollector();
        try (var ignored = Gizmos.withCollector(collector)) {
            NexusArrayVisualizationClient.submit(payload);
        }
        return collector;
    }

    private static TeleportArrayVisualizationPayload payload(UUID source, BlockPos origin) {
        return new TeleportArrayVisualizationPayload(
                source,
                "minecraft:overworld",
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                true,
                true,
                List.of(
                        new TeleportArrayVisualizationPayload.RelativeBlock(-1, 0, 0, false, false),
                        new TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 0, true, false),
                        new TeleportArrayVisualizationPayload.RelativeBlock(-2, 0, 3, false, true)
                )
        );
    }

}
