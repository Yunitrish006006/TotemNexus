package dev.totem.nexus.network;

import net.minecraft.server.level.ServerPlayer;

/** Server-authoritative Death Node admin endpoints supplied at Nexus cutover. */
public interface NexusDeathNodeAdminHandler {
    void requestAdminList(ServerPlayer player, RequestDeathNodeAdminPayload payload);
    void manageNode(ServerPlayer player, ManageDeathNodeAdminPayload payload);
}
