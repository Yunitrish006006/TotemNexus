package dev.totem.nexus.client;

import java.util.List;

/** Runtime-only Client GameTest probe implemented by the production map Screen mixin. */
public interface NexusMapDetailVisualTestAccess {
    boolean totem$localPlayerMarkerRenderedForVisualTest();
    boolean totem$observedPlayerMarkerRenderedForVisualTest();
    List<Integer> totem$detailLayersRenderedForVisualTest();
}
