package dev.totem.nexus.client;
/** Observes the actual held-item render hook; it does not create a substitute rendering path. */
public interface NexusHeldMapVisualTestAccess {
    int totem$lastHeldMap();
    int totem$heldMarkerCount();
    void totem$resetHeldProbe();
}
