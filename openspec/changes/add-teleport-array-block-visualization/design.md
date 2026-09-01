## Context

`TeleportArrayMaterialScan` already computes two transient position sets:
`structuralPositions`, which are the blocks that contribute material values,
and `visitedPositions`, which includes every loaded position reached by the
bounded expansion graph. Persisted snapshots intentionally keep only aggregate
material totals. The Space Unit map can only be opened from a validated local
source lodestone, and its Material view already exposes those aggregates.

The visualization must answer a builder-facing question without creating a
second scan algorithm: "Which blocks does this lodestone count right now?"

Excavation already has a second world-outline use case, but its selection must
remain hidden by opaque terrain while the Nexus diagnostic must deliberately
remain visible through terrain. The reusable concern is therefore outline
submission and occlusion style, not either feature's selection or array state.

## Goals / Non-Goals

### Goals

- Show exactly the structural positions returned by the live authoritative
  scan for the local source lodestone.
- Independently show every currently replaceable build site reached by that
  same scan, so builders can see where a valid next block may be placed.
- Show only each semantic set's exact outer boundary, without shared block
  edges or coplanar surface grid seams obscuring a complex build.
- Keep rendering local, dynamically refreshed, bounded and lifecycle-safe.
- Give Totem modules one versioned client-only outline primitive with an
  explicit occlusion choice instead of duplicating raw gizmo setup.
- Reuse the production scan and authorization rules rather than reproducing
  material logic on the client.

### Non-Goals

- Do not draw a global 11x11x11 shell. A build site must be reached, loaded,
  non-structural and currently replaceable; unreachable, unloaded and solid
  non-material positions remain omitted.
- Do not allow remote target inspection from the map, reveal unloaded chunks,
  or force chunks to load.
- Do not persist a per-array display setting or change material attributes,
  tier, cost, wear, teleport behavior or SavedData.
- Do not transmit screenshots, framebuffers, video, pixels or complete block
  states.
- Do not move Excavation selection state, Nexus preview state, render-event
  lifecycle, feature networking, permissions or gameplay rules into Core.

## Decisions

### TotemCore world-outline contract

TotemCore provides a client-only API under `dev.totem.core.api.v1.client.world`
with four small public concepts:

- an immutable outline style carrying ARGB colour and finite positive line
  width;
- an explicit occlusion enum with `DEPTH_TESTED` and `THROUGH_WALLS`; and
- stateless cuboid/block/line submission helpers backed by Minecraft's normal
  gizmo extraction path; and
- an immutable voxel-union outline plan that retains only exterior corners,
  merges maximal collinear lines, ignores enclosed cavities and processes
  disconnected components without one coordinate-span-sized flood fill.

The helper returns no mutable renderer state. It submits a default depth-tested
gizmo for `DEPTH_TESTED` and applies always-on-top only for `THROUGH_WALLS`.
Feature modules register their own level-render callback, derive a plan only
when their semantic block set changes, cache it, and submit its lines each
frame. This keeps Core free of
selection sets, teleport-array positions, timers, packets, permissions and
world lifecycle ownership.

The API and its Minecraft client types are environment-annotated client-only
and referenced only from client source sets or client entrypoints. Dedicated
server initialization must not load them. Core documents this generic outline
primitive as a shared presentation exception alongside its existing manual
overlay contract and publishes it as a backward-compatible 0.7.x API addition.
Only consumers that use the new API raise their minimum Core patch; unrelated
modules keep their current 0.7.x constraints.

Excavation migrates its current selection renderer to the helper with
`DEPTH_TESTED`, retaining the already-validated no-through-wall behavior. Nexus
uses one cyan `THROUGH_WALLS` voxel-union plan for the counted array and origin,
plus an independent green `DEPTH_TESTED` plan for build sites. The two consumers
therefore prove both modes without Core learning either feature's semantics.

Automata's planned area-job selection marker is a future consumer of the same
primitive. This change does not implement that area-job or select its occlusion
policy; its eventual approved spec must keep Automata state and lifecycle in
Automata while using the Core submission contract.

### Source-local independent controls

The production Space Unit map Material view gains independent `Show Array` /
`Hide Array` and `Show Build Sites` / `Hide Build Sites` controls. Either or
both may be active. They apply only while the Material view is showing the
map's source lodestone. A remote selected entry keeps its existing aggregate
diagnostics but cannot request a world preview; both controls are disabled with
a localized reason. Native buttons, font, narration, tooltips and integer
spacing are retained. Full labels are used at normal width and concise
localized labels are used at narrow width so the repair and overlay controls
never overlap.

The source-only rule matches the existing map authority: opening a lodestone map
already proves that the player is in the same dimension, within eight blocks,
has discovered the source and may view it. The server revalidates all of these
facts for every visualization request. Observer mode never enables the control
and never sends a visualization packet.

### One authoritative scan, two outline groups

The visualization authority invokes the same `TeleportArrayMaterialScan.scan`
path used by registration, calibration and maintenance. The response contains:

- the source unit identifier and lodestone origin;
- every position in `structuralPositions`, encoded as a relative offset;
- a flag for positions whose effective local scan-expansion radius is positive;
- every position in `buildablePositions`, defined as a loaded position in
  `visitedPositions` that is not structural and whose current block state can
  be replaced by block placement; and
- the source dimension and enabled-class flags needed to reject a stale
  response.

Every counted structural position and the centre lodestone form one cyan array
outline group. Expansion-emitter metadata remains authoritative scan data but
does not add a per-block colour or box. Build sites form a separate lower-noise
green group. A position is in at most one transmitted class: placing a valid
structural block moves it from buildable to counted on the next refresh, and
placing an expansion emitter may make additional build sites reachable.
Unreachable material, unloaded cells, the lodestone origin and reached but
non-replaceable solid blocks are omitted from the transmitted relative list;
the client adds the trusted payload origin to the counted outline group. The
response contains no block identifiers or block-state properties.

### Bounded protocol and request policy

Offsets are limited to `TeleportArrayMaterialScan.MAX_DISTANCE` on every axis,
the lodestone origin offset is forbidden, duplicates are rejected, and the
response count cannot exceed the scan envelope maximum of 1,330 positions. The
server sorts offsets deterministically before encoding. Requests contain the
active map source type, source unit ID and the two enabled-class flags; they
contain no client-provided radius, position set, material value or duration. A
request with both flags false disables the session without scanning.

An initial enable is rejected unless a currently held bound interface has a
live map/interface context matching that source and the unit is still an active
lodestone that passes discovery, view, dimension, eight-block proximity,
loaded-only and physical-lodestone validation. A successful initial enable
creates one non-persistent, server-only per-player visualization session holding
the exact source type and unit ID. Later requests for that same source may
update the enabled classes without requiring the interface item to remain held,
so the player can close the Screen, switch to building materials and work from
the live overlay. Those requests do not renew or reuse the general teleport
interface context: every accepted refresh independently repeats the active
lodestone, discovery, view/friend, dimension, proximity, loaded and physical
block checks through the map-source authority. A different source always
requires a new held-interface validation.

The scan reads only loaded positions. Enable/refresh requests are rate-limited
per player to at most one accepted scan every 20 server ticks; disable is always
cheap and clears server session state without scanning. Each accepted refresh
returns a small status acknowledgement. A full position snapshot is sent only
when the source, enabled classes, origin/dimension or deterministically sorted
position/class content differs from the player's last accepted snapshot. A
rejected active session returns only an invalid status and no positions, and
clears its server session and cached snapshot so the client removes stale data
without learning remote structure. Disconnect and server lifecycle cleanup also
drop this non-persistent state.

### Session-persistent dynamic client preview

At most one source preview is active per client. Enabling either control
replaces any preview for another source and persists for that client session;
there is no elapsed-time expiry, and closing the map Screen does not disable it.
After the initial held-interface authorization, the player may put the interface
away and select building materials without losing the preview. While enabled,
the client requests one refresh no more frequently than every 20 client ticks.
The server remains authoritative and applies its own 20-tick rate limit. The
client clears both modes immediately on explicit final disable, world or
dimension change, disconnect, missing/replaced lodestone, server invalidation,
acknowledgement loss, or moving outside the authoritative eight-block
source-open radius. Clearing rather than retaining a remote paused snapshot is
the chosen anti-disclosure policy; returning later requires an explicit new
enable.

On each accepted changed payload, the client independently derives two immutable
TotemCore voxel-union plans. The counted plan includes the trusted lodestone
origin and every non-buildable returned position, and uses cyan
`THROUGH_WALLS`. The build-site plan includes every buildable position and uses
green `DEPTH_TESTED` so terrain suppresses distant clutter. Each plan removes
face-shared internal edges and coplanar surface seams, ignores fully enclosed
cavities, preserves true irregular and disconnected geometry, and merges
contiguous collinear unit edges into maximal line segments. It must not replace
the set with an inaccurate global bounding box. The plans are cached until the
next accepted payload and cleared with all existing preview lifecycle cleanup;
render frames only submit cached lines through the shared API. Normal frustum
and distance bounds still apply. The implementation must not use particles as a
substitute for exact membership and must not access a framebuffer.

### Screen ownership and Observer safety

Both controls remain part of `NexusSpaceUnitMapScreen`, including its production
layout and native Observer reconstruction. Observer mode exposes the same
descriptive Material contents but both controls are non-interactive and cannot
create request packets or a world overlay for the viewer. The semantic Observer
payload remains format/protocol 3 and is not expanded with world positions or
local toggle state because visualization is local gameplay context, not remote
screen state. Provider tests must prove that the unchanged semantic contract is
sufficient and that clicks cannot send packets.

## Risks / Trade-offs

- A 1,330-position preview can still have a complex outer surface and dynamic
  scans cost more than the common small array. Cached maximal boundary lines,
  the shared 1,330 union cap, one-source client limit, depth-tested build sites,
  eight-block authority radius, 20-tick request cadence and unchanged-snapshot
  suppression bound geometry, network and render work.
- Through-wall display reveals the counted positions behind nearby terrain.
  Source-only authority, the existing eight-block request radius and
  non-persistent per-player session state prevent it from becoming a remote
  structure-inspection tool.
- A block change can occur between scans. The next accepted refresh is targeted
  within about one second; neither side scans every tick and tests use
  deterministic tick advancement rather than sleeps.
- Source-only inspection does not preview a distant selected destination. This
  prevents remote structure disclosure and ensures the player can compare the
  overlay with blocks physically present in loaded chunks.

## Migration Plan

No persisted schema changes are required. First publish and validate the
backward-compatible TotemCore 0.7.x client API. Then migrate Excavation to its
`DEPTH_TESTED` mode and implement Nexus with `THROUGH_WALLS`, raising only those
two modules' minimum Core patch to the release that contains the API. Ship the
Nexus request/snapshot/status codecs and server receiver together with the
client state, renderer and UI controls. Older Nexus clients do not encode the
added dual-mode fields, so Nexus clients and servers must upgrade together
for this feature. The established payload identifiers remain stable; the
existing map and material diagnostics are otherwise unchanged.

## Open Questions

- None. Session persistence, bounded live refresh and reached replaceable build
  sites are approved extensions of this active change.
