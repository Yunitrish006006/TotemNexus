package dev.totem.nexus.client;

import dev.totem.nexus.network.DeathNodeAdminPayload;
import dev.totem.nexus.network.SpaceUnitFriendsPayload;
import dev.totem.nexus.network.SpaceUnitMapPayload;
import dev.totem.nexus.network.SpaceUnitRegistrationPreviewPayload;

import java.util.Optional;
import java.util.function.Consumer;

/** Client-only authoritative payload snapshot; screens consume this after UI migration. */
public final class NexusClientState {
    private SpaceUnitMapPayload map;
    private SpaceUnitFriendsPayload friends;
    private SpaceUnitRegistrationPreviewPayload registrationPreview;
    private DeathNodeAdminPayload deathNodeAdmin;
    private Consumer<SpaceUnitRegistrationPreviewPayload> registrationPreviewConsumer = ignored -> { };
    private Consumer<SpaceUnitFriendsPayload> friendsConsumer = ignored -> { };
    private Consumer<SpaceUnitMapPayload> mapConsumer = ignored -> { };

    public void acceptMap(SpaceUnitMapPayload value) { map = value; mapConsumer.accept(value); }
    public void acceptFriends(SpaceUnitFriendsPayload value) { friends = value; friendsConsumer.accept(value); }
    public void acceptRegistrationPreview(SpaceUnitRegistrationPreviewPayload value) { registrationPreview = value; registrationPreviewConsumer.accept(value); }
    public void acceptDeathNodeAdmin(DeathNodeAdminPayload value) { deathNodeAdmin = value; }
    public Optional<SpaceUnitMapPayload> map() { return Optional.ofNullable(map); }
    public Optional<SpaceUnitFriendsPayload> friends() { return Optional.ofNullable(friends); }
    public Optional<SpaceUnitRegistrationPreviewPayload> registrationPreview() { return Optional.ofNullable(registrationPreview); }
    public Optional<DeathNodeAdminPayload> deathNodeAdmin() { return Optional.ofNullable(deathNodeAdmin); }
    public void clear() { map = null; friends = null; registrationPreview = null; deathNodeAdmin = null; }
    public void onRegistrationPreview(Consumer<SpaceUnitRegistrationPreviewPayload> consumer) { registrationPreviewConsumer = consumer == null ? ignored -> { } : consumer; }
    public void onFriends(Consumer<SpaceUnitFriendsPayload> consumer) { friendsConsumer = consumer == null ? ignored -> { } : consumer; }
    public void onMap(Consumer<SpaceUnitMapPayload> consumer) { mapConsumer = consumer == null ? ignored -> { } : consumer; }
}
