package dev.totem.nexus.client;

import dev.totem.nexus.network.SpaceUnitFriendsPayload;
import dev.totem.nexus.network.DeathNodeAdminPayload;
import dev.totem.nexus.network.SpaceUnitRegistrationPreviewPayload;
import dev.totem.nexus.network.SpaceUnitMapPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Clientbound registration boundary for the future Space Unit UI cutover. */
public final class NexusClientPayloadRegistration {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final AtomicBoolean MAP_RECEIVER_REGISTERED = new AtomicBoolean();
    private static final AtomicBoolean ADDITIONAL_RECEIVERS_REGISTERED = new AtomicBoolean();

    private NexusClientPayloadRegistration() {
    }

    public static void registerSpaceUnitFriends(Consumer<SpaceUnitFriendsPayload> receiver) {
        Objects.requireNonNull(receiver, "receiver");
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        PayloadTypeRegistry.clientboundPlay().register(SpaceUnitFriendsPayload.TYPE, SpaceUnitFriendsPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(
                SpaceUnitFriendsPayload.TYPE,
                (payload, context) -> context.client().execute(() -> receiver.accept(payload))
        );
    }

    /** Activates the Space Unit map only when its complete client UI is migrated. */
    public static void registerSpaceUnitMap(Consumer<SpaceUnitMapPayload> receiver) {
        Objects.requireNonNull(receiver, "receiver");
        if (!MAP_RECEIVER_REGISTERED.compareAndSet(false, true)) return;
        PayloadTypeRegistry.clientboundPlay().register(SpaceUnitMapPayload.TYPE, SpaceUnitMapPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(
                SpaceUnitMapPayload.TYPE,
                (payload, context) -> context.client().execute(() -> receiver.accept(payload))
        );
    }

    /** Activates remaining UI payloads only when their client state consumers migrate. */
    public static void registerAdditionalReceivers(
            Consumer<DeathNodeAdminPayload> deathNodeReceiver,
            Consumer<SpaceUnitRegistrationPreviewPayload> previewReceiver
    ) {
        Objects.requireNonNull(deathNodeReceiver, "deathNodeReceiver");
        Objects.requireNonNull(previewReceiver, "previewReceiver");
        if (!ADDITIONAL_RECEIVERS_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        PayloadTypeRegistry.clientboundPlay().register(DeathNodeAdminPayload.TYPE, DeathNodeAdminPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                SpaceUnitRegistrationPreviewPayload.TYPE,
                SpaceUnitRegistrationPreviewPayload.CODEC
        );
        ClientPlayNetworking.registerGlobalReceiver(
                DeathNodeAdminPayload.TYPE,
                (payload, context) -> context.client().execute(() -> deathNodeReceiver.accept(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(
                SpaceUnitRegistrationPreviewPayload.TYPE,
                (payload, context) -> context.client().execute(() -> previewReceiver.accept(payload))
        );
    }
}
