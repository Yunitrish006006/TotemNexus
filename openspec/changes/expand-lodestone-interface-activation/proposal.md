## Why

Nexus recognizes several handheld interfaces, but registration, binding,
management, and map presentation are inconsistent. In particular, arbitrary
filled maps can currently act as interfaces even though they are not anchored
to a Nexus, while non-compass interfaces cannot carry the server-owned Space
Unit identity required for a durable and secure management workflow.

## What Changes

- Allow ordinary compasses, recovery compasses, plain books, and Nexus-bound
  filled maps to bind to a Space Unit and use the existing server-authoritative
  discovery, friendship, membership, and lodestone-management capabilities.
- Keep authority out of the item: an interface stores only a Space Unit UUID and
  data version; the server authenticates the logged-in player's UUID against
  the Space Unit owner, administrator, and allowed-player records for every
  privileged action.
- Bind pending registration to the initiating hand and exact server-resolved
  interface identity so item swaps, map swaps, stale confirmations, copies, and
  forged item data cannot grant authority.
- Preserve native components and behavior. Ordinary compasses retain their
  vanilla lodestone tracker; recovery compasses retain recovery behavior; books
  remain books and split one safe bound copy from a stack; filled maps retain
  their server-created map ID.
- Reserve normal plain-book right-click for manual acquisition/refresh and use
  crouch + right-click for Nexus activation.
- Require the map lifecycle to begin with an empty map used on an authorized
  lodestone. The server creates a new vanilla map centered on that lodestone and
  binds it to the Space Unit. Existing or arbitrary filled maps cannot be
  converted into Nexus maps.
- Restrict the Nexus map view to a correctly bound Nexus filled map. It uses the
  associated vanilla map coverage and shows only authorized same-dimension,
  in-bounds Nexus markers with their names; it never exposes out-of-bounds or
  permission-specific markers through shared map SavedData.
- Keep Observer rendering framebuffer-free and keep the existing semantic
  protocol stable unless production Screen state genuinely changes.
- Add focused unit/GameTest coverage and update English, Traditional Chinese,
  manual, and README guidance.

## Impact

- Affected specs: new `lodestone-interface-activation` capability.
- Affected code: item resolver/binding, server interaction and pending
  registration authority, map lifecycle and payload filtering, management and
  friendship gates, localization, manual content, README, and tests.
- Security: items contain no player authority; all permissions are resolved
  from server-owned SavedData using the authenticated player's UUID. No client
  hand, type, map ID, binding, owner UUID, coordinates, or permissions are
  trusted.
- Compatibility: existing ordinary-compass bindings remain readable. Existing
  arbitrary filled maps remain ordinary maps and cannot silently become Nexus
  maps or have a shared map ID recentered.
- Observer: no framebuffer, screenshot, video, reflection renderer copy, or
  hand-drawn Observer mirror is introduced.
