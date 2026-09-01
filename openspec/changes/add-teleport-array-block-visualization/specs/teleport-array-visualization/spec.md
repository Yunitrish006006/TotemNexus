## ADDED Requirements

### Requirement: Authoritative used-block visualization

The system SHALL let a player request a session-persistent visualization of
the active source lodestone's currently counted teleport-array blocks from the
production Space Unit Material view. The server MUST derive membership from
the same bounded material scan used for registration, calibration and
maintenance, and MUST classify every counted block whose effective local
scan-expansion radius is positive as an expansion emitter.

#### Scenario: Connected expansion path is visualized

- **WHEN** a nearby authorized player enables visualization for the active map
  source whose loaded material chain reaches structural blocks beyond the seed
  cube
- **THEN** the response contains every and only the scan's counted structural
  positions
- **AND** the response marks the counted blocks that emitted positive local
  scan expansion

#### Scenario: Unreachable material is not visualized

- **WHEN** a valid structural material lies inside the global distance cap but
  has no scanned expansion path from the source lodestone
- **THEN** that material is absent from the visualization response

#### Scenario: Empty visited position is not counted material

- **WHEN** the scan reaches loaded air or a non-structural block while following
  an expansion emitter
- **THEN** the reached position may participate in bounded traversal but is not
  returned as a counted array block

### Requirement: Authoritative build-site visualization

The system SHALL provide an independent build-site visualization for positions
reached by the same authoritative scan. A build site MUST be loaded, MUST NOT
be the lodestone origin or a structural position, and its current block state
MUST be replaceable by block placement. Unloaded, unreachable and solid
non-replaceable positions MUST be omitted.

#### Scenario: Replaceable reached position is shown

- **WHEN** a loaded replaceable position is reached by the source array scan
- **THEN** the position is returned in the buildable class when build-site
  visualization is enabled
- **AND** its relative coordinate participates in the same 1,330-position union
  bound and duplicate rejection as counted material

#### Scenario: Material is placed into a build site

- **WHEN** a player places a valid structural material at a currently displayed
  build site
- **THEN** the next changed snapshot removes it from the buildable class and
  adds it to the counted class
- **AND** if the material is an expansion emitter, newly reached replaceable
  positions appear as build sites on that refresh

#### Scenario: Reached solid block cannot be replaced

- **WHEN** the scan reaches a non-structural state that cannot currently be
  replaced by block placement
- **THEN** the position is not returned in either visualization class

### Requirement: Visualization authority, bounds and refresh suppression

The server MUST accept initial visualization enable only for an active local map
source proven by a currently held, legally bound interface and matching
interface context. On success it MUST create one non-persistent, server-only
visualization session for that player's exact source type and unit ID. A later
request for the same session source MUST NOT require that interface item to
remain held, but MUST independently revalidate source identity, active lodestone
state, discovery, view/friend permission, dimension, proximity, loaded state and
the physical lodestone on every accepted refresh. A different source MUST pass
the full initial held-interface validation. The scan MUST read only loaded
chunks and MUST NOT force-load a chunk. Requests and responses MUST have fixed
count, offset and rate bounds. Client-session lifetime MUST remain bounded by
source authority and disable, rejection, disconnect and server lifecycle
cleanup rather than elapsed-time expiry.

#### Scenario: Initial local source request succeeds

- **WHEN** the held bound interface and context match an active discovered
  lodestone that the player may view in the same dimension within the
  source-open radius
- **THEN** the server performs one loaded-only authoritative scan and returns a
  deterministically ordered relative-position response
- **AND** it establishes a server-only visualization session for that exact
  player and source

#### Scenario: Player switches from the map to building materials

- **WHEN** a player has successfully enabled a source visualization, closes the
  map, puts away the interface item and refreshes the same source while building
- **THEN** the refresh does not depend on the general interface context or held
  item
- **AND** the server repeats every source, permission, distance, loaded and
  physical-lodestone check before returning changed positions or an unchanged
  acknowledgement

#### Scenario: Forged or remote request is rejected

- **WHEN** a client requests a different source ID, a remote selected target, a
  missing or inactive lodestone, or a source outside its dimension or proximity
  boundary
- **THEN** the server sends no structural or buildable position response
- **AND** it does not load a chunk or mutate Space Unit state
- **AND** any active visualization session and cached snapshot for that player
  are cleared after an authority rejection

#### Scenario: Payload exceeds a fixed bound

- **WHEN** a visualization payload contains more than 1,330 entries, a duplicate
  entry, the lodestone origin, or an offset outside the global scan distance
- **THEN** the codec rejects the payload rather than allocating or rendering the
  invalid set

#### Scenario: Requests are spammed

- **WHEN** a player sends enable or refresh requests faster than the configured
  20-tick per-player interval
- **THEN** the server rejects or coalesces excess requests without rescanning
  the array

#### Scenario: An unchanged refresh is accepted

- **WHEN** an authorized client refreshes at or after the 20-tick interval and
  the deterministic position/class snapshot is unchanged
- **THEN** the server returns only a bounded status acknowledgement and does not
  resend the full position payload

#### Scenario: A nearby block changes

- **WHEN** placement or breaking changes the authoritative counted/buildable
  sets while a mode remains enabled
- **THEN** the next accepted refresh, targeted within about one second, sends
  the changed full snapshot
- **AND** neither client nor server scans the array every tick

### Requirement: Persistent mixed-occlusion world rendering

The client SHALL render one local outline for each server-returned enabled
class. It MUST render counted blocks in cyan, expansion emitters in gold and the
lodestone origin in purple through the shared TotemCore API using
`THROUGH_WALLS`. It MUST render build sites in a lower-interference green using
`DEPTH_TESTED`. The preview MUST remain framebuffer-free and MUST clear on
explicit disable, invalid distance, acknowledgement/authority loss, world or
dimension transition, disconnect, or source invalidation. It MUST NOT expire
solely because time elapsed, and closing the production Screen MUST NOT clear it.

#### Scenario: Array continues behind a wall

- **WHEN** counted array blocks are behind opaque terrain from the local
  player's current camera
- **THEN** the obstructed array outlines remain visible through that terrain
- **AND** ordinary blocks, expansion emitters and the origin remain visually
  distinguishable

#### Scenario: Build site is behind a wall

- **WHEN** opaque terrain lies between the player and a green build-site outline
- **THEN** the shared Core `DEPTH_TESTED` mode may occlude that outline to avoid
  through-wall clutter

#### Scenario: Map Screen closes while source remains valid

- **WHEN** the player closes the map after enabling either visualization mode
- **THEN** the enabled mode remains active and continues bounded refreshes in
  the same client session
- **AND** the player may hold and place building materials without losing the
  same-source visualization session

#### Scenario: Client context changes

- **WHEN** the player disconnects, changes world or dimension, moves beyond the
  eight-block source authority radius, loses acknowledgements or authority, or
  receives source invalidation
- **THEN** the client clears both modes and every cached outline immediately

### Requirement: Production-screen and Observer behavior

The source lodestone's production Material view SHALL provide independently
localized Show/Hide Array and Show/Hide Build Sites controls that may be active
alone or together. A remote selected destination SHALL retain aggregate
material diagnostics but SHALL NOT enable world visualization. Observer mode
MUST remain read-only and MUST NOT send visualization requests or activate an
overlay for the viewer. This local-only state MUST NOT change the version-3
Observer semantic snapshot.

#### Scenario: Player enables the source preview

- **WHEN** a player opens a nearby source lodestone's Material view and presses
  Show Array
- **THEN** the client sends the bounded request and, after server approval,
  stores one session-persistent preview for that source

#### Scenario: Player enables both visual classes

- **WHEN** a player independently enables counted array and build-site display
- **THEN** both classes render from one authoritative snapshot and refresh
  cadence
- **AND** hiding one class leaves the other enabled

#### Scenario: Remote destination is selected

- **WHEN** the Material view is showing a destination other than the active
  local source
- **THEN** both visualization controls are unavailable with a localized reason

#### Scenario: Observer activates a control

- **WHEN** a viewer observes the same production Material screen and clicks or
  otherwise activates either visualization area
- **THEN** no request packet, source mutation or viewer world overlay occurs
