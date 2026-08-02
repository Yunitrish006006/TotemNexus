package dev.totem.nexus.client;

import dev.totem.nexus.network.SpaceUnitMapPayload;
import dev.totem.nexus.space.TeleportInterfaceType;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Captures the signed material diagnostics panel with families and a maintenance target. */
@SuppressWarnings("UnstableApiUsage")
public final class NexusSpaceUnitMaterialVisualGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();
            context.setScreen(() -> {
                NexusSpaceUnitMapScreen screen = new NexusSpaceUnitMapScreen(materialPayload());
                screen.showMaterialDiagnosticsForVisualTest();
                return screen;
            });
            context.waitForScreen(NexusSpaceUnitMapScreen.class);
            context.waitTicks(2);
            context.takeScreenshot("totem-nexus-space-unit-material-diagnostics");
            context.setScreen(() -> null);
        }
    }

    private static SpaceUnitMapPayload materialPayload() {
        SpaceUnitMapPayload.MaterialSummary material = new SpaceUnitMapPayload.MaterialSummary(
                14, 20, 5, 3,
                4, 3, 2, 1, -2, 2, -1, 3, -2, 1, 2, 4,
                Map.of("iron", 6, "cracked_stone", 2),
                Map.of("minecraft:overworld", 2, "minecraft:the_nether", -1),
                List.of(
                        new SpaceUnitMapPayload.FamilyContribution("iron", 6,
                                Map.of("structure_capacity", 12, "route_load_capacity", 2, "wear_resistance", 6)),
                        new SpaceUnitMapPayload.FamilyContribution("cracked_stone", 2,
                                Map.of("stability", -4, "wear_resistance", -6, "maintenance_efficiency", -4))),
                3,
                List.of(new SpaceUnitMapPayload.MaintenanceTarget(8, 65, -4, "cracked_stone"))
        );
        return new SpaceUnitMapPayload(
                UUID.fromString("00000000-0000-0000-0000-000000000301"), "lodestone", "Material Test Array",
                "minecraft:overworld", 0, 64, 0, TeleportInterfaceType.COMPASS, List.of(), material);
    }
}
