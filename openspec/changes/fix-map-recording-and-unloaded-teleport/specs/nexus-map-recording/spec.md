## ADDED Requirements

### Requirement: Map-owned transferable records
Nexus SHALL associate terrain and fine-detail records with the server-validated MapId and SHALL keep viewer permissions and personal favorites separate.

#### Scenario: Transfer to a second player
- **WHEN** a second player receives the same map without personal discovery history
- **THEN** recorded terrain and detail remain available while node disclosure and teleport still require that player's permissions

#### Scenario: Copy, scale and lock
- **WHEN** a map is copied, scaled or locked
- **THEN** equal MapIds share updates, a scaled MapId retains proven prior coverage, and a locked result remains unchanged by subsequent mutable-map exploration

#### Scenario: Mutable ancestors and legacy locked maps
- **WHEN** an ancestor is mutable or a legacy locked map has no provable frozen detail snapshot
- **THEN** new SCALE and LOCK operations snapshot ancestor pixels and known coverage, while the legacy locked map preserves its own base terrain without reading mutable ancestor detail

### Requirement: Transient held-map marker
Nexus SHALL render a local-player marker through the vanilla held-map rendering path without persisting viewer coordinates or drawing the observer's own player.

#### Scenario: Main hand and off hand
- **WHEN** a recognized Nexus map is held in either hand in its matching dimension
- **THEN** exactly one correctly transformed local-player marker is rendered and shared map decorations remain unchanged

### Requirement: Newly explored fine detail
Nexus SHALL record bounded, map-owned fine-detail pages from legitimately explored loaded terrain even when no ancestor map covers the location.

#### Scenario: Expanded outer region without an ancestor
- **WHEN** a player explores a newly added region of an expanded unlocked map
- **THEN** fine detail is progressively recorded and available after handoff and restart without loading unexplored chunks for cartography

#### Scenario: Unknown old detail
- **WHEN** a legacy map only has coarse pixels in a region
- **THEN** those pixels remain a fallback until valid terrain sampling supplies detail and migration does not invent ancestry or fine pixels

### Requirement: Deferred unloaded endpoint validation
Nexus SHALL distinguish an unloaded destination from an invalid one and SHALL use bounded asynchronous loading before physical endpoint validation.

#### Scenario: Valid unloaded lodestone
- **WHEN** an authorized player starts teleporting to a persisted active lodestone in an unloaded chunk
- **THEN** the server loads the required bounded region, revalidates the endpoint and quote, and proceeds only with a safe landing and unchanged accepted cost

#### Scenario: Missing lodestone after loading or interrupted loading
- **WHEN** loading reveals a missing lodestone or the request times out or is cancelled
- **THEN** the server reports the appropriate failure, consumes no teleport resources, and releases all route reservations and tickets
