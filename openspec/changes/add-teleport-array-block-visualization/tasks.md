## 1. Shared TotemCore outline API

- [x] 1.1 Add immutable client-only outline style and explicit
  `DEPTH_TESTED` / `THROUGH_WALLS` occlusion contracts under Core API v1, plus a
  stateless cuboid/block submission helper that does not own feature state or
  render callbacks.
- [x] 1.2 Unit-test both depth modes, style validation and submitted gizmo
  properties; update Core API documentation to record the generic world-outline
  presentation exception and dedicated-server boundary.
- [ ] 1.3 Run Core's required unit, build, Client GameTest, dedicated three-JVM
  E2E and Production Runtime checks, then assign/publish the compatible 0.7.x
  patch before consumers require it.
- [x] 1.4 Add a reusable immutable voxel-union outer-boundary plan that removes
  shared/coplanar seams and enclosed cavities, preserves disconnected geometry,
  merges maximal lines, bounds pathological component floods and submits only
  through the existing Core line API; cover it with deterministic unit tests.

## 2. Excavation migration

- [x] 2.1 Replace Excavation's direct gizmo submission with the Core API in
  `DEPTH_TESTED` mode without moving selection state or lifecycle into Core.
- [x] 2.2 Preserve unit and Client GameTest proof that opaque terrain hides the
  selection marker; update the minimum Core dependency to the publishing patch
  and run Excavation's repository-required checks.

## 3. Authoritative Nexus scan and protocol

- [x] 3.1 Expose a reusable bounded visualization result from the production
  material scan, including structural positions and the expansion-emitter
  subset without duplicating profile resolution.
- [x] 3.2 Add bounded enable/disable request and clientbound visualization
  payload codecs with deterministic relative offsets, duplicate rejection and
  the 1,330-position limit.
- [x] 3.3 Register the payload types and implement server authority checks for
  strict initial held-interface context, same-source visualization sessions,
  per-refresh discovery/view permission, active lodestone identity, same
  dimension, proximity, loaded-only scanning and per-player throttling.
- [x] 3.4 Extend the request/snapshot contract with independent counted and
  build-site modes, exact buildable classification, bounded status
  acknowledgements and unchanged-snapshot suppression.

## 4. Nexus client visualization and UI

- [x] 4.1 Add one-preview client state with explicit/lifecycle cleanup on
  toggle-off, disconnect, world/dimension transition, invalid source and
  distance cutoff, without elapsed-time expiry.
- [x] 4.2 Add a module-owned level gizmo renderer that submits counted blocks,
  expansion emitters and the lodestone origin through the Core API in
  `THROUGH_WALLS` mode, without framebuffer, screenshot, video or pixel paths.
- [x] 4.3 Add localized `Show Array` / `Hide Array` controls and unavailable
  reasons to the production Material view, preserving narrow layouts.
- [x] 4.4 Keep Observer mode read-only: it may show descriptive material data
  but cannot enable the control, send packets or activate a viewer overlay.
- [x] 4.5 Replace elapsed expiry with client-session persistence, 20-tick
  dynamic refresh and authority/lifecycle cleanup; closing the Screen must not
  disable either mode.
- [x] 4.6 Add the independent localized build-site toggle and green
  `DEPTH_TESTED` rendering with collision-free normal and narrow layouts.
- [x] 4.7 Replace per-block cyan/gold/purple boxes with cached outer-only plans:
  one cyan `THROUGH_WALLS` counted/origin union and one independent green
  `DEPTH_TESTED` build-site union, with all lifecycle cleanup preserved.

## 5. Nexus verification

- [x] 5.1 Unit-test payload bounds, deterministic ordering, duplicate/offset
  rejection, request throttling, session persistence and lifecycle cleanup.
- [x] 5.2 GameTest straight, chained and disconnected expansion layouts to
  prove the visualization set equals the authoritative structural set, marks
  emitters correctly and does not include air or unreachable materials.
- [x] 5.3 GameTest forged source IDs, remote/different-dimension sources,
  permission loss, missing lodestones and unloaded boundaries without force
  loading or returning a position payload.
- [x] 5.4 Client GameTest the production control and capture native-scale
  screenshots showing ordinary blocks, expansion emitters, through-wall
  visibility, narrow layout and Observer-disabled behavior; inspect rather than
  blindly blessing screenshots.
- [ ] 5.5 Add and run the repository-required dedicated three-JVM E2E and
  Production Runtime checks for enable, response, render activation, disable
  and disconnect cleanup.
- [x] 5.6 Update Nexus's minimum Core dependency to the publishing patch and run
  unit tests, assemble, server GameTests, Client GameTests and final JAR
  resource/metadata inspection with that Core build.
- [x] 5.7 Cover refresh cadence, unchanged suppression, status invalidation,
  exact buildable sets, place/break transitions, dual-mode rendering and both
  disabled Observer controls without timer sleeps.
- [x] 5.8 Cover strict initial held-interface authorization, same-source refresh
  after switching to building materials, changed place/break snapshots, source
  switch rejection, distance/permission invalidation and disable/disconnect/
  server-lifecycle session cleanup without sleeps.
- [x] 5.9 Cover adjacent cuboid collapse, coplanar seam removal, irregular and
  disconnected exteriors, cavity suppression, maximal segment merging,
  independent dual-mode styles, accepted-payload caching and cleanup, plus
  material submission reduction against twelve per-block edges.

## 6. Documentation and completion

- [x] 6.1 Update the README and Nexus manual in English and Traditional Chinese
  with source-local use, colour meaning, persistent refresh, secure cleanup and
  intentional through-wall behavior.
- [x] 6.2 Validate this OpenSpec change strictly, record executed evidence, then
  mark every completed task accurately before release review.
- [x] 6.3 Update English/Traditional Chinese manual and README guidance for
  persistent dynamic modes, colour/occlusion meaning and secure cleanup, then
  rerun strict OpenSpec validation.
- [x] 6.4 Update current design/spec guidance for outer-only merged boundaries
  while retaining historical release evidence as historical records.
