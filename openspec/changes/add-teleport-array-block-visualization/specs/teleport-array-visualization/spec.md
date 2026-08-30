## ADDED Requirements

### Requirement: Authoritative used-block visualization

The system SHALL let a player request a temporary visualization of the active
source lodestone's currently counted teleport-array blocks from the production
Space Unit Material view. The server MUST derive membership from the same
bounded material scan used for registration, calibration and maintenance, and
MUST classify every counted block whose effective local scan-expansion radius
is positive as an expansion emitter.

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

#### Scenario: Empty visited positions are not visualized

- **WHEN** the scan reaches loaded air or a non-structural block while following
  an expansion emitter
- **THEN** the reached position may participate in bounded traversal but is not
  returned as an array block

### Requirement: Visualization authority and bounds

The server MUST accept visualization only for the active local map source and
MUST revalidate source identity, active lodestone state, interface/map context,
discovery, view permission, dimension, proximity and the physical lodestone.
The scan MUST read only loaded chunks and MUST NOT force-load a chunk. Requests
and responses MUST have fixed count, offset, rate and lifetime bounds.

#### Scenario: Local source request succeeds

- **WHEN** the map context matches an active discovered lodestone that the
  player may view in the same dimension within the source-open radius
- **THEN** the server performs one loaded-only authoritative scan and returns a
  deterministically ordered relative-position response

#### Scenario: Forged or remote request is rejected

- **WHEN** a client requests a different source ID, a remote selected target, a
  missing or inactive lodestone, or a source outside its dimension or proximity
  boundary
- **THEN** the server sends no structural position response
- **AND** it does not load a chunk or mutate Space Unit state

#### Scenario: Payload exceeds a fixed bound

- **WHEN** a visualization payload contains more than 1,330 entries, a duplicate
  entry, the lodestone origin, or an offset outside the global scan distance
- **THEN** the codec rejects the payload rather than allocating or rendering the
  invalid set

#### Scenario: Requests are spammed

- **WHEN** a player sends enable or refresh requests faster than the configured
  per-player interval
- **THEN** the server rejects or coalesces excess requests without rescanning
  the array

### Requirement: Through-wall and temporary world rendering

The client SHALL render one local outline for each server-returned counted
block, SHALL visually distinguish expansion emitters and the lodestone origin,
and MUST submit these diagnostic outlines through the shared TotemCore API in
`THROUGH_WALLS` mode so opaque terrain does not hide counted positions. The
preview MUST remain framebuffer-free and MUST clear on explicit disable,
timeout, invalid distance, world or dimension transition, disconnect, or
source invalidation.

#### Scenario: Array continues behind a wall

- **WHEN** counted array blocks are behind opaque terrain from the local
  player's current camera
- **THEN** the obstructed array outlines remain visible through that terrain
- **AND** ordinary blocks, expansion emitters and the origin remain visually
  distinguishable

#### Scenario: Preview lifetime ends

- **WHEN** 30 seconds elapse without a new authorized response
- **THEN** the client removes every outline and origin marker for that preview

#### Scenario: Client context changes

- **WHEN** the player disconnects, changes world or dimension, moves beyond the
  client display cutoff, or receives source invalidation
- **THEN** the client clears the preview immediately

### Requirement: Production-screen and Observer behavior

The source lodestone's production Material view SHALL provide localized Show
and Hide Array controls. A remote selected destination SHALL retain aggregate
material diagnostics but SHALL NOT enable world visualization. Observer mode
MUST remain read-only and MUST NOT send visualization requests or activate an
overlay for the viewer.

#### Scenario: Player enables the source preview

- **WHEN** a player opens a nearby source lodestone's Material view and presses
  Show Array
- **THEN** the client sends the bounded request and, after server approval,
  stores one temporary preview for that source

#### Scenario: Remote destination is selected

- **WHEN** the Material view is showing a destination other than the active
  local source
- **THEN** the visualization control is unavailable with a localized reason

#### Scenario: Observer activates the control

- **WHEN** a viewer observes the same production Material screen and clicks or
  otherwise activates the visualization area
- **THEN** no request packet, source mutation or viewer world overlay occurs
