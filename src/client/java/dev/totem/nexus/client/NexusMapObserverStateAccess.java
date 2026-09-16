package dev.totem.nexus.client;

/** Mixin-backed bridge for map-local Observer presentation state. */
public interface NexusMapObserverStateAccess {
    NexusMapPlayerMarker.Marker totem$captureLocalPlayerMarker();
    void totem$applyObservedPlayerMarker(NexusMapPlayerMarker.Marker marker);
}
