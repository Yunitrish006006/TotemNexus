package dev.totem.nexus.client;

import dev.totem.core.api.v1.client.observer.ObserverRemoteCursor;
import dev.totem.core.api.v1.client.observer.ObserverScreenContext;
import dev.totem.core.api.v1.client.observer.ObserverScreenHandle;
import dev.totem.core.api.v1.client.observer.ObserverScreenProvider;
import dev.totem.core.api.v1.client.observer.ObserverScreenSnapshot;
import dev.totem.nexus.network.SpaceUnitFriendsPayload;
import dev.totem.nexus.network.SpaceUnitMapPayload;
import dev.totem.nexus.network.SpaceUnitRegistrationPreviewPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Set;
import java.util.Optional;
import java.util.Map;

/** Nexus-owned factories for modern and compatibility production screens. */
public final class NexusObserverScreenProvider implements ObserverScreenProvider {
    private static final Set<String> VARIANTS = Set.of(
            "map", "map_legacy", "friends", "friends_legacy", "registration", "registration_legacy");

    @Override public String familyId() { return "nexus"; }
    @Override public int protocolVersion() { return 3; }
    @Override public Set<String> variants() { return VARIANTS; }

    @Override public Optional<ObserverScreenSnapshot> capture(Screen screen, long sequence) {
        String variant;
        Object payload;
        StreamCodec<FriendlyByteBuf, ?> codec;
        if (screen instanceof NexusSpaceUnitMapScreen owned && !owned.totem$isObserverReadOnly()) {
            variant = "map"; payload = owned.observerPayload(); codec = SpaceUnitMapPayload.CODEC;
        } else if (screen instanceof NexusMapScreen owned && !owned.totem$isObserverReadOnly()) {
            variant = "map_legacy"; payload = owned.observerPayload(); codec = SpaceUnitMapPayload.CODEC;
        } else if (screen instanceof NexusSpaceUnitFriendsScreen owned && !owned.totem$isObserverReadOnly()) {
            variant = "friends"; payload = owned.observerPayload(); codec = SpaceUnitFriendsPayload.CODEC;
        } else if (screen instanceof NexusFriendsScreen owned && !owned.totem$isObserverReadOnly()) {
            variant = "friends_legacy"; payload = owned.observerPayload(); codec = SpaceUnitFriendsPayload.CODEC;
        } else if (screen instanceof NexusSpaceUnitRegistrationPreviewScreen owned && !owned.totem$isObserverReadOnly()) {
            variant = "registration"; payload = owned.observerPayload(); codec = SpaceUnitRegistrationPreviewPayload.CODEC;
        } else if (screen instanceof NexusRegistrationPreviewScreen owned && !owned.totem$isObserverReadOnly()) {
            variant = "registration_legacy"; payload = owned.observerPayload(); codec = SpaceUnitRegistrationPreviewPayload.CODEC;
        } else return Optional.empty();
        if (payload == null) return Optional.empty();
        return Optional.of(new ObserverScreenSnapshot(familyId(), variant, protocolVersion(), sequence,
                screen.getTitle(), java.util.List.of(), new int[0], Map.of(), encodeUnchecked(codec, payload)));
    }

    @Override public ObserverScreenHandle create(ObserverScreenContext context, ObserverScreenSnapshot snapshot) {
        if (!supports(snapshot)) throw new IllegalArgumentException("Incompatible Nexus Observer snapshot");
        Screen screen = switch (snapshot.variant()) {
            case "map" -> new NexusSpaceUnitMapScreen(decode(snapshot, SpaceUnitMapPayload.CODEC), true,
                    context.stopObserving());
            case "map_legacy" -> new NexusMapScreen(decode(snapshot, SpaceUnitMapPayload.CODEC), true,
                    context.stopObserving());
            case "friends" -> new NexusSpaceUnitFriendsScreen(null,
                    decode(snapshot, SpaceUnitFriendsPayload.CODEC), true, context.stopObserving());
            case "friends_legacy" -> new NexusFriendsScreen(
                    decode(snapshot, SpaceUnitFriendsPayload.CODEC), true, context.stopObserving());
            case "registration" -> new NexusSpaceUnitRegistrationPreviewScreen(
                    decode(snapshot, SpaceUnitRegistrationPreviewPayload.CODEC), true, context.stopObserving());
            case "registration_legacy" -> new NexusRegistrationPreviewScreen(
                    decode(snapshot, SpaceUnitRegistrationPreviewPayload.CODEC), true, context.stopObserving());
            default -> throw new IllegalArgumentException("Unsupported Nexus Observer variant");
        };
        return new Handle(screen, snapshot.variant(), snapshot.sequence());
    }

    private static <T> T decode(ObserverScreenSnapshot snapshot, StreamCodec<FriendlyByteBuf, T> codec) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(snapshot.ownerPayload()));
        try {
            T result = codec.decode(buffer);
            if (buffer.readableBytes() != 0) throw new IllegalArgumentException("Trailing Nexus Observer payload bytes");
            return result;
        } finally {
            buffer.release();
        }
    }

    @SuppressWarnings("unchecked")
    private static byte[] encodeUnchecked(StreamCodec<FriendlyByteBuf, ?> codec, Object value) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ((StreamCodec<FriendlyByteBuf, Object>) codec).encode(buffer, value);
            byte[] bytes = new byte[buffer.readableBytes()]; buffer.getBytes(buffer.readerIndex(), bytes); return bytes;
        } finally { buffer.release(); }
    }

    private final class Handle implements ObserverScreenHandle {
        private final Screen screen;
        private final String variant;
        private long sequence;
        private Handle(Screen screen, String variant, long sequence) {
            this.screen = screen;
            this.variant = variant;
            this.sequence = sequence;
        }
        @Override public Screen screen() { return screen; }
        @Override public void applySnapshot(ObserverScreenSnapshot snapshot) {
            if (!NexusObserverScreenProvider.this.supports(snapshot)
                    || !variant.equals(snapshot.variant())
                    || snapshot.sequence() <= sequence) return;
            if (screen instanceof NexusSpaceUnitMapScreen modernMap)
                modernMap.applyPayload(decode(snapshot, SpaceUnitMapPayload.CODEC));
            else if (screen instanceof NexusMapScreen legacyMap)
                legacyMap.apply(decode(snapshot, SpaceUnitMapPayload.CODEC));
            else if (screen instanceof NexusSpaceUnitFriendsScreen modernFriends)
                modernFriends.applyPayload(decode(snapshot, SpaceUnitFriendsPayload.CODEC));
            else if (screen instanceof NexusFriendsScreen legacyFriends)
                legacyFriends.apply(decode(snapshot, SpaceUnitFriendsPayload.CODEC));
            else if (screen instanceof NexusSpaceUnitRegistrationPreviewScreen modernRegistration)
                modernRegistration.applyPayload(decode(snapshot, SpaceUnitRegistrationPreviewPayload.CODEC));
            else if (screen instanceof NexusRegistrationPreviewScreen legacyRegistration)
                legacyRegistration.apply(decode(snapshot, SpaceUnitRegistrationPreviewPayload.CODEC));
            sequence = snapshot.sequence();
        }
        @Override public void applyCursor(ObserverRemoteCursor ignored) { }
    }
}
