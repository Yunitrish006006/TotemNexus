## Context

`TeleportInterfaceItemResolver` recognizes ordinary compasses, recovery
compasses, plain books, and any filled map carrying a map ID. Registration,
binding, player interaction, discovery, and management still contain ordinary-
compass gates. Pending registration contains only dimension, position, and
expiry, and GUI confirmation searches either hand for an ordinary compass.

The existing item binding contains a Space Unit UUID, while the Space Unit
record contains owner, administrator, and allowed-player UUIDs. This is the
correct trust boundary: the item identifies a server record but cannot grant a
player role. Filled maps additionally have shared vanilla SavedData, so viewer-
specific Nexus marker visibility must not be persisted into that shared data.

## Goals / Non-Goals

- Goals:
  - give every supported interface a durable Space Unit binding and common
    discovery/friend/member/management access;
  - keep every privileged operation authorized from the authenticated player
    UUID and server-owned Space Unit records;
  - create Nexus maps from empty maps at an authorized lodestone, centered on
    that lodestone, without mutating an existing filled map ID;
  - preserve each item's native identity and components;
  - expose map visuals only to a correctly bound Nexus map and filter named
    markers by viewer permission, dimension, and vanilla map bounds;
  - retain all existing safety checks and framebuffer-free Observer behavior.
- Non-Goals:
  - store or trust an owner/player UUID in an item;
  - make written books, system manuals, arbitrary filled maps, malformed maps,
    or other items Nexus interfaces;
  - force-load chunks to register, expand, render, or validate a map;
  - change teleport-interface bonuses beyond requiring a valid Nexus map for
    filled-map behavior;
  - stream pixels, screenshots, framebuffers, or video.

## Decisions

### Use a shared server-owned interface binding

The binding codec stores only the Space Unit UUID and current data version in
item CustomData. Reading validates that the stack resolves to a supported
interface and that any filled map is a valid Nexus map. The binding never stores
an owner UUID and never substitutes for `NexusSpaceUnitRecord.canManage` or
visibility/access checks.

The ordinary compass also receives its vanilla `LodestoneTracker`. A recovery
compass receives only Nexus CustomData, preserving recovery-target components.
A book receives only Nexus CustomData; when a non-creative stack contains more
than one book, one exact component-preserving copy is split, bound, and inserted
or dropped. A filled map receives its binding only after the server creates its
new MapId.

Copied binding data identifies the same Space Unit but conveys no authority.
Forged, missing, inactive, inaccessible, or mismatched bindings are rejected by
server record and authenticated-player checks.

### Bind pending registration to exact initiating identity

The pending record retains dimension, position, expiry, initiating
`InteractionHand`, resolved interface kind, and relevant map identity. A shared
validator re-resolves the exact hand at confirmation and refuses substitution.

Ordinary compass, recovery compass, and crouching plain book may begin the
existing preview flow directly. An empty map is a registration input kind: it
is accepted only on the lodestone interaction path, then converted after
successful confirmation into a newly allocated filled map centered on the
lodestone. Arbitrary filled maps are not registration inputs. A bound Nexus map
may open only the Space Unit named by its own binding.

### Keep plain-book gestures unambiguous

At a lodestone, normal right-click with a plain book continues to acquire or
refresh the manual before interface routing. Crouch + right-click enters Nexus
registration/opening. Written/system manuals are never resolved as interfaces.

### Make capabilities explicit

Interface type/capability methods distinguish registration inputs, durable
binding, management/friend access, and map-view access. All four bound interface
families support discovery and management/friend/member actions. Only a valid
bound filled map supports the map visualization. Ordinary compasses, recovery
compasses, and books receive management UI without a map canvas.

### Anchor and filter the Nexus map without leaking shared state

The server allocates vanilla map data with the lodestone's X/Z and dimension as
its center. Future scaling/expansion derives from that persisted center, never
the player's location. If the source lodestone is missing or unloaded, existing
map data remains readable but scaling/expansion is refused until the anchor is
restored or an authorized rebind is performed; validation never force-loads.

Map entries sent to a player are filtered to active Space Units the player may
view, in the same dimension as the map, and within `FilledMapCoverage`. No edge
arrows are synthesized. Marker labels use the Space Unit name, with a localized
fallback for a blank legacy name. Viewer-specific decorations are transient
payload/render state and are never written into the shared `MapItemSavedData`.
The vanilla map pixels and map rendering/decorations path remain the source of
the map background and marker presentation.

### Preserve Observer contracts

No raw visual data is transmitted. If the existing production screen requires
new semantic state, TotemNexus owns its versioned provider and the observer
reconstructs the production/vanilla rendering path read-only with sequence,
client-thread, lifecycle, cursor, input/packet suppression, and privacy rules.
When the existing screen protocol is sufficient, it remains unchanged.

## Risks / Trade-offs

- Empty-map conversion is more specialized than the resolver's current filled-
  map path. Mitigation: model it as a server-only registration input and test
  exact hand/type replacement plus atomic stack conversion.
- Books can stack. Mitigation: copy one item with exact components, shrink only
  after successful bind, then insert or drop through the existing safe pattern.
- A copied bound item still names a real Space Unit. Mitigation: every operation
  resolves the current server record and authenticates the acting player's UUID;
  the binding is identity, never authority.
- Vanilla map SavedData is shared by MapId. Mitigation: keep permission-specific
  Nexus markers out of SavedData and deliver them only in the authorized UI
  payload.
- A missing anchor could tempt implicit chunk loading. Mitigation: use explicit
  loaded-state checks and preserve data without expanding.

## Migration Plan

Existing ordinary-compass CustomData remains readable. Existing recovery
compasses and books are bound on their next authorized lodestone interaction.
Existing filled maps remain ordinary maps and are not migrated because changing
their center or shared ID would affect every copy. Players create a Nexus map by
using an empty map on an authorized lodestone. Rollback leaves additional item
CustomData harmless and leaves vanilla map IDs valid.

## Open Questions

- None. The item binding, UUID authority, map creation, coverage, marker, book
  gesture, management scope, and Observer boundaries were explicitly approved.
