package dev.totem.nexus.space;

import dev.totem.nexus.network.ManageDeathNodeAdminPayload;
import dev.totem.nexus.network.NexusDeathNodeAdminHandler;
import dev.totem.nexus.network.RequestDeathNodeAdminPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Wire adapter for the complete Nexus-owned death-node administration
 * service. It is instantiated only by the future atomic Nexus cutover.
 */
public final class NexusDeathNodeAdminAuthority implements NexusDeathNodeAdminHandler {
    @Override
    public void requestAdminList(ServerPlayer player, RequestDeathNodeAdminPayload payload) {
        NexusDeathNodeAdminService.sendSnapshot(player, payload);
    }

    @Override
    public void manageNode(ServerPlayer player, ManageDeathNodeAdminPayload payload) {
        NexusDeathNodeAdminService.handleAction(
                player,
                payload.nodeId(),
                payload.action(),
                payload.confirmationToken()
        );
    }
}
