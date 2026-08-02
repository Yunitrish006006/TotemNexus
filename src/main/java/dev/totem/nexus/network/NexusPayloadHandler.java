package dev.totem.nexus.network;

import net.minecraft.server.level.ServerPlayer;

/** Server-authoritative Space Unit endpoints supplied by the future Nexus behavior layer. */
public interface NexusPayloadHandler {
    void requestMap(ServerPlayer player, RequestSpaceUnitMapPayload payload);
    void requestFriends(ServerPlayer player, RequestSpaceUnitFriendsPayload payload);
    void removeFriend(ServerPlayer player, RemoveSpaceUnitFriendPayload payload);
    void startTeleport(ServerPlayer player, StartSpaceUnitTeleportPayload payload);
    void toggleFavorite(ServerPlayer player, ToggleSpaceUnitFavoritePayload payload);
    void calibrate(ServerPlayer player, CalibrateSpaceUnitPayload payload);
    void updateVisibility(ServerPlayer player, UpdateSpaceUnitVisibilityPayload payload);
    void rename(ServerPlayer player, RenameSpaceUnitPayload payload);
    void updateAccess(ServerPlayer player, UpdateSpaceUnitAccessPayload payload);
    void repair(ServerPlayer player, RepairSpaceUnitPayload payload);
    void confirmRegistration(ServerPlayer player, ConfirmSpaceUnitRegistrationPayload payload);
}
