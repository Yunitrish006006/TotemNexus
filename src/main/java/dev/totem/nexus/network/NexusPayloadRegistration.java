package dev.totem.nexus.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serverbound wire-type registration for the future Nexus cutover.
 *
 * <p>This class is deliberately not invoked from the module initializer while
 * DeadRecall remains the live owner of these {@code deadrecall:*} identifiers.
 */
public final class NexusPayloadRegistration {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final AtomicBoolean CLIENTBOUND_TYPES_REGISTERED = new AtomicBoolean();
    private static final AtomicBoolean RECEIVERS_REGISTERED = new AtomicBoolean();
    private static final AtomicBoolean DEATH_NODE_RECEIVERS_REGISTERED = new AtomicBoolean();

    private NexusPayloadRegistration() {
    }

    public static void registerServerboundTypes() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        PayloadTypeRegistry.serverboundPlay().register(RequestSpaceUnitMapPayload.TYPE, RequestSpaceUnitMapPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestSpaceUnitFriendsPayload.TYPE, RequestSpaceUnitFriendsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RemoveSpaceUnitFriendPayload.TYPE, RemoveSpaceUnitFriendPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StartSpaceUnitTeleportPayload.TYPE, StartSpaceUnitTeleportPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ToggleSpaceUnitFavoritePayload.TYPE, ToggleSpaceUnitFavoritePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CalibrateSpaceUnitPayload.TYPE, CalibrateSpaceUnitPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateSpaceUnitVisibilityPayload.TYPE, UpdateSpaceUnitVisibilityPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RenameSpaceUnitPayload.TYPE, RenameSpaceUnitPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateSpaceUnitAccessPayload.TYPE, UpdateSpaceUnitAccessPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ConfirmSpaceUnitRegistrationPayload.TYPE, ConfirmSpaceUnitRegistrationPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestDeathNodeAdminPayload.TYPE, RequestDeathNodeAdminPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ManageDeathNodeAdminPayload.TYPE, ManageDeathNodeAdminPayload.CODEC);
    }

    /** Registers every Nexus-owned clientbound codec before a server may send it. */
    public static void registerClientboundTypes() {
        if (!CLIENTBOUND_TYPES_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        PayloadTypeRegistry.clientboundPlay().register(SpaceUnitMapPayload.TYPE, SpaceUnitMapPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SpaceUnitFriendsPayload.TYPE, SpaceUnitFriendsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                SpaceUnitRegistrationPreviewPayload.TYPE,
                SpaceUnitRegistrationPreviewPayload.CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(DeathNodeAdminPayload.TYPE, DeathNodeAdminPayload.CODEC);
    }

    /** Activates receivers only when the complete server authority layer is cut over. */
    public static void registerReceivers(NexusPayloadHandler handler) {
        Objects.requireNonNull(handler, "handler");
        if (!RECEIVERS_REGISTERED.compareAndSet(false, true)) return;
        ServerPlayNetworking.registerGlobalReceiver(RequestSpaceUnitMapPayload.TYPE,
                (payload, context) -> context.server().execute(() -> handler.requestMap(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(RequestSpaceUnitFriendsPayload.TYPE,
                (payload, context) -> context.server().execute(() -> handler.requestFriends(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(RemoveSpaceUnitFriendPayload.TYPE,
                (payload, context) -> context.server().execute(() -> handler.removeFriend(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(StartSpaceUnitTeleportPayload.TYPE,
                (payload, context) -> context.server().execute(() -> handler.startTeleport(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ToggleSpaceUnitFavoritePayload.TYPE,
                (payload, context) -> context.server().execute(() -> handler.toggleFavorite(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(CalibrateSpaceUnitPayload.TYPE,
                (payload, context) -> context.server().execute(() -> handler.calibrate(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(UpdateSpaceUnitVisibilityPayload.TYPE,
                (payload, context) -> context.server().execute(() -> handler.updateVisibility(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(RenameSpaceUnitPayload.TYPE,
                (payload, context) -> context.server().execute(() -> handler.rename(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(UpdateSpaceUnitAccessPayload.TYPE,
                (payload, context) -> context.server().execute(() -> handler.updateAccess(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ConfirmSpaceUnitRegistrationPayload.TYPE,
                (payload, context) -> context.server().execute(() -> handler.confirmRegistration(context.player(), payload)));
    }

    /** Activates Death Node admin receivers only when their service migrates with Nexus. */
    public static void registerDeathNodeAdminReceivers(NexusDeathNodeAdminHandler handler) {
        Objects.requireNonNull(handler, "handler");
        if (!DEATH_NODE_RECEIVERS_REGISTERED.compareAndSet(false, true)) return;
        ServerPlayNetworking.registerGlobalReceiver(RequestDeathNodeAdminPayload.TYPE,
                (payload, context) -> context.server().execute(() -> handler.requestAdminList(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ManageDeathNodeAdminPayload.TYPE,
                (payload, context) -> context.server().execute(() -> handler.manageNode(context.player(), payload)));
    }
}
