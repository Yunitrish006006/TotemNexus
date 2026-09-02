## Context

`TeleportInterfaceType.hasMapVisualization()` currently controls three distinct
concerns: destination payload breadth, client target selection, and teleport
button visibility. That coupling correctly hides the map from a compass but
also makes compass teleport impossible. The server quote and teleport-session
authority already accept an interface type independently of map rendering.

The Nexus map has a separate failure risk: selecting a target from map
coordinates depends on validated payload entries and vanilla `MapItemSavedData`
being available to the production screen. The behavior must remain testable
without introducing map pixels into the Observer protocol.

## Goals / Non-Goals

- Goals:
  - Give the ordinary compass a list-only teleport selection flow.
  - Preserve Nexus-map marker-coordinate selection without a destination list.
  - Prove both paths reach and complete the same server-authoritative teleport
    flow while holding the exact valid interface.
  - Keep the changed production Screen reconstructable as a read-only Observer
    screen with remote cursor state and no framebuffer transport.
- Non-Goals:
  - Give recovery compasses or books teleport selection.
  - Show a map, minimap, fake terrain, or map markers in the compass variant.
  - Show a destination list in the Nexus-map variant.
  - Convert arbitrary filled maps or trust item data as authorization.
  - Persist viewer-specific Nexus markers into shared vanilla map SavedData.

## Decisions

### Model teleport and visualization as independent capabilities

The interface model will expose an explicit destination-teleport capability.
Ordinary compass and valid Nexus map enable it; recovery compass and book do
not. `hasMapVisualization()` remains true only for the filled-map interface.

This avoids granting a map merely because an interface can teleport and avoids
scattering item-type comparisons across payload and UI code.

### Use authorized discovered entries for the compass list

The compass payload will include active Space Units already admitted by the
server's viewer/access/discovery policy, bounded by the existing payload entry
limit. It will not apply filled-map coverage because the compass has no map
extent. The source remains present, while source-to-source quotes stay blocked.

The filled-map payload remains restricted by its persisted vanilla map
dimension, center, scale, and coverage. Thus expanding compass reach does not
weaken map privacy or change map marker bounds.

### Keep the compass list and Nexus map as distinct selection surfaces

The compass variant will render a searchable, sortable, scrollable
destination-list family in the content area, plus the selected target's
server-calculated quote and teleport button. It will not call the map render
path or draw a lookalike background.

The map variant will render the vanilla map and transient Nexus markers without
a destination list. A primary click within a marker's bounded hit radius
selects the entry at that map coordinate. Keyboard navigation will cycle or
focus the same in-bounds markers without adding a visible list. Marker selection
updates the selected UUID, narration, quote footer, and teleport button.

The map uses integer `1x` through `4x` scaling so vanilla pixels remain crisp.
The mouse wheel or plus/minus keys change zoom, while primary-button dragging
or Shift+arrow keys pan within bounds that keep the map over its viewport. A
drag beyond a small threshold cancels the pending marker click.

Both variants use native Minecraft widgets, integer coordinates, font,
hover/focus feedback, narration, and localized text as the visual and
interaction baseline, but they do not combine their selection surfaces.

### Revalidate the exact interface at teleport start and throughout preparation

The client sends only source type, source UUID, and selected target UUID. The
server re-resolves the recorded hand and exact item identity, then re-resolves
source proximity, target visibility/access, current structures, route, costs,
and resources. For a map, the MapId and server-owned Nexus map binding must
still match. The existing teleport-session cancellation checks remain active
during preparation.

### Keep Observer semantic and versioned

The owning module will capture the changed Screen's bounded semantic state
needed to reconstruct the correct map or compass variant, its current
selection, and the map viewport's bounded zoom/pan values. It will use an
incremented protocol if the snapshot shape changes,
accept only the exact family/variant/protocol with increasing sequence values,
and apply capture/create/update on the client thread.

Observer will instantiate the same production Screen and production render
branches, render the relay-provided remote cursor/carried stack, and suppress
mouse, keyboard, scrolling, widget actions, lifecycle requests, and packets
except the explicit stop-observing action. It will not transmit vanilla map
pixels, screenshots, framebuffers, video, private input, or secrets; a viewer
without the matching local vanilla MapId cache receives the existing explicit
map-unavailable presentation.

## Risks / Trade-offs

- A compass can expose more destinations than a bounded map. Mitigation: use
  only the already-authorized discovered view and retain the payload size cap.
- A map may open before its vanilla client map cache is populated. Mitigation:
  use normal vanilla map-data synchronization for the held valid map and retain
  a deterministic unavailable state; never copy pixel data into Observer.
- The two deliberate selection surfaces can drift in quote or selection state.
  Mitigation: share the selected-entry, quote-footer, teleport-widget, and
  server request state, and cover both variants with native-scale screenshots
  and interaction tests in English and Traditional Chinese where text fit
  differs.

## Migration Plan

No SavedData or item rewrite is required. Existing valid bindings continue to
resolve. The new interface capability is derived from the item type at runtime.
If validation fails, the feature can be rolled back without changing world or
item data.
