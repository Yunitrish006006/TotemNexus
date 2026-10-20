## ADDED Requirements

### Requirement: Nexus maps expand past the vanilla limit around their original center

A server-registered, unlocked Nexus map SHALL support the existing paper-based
expansion workflows beyond vanilla scale 4. Each successful expansion SHALL
double width and height around the persisted exact binding center and preserve
the dimension and binding UUID. Expansion SHALL require the currently supported
loaded original lodestone and SHALL stop only when its extent contains the
whole supported Minecraft coordinate domain. It SHALL NOT load remote chunks.

#### Scenario: Repeated expansion past scale four
- **GIVEN** a valid unlocked Nexus map already at vanilla's expansion limit
- **WHEN** its owner repeatedly completes a valid expansion recipe at a loaded origin
- **THEN** each result covers twice the preceding width and height
- **AND** the exact original center, dimension and UUID remain unchanged
- **AND** each result has server-proven lineage and retains recorded terrain

#### Scenario: No meaningful larger world extent remains
- **GIVEN** a map whose extent already contains the supported world coordinate domain
- **WHEN** the player supplies more expansion paper
- **THEN** no expansion result is offered and no input is consumed
- **AND** extent calculations never overflow or wrap around coordinates

#### Scenario: Off-center map rectangle extends beyond the world domain
- **GIVEN** a valid expansion whose doubled rectangle extends outside supported world coordinates
- **WHEN** the extended map is sampled or used to select a destination
- **THEN** only its intersection with supported coordinates is sampled
- **AND** outside space stays blank and cannot trigger chunk requests or teleport

#### Scenario: Expansion cannot consume inputs on a stale result
- **GIVEN** an expansion preview whose source map, lock state or original anchor changes
- **WHEN** a player attempts to take the result
- **THEN** the server rejects the stale expansion without consuming or duplicating items

### Requirement: Effective map extent is durable and server-owned

The server SHALL persist and validate extended extent independently of vanilla
map scale. Existing maps SHALL retain their prior extent and exploration when
the extension metadata is absent. Unbound vanilla maps SHALL remain unchanged.
Origin destruction SHALL NOT revoke an issued map's independent use, although
it SHALL continue to prevent expansion under the current anchor requirement.

#### Scenario: Extended map survives a reload
- **GIVEN** a legitimately expanded Nexus map beyond vanilla scale 4
- **WHEN** its world is saved and reopened
- **THEN** its extent, center, binding and explored terrain remain usable
- **AND** a same-MapId clone resolves to the same authoritative extent

#### Scenario: Forged or malformed extent
- **GIVEN** a client item claims an unregistered, mismatched or overflowing extent
- **WHEN** it is used for rendering requests, expansion or teleport
- **THEN** the server rejects the unproven claim before consuming inputs or loading chunks

### Requirement: Extended coverage and detail remain bounded and authorization-safe

Extended maps SHALL record and transmit only bounded work over explored terrain.
Overview aggregation SHALL NOT by itself authorize unrecorded subregions. Teleports SHALL
still require a permitted active destination, either recorded coverage or
current-holder compass authorization, current held
identity, valid cost and safe landing. Existing exploration SHALL NOT be silently
discarded to make expansion succeed. Locked maps SHALL remain immutable.

#### Scenario: Coarse overview contains an unknown destination
- **GIVEN** a coarse overview pixel summarizes some explored terrain
- **AND** a target in its footprint has no sufficient recorded coverage proof
- **AND** the holder has no independent compass authorization for that target
- **WHEN** a teleport is requested to that target
- **THEN** the server denies it even though the overview pixel is colored

#### Scenario: Very large map is expanded or opened
- **GIVEN** an expanded map covering a large portion of the world
- **WHEN** it is expanded, opened, zoomed or recorded near the player
- **THEN** memory, packets and work per tick remain bounded
- **AND** remote chunks are neither generated nor loaded merely to cover that extent

#### Scenario: Persistent storage capacity is exhausted
- **GIVEN** no persistent capacity is available for new map terrain
- **WHEN** the player explores new terrain or attempts another expansion
- **THEN** new recording pauses and expansion is rejected before consuming inputs
- **AND** localized rate-limited feedback explains the storage condition
- **AND** existing exploration remains readable and usable without silent eviction
- **AND** new recording and expansion can resume when capacity is available

#### Scenario: Extended map is rendered outside the Nexus Screen
- **GIVEN** a server-registered map whose effective extent exceeds vanilla scale 4
- **WHEN** it is held or displayed in an item frame
- **THEN** its terrain and decorations use the same effective extent as the Nexus map Screen
- **AND** ordinary unbound maps retain vanilla presentation and behavior

#### Scenario: Observer watches an extended map
- **GIVEN** an owner viewing a valid extended map
- **WHEN** a compatible Observer receives its semantic state
- **THEN** the owning production rendering path shows the same extent and world-aligned markers
- **AND** local input cannot mutate state or send action packets
- **AND** incompatible snapshot formats fail closed without a substitute UI

### Requirement: The map preserves compass teleport capabilities without a destination list

A registered held map SHALL allow every destination currently authorized by the
holder's compass discovery policy, whether outside its recorded extent or on
unpainted terrain. It SHALL additionally retain legitimate coverage-based map
destinations. Shared identity, visibility, active-node, cost and landing gates
SHALL remain authoritative. No separate compass SHALL be required. Destination
selection SHALL use the map, edge indicators and navigation, not a list.

#### Scenario: Known destination lies outside the map
- **GIVEN** the holder can currently authorize a destination using the compass policy
- **AND** it is outside the map's recorded extent or painted terrain
- **WHEN** the holder selects its map marker or edge indicator
- **THEN** the existing quote/confirmation flow can start its permitted teleport without first expanding the map
- **AND** unknown terrain remains blank and the original map center stays fixed

#### Scenario: Neither discovery nor coverage authorizes a destination
- **GIVEN** a target has neither current-holder compass discovery nor legitimate map coverage
- **WHEN** a forged marker request or teleport request names its UUID
- **THEN** it is neither disclosed nor authorized merely because the client supplied that UUID

#### Scenario: Authorization changes during countdown
- **GIVEN** a map teleport whose destination is authorized by discovery or coverage
- **WHEN** one proof is lost but the other still holds
- **THEN** it is not cancelled solely due to that lost proof
- **WHEN** both proofs are lost or the target becomes inaccessible or inactive
- **THEN** the server cancels before teleporting

#### Scenario: A bound map changes hands
- **GIVEN** two players with different compass discoveries
- **WHEN** the second player holds a legitimately transferred map
- **THEN** only the second player's compass discovery supplements that map's legitimate coverage
- **AND** the first player's private destination discoveries are not inherited

#### Scenario: Edge markers overlap or the destination count exceeds one payload
- **GIVEN** many currently authorized off-screen destinations
- **WHEN** the holder navigates and selects destinations on the map
- **THEN** every destination remains reachable through bounded map navigation or icon cycling
- **AND** no destination list is shown and cycling alone never starts a teleport

#### Scenario: Compass-authorized target is in another dimension
- **GIVEN** an existing compass-authorized cross-dimension route
- **WHEN** its dimension marker is selected on the map
- **THEN** the existing cross-dimension rules and costs apply
- **AND** no false same-dimension direction or unexplored terrain is rendered
