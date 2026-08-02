package dev.totem.nexus.network;

import dev.totem.nexus.space.NexusTeleportAuthority;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/**
 * Mechanical wire-to-authority adapter used only once the complete Nexus
 * authority implementation is ready for receiver cutover.
 */
public final class NexusAuthorityPayloadHandler implements NexusPayloadHandler {
    private final NexusTeleportAuthority authority;

    public NexusAuthorityPayloadHandler(NexusTeleportAuthority authority) {
        this.authority = Objects.requireNonNull(authority, "authority");
    }

    @Override public void requestMap(ServerPlayer player, RequestSpaceUnitMapPayload payload) {
        authority.sendMap(player, payload.sourceType(), payload.sourceUnitId());
    }
    @Override public void requestFriends(ServerPlayer player, RequestSpaceUnitFriendsPayload payload) {
        authority.sendFriends(player);
    }
    @Override public void removeFriend(ServerPlayer player, RemoveSpaceUnitFriendPayload payload) {
        authority.removeFriend(player, payload.friendId());
    }
    @Override public void startTeleport(ServerPlayer player, StartSpaceUnitTeleportPayload payload) {
        authority.startTeleport(player, payload.sourceType(), payload.sourceUnitId(), payload.targetUnitId());
    }
    @Override public void toggleFavorite(ServerPlayer player, ToggleSpaceUnitFavoritePayload payload) {
        authority.setFavorite(player, payload.sourceType(), payload.sourceUnitId(), payload.targetUnitId(), payload.favorite());
    }
    @Override public void calibrate(ServerPlayer player, CalibrateSpaceUnitPayload payload) {
        authority.calibrate(player, payload.sourceType(), payload.sourceUnitId(), payload.targetUnitId());
    }
    @Override public void updateVisibility(ServerPlayer player, UpdateSpaceUnitVisibilityPayload payload) {
        authority.setVisibility(player, payload.sourceType(), payload.sourceUnitId(), payload.targetUnitId(), payload.visibility());
    }
    @Override public void rename(ServerPlayer player, RenameSpaceUnitPayload payload) {
        authority.rename(player, payload.sourceType(), payload.sourceUnitId(), payload.targetUnitId(), payload.name());
    }
    @Override public void updateAccess(ServerPlayer player, UpdateSpaceUnitAccessPayload payload) {
        authority.setAccess(player, payload.sourceType(), payload.sourceUnitId(), payload.targetUnitId(),
                payload.role(), payload.playerName(), payload.enabled());
    }
    @Override public void repair(ServerPlayer player, RepairSpaceUnitPayload payload) {
        authority.repair(player, payload.sourceType(), payload.sourceUnitId(), payload.targetUnitId(),
                payload.x(), payload.y(), payload.z());
    }
    @Override public void confirmRegistration(ServerPlayer player, ConfirmSpaceUnitRegistrationPayload payload) {
        authority.confirmRegistration(player, payload.dimension(), payload.x(), payload.y(), payload.z());
    }
}
