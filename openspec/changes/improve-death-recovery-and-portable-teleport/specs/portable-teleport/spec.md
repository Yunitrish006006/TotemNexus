## ADDED Requirements
### Requirement: Portable teleport outside arrays
A valid compass, recovery compass or Nexus map SHALL allow opening and teleport initiation outside a lodestone's eight-block radius and outside a teleport array. When opened without clicking a lodestone, source position SHALL be the player's server-authoritative position. Clicking an eligible lodestone with the interface SHALL instead establish that lodestone as the source; previewing materials SHALL NOT replace the established teleport source. Being outside an array or having reduced field stability SHALL NOT by itself prohibit teleport. Destination authorization, map coverage, item identity, costs and safe landing SHALL still be enforced.
#### Scenario: Field teleport
- **WHEN** a player holds an eligible interface in the wilderness with an authorized target and sufficient resources
- **THEN** the player can teleport with the displayed lower field stability
#### Scenario: Forged source or insufficient resources
- **WHEN** a request lies about source/held item, lacks destination access or cannot pay
- **THEN** the server rejects it regardless of array membership

### Requirement: Actual array footprint increases stability
The system SHALL derive array membership from recognized construction under the configured scanner mode, with bounded loaded-only reads. Field source stability SHALL start at 60%; a valid array footprint SHALL add up to 35 percentage points from effective resonance, capped at 95%, before route modifiers. Array membership SHALL never reduce the corresponding valid field route's stability; departure SHALL remove array-only bonuses rather than forbid teleport.
#### Scenario: Walk into and out of the construction
- **WHEN** a player crosses the actual array boundary
- **THEN** quote and final execution use the corresponding field or enhanced array state, not a fixed eight-block sphere
#### Scenario: No actual construction at scanner limit
- **WHEN** a position lies in the maximum possible scanner envelope but outside the recognized construction footprint
- **THEN** the player receives field stability and can still initiate an otherwise valid route

### Requirement: Preserve map and Observer identity
Portable source selection SHALL preserve map anchor, MapId, painted-coverage rules and authorized target visibility. Changed UI SHALL use Nexus production rendering and its bounded versioned Observer snapshot, including remote cursor, monotonic updates, input/packet suppression and privacy redaction.
#### Scenario: Map opened away from its anchor
- **WHEN** a valid map is opened away from the bound lodestone
- **THEN** player location becomes the source without changing the map anchor or revealing unauthorized/unpainted targets
#### Scenario: Observer receives changed source state
- **WHEN** initial or later snapshots show field versus array status
- **THEN** the read-only production projection updates without sending mutation packets or private input
