# Implementation evidence

## 2026-11-01: MapId-owned record foundation

Added the standalone `NexusMapRecordSavedData` model under the new
`totem:nexus_map_records` SavedData key. Each strict record is keyed by MapId
and persists native geometry expectations, native/extended logical scale,
lock state, bounded unique lineage, optional SHA-256 detail and coverage roots,
and a non-negative revision. It contains no Space Unit UUID or origin position.

Compatible registration retries are idempotent; a conflicting record for the
same MapId is refused without replacement. Resolution compares the current
server MapItemSavedData dimension, center, native scale and lock state. Codecs
reject invalid scale relationships, roots, lineage, revisions and duplicate
MapIds. Actual SavedDataStorage save/reload is covered.

This foundation has no production caller. It does not yet bootstrap ordinary
maps, open a Screen, replace the origin-bound ledger, derive maps, change a
packet/provider, or implement cartography cleansing.

Validation used Temurin Java 25 and the TotemCore Gradle 9.6.1 wrapper:

- Focused `NexusMapRecordSavedDataTest`: 4 tests passed.
- Full JUnit report: 329 tests, 0 failures, 0 skipped, 100% successful.
- `test assemble compileGametestJava validateAccessWidener` completed with exit
  0; the quiet confirmation run also exited 0.
- `git diff --check` passed for the implementation files.
- Existing deprecation/removal warnings remain; no new warning was accepted as
  validation failure.

Checkpoint:

- taskId: `mapid-record-1.1`
- repository: `TotemNexus`
- branch: `master`
- starting HEAD: `e09a24faffdb884f63648382d27fafd2dfeccc94`
- files: `NexusMapRecordSavedData.java`,
  `NexusMapRecordSavedDataTest.java`, this evidence and task checkbox
- remaining risk: no production integration has exercised bootstrap,
  derivation, UI, Observer or runtime lifecycle
- next task: 1.2 strict atomic bootstrap from immutable native map data

## 2026-11-01: Atomic native-map bootstrap

Added an immutable `NativeSnapshot` that copies MapId, dimension, center,
native scale and lock state from server-owned `MapItemSavedData`. The
synchronized bootstrap operation inserts exactly one baseline record, treats
compatible retries as idempotent, preserves a compatible existing extended
record, and fails closed without replacement when the same MapId contradicts
any native geometry field. Invalid snapshots also leave the index unchanged.

Focused coverage includes initial bootstrap, compatible retry, preservation of
logical scale/root/lineage extensions, each native-field conflict, invalid
MapIds, immutable capture, two-thread competing bootstrap, and actual
SavedDataStorage save/reload. This task still has no production caller and does
not change map opening, derivation, UI, Observer behavior or cleansing.

Validation used Temurin Java 25 and the TotemCore Gradle 9.6.1 wrapper:

- Focused `NexusMapRecordSavedDataTest`: 10 tests passed.
- Full JUnit report: 335 tests, 0 failures, 0 errors, 0 skipped.
- `test assemble compileGametestJava validateAccessWidener` completed with exit
  0.
- Existing Minecraft/Fabric deprecation warnings remain; no new compilation
  error or validation failure was introduced.

Checkpoint:

- taskId: `mapid-bootstrap-1.2`
- repository: `TotemNexus`
- branch: `master`
- starting HEAD: `e09a24faffdb884f63648382d27fafd2dfeccc94`
- files: `NexusMapRecordSavedData.java`,
  `NexusMapRecordSavedDataTest.java`, this evidence and task checkbox
- remaining risk: bootstrap is not yet wired into map opening; runtime server
  authority, derivation, UI and Observer paths remain unexercised
- next task: 1.3 explicit unsupported legacy behavior

## 2026-11-01: Unsupported legacy extension boundary

Documented that bootstrap intentionally has no legacy item-identity or
`NexusMapBindingSavedData` input. Added a regression that constructs an old
Nexus map with a Space Unit UUID, native scale 4, logical scale 7 and detail
ancestry, then bootstraps its MapId through the new record store. The new record
starts at logical scale 4 with empty lineage, roots and revision, while the
native MapId and old item metadata remain untouched. This proves old claims do
not grant authority or silently import unsupported extension data.

This task does not delete or migrate the old ledger and still does not connect
the new record store to production map opening. Those transitions remain later
microtasks.

Validation used Temurin Java 25 and the TotemCore Gradle 9.6.1 wrapper:

- Focused `NexusMapRecordSavedDataTest`: 11 tests passed.
- Full JUnit report: 336 tests, 0 failures, 0 errors, 0 skipped.
- `test assemble compileGametestJava validateAccessWidener` completed with exit
  0.
- OpenSpec strict validation and whitespace checks passed.

Checkpoint:

- taskId: `mapid-legacy-boundary-1.3`
- repository: `TotemNexus`
- branch: `master`
- starting HEAD: `e09a24faffdb884f63648382d27fafd2dfeccc94`
- files: `NexusMapRecordSavedData.java`,
  `NexusMapRecordSavedDataTest.java`, this evidence and task checkbox
- remaining risk: the existing production resolver still requires legacy
  identity until the subsequent ordinary-map resolution/open tasks replace it
- next task: 2.1 server-side held MapId/native-data resolution without Nexus
  item identity

## 2026-11-01: Server-owned held-map resolution

Added `NexusHeldMapResolver` as a dedicated-server-safe boundary that reads the
actual held filled-map stack, extracts only its MapId, and resolves
`MapItemSavedData` through the player's server level. It never reads or
requires Nexus/Space Unit identity metadata. The returned pair is validated
against the strict native snapshot invariants before use.

Focused coverage proves that ordinary filled maps resolve, legacy-bound maps
resolve without consuming their UUID claim, physical copies with one MapId
share the same native record, and non-filled items, missing/negative MapIds,
missing native data and missing lookup authority fail closed. The new resolver
is intentionally not connected to production opening yet; bootstrap/session
publication belongs to task 2.2.

Validation used Temurin Java 25 and the TotemCore Gradle 9.6.1 wrapper:

- Focused `NexusHeldMapResolverTest`: 4 tests passed.
- Full JUnit report: 340 tests, 0 failures, 0 errors, 0 skipped.
- `test assemble compileGametestJava validateAccessWidener` completed with exit
  0.
- Existing deprecation warnings remain; no new compilation or validation
  failure was introduced.

Checkpoint:

- taskId: `mapid-held-resolution-2.1`
- repository: `TotemNexus`
- branch: `master`
- starting HEAD: `e09a24faffdb884f63648382d27fafd2dfeccc94`
- files: `NexusHeldMapResolver.java`, `NexusHeldMapResolverTest.java`, this
  evidence and task checkbox
- remaining risk: no production caller uses the resolver, so map opening still
  follows the legacy identity/binding path
- next task: 2.2 atomic bootstrap and fail-closed session opening integration

## 2026-11-01: Atomic bootstrap before map-session publication

Added `NexusMapOpenPreflight` and connected ordinary held filled maps through
`NexusTeleportInterfaceAuthority.establishPlayerAnchor`. The preflight resolves
record storage, atomically bootstraps or validates the MapId record, and only
then publishes a short-lived player-source map session. Storage exceptions,
missing storage and contradictory native geometry return no context and leave
the session store empty. Compatible extended records are retained unchanged.

Unbound MapId sessions now revalidate that the same filled-map MapId remains in
the initiating hand through `NexusHeldMapResolver`; legacy bound-interface
revalidation remains unchanged. The production use-item gesture is not yet
rerouted, and node calculation remains task 2.3.

Validation used Temurin Java 25, Minecraft 26.3, Fabric Loader 0.19.5, Fabric
API 0.160.5+26.3 and the TotemCore Gradle 9.6.1 wrapper:

- Focused `NexusMapOpenPreflightTest`: 5 tests passed.
- Full JUnit report: 345 tests, 0 failures, 0 errors, 0 skipped.
- Focused dedicated-server `runGameTest -PnexusTestFocus=map-open-preflight`
  with the available compatible TotemCore 0.7.23 artifact: all 3 required tests
  passed.
- `test assemble compileGametestJava validateAccessWidener` completed with exit
  0 using the same Core artifact.
- The first GameTest launch was correctly rejected before tests because the
  default local path still selected stale TotemCore 0.7.22 while Nexus requires
  `>=0.7.23`; no test case ran in that rejected launch.

Checkpoint:

- taskId: `mapid-open-preflight-2.2`
- repository: `TotemNexus`
- branch: `master`
- starting HEAD: `e09a24faffdb884f63648382d27fafd2dfeccc94`
- files: `NexusMapOpenPreflight.java`, `NexusTeleportInterfaceAuthority.java`,
  `TeleportInterfaceContext.java`, focused unit/GameTest files, GameTest
  manifest/focus configuration, this evidence and task checkbox
- remaining risk: ordinary map use is not yet routed from the production item
  gesture, and payload node visibility/extent rules still use legacy behavior
- next task: 2.3 current-holder live node recomputation within MapId extent

## 2026-11-01: MapId teleport commit authority

The new MapId teleport authority now preserves the exact initiating hand and
MapId through its immutable preparation session. At final resolution it
requires the same held MapId and a compatible server record, requires the map
source to remain the current player, and re-resolves the destination through
the live current-holder node projection. The quote is recalculated from the
player's current position/dimension, live destination and current resources;
only that final server quote reaches cost deduction and movement.

The not-yet-cut-over legacy production path remains unchanged. An initial
attempt to apply the new gate there exposed the ordering dependency on task
3.1 and was removed before final validation.

Validation used Temurin Java 25, Minecraft 26.3, Fabric Loader 0.19.5, Fabric
API 0.160.5+26.3 and TotemCore 0.7.23:

- Focused `runGameTest -PnexusTestFocus=map-teleport-commit`: all 3 required
  dedicated-server tests passed.
- Full JUnit report: 349 tests, 0 failures, 0 errors, 0 skipped.
- `test assemble compileGametestJava validateAccessWidener` completed with exit
  0.
- A fresh generated-world `map-interface` run passed 41 of 42 tests. Its only
  failure, transferred-map public-node projection, was already present before
  the finalized task-2.4 integration and belongs to the task-2.3 payload path;
  all teleport countdown, destination invalidation and one-time payment cases
  passed.
- The stale generated GameTest world was moved, not deleted, to
  `/tmp/totem-nexus-gametest-backup-20261101-0902`; the fresh failed-run world
  was retained at `/tmp/totem-nexus-gametest-clean-20261101-0905`.

Checkpoint:

- taskId: `mapid-teleport-commit-2.4`
- repository: `TotemNexus`
- branch: `master`
- starting HEAD: `e09a24faffdb884f63648382d27fafd2dfeccc94`
- files: `NexusMapTeleportCommitAuthority.java`,
  `NexusTeleportExecutionSession.java`, `NexusTeleportStartAuthority.java`,
  `NexusTeleportResolver.java`, focused GameTest and registration/focus files,
  `NexusDistributedSpawnGameTest.java`, this evidence and task checkbox
- remaining risk: production item-use cutover is still pending, and the
  transferred-map payload regression from task 2.3 remains open
- next task: 3.1 exact-centered lodestone creation with the shared MapId
  baseline record

## 2026-11-01: Exact-centered lodestone map creation

The empty-map lodestone path now creates a native scale-0 map centered on the
exact lodestone block and publishes a compatible `NexusMapRecordSavedData`
baseline before exposing the filled-map item. The output retains unrelated
item components but explicitly strips canonical and legacy Nexus identity
fields. It creates neither item-level Space Unit identity nor a legacy
MapId-to-Space-Unit ledger entry.

The former bound-map constructor remains isolated for not-yet-migrated
derivation tests and is no longer used by the production empty-map binding
branch. Removing that superseded path belongs to task 3.4.

Validation used Temurin Java 25, Minecraft 26.3, Fabric Loader 0.19.5, Fabric
API 0.160.5+26.3 and TotemCore 0.7.23:

- Focused `runGameTest -PnexusTestFocus=map-exact-creation`: all 3 required
  dedicated-server tests passed.
- Full JUnit report: 349 tests, 0 failures, 0 errors, 0 skipped.
- `test assemble compileGametestJava validateAccessWidener` completed with exit
  0.
- The focused assertions cover exact center/dimension/scale, compatible MapId
  baseline, absence of item and legacy-ledger identity, unrelated component
  preservation and no input consumption when the lodestone is invalid.

Checkpoint:

- taskId: `mapid-exact-centered-creation-3.1`
- repository: `TotemNexus`
- branch: `master`
- starting HEAD: `e09a24faffdb884f63648382d27fafd2dfeccc94`
- files: `NexusMapLifecycleAuthority.java`, `NexusInterfaceBinding.java`,
  `NexusSpaceUnitAuthority.java`, `NexusMapExactCreationGameTest.java`, GameTest
  manifest/focus configuration, this evidence and task checkbox
- remaining risk: copy/lock/scale still use the legacy derivation ledger, and
  the superseded existing-filled-map activation path remains until tasks 3.2
  and 3.4
- next task: 3.2 preserve exact center and dimension through copy, lock,
  native scale and logical scale above 4 with atomic MapId lineage publication
