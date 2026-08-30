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
- Make expansion-emitting blocks distinguishable so a builder can understand
  why the scan reaches a distant block.
- Keep rendering local, deliberately visible through terrain, bounded and
  lifecycle-safe.
- Give Totem modules one versioned client-only outline primitive with an
  explicit occlusion choice instead of duplicating raw gizmo setup.
- Reuse the production scan and authorization rules rather than reproducing
  material logic on the client.

### Non-Goals

- Do not visualize every empty `visitedPositions` cell or draw a global 11x11x11
  shell; the requested view is the set of blocks actually used by the array.
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

TotemCore adds a client-only API under `dev.totem.core.api.v1.client` with three
small public concepts:

- an immutable outline style carrying ARGB colour and finite positive line
  width;
- an explicit occlusion enum with `DEPTH_TESTED` and `THROUGH_WALLS`; and
- a stateless cuboid/block submission helper backed by Minecraft's normal
  gizmo extraction path.

The helper returns no mutable renderer state. It submits a default depth-tested
gizmo for `DEPTH_TESTED` and applies always-on-top only for `THROUGH_WALLS`.
Feature modules register their own level-render callback and call the helper
each frame for their current immutable snapshot. This keeps Core free of
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
uses `THROUGH_WALLS` for every counted block, expansion emitter and origin
marker. The two consumers therefore prove both modes without Core learning
either feature's semantics.

Automata's planned area-job selection marker is a future consumer of the same
primitive. This change does not implement that area-job or select its occlusion
policy; its eventual approved spec must keep Automata state and lifecycle in
Automata while using the Core submission contract.

### Source-local Material control

The production Space Unit map Material view gains a `Show Array` / `Hide Array`
control. It applies only when the Material view is showing the map's source
lodestone. A remote selected entry keeps its existing aggregate diagnostics but
cannot request a world preview; the control is disabled with a localized reason.

The source-only rule matches the existing map authority: opening a lodestone map
already proves that the player is in the same dimension, within eight blocks,
has discovered the source and may view it. The server revalidates all of these
facts for every visualization request. Observer mode never enables the control
and never sends a visualization packet.

### One authoritative scan, two visual classes

The visualization authority invokes the same `TeleportArrayMaterialScan.scan`
path used by registration, calibration and maintenance. The response contains:

- the source unit identifier and lodestone origin;
- every position in `structuralPositions`, encoded as a relative offset;
- a flag for positions whose effective local scan-expansion radius is positive;
  and
- a short bounded client expiry time.

Ordinary counted structural blocks use the standard array-outline colour.
Expansion emitters use a distinct colour, and the centre lodestone is rendered
as an origin marker. Air, non-structural visited cells and unreachable material
blocks are omitted. The response contains no block identifiers or block-state
properties because the client already renders the physical world and needs only
the authoritative membership/classification result.

### Bounded protocol and request policy

Offsets are limited to `TeleportArrayMaterialScan.MAX_DISTANCE` on every axis,
the lodestone origin offset is forbidden, duplicates are rejected, and the
response count cannot exceed the scan envelope maximum of 1,330 positions. The
server sorts offsets deterministically before encoding. Requests contain the
active map source type, source unit ID and an enable/disable action; they contain
no client-provided radius, position set, material value or duration.

The server rejects a request unless the unit remains an active lodestone, the
active map/interface context matches that source, the player still has view and
discovery authority, the player is in the source dimension and within the
existing eight-block source-open radius, and the lodestone block still exists.
The scan reads only loaded positions. Enable/refresh requests are rate-limited
per player; disable is always cheap and clears client state without scanning.

### Temporary client preview

At most one array preview is active per client. A successful enable replaces
the old preview and lasts 30 seconds. Pressing the control again hides it;
pressing `Show Array` after expiry or after returning to the Material view gets
a fresh server scan. The client also clears the preview immediately on world or
dimension change, disconnect, source invalidation, or moving more than 16
blocks from the lodestone. This wider client display cutoff avoids flicker near
the eight-block request boundary without granting another scan.

The renderer submits one cuboid per returned block through TotemCore's shared
outline API during the module-owned normal level gizmo phase and selects
`THROUGH_WALLS`. This intentionally bypasses terrain depth occlusion so a
builder can follow a counted extension path through walls. Normal frustum and
distance bounds still apply. The implementation must not use particles as a
substitute for exact membership and must not access a framebuffer.

### Screen ownership and Observer safety

The control remains part of `NexusSpaceUnitMapScreen`, including its production
layout and native Observer reconstruction. Observer mode exposes the same
descriptive Material contents but the preview control is non-interactive and
cannot create request packets or a world overlay for the viewer. The semantic
Observer payload is not expanded with world positions because visualization is
local gameplay context, not remote screen state.

## Risks / Trade-offs

- A 1,330-block through-wall outline is visually dense and costs more than the
  common small array. The hard scan cap, one-preview client limit, frustum and
  distance bounds, distinct colours and 30-second lifetime bound the work.
- Through-wall display reveals the counted positions behind nearby terrain.
  Source-only authority, the existing eight-block request radius and temporary
  local state prevent it from becoming a remote structure-inspection tool.
- A temporary snapshot can become stale after a block change. Reopening or
  pressing `Show Array` obtains a new authoritative scan; continuous polling is
  deliberately excluded from this change.
- Source-only inspection does not preview a distant selected destination. This
  prevents remote structure disclosure and ensures the player can compare the
  overlay with blocks physically present in loaded chunks.

## Migration Plan

No persisted schema changes are required. First publish and validate the
backward-compatible TotemCore 0.7.x client API. Then migrate Excavation to its
`DEPTH_TESTED` mode and implement Nexus with `THROUGH_WALLS`, raising only those
two modules' minimum Core patch to the release that contains the API. Ship the
Nexus request/response codecs and server receiver together with the client
state, renderer and UI control. Older Nexus clients do not advertise the new
serverbound payload and therefore cannot request the preview; the existing map
and material diagnostics remain usable.

## Open Questions

- None for the initial implementation. Continuous live refresh and empty scan
  envelope visualization require separate performance and UX approval.
