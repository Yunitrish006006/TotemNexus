## ADDED Requirements

### Requirement: Nexus map zoom restores proven historical detail

A valid Nexus filled map SHALL retain its current vanilla MapId as the complete
coarse terrain fallback and MAY use only server-proven historical Nexus map
ancestors as progressively finer vanilla terrain layers. Zoom SHALL use bounded
power-of-two detail levels aligned to vanilla map scales, SHALL stop at scale 0,
and MUST NOT synthesize unrecorded terrain detail, infer legacy ancestry from a
matching anchor alone, or transport map pixels in the Nexus semantic payload.

#### Scenario: Expanded map is inspected at higher detail

- **WHEN** a valid Nexus map with server-proven finer SCALE ancestors is opened
  and the player increases map zoom
- **THEN** the Screen keeps the current MapId terrain as the coarse fallback and
  overlays the compatible cached ancestor terrain over its true smaller world
  extent
- **AND** each available finer level replaces coarse pixels only where that
  historical vanilla map actually contains data
- **AND** zoom never exposes detail finer than vanilla scale 0

#### Scenario: Expanded legacy map has no lineage proof

- **WHEN** a valid map created before lineage tracking has no persisted ancestor
  identities
- **THEN** the map remains usable with its current vanilla terrain and bounded
  viewport controls
- **AND** the server and client do not guess another MapId as its ancestor merely
  because unit, anchor, center, dimension, or scale appear compatible

#### Scenario: Ancestor data is missing or invalid

- **WHEN** a recorded ancestor MapId is absent, has mismatched binding identity,
  center, dimension, or scale, or is not available in the client map cache
- **THEN** that layer is ignored independently
- **AND** the current map remains the complete visible fallback without forcing
  a chunk load or generating substitute terrain

### Requirement: Nexus map shows player position transiently and Observer-safely

The owning production Nexus map Screen SHALL render the current local player's
map position and facing as a transient vanilla-style player decoration when the
player is in the map dimension and within current map coverage. This decoration
MUST NOT be persisted into `MapItemSavedData`, item components, or Nexus SavedData.

A protocol-5 Observer reconstruction MAY receive the observed target's derived
map-local player decoration, but MUST NOT receive the target's raw world X/Z
coordinates for that marker. The Observer decoration SHALL consist only of an
off-map flag, signed map-local X/Y bytes, and a vanilla rotation value in
`[0, 15]`. An Observer reconstruction MUST NOT substitute the observer client's
own local player position for the observed target.

#### Scenario: Owner opens map while inside its coverage

- **WHEN** the owning client opens a valid Nexus map while its player is in the
  map dimension and inside the current map bounds
- **THEN** the map displays a player decoration at the viewport position derived
  from the player's current world X/Z and facing
- **AND** movement or rotation can update that transient marker without changing
  persisted map data

#### Scenario: Owner is outside the map presentation

- **WHEN** the owning player is in another dimension or outside the current map
  coverage
- **THEN** the Nexus Screen does not fabricate an in-bounds player marker

#### Scenario: Observer watches the target map

- **WHEN** a protocol-5 read-only Observer Screen is reconstructed on another
  client and the target snapshot contains a complete valid map-local player
  decoration
- **THEN** the Observer Screen renders that observed-target decoration using the
  same map viewport transform as the owning Screen
- **AND** the semantic snapshot contains no target raw world X/Z coordinates,
  map pixels, framebuffer, screenshot, or video for that marker
- **AND** the observer client's own `Minecraft.player` coordinates are never used
  as the observed target marker

#### Scenario: Observer target marker is absent

- **WHEN** a valid protocol-5 map snapshot contains no target player decoration
- **THEN** the Observer Screen renders no player marker
- **AND** it does not fall back to the observer client's local position

#### Scenario: Observer target marker is malformed

- **WHEN** only part of the target marker metadata is present, or any marker byte
  or rotation lies outside its protocol-5 bounds
- **THEN** the snapshot is rejected instead of partially mutating the Screen

### Requirement: Map detail viewport remains selection and Observer safe

Detail-aware terrain composition SHALL use the same bounded world-to-screen
viewport transform as Nexus marker placement, hit testing, panning, and labels.
Terrain layers MUST NOT duplicate Nexus decorations. The module-owned Observer
provider SHALL version the changed zoom/terrain semantics, validate bounded
semantic state, and preserve read-only framebuffer-free reconstruction.

#### Scenario: Player zooms, pans, and selects a Nexus marker

- **WHEN** the player changes detail zoom, pans the map, and clicks a visible
  Nexus marker
- **THEN** terrain, marker, label, and hit-test coordinates remain aligned to the
  same world position
- **AND** a drag beyond the existing threshold does not become a marker click

#### Scenario: Observer receives protocol-5 viewport semantics

- **WHEN** an Observer snapshot uses protocol 5 and compatible finer map data has
  already been authorized and cached through the Nexus/Observer terrain relay
- **THEN** zoom and pan values are range-checked and applied to the same
  production Screen in read-only mode
- **AND** 2x/4x zoom submits the compatible real finer MapIds at the corresponding
  vanilla map scales rather than merely magnifying the coarse render state
- **AND** an older or mismatched provider protocol is rejected rather than being
  interpreted or converted as protocol 5
