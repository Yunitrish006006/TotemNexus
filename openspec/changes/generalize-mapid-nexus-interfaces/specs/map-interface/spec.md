## ADDED Requirements

### Requirement: Every resolvable filled map is a Nexus map interface

A server-held filled map with a MapId and resolvable native MapItemSavedData SHALL
be eligible to open the Nexus map interface without Nexus-specific item
identity or prior lodestone activation. Before opening, the server SHALL ensure
a compatible Nexus record exists for that MapId. Missing native data,
contradictory persisted geometry or unavailable record storage MUST fail closed
without opening a partial session or mutating the item.

#### Scenario: Ordinary vanilla map opens Nexus

- **GIVEN** a vanilla filled map with valid server MapId data and no Nexus item
  metadata or Nexus record
- **WHEN** its holder uses it to open Nexus
- **THEN** the server atomically creates a baseline MapId-owned Nexus record
- **AND** opens the interface without changing its MapId, center, dimension,
  scale, pixels, decorations, tracking flags or lock state

#### Scenario: A copied map shares one identity

- **GIVEN** two physical map items carry the same valid MapId
- **WHEN** either copy opens or derives Nexus state
- **THEN** both resolve the same native and Nexus records
- **AND** no item-carried Space Unit claim creates a second identity

#### Scenario: Native data is missing or contradictory

- **WHEN** the held item has no MapId, its native data is missing, or an existing
  Nexus record contradicts its native center, dimension or backing scale
- **THEN** the server refuses to open or derive the interface
- **AND** it does not guess, repair, replace or partially register the map

### Requirement: Nexus extensions are owned by MapId

The server SHALL persist logical scale, detail/coverage roots, derivation
lineage, revision and native-geometry integrity by MapId. Native map data SHALL
remain authoritative for center, dimension, pixels, decorations, tracking
flags, lock state and scale 0–4. For logical scale above 4, native scale SHALL
remain at 4 and the Nexus record SHALL be authoritative for logical extent.
No record SHALL require a creating Space Unit or origin lodestone.

#### Scenario: Ordinary map receives a baseline record

- **WHEN** a valid unregistered map is first opened
- **THEN** its initial logical scale equals its native scale
- **AND** its existing center and dimension become the immutable geometry for
  later derivations
- **AND** its initial Nexus detail and lineage are empty

#### Scenario: Map expands beyond vanilla scale four

- **GIVEN** a registered map at logical scale 4 with compatible native data
- **WHEN** the server accepts another valid expansion
- **THEN** it allocates a derived MapId with the exact same center and dimension
- **AND** records logical scale 5 and parent/detail lineage atomically
- **AND** no original lodestone is queried, loaded or required

#### Scenario: Unsupported old extension data is encountered

- **GIVEN** a valid native map whose pre-change Nexus item claim or extension
  data is not represented by a compatible new MapId-owned record
- **WHEN** it first enters the new interface flow
- **THEN** item-carried claims grant no authority
- **AND** the server may bootstrap only from current native data without
  inferring a logical scale above its native scale or importing old detail

### Requirement: Map contents are evaluated for the current holder

The map SHALL NOT persist a Space Unit result list. On open and refresh, the
server SHALL compute nodes from current records and filter them for the current
holder by active status, visibility/friendship, discovery or painted coverage,
map dimension and logical extent. The current player position and dimension,
not the map center, SHALL remain the authoritative teleport source.

#### Scenario: A map changes holders

- **WHEN** a map copied or created by one player is opened by another player
- **THEN** the server computes the second player's currently visible nodes
- **AND** no node list or permission result from the first player is reused

#### Scenario: Map center differs from player position

- **WHEN** a player opens or teleports with a map centered elsewhere
- **THEN** its center controls viewport and painted-coverage evaluation only
- **AND** quotes and teleport execution use the player's current server position
