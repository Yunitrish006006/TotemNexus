## ADDED Requirements
### Requirement: Compass family destination capability
Ordinary and recovery compasses SHALL expose a destination list and teleport selection, without map visualization. Lodestone entries SHALL be discovered by the current player and currently authorized, ordered by source distance. Both SHALL retain rebind behavior and native components. Books SHALL remain management-only.
#### Scenario: Discovered authorized destination
- **WHEN** either compass opens at an eligible source
- **THEN** eligible discovered lodestones appear in increasing source distance and can be selected and teleported to
- **AND** undiscovered or unauthorized lodestones are excluded, including forged requests

### Requirement: Painted map coverage and independent source
A Nexus map SHALL retain immutable registry anchor, MapId, center and expansion center while permitting other ACTIVE authorized loaded lodestones as teleport sources within legal interaction range and painted map coverage in the same dimension. Map source and target eligibility SHALL NOT require compass discovery. Server authority SHALL re-resolve identities, permissions, held item, painted coverage and endpoint validity; safe landing, costs, route reservations and wear SHALL remain enforced.
#### Scenario: Covered undiscovered source and destination
- **WHEN** a player uses a valid map at another authorized covered lodestone without discovery
- **THEN** it becomes the session source and covered authorized destinations appear without rewriting map data or anchor
#### Scenario: Unpainted or forged input
- **WHEN** source or target is outside painted coverage, unloaded where loaded validation is required, unauthorized, stale, or supplied with forged map identity
- **THEN** the server rejects the action without force-loading or mutating map identity

### Requirement: Once-per-death-node recovery grace
Only successful recovery-compass teleport to the player's own ACTIVE DEATH node SHALL grant server-tracked invisibility and quarter normal maximum horizontal deviation through NexusSafeLanding. Grace SHALL be consumed once per player/node lifecycle across relog and save/reload. Exact target backpack recovery SHALL leave at most three seconds, bounded by sixty seconds from arrival; wrong backpack recovery SHALL have no effect. Attack, PvP, offensive projectile launch, another Nexus teleport, node invalidation or logout SHALL cancel grace. Existing invisibility effects SHALL not be removed, replaced or shortened.
#### Scenario: Rescue and exact recovery
- **WHEN** the player completes a qualifying rescue and recovers its exact backpack
- **THEN** grace ends after three seconds or the sixty-second arrival deadline, whichever is first
#### Scenario: Ineligible or repeated rescue
- **WHEN** a compass or map reaches a death node, a recovery compass reaches a lodestone/other owner's node, or a consumed rescue repeats
- **THEN** no recovery grace is granted or refreshed
#### Scenario: Cancellation and effect preservation
- **WHEN** an offensive action, new teleport, invalidation, logout or timeout occurs
- **THEN** Nexus grace ends while preexisting vanilla invisibility effects and their remaining durations are preserved

### Requirement: Recovery presentation remains Observer safe
The module SHALL reconstruct compass-family and map UI through its production Screen and versioned semantic provider, with monotonic snapshots/cursors, input and packet suppression, close handling and no framebuffer or private input transport.
#### Scenario: Recovery compass observed
- **WHEN** an Observer receives initial and later recovery-compass selection snapshots
- **THEN** the list-only production path updates with remote cursor and read-only controls, without map canvas or action packets
