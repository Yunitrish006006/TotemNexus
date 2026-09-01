## Why

Material-driven scan expansion is currently visible only as aggregate reach and
attribute values. Builders cannot tell which physical blocks the authoritative
scan actually counted, so a disconnected extension path can look valid even
though its distant blocks do not contribute to the array.

## What Changes

- Add a versioned, client-only TotemCore world-outline API with immutable style
  values and explicit `DEPTH_TESTED` and `THROUGH_WALLS` occlusion modes. Core
  owns only the generic cuboid-submission primitive; callers retain feature
  state, render-event registration, networking, authorization and cleanup.
- Add independent local `Show Array` and `Show Build Sites` controls to the
  source lodestone's Material view in the Space Unit map.
- Re-run the bounded server-authoritative material scan when visualization is
  requested and return only the structural block positions that scan actually
  counted, with the local expansion emitters identified as a subset.
- Render the counted blocks through the shared Core outline API in
  `THROUGH_WALLS` mode, using a distinct treatment for expansion emitters and
  the lodestone origin so the diagnostic remains visible through walls.
- Derive buildable positions from the same authoritative scan: reached,
  loaded, non-structural positions whose current state can be replaced by a
  placed block. Render those positions as lower-noise green `DEPTH_TESTED`
  outlines.
- Migrate Excavation's existing selection outline to the same Core API in
  `DEPTH_TESTED` mode, preserving its rule that selection markers do not show
  through opaque terrain.
- Establish this API as the required primitive for Automata's later area-job
  selection marker; that follow-up retains Automata-owned state and chooses its
  occlusion mode in its own approved feature spec.
- Keep enabled visualization active for the current client session and refresh
  it dynamically at most once per 20 ticks. Initial enable requires the held,
  bound interface; the resulting server-only session lets the player put that
  interface away and build while every same-source refresh continues full
  source-authority validation. It is cleared on explicit hide, dimension/world
  changes, disconnect, invalid source/authority, or invalid distance, and is
  never persisted in Space Unit SavedData.
- Bound and authorize the request and response so the feature cannot inspect a
  remote array, force-load chunks, leak unrelated block state, or create an
  unbounded render/network workload.

## Impact

- Affected specs: `shared-world-outline-api` and
  `teleport-array-visualization` (new capabilities).
- Affected repositories in this change: TotemCore supplies the generic client
  contract; TotemExcavation consumes the depth-tested mode; TotemNexus consumes
  the through-wall mode and owns the teleport-array feature. TotemAutomata is a
  documented future consumer and receives no behavior change in this proposal.
- Affected dependency metadata: Excavation and Nexus raise their minimum
  TotemCore version to the first 0.7.x patch that publishes this API while
  retaining the existing `<0.8.0` compatibility ceiling.
- Affected server code: material scan result exposure, visualization authority,
  payload registration and request throttling.
- Affected client code: Space Unit Material view, session-persistent preview state,
  through-wall world rendering and lifecycle cleanup.
- Affected resources and validation: English/Traditional Chinese text, unit
  tests, server GameTests, native-scale Client GameTest screenshots, dedicated
  three-JVM E2E and Production Runtime validation.
- No SavedData migration, remote Observer world-position transport, screenshot
  transport or framebuffer access is introduced.
