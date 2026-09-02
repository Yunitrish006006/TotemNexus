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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Nexus-owned factories for modern and compatibility production screens. */
public final class NexusObserverScreenProvider implements ObserverScreenProvider {
    private static final Set<String> VARIANTS = Set.of(
            "compass", "map", "management", "map_legacy", "friends", "friends_legacy",
            "registration", "registration_legacy");
    private static final String SELECTED_UNIT_ID = "selected_unit_id";
    private static final String MAP_ZOOM = "map_zoom";
    private static final String MAP_PAN_X = "map_pan_x";
    private static final String MAP_PAN_Y = "map_pan_y";

    @Override public String familyId() { return "nexus"; }
    @Override public int protocolVersion() { return 3; }
    @Override public Set<String> variants() { return VARIANTS; }

    @Override public Optional<ObserverScreenSnapshot> capture(Screen screen, long sequence) {
        String variant;
        Object payload;
        StreamCodec<FriendlyByteBuf, ?> codec;
        Map<String, String> metadata = Map.of();
        if (screen instanceof NexusSpaceUnitMapScreen owned && !owned.totem$isObserverReadOnly()) {
            variant = owned.observerVariant(); payload = owned.observerPayload(); codec = SpaceUnitMapPayload.CODEC;
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            UUID selectedUnitId = owned.observerSelectedUnitId();
            if (selectedUnitId != null) {
                values.put(SELECTED_UNIT_ID, selectedUnitId.toString());
            }
            if ("map".equals(variant)) {
                values.put(MAP_ZOOM, Integer.toString(owned.observerMapZoom()));
                values.put(MAP_PAN_X, Integer.toString(owned.observerMapPanX()));
                values.put(MAP_PAN_Y, Integer.toString(owned.observerMapPanY()));
            }
            metadata = Map.copyOf(values);
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
                screen.getTitle(), java.util.List.of(), new int[0], metadata, encodeUnchecked(codec, payload)));
    }

    @Override public ObserverScreenHandle create(ObserverScreenContext context, ObserverScreenSnapshot snapshot) {
        if (!supports(snapshot)) throw new IllegalArgumentException("Incompatible Nexus Observer snapshot");
        Screen screen = switch (snapshot.variant()) {
            case "compass", "map", "management" -> createMapScreen(context, snapshot);
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

    private static NexusSpaceUnitMapScreen createMapScreen(
            ObserverScreenContext context,
            ObserverScreenSnapshot snapshot) {
        SpaceUnitMapPayload payload = decode(snapshot, SpaceUnitMapPayload.CODEC);
        if (!snapshot.variant().equals(variantFor(payload.interfaceType()))) {
            throw new IllegalArgumentException("Nexus Observer interface type does not match its variant");
        }
        Optional<UUID> selectedUnitId = selectedUnitId(snapshot);
        MapViewState mapView = mapViewState(snapshot);
        NexusSpaceUnitMapScreen screen = new NexusSpaceUnitMapScreen(payload, true, context.stopObserving());
        selectedUnitId.ifPresent(screen::applyObserverSelection);
        screen.applyObserverMapView(mapView.zoom(), mapView.panX(), mapView.panY());
        return screen;
    }

    private static String variantFor(dev.totem.nexus.space.TeleportInterfaceType interfaceType) {
        return switch (interfaceType) {
            case COMPASS -> "compass";
            case FILLED_MAP -> "map";
            case RECOVERY_COMPASS, BOOK -> "management";
        };
    }

    private static Optional<UUID> selectedUnitId(ObserverScreenSnapshot snapshot) {
        String value = snapshot.metadata().get(SELECTED_UNIT_ID);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid Nexus Observer selected Space Unit identity", invalid);
        }
    }

    private static MapViewState mapViewState(ObserverScreenSnapshot snapshot) {
        if (!"map".equals(snapshot.variant())) {
            return MapViewState.DEFAULT;
        }
        return new MapViewState(
                boundedInt(snapshot.metadata(), MAP_ZOOM, 1, 1, 4),
                boundedInt(snapshot.metadata(), MAP_PAN_X, 0, -4096, 4096),
                boundedInt(snapshot.metadata(), MAP_PAN_Y, 0, -4096, 4096));
    }

    private static int boundedInt(
            Map<String, String> metadata, String key, int fallback, int minimum, int maximum) {
        String value = metadata.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minimum || parsed > maximum) {
                throw new IllegalArgumentException("Nexus Observer " + key + " is outside its legal range");
            }
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid Nexus Observer " + key, invalid);
        }
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
        private long cursorSequence = -1L;
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
            if (screen instanceof NexusSpaceUnitMapScreen modernMap) {
                SpaceUnitMapPayload payload = decode(snapshot, SpaceUnitMapPayload.CODEC);
                if (!variant.equals(variantFor(payload.interfaceType()))) return;
                Optional<UUID> selectedUnitId = selectedUnitId(snapshot);
                MapViewState mapView = mapViewState(snapshot);
                modernMap.applyPayload(payload);
                selectedUnitId.ifPresent(modernMap::applyObserverSelection);
                modernMap.applyObserverMapView(mapView.zoom(), mapView.panX(), mapView.panY());
            } else if (screen instanceof NexusMapScreen legacyMap)
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
        @Override public void applyCursor(ObserverRemoteCursor cursor) {
            if (cursor.sequence() <= cursorSequence) return;
            cursorSequence = cursor.sequence();
            // TotemVanillaTweaks owns normalized cursor rendering and carried-stack transport.
        }
    }

    private record MapViewState(int zoom, int panX, int panY) {
        private static final MapViewState DEFAULT = new MapViewState(1, 0, 0);
    }
}
