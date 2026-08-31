## ADDED Requirements

### Requirement: Supported interfaces bind to a Space Unit without granting authority

An ordinary compass, recovery compass, plain book, or Nexus map SHALL be able to
carry a server-issued binding containing only a Space Unit UUID and data version.
The server MUST authorize every privileged operation with the authenticated
player UUID against the server-owned Space Unit owner, administrator, and
allowed-player records. An item binding, copied item, or forged CustomData MUST
NOT grant a player a role or bypass visibility, discovery, or management checks.

#### Scenario: Authorized interface binds

- **WHEN** an authorized player completes the binding flow with a supported
  interface
- **THEN** the server writes the Space Unit UUID and data version to that item
- **AND** does not write a player or owner UUID as item authority

#### Scenario: Another player holds a copied binding

- **WHEN** a player who lacks server-record permission holds a copied or forged
  item naming an existing private Space Unit
- **THEN** the server rejects opening and every privileged action
- **AND** the item binding does not alter the Space Unit access records

#### Scenario: Server record changes after binding

- **WHEN** a previously authorized player is removed from the Space Unit access
  records while retaining a bound item
- **THEN** later operations are denied according to current server data

### Requirement: Native interface item behavior is preserved

Binding MUST preserve the item's type and unrelated components. An ordinary
compass MAY additionally receive its vanilla `LodestoneTracker`; a recovery
compass MUST retain its recovery behavior; a book MUST remain a book; and a
Nexus map MUST retain its server-created MapId. When binding one item from a
non-creative stack, the server MUST split one exact component-preserving copy
and leave the remainder unchanged.

#### Scenario: Recovery compass binds

- **WHEN** a recovery compass is bound to a Space Unit
- **THEN** only Nexus binding CustomData is added or updated
- **AND** its recovery-target behavior and unrelated components remain intact

#### Scenario: Book stack binds one copy

- **WHEN** a survival player binds one plain book from a stack
- **THEN** exactly one component-preserving book is split and bound
- **AND** the remaining stack is not converted or rebound

#### Scenario: Nexus map remains the same map

- **WHEN** a created Nexus map is reopened or used for management
- **THEN** its MapId and existing map data remain unchanged

### Requirement: Registration confirmation preserves exact server-resolved identity

Each pending registration MUST retain its player, dimension, lodestone position,
expiry, initiating hand, registration input kind, and relevant map identity. On
repeat interaction and GUI confirmation the server MUST re-resolve that exact
hand and require an exact identity match. Client-provided fields MUST NOT select
or authorize a hand, item, map ID, binding, player role, or Space Unit.

#### Scenario: Matching confirmation succeeds

- **WHEN** the same eligible item remains in the initiating hand and the player
  confirms before expiry at the same valid lodestone
- **THEN** registration may complete after all server safety checks pass

#### Scenario: Interface moves or changes

- **WHEN** the item is removed, moved to the other hand, replaced by another
  type, or its relevant identity changes
- **THEN** confirmation is rejected and invalidated without searching for a
  substitute

#### Scenario: Client replays confirmation

- **WHEN** a client confirms an expired, consumed, mismatched, or absent pending
  registration
- **THEN** the server rejects it even if the coordinates identify a lodestone

### Requirement: Plain-book manual and Nexus gestures remain distinct

Normal right-click with a plain book on a lodestone MUST continue to acquire or
refresh the manual. Only crouch + right-click with a plain book SHALL enter the
Nexus binding, registration, opening, or management path. Written books and
system manuals MUST NOT become Nexus interfaces.

#### Scenario: Normal plain-book use requests the manual

- **WHEN** a non-crouching player right-clicks a lodestone with a plain book
- **THEN** Nexus handles manual acquisition or refresh
- **AND** does not create a pending registration or open Nexus management

#### Scenario: Crouching plain-book use activates Nexus

- **WHEN** a crouching player right-clicks a lodestone with a plain book
- **THEN** Nexus uses the server-authoritative binding/registration/open path

#### Scenario: System manual is not an interface

- **WHEN** a player uses a written Totem or legacy Nexus manual
- **THEN** its existing manual behavior may run
- **AND** it cannot authorize Nexus discovery, binding, management, or map view

### Requirement: Every bound interface supports discovery and management

An ordinary compass, recovery compass, plain book, and Nexus map SHALL support
the existing discovery, friend/member, and lodestone-management operations when
the exact held binding and interface context remain valid. Management MUST still
enforce the current owner/administrator/allowed-player rules using the acting
player's authenticated UUID.

#### Scenario: Any supported interface discovers an accessible lodestone

- **WHEN** a player attacks an accessible registered lodestone within range
  while holding a supported interface
- **THEN** the server records discovery without changing the Space Unit roles

#### Scenario: Authorized manager uses a non-compass interface

- **WHEN** an owner or administrator opens management with a correctly bound
  recovery compass, book, or Nexus map and requests an allowed action
- **THEN** the server processes the action under the same UUID authorization as
  an ordinary compass

#### Scenario: Unauthorized holder requests management

- **WHEN** a holder's authenticated UUID lacks the required server role
- **THEN** the request is rejected regardless of the item's binding

### Requirement: Nexus maps are created from empty maps at an authorized lodestone

A player MUST create a Nexus map by using an empty map on a loaded lodestone
they are authorized to bind. After confirmation, the server SHALL allocate new
vanilla map data centered on that lodestone's X/Z and dimension, produce a
filled map with that new MapId, and bind it to the Space Unit. The server MUST
NOT recenter or repurpose an existing filled map or shared MapId.

#### Scenario: Empty map creates a Nexus map

- **WHEN** an authorized player confirms an empty-map binding at a lodestone
- **THEN** the server consumes or splits one empty map atomically
- **AND** creates a filled Nexus map centered on the lodestone with a new MapId

#### Scenario: Existing filled map is rejected

- **WHEN** a player uses an arbitrary or already explored filled map that lacks
  a valid Nexus binding
- **THEN** it cannot register, bind, or open the Nexus map view
- **AND** its MapId and SavedData remain unchanged

#### Scenario: Bound Nexus map opens only its source

- **WHEN** a valid Nexus map is used at another lodestone or with a client-
  supplied different source UUID
- **THEN** the server retains the map's bound Space Unit identity and rejects a
  mismatched or unauthorized source

### Requirement: Nexus map coverage remains centered on its lodestone

The map center and dimension SHALL remain those created from the bound
lodestone. Scaling or expansion MUST use that persisted center rather than the
player position. If the anchor is missing or unloaded, existing map data SHALL
remain available but new expansion MUST be rejected until the anchor is restored
or an authorized rebind occurs. Validation MUST NOT force-load chunks.

#### Scenario: Player moves before expansion

- **WHEN** a player carries the Nexus map away from its source and expands it
- **THEN** the expanded coverage remains centered on the source lodestone

#### Scenario: Anchor is missing

- **WHEN** expansion is requested while the source lodestone is absent or its
  chunk is not loaded
- **THEN** existing map data remains unchanged and the expansion is refused
- **AND** no chunk is force-loaded

### Requirement: Only a Nexus map exposes the map visualization

The system MUST expose the Nexus map visualization only for a valid filled map
whose MapId and Space Unit binding pass server validation. Ordinary compasses,
recovery compasses, and books MAY expose management controls but MUST NOT expose
the Nexus map canvas.

#### Scenario: Bound Nexus map opens map view

- **WHEN** an authorized player opens a valid bound Nexus map
- **THEN** the client receives the map presentation for that MapId

#### Scenario: Other bound interface opens management

- **WHEN** an authorized player opens a bound ordinary compass, recovery compass,
  or book
- **THEN** management remains available without a map visualization

#### Scenario: Arbitrary filled map is used

- **WHEN** a filled map lacks a valid server-recognized Nexus binding
- **THEN** Nexus does not open a map or management view for it

### Requirement: Nexus map markers are named and visibility bounded

The Nexus map SHALL use the vanilla MapId's pixels, dimension, center, scale,
coverage, and map rendering/decorations path. It SHALL show only active Nexus
Space Units the authenticated viewer may view that are in the same dimension and
inside the map bounds. Each visible marker SHALL display the Space Unit name, or
a localized unnamed fallback. Out-of-bounds markers and edge arrows MUST NOT be
shown. Viewer-specific markers MUST NOT be persisted into shared vanilla map
SavedData.

#### Scenario: Authorized in-bounds Nexus is visible

- **WHEN** an active visible Space Unit lies in the map dimension and bounds
- **THEN** its marker and name appear over the vanilla map presentation

#### Scenario: Nexus is outside map coverage

- **WHEN** a Space Unit is in another dimension or outside the map bounds
- **THEN** no marker, label, or edge arrow for it is sent or shown

#### Scenario: Viewer lacks access

- **WHEN** a private Space Unit is inside map bounds but the current viewer may
  not view it
- **THEN** it is absent from that viewer's transient map presentation
- **AND** no permission-specific marker is written into shared map SavedData

### Requirement: Existing activation safety and Observer contracts remain authoritative

All paths MUST retain spectator, interaction-range, permission, loaded-state,
lodestone-existence, structure, discovery, exact-held-item, monotonic context,
and no-force-load validation. Observer behavior MUST remain framebuffer-free and
read-only, use the owning production/vanilla rendering path, suppress viewer
mutation and packets, and disclose no private input or secrets.

#### Scenario: Unsafe activation is attempted

- **WHEN** a spectator, out-of-range player, unauthorized player, stale context,
  missing item, unloaded target, or missing lodestone attempts an operation
- **THEN** the server rejects it without changing item, Space Unit, or map data

#### Scenario: Nexus presentation is observed

- **WHEN** an Observer viewer receives an existing Nexus semantic snapshot
- **THEN** TotemNexus reconstructs the read-only production/vanilla rendering
  path without transmitting a framebuffer, screenshot, video, or secret
- **AND** viewer input cannot mutate or send action packets
