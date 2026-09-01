package dev.totem.nexus;

import dev.totem.nexus.network.CalibrateSpaceUnitPayload;
import dev.totem.nexus.network.ConfirmSpaceUnitRegistrationPayload;
import dev.totem.nexus.network.DeathNodeAdminPayload;
import dev.totem.nexus.network.ManageDeathNodeAdminPayload;
import dev.totem.nexus.network.RemoveSpaceUnitFriendPayload;
import dev.totem.nexus.network.RepairSpaceUnitPayload;
import dev.totem.nexus.network.RenameSpaceUnitPayload;
import dev.totem.nexus.network.RequestDeathNodeAdminPayload;
import dev.totem.nexus.network.RequestSpaceUnitFriendsPayload;
import dev.totem.nexus.network.RequestSpaceUnitMapPayload;
import dev.totem.nexus.network.RequestTeleportArrayVisualizationPayload;
import dev.totem.nexus.network.SpaceUnitFriendsPayload;
import dev.totem.nexus.network.SpaceUnitMapPayload;
import dev.totem.nexus.network.SpaceUnitRegistrationPreviewPayload;
import dev.totem.nexus.network.TeleportArrayVisualizationPayload;
import dev.totem.nexus.network.TeleportArrayVisualizationStatusPayload;
import dev.totem.nexus.network.StartSpaceUnitTeleportPayload;
import dev.totem.nexus.network.ToggleSpaceUnitFavoritePayload;
import dev.totem.nexus.network.UpdateSpaceUnitAccessPayload;
import dev.totem.nexus.network.UpdateSpaceUnitVisibilityPayload;
import dev.totem.nexus.space.NexusTeleportArrayExpansionRules;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Guards the persisted and network names that the compatibility bundle must retain. */
class NexusCompatibilitySurfaceTest {
    @Test
    void copiedPayloadIdsRemainExact() {
        assertEquals(List.of(
                "deadrecall:request_space_unit_map", "deadrecall:request_space_unit_friends",
                "deadrecall:remove_space_unit_friend", "deadrecall:start_space_unit_teleport",
                "deadrecall:toggle_space_unit_favorite", "deadrecall:calibrate_space_unit",
                "deadrecall:update_space_unit_visibility", "deadrecall:rename_space_unit",
                "deadrecall:update_space_unit_access", "deadrecall:confirm_space_unit_registration",
                "deadrecall:repair_space_unit",
                "deadrecall:space_unit_friends", "deadrecall:request_death_node_admin",
                "deadrecall:manage_death_node_admin", "deadrecall:death_node_admin",
                "deadrecall:space_unit_registration_preview", "deadrecall:space_unit_map",
                "deadrecall:request_teleport_array_visualization",
                "deadrecall:teleport_array_visualization",
                "deadrecall:teleport_array_visualization_status"),
                List.of(
                        RequestSpaceUnitMapPayload.TYPE.id().toString(), RequestSpaceUnitFriendsPayload.TYPE.id().toString(),
                        RemoveSpaceUnitFriendPayload.TYPE.id().toString(), StartSpaceUnitTeleportPayload.TYPE.id().toString(),
                        ToggleSpaceUnitFavoritePayload.TYPE.id().toString(), CalibrateSpaceUnitPayload.TYPE.id().toString(),
                        UpdateSpaceUnitVisibilityPayload.TYPE.id().toString(), RenameSpaceUnitPayload.TYPE.id().toString(),
                        UpdateSpaceUnitAccessPayload.TYPE.id().toString(), ConfirmSpaceUnitRegistrationPayload.TYPE.id().toString(),
                        RepairSpaceUnitPayload.TYPE.id().toString(),
                        SpaceUnitFriendsPayload.TYPE.id().toString(), RequestDeathNodeAdminPayload.TYPE.id().toString(),
                        ManageDeathNodeAdminPayload.TYPE.id().toString(), DeathNodeAdminPayload.TYPE.id().toString(),
                        SpaceUnitRegistrationPreviewPayload.TYPE.id().toString(), SpaceUnitMapPayload.TYPE.id().toString(),
                        RequestTeleportArrayVisualizationPayload.TYPE.id().toString(),
                        TeleportArrayVisualizationPayload.TYPE.id().toString(),
                        TeleportArrayVisualizationStatusPayload.TYPE.id().toString()));
    }

    @Test
    void teleportArrayExpansionModeCommandValuesRemainStable() {
        assertEquals(NexusTeleportArrayExpansionRules.ExpansionMode.LOCAL,
                NexusTeleportArrayExpansionRules.ExpansionMode.DEFAULT);
        assertEquals(List.of(0, 1), Arrays.stream(
                        NexusTeleportArrayExpansionRules.ExpansionMode.values())
                .map(NexusTeleportArrayExpansionRules.ExpansionMode::snapshotCode)
                .toList());
        assertEquals(List.of("local", "centered"), Arrays.stream(
                        NexusTeleportArrayExpansionRules.ExpansionMode.values())
                .map(NexusTeleportArrayExpansionRules.ExpansionMode::toString)
                .toList());
    }

    @Test
    void internalExpansionModeMarkerIsNotAddedToClientPayloads() {
        assertFalse(Stream.of(
                        SpaceUnitMapPayload.MaterialSummary.class,
                        TeleportArrayVisualizationPayload.class)
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .map(component -> component.getName().toLowerCase(java.util.Locale.ROOT))
                .anyMatch(name -> name.contains("expansionmode") || name.contains("expansion_mode")));
    }
}
