## ADDED Requirements

### Requirement: Ordinary compass supports list-only teleport selection

A correctly bound ordinary compass SHALL expose eligible active destinations
already admitted by the server's authenticated access and discovery policy. It
SHALL present those destinations through a bounded list with server-calculated
quotes and SHALL permit the player to request teleport to the selected eligible
destination. The ordinary compass MUST NOT expose a vanilla map canvas, map
pixels, terrain, or map markers. Recovery compasses and books MUST remain
management-only unless a later approved change grants them teleport capability.

#### Scenario: Compass selects and teleports from a list

- **WHEN** an authorized player near the bound source opens an ordinary compass,
  selects an eligible discovered destination from its list, and activates the
  teleport control while still holding the exact compass
- **THEN** the server revalidates the source, target, item, access, route, quote,
  and resources and starts the existing teleport preparation flow
- **AND** the client never renders a map canvas for that compass

#### Scenario: Compass list exceeds the visible rows

- **WHEN** the authorized destination set is larger than the visible list
- **THEN** the player can reach every bounded entry with scrolling, filtering,
  and keyboard/focus navigation
- **AND** selection, quote, narration, and teleport-button state refer to the
  same destination

#### Scenario: Compass context becomes stale

- **WHEN** the compass is moved, replaced, rebound, dropped, or no longer held
  in the recorded hand before or during teleport preparation
- **THEN** the server rejects or cancels the teleport without consuming costs or
  accepting a substitute interface

#### Scenario: Recovery compass or book opens

- **WHEN** an authorized player opens a bound recovery compass or book
- **THEN** its management presentation remains available
- **AND** it receives no teleport destination selector, teleport button, or map
  visualization

### Requirement: Nexus map preserves coordinate selection and teleport

A valid Nexus filled map SHALL retain its vanilla MapId presentation and SHALL
allow an in-bounds visible Nexus marker to be selected by clicking its rendered
map coordinate. The Nexus-map presentation MUST NOT expose a destination list.
Mouse selection and non-list keyboard marker navigation MUST update one selected
Space Unit and its server-calculated quote, and an eligible selection SHALL be
able to start the existing server-authoritative teleport flow. The map SHALL
support bounded integer zoom and bounded drag/keyboard panning without
converting a drag gesture into a marker selection.

#### Scenario: Player selects a destination on the map

- **WHEN** the player clicks within the bounded hit area of an in-bounds visible
  Nexus marker
- **THEN** that marker's Space Unit becomes the selected destination
- **AND** the footer, narration, and teleport-button state update for that same
  destination
- **AND** no destination list is rendered beside or over the map

#### Scenario: Player inspects the map at another scale or position

- **WHEN** the player uses the mouse wheel or zoom keys over the map
- **THEN** the vanilla map is redrawn at a crisp bounded integer scale
- **WHEN** the player drags the map or uses the keyboard pan controls
- **THEN** the viewport moves only within its bounded map extent
- **AND** the drag does not replace the selected teleport destination

#### Scenario: Valid existing Nexus map teleports

- **WHEN** a previously issued Nexus map still has a matching item binding,
  MapId, server-owned Nexus map binding, and anchor identity and the player
  selects an eligible destination
- **THEN** the map retains its MapId and coverage and can start and complete the
  teleport under current server validation

#### Scenario: Map target is not eligible

- **WHEN** a marker is outside persisted map coverage, inaccessible to the
  authenticated viewer, inactive, undiscovered, or no longer valid
- **THEN** it is not selectable from the transient map presentation
- **AND** a forged or stale target UUID cannot start teleport

#### Scenario: Arbitrary filled map is used

- **WHEN** a filled map lacks a matching server-recognized Nexus map binding
- **THEN** it cannot open the Nexus presentation or request Nexus teleport
- **AND** it is not converted, recentered, or rebound

### Requirement: Teleport capability is independent from map visualization

Interface capability policy MUST model destination teleport separately from map
visualization. A valid Nexus map and an ordinary compass SHALL support target
selection and teleport, while only the valid Nexus map SHALL expose the map
rendering path. Payload breadth, selection controls, and teleport-button
visibility MUST use the appropriate capability rather than inferring all three
from map presence.

#### Scenario: Compass and map receive different presentations

- **WHEN** equivalent authorized destination records are opened through an
  ordinary compass and a valid Nexus map
- **THEN** both interfaces may expose eligible teleport targets according to
  their server projection rules
- **AND** only the map variant uses persisted map coverage and renders vanilla
  map data
- **AND** only the compass variant renders a destination list

#### Scenario: Server receives a teleport request

- **WHEN** either presentation sends its selected target UUID
- **THEN** the server treats the UUID only as a request and independently
  re-resolves the exact held interface, current context, target access, quote,
  resources, and route safety

### Requirement: Changed teleport presentation remains Observer-safe

The owning TotemNexus module MUST reconstruct the changed map/compass production
Screen from a bounded, versioned semantic snapshot, including bounded map
zoom/pan state when the map variant is active. Capture, creation, and apply
operations MUST run on the Minecraft client thread and accept only the exact
family, variant, protocol, and monotonically increasing snapshot/cursor
sequences. The Observer projection MUST render the remote cursor and carried
stack, suppress local mouse, keyboard, scroll, widget, lifecycle, and packet
mutation except explicit stop-observing, and MUST remain framebuffer-free. It
MUST NOT include map pixels, screenshots, video, secrets, credentials, URLs,
prompts, commands, chat drafts, or comparable private input.

#### Scenario: Observer watches compass selection presentation

- **WHEN** an Observer receives a valid compass-variant snapshot and later
  monotonic selection update
- **THEN** TotemNexus applies it to the same production list-only Screen and
  renders the remote cursor/carried stack
- **AND** local viewer input cannot select, scroll, activate teleport, send a
  packet, or mutate the target state

#### Scenario: Observer watches Nexus map without local map cache

- **WHEN** an Observer receives a valid map-variant snapshot but lacks the
  target player's local vanilla MapId cache
- **THEN** the production Screen shows the explicit map-unavailable state
- **AND** no map pixels or framebuffer data are added to the snapshot

#### Scenario: Observer receives stale or mismatched state

- **WHEN** a snapshot or cursor has a non-increasing sequence or mismatched
  family, variant, or protocol
- **THEN** TotemNexus ignores it without replacing or mutating the active
  Observer Screen
