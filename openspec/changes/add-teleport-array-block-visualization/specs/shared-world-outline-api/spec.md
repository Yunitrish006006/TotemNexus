## ADDED Requirements

### Requirement: Versioned client-only outline primitive

TotemCore SHALL expose a client-only API v1 primitive that submits block,
cuboid, line or immutable voxel-union outlines with an immutable ARGB colour,
finite positive line width and an explicit occlusion mode. The API MUST provide
`DEPTH_TESTED` and `THROUGH_WALLS` modes and MUST NOT expose mutable internal
renderer state.

#### Scenario: Depth-tested outline is submitted

- **WHEN** a feature submits a cuboid with `DEPTH_TESTED`
- **THEN** Core submits the outline through the normal world gizmo path without
  always-on-top behavior
- **AND** opaque terrain may hide the obstructed portion

#### Scenario: Through-wall outline is submitted

- **WHEN** a feature submits a cuboid with `THROUGH_WALLS`
- **THEN** Core marks that outline always-on-top
- **AND** opaque terrain does not hide the obstructed portion

#### Scenario: Invalid style is constructed

- **WHEN** a caller supplies a non-finite or non-positive line width
- **THEN** the immutable style value rejects it before submitting a gizmo

#### Scenario: Adjacent voxels form one outer boundary

- **WHEN** a feature derives a voxel-union outline from adjacent block
  positions
- **THEN** Core omits face-shared edges and coplanar surface grid seams
- **AND** it merges contiguous collinear unit edges into deterministic maximal
  segments

#### Scenario: Irregular or disconnected voxels are submitted

- **WHEN** a feature derives an outline from concave or disconnected block
  positions
- **THEN** Core preserves every true exterior turn and component without
  replacing them with one global bounding box
- **AND** fully enclosed cavity surfaces do not produce internal wireframes

#### Scenario: A connected component has a pathological envelope

- **WHEN** one face-connected voxel component's padded exterior envelope would
  exceed the documented fixed cell bound
- **THEN** Core rejects derivation before starting an unbounded flood fill

### Requirement: Feature ownership and server isolation

The shared outline API SHALL remain a stateless client presentation primitive.
Core MUST NOT own feature position sets, selection or preview state, timers,
network payloads, permissions, gameplay decisions, render-event registration
or world lifecycle cleanup. Dedicated-server initialization MUST NOT load the
client-only API or Minecraft client rendering types.

#### Scenario: A feature renders its current snapshot

- **WHEN** a feature derives and caches an outline for its current immutable
  snapshot, then its module-owned render callback submits that plan
- **THEN** Core applies only the requested generic style and occlusion behavior
- **AND** the feature remains responsible for changing or clearing that
  snapshot and cached plan

#### Scenario: Dedicated server starts with Core installed

- **WHEN** TotemCore initializes in a dedicated-server environment
- **THEN** no shared-outline class or Minecraft client rendering class is
  loaded by the common entrypoint

### Requirement: Consumer-specific occlusion policy

Each consuming feature SHALL select occlusion according to its own player-facing
behavior. Excavation MUST submit its selection marker as `DEPTH_TESTED`, while
Nexus MUST submit its authorized teleport-array diagnostic as
`THROUGH_WALLS`.

#### Scenario: Excavation selection is behind a wall

- **WHEN** opaque terrain lies between the player and an Excavation selection
  marker
- **THEN** the marker is occluded through the shared Core API

#### Scenario: Nexus array is behind a wall

- **WHEN** an authorized Nexus preview contains counted blocks behind opaque
  terrain
- **THEN** those outlines remain visible through the shared Core API

#### Scenario: Automata later adds an area selection marker

- **WHEN** an approved Automata area-job feature submits its selected bounds
- **THEN** it uses the shared Core outline API with the occlusion mode required
  by that feature's own specification
- **AND** Automata retains ownership of selection state and cleanup
