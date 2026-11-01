## 1. Establish MapId-owned persistence

- [x] 1.1 Add a server-owned record keyed by MapId for logical scale, expected
  native geometry, detail/coverage roots, lineage, revision and integrity.
- [x] 1.2 Add strict atomic bootstrap from immutable native MapItemSavedData,
  including compatible retries, conflicting records and save/reload coverage.
- [x] 1.3 Define the unsupported legacy behavior: ignore item Space Unit claims,
  retain native data, and do not infer old logical scale/detail from scale 4.

## 2. Open every valid filled map

- [x] 2.1 Resolve the held MapId and native data on the server without requiring
  Nexus item identity or a creating Space Unit.
- [x] 2.2 Bootstrap missing records before opening and refuse missing,
  contradictory or unavailable storage without creating a partial session.
- [x] 2.3 Recompute current-holder nodes from live visibility, discovery,
  painted coverage, dimension and logical extent without loading chunks.
- [x] 2.4 Keep current player position/dimension as teleport source and
  revalidate held MapId, destination, quote and cost on commit.

## 3. Unify exact-center creation and derivation

- [x] 3.1 Make lodestone use allocate an exact-centered native map plus the same
  MapId-owned baseline record, without item Space Unit identity.
- [x] 3.2 Preserve exact center and dimension through lock, scale, copy and
  logical scale above 4; atomically publish each derived MapId and lineage.
- [x] 3.3 Remove original-lodestone existence/loading from map open and
  derivation, with destroyed/unloaded-origin regressions.
- [x] 3.4 Remove the superseded filled-map-at-lodestone activation path and
  update player-visible documentation and messages.

## 4. Validate the integrated behavior

- [x] 4.1 Add focused unit and server GameTests for vanilla-grid and exact
  centers, bootstrap races/conflicts, copies, missing data, save/reload,
  scale 0–4 and logical scale 5+.
- [x] 4.2 Run native map-copy, scale, lock, stale-preview, quick-move and
  inventory-conservation regressions.
- [x] 4.3 Validate current-holder privacy/permissions, input and packet
  suppression, close cleanup, provider compatibility and framebuffer-free
  Observer reconstruction.
- [x] 4.4 Run Java 25 Gradle build, dedicated server/client GameTests,
  three-JVM E2E, Production Runtime probe and cross-module consumer review;
  record exact evidence without committing, pushing or publishing.

## Evidence checkpoints

### 2.3 Current-holder node projection

- Replaced the map payload's legacy item-binding access predicate with a pure
  MapId-record projection that is recalculated for every payload send.
- The projection requires active state, current-holder visibility, discovery or
  native painted coverage, matching dimension and the record's logical extent.
- The selector reads only persisted unit/map data. It neither loads chunks nor
  stores a node list in the item, native map data or MapId record.
- Focused JUnit: `NexusMapNodeProjectionTest` — 4 tests, 0 failures/errors.
- Java 25 Gradle validation with TotemCore 0.7.23: `test`, `assemble`,
  `compileGametestJava` and `validateAccessWidener` completed successfully.
- Checkpoint: taskId `mapid-live-node-projection-2.3`; repository
  `TotemNexus`; branch `master`; starting HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; changed files
  `NexusMapPayloadAuthority.java`, `NexusMapNodeProjection.java`,
  `NexusMapNodeProjectionTest.java` and this task log.
- Remaining risk: extended painted-detail roots are not produced yet; outside
  native coverage, only discovered nodes can qualify. Production use-item
  routing is still pending. Next task is 2.4 commit-time source, held MapId,
  destination, quote and cost revalidation.

### 2.4 MapId teleport commit authority

- The new MapId teleport chain now carries the exact initiating hand and MapId
  through its immutable countdown session and requires the same compatible
  held record again at final resolution.
- Final resolution uses the player's live position/dimension as source,
  recalculates destination authorization through the current-holder projection,
  recalculates the quote from live endpoints/resources and deducts only that
  final server quote.
- Focused dedicated-server GameTest: 3 required tests passed, covering player
  source identity, MapId swaps/native conflicts, session identity retention and
  current-holder destination recomputation.
- Full JUnit: 349 tests, 0 failures/errors/skips. Java 25 Gradle `test`,
  `assemble`, `compileGametestJava` and `validateAccessWidener` passed.
- Clean legacy `map-interface` regression: 41/42 passed. The sole failure is
  the pre-existing task-2.3 transferred-map payload projection case; all
  countdown, destination-change and single-payment cases passed after the new
  chain was isolated from the not-yet-cut-over legacy path.
- Checkpoint: taskId `mapid-teleport-commit-2.4`; repository `TotemNexus`;
  branch `master`; starting HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; next task 3.1 exact-centered
  lodestone creation using the shared MapId baseline record.

### 3.1 Exact-centered lodestone creation

- Empty-map lodestone binding now publishes a scale-0 native map centered on
  the exact lodestone block and atomically establishes its MapId baseline
  before returning the item.
- The created item carries no canonical or legacy Space Unit identity, and no
  legacy MapId-to-unit binding is created. Unrelated custom components survive
  the identity cleanup.
- Focused dedicated-server GameTest: all 3 required tests passed, covering
  successful exact creation and fail-closed behavior without a valid lodestone.
- Full JUnit: 349 tests, 0 failures/errors/skips. Java 25 Gradle `test`,
  `assemble`, `compileGametestJava` and `validateAccessWidener` passed.
- Checkpoint: taskId `mapid-exact-centered-creation-3.1`; repository
  `TotemNexus`; branch `master`; starting HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; next task 3.2 MapId derivation
  through copy, lock, native scale and logical scale above 4.

### 3.2 MapId-owned lock and scale derivation

- Registered maps now preflight and atomically publish a child MapId record
  before exposing the derived native map. Every child retains the parent's
  exact center, dimension, detail/coverage roots and ordered MapId lineage,
  while incrementing its revision.
- Native scale advances only through 0–4. Further expansions keep native scale
  4 and increase the MapId record's logical scale; locking preserves both
  native and logical extent. Vanilla physical copies continue to share their
  one MapId and therefore one native/Nexus identity.
- Contradictory native geometry, unavailable terrain storage, locked expansion,
  exhausted extent and allocation conflicts fail before publishing a child.
- Focused JUnit `NexusMapRecordSavedDataTest` passed. Focused clean-world
  dedicated-server GameTest: all 3 required tests passed, including exact
  center scale 0–5, extended locking, shared-copy identity and fail-closed
  contradictory native data without MapId allocation.
- Full JUnit: 352 tests, 0 failures/errors/skips. Java 25 Gradle `test`,
  `assemble`, `compileGametestJava` and `validateAccessWidener` passed with
  TotemCore 0.7.23.
- Checkpoint: taskId `mapid-derived-map-lifecycle-3.2`; repository `TotemNexus`;
  branch `master`; starting HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; changed files
  `NexusMapRecordSavedData.java`, `NexusMapDetailSavedData.java`,
  `NexusMapLifecycleAuthority.java`, `NexusMapRecordSavedDataTest.java`,
  `NexusMapDerivationGameTest.java`, GameTest manifest/focus and this task log.
  Next task is 3.3 removal of original-lodestone existence/loading gates with
  destroyed and unloaded former-origin regressions.

### 3.3 Former-origin independence

- MapId-owned opening already resolves only the held MapId and native data; the
  remaining legacy derivation fallback now also stops resolving a Space Unit,
  checking a lodestone block or requiring its chunk to be loaded. Native map
  dimension is used directly during derivation.
- Focused clean-world dedicated-server GameTest: all 3 required tests passed,
  covering a production exact-centered map after its creating lodestone was
  destroyed and its unit disabled, plus a remote former-origin chunk that
  remained unloaded through open, scale and lock.
- The map-crafting regression suite was updated from the superseded
  missing-origin refusal contract. All 35 required tests passed, including
  cartography removal immediately before post-processing and inventory/MapId
  commit behavior.
- Full JUnit remains 352 tests with 0 failures/errors/skips. Java 25 Gradle
  `test`, `assemble`, `compileGametestJava` and `validateAccessWidener` passed
  with TotemCore 0.7.23.
- Checkpoint: taskId `mapid-former-origin-independence-3.3`; repository
  `TotemNexus`; branch `master`; starting HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; changed files
  `NexusMapLifecycleAuthority.java`, `NexusMapOriginIndependenceGameTest.java`,
  `NexusMapCraftingTransactionGameTest.java`, GameTest manifest/focus and this
  task log. Next task is 3.4 removal of the superseded filled-map-at-lodestone
  activation path plus player-visible documentation and messages.

### 3.4 Filled-map activation removal

- Filled maps, including items carrying pre-change Nexus identity metadata, no
  longer resolve as lodestone registration or binding inputs. The dedicated
  existing-MapId binding mutation and its source-mismatch message were removed;
  empty maps remain the exact-center lodestone creation input.
- README and in-game manual guidance now describe direct MapId opening, exact
  center creation and expansion without the creating lodestone. The superseded
  `enable-existing-map-activation` proposal is explicitly marked historical.
- Focused JUnit passed: 7 tests across registration resolution, pending identity
  and locale guidance. Focused clean-world dedicated-server GameTest passed all
  3 required tests, covering ordinary maps, legacy-claimed map stacks,
  inventory conservation and native map-data preservation.
- Full JUnit: 352 tests, 0 failures/errors. Java 25 Gradle `test`, `assemble`,
  `compileGametestJava` and `validateAccessWidener` passed with TotemCore 0.7.23.
  Strict OpenSpec validation passed for both the active and superseded changes.
- The broad legacy `map-interface` run passed 25/46 and failed 21 tests whose
  assertions still require origin-bound map identity or the retired legacy map
  session. Those expected stale-contract tests are retained for the matching
  regression, privacy and production-cutover work in tasks 4.2–4.4; the two
  activation-removal cases passed independently.
- Checkpoint: taskId `mapid-activation-removal-3.4`; repository `TotemNexus`;
  branch `master`; starting HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; changed files include the three
  map registration/lifecycle authorities, README, four locale files, three
  focused JUnit files, `NexusInterfaceLifecycleGameTest`, the new
  `NexusMapActivationRemovalGameTest`, GameTest manifest/focus and both relevant
  OpenSpec changes. Focused world archived at
  `/tmp/totem-nexus-map-activation-removal-pass-20261101-1200`; next task 4.1
  integrated MapId unit and server GameTest coverage.

### 4.1 Integrated MapId record coverage

- Focused JUnit passed all 23 tests across held-map resolution, atomic open
  preflight and MapId SavedData. Coverage includes compatible retry,
  competing bootstrap, conflict preservation, immutable native snapshots,
  unsupported legacy claims and actual SavedData save/reload.
- The ordinary-map server test now proves vanilla grid alignment at native
  scale 2, matching baseline-record geometry and zero item mutation. A new
  fail-closed test proves a held MapId with missing native map data creates
  neither a MapId record nor a session and leaves the item unchanged.
- Focused clean-world dedicated-server GameTest passed all 8 required tests.
  Together the ordinary-grid, exact-center and derivation suites cover shared
  physical copies, missing/conflicting data, native scales 0–4, logical scale
  5, lock lineage and preservation of center/dimension.
- Full JUnit remains 352 tests with 0 failures/errors. Java 25 Gradle `test`,
  `assemble`, `compileGametestJava` and `validateAccessWidener` passed with
  TotemCore 0.7.23.
- Checkpoint: taskId `mapid-integrated-record-coverage-4.1`; repository
  `TotemNexus`; branch `master`; starting HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; changed files for this task are
  `NexusMapOpenPreflightGameTest.java`, `build.gradle` and this task log.
  Focused world archived at
  `/tmp/totem-nexus-map-integrated-records-pass-20261101-1252`. Remaining risk:
  broad legacy interface regressions still encode origin-bound map behavior;
  next task 4.2 owns copy/scale/lock/stale-preview/quick-move and inventory
  conservation regression cutover.

### 4.2 Cartography and inventory regressions

- The focused dedicated-server map-crafting suite passed all 35 required tests
  on the task-4.1 code state. It covers physical same-MapId copies, native
  scaling and logical scale 5+, locking, forged/stale previews, ordinary and
  direct quick-move, hotbar swap, drop, creative clone and full inventories.
- Failure-path coverage proves unavailable/frozen storage, occupied or
  exhausted MapId allocation, post-consumption origin removal and late scale or
  lock denial do not leak maps, duplicate outputs or lose ingredients. Multi-
  craft cases preserve the first committed result while restoring only the
  denied batch.
- Reused unchanged task-4.1 derivation evidence: all 8 focused integrated
  server tests passed, including copy identity, exact center, native scale 0–4,
  logical scale 5 and lock lineage. No source changed after that run, so the
  suite was not repeated.
- Reused unchanged task-4.1 deterministic build evidence: 352 JUnit tests with
  0 failures/errors, plus successful Java 25 `assemble`,
  `compileGametestJava` and `validateAccessWidener` using TotemCore 0.7.23.
- Checkpoint: taskId `mapid-cartography-regressions-4.2`; repository
  `TotemNexus`; branch `master`; starting HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; only this task log changed in
  this microtask. Focused world archived at
  `/tmp/totem-nexus-map-crafting-regressions-pass-20261101-1612`. Remaining
  risk: privacy/permissions and Observer lifecycle remain for task 4.3; the
  broad legacy interface suite still contains origin-bound expectations.

### 4.3 Current-holder privacy and Observer reconstruction

- Focused `NexusMapNodeProjectionTest` passed 4/4 JUnit tests. The projection
  recalculates visibility, discovery, dimension, coverage and logical extent
  for the current holder, including after the same MapId changes hands.
- Focused `map-teleport-commit` server GameTest passed all 3 required tests.
  Teleport commit revalidates the current holder, MapId, destination and access
  immediately before execution rather than trusting an earlier preview.
- Focused `map-observer` client GameTest completed successfully under a virtual
  display. Both Observer entrypoints ran: the provider compatibility/lifecycle
  suite and the MapId detail reconstruction suite. They cover protocol/variant
  rejection, monotonic snapshots, read-only mouse, drag, scroll and keyboard
  suppression, zero action packets, one-shot Escape cleanup and native-scale
  screenshots for all supported Nexus variants.
- Provider inspection confirms framebuffer-free reconstruction: capture emits
  bounded semantic metadata plus codec payload bytes, and create/apply rebuild
  module-owned Screens from those values; there is no pixel, screenshot or
  framebuffer transport path.
- Java 25 Gradle validation with Minecraft 26.3, Loader 0.19.5, Fabric API
  0.160.5+26.3 and TotemCore 0.7.23 passed `test`, `assemble`,
  `compileGametestJava` and `validateAccessWidener`; JUnit was 352/352 with no
  failures, errors or skips. Headless OpenGL/audio and Mojang/Realms network
  warnings were environmental and did not fail either client test entrypoint.
- Checkpoint: taskId `mapid-current-holder-observer-4.3`; repository
  `TotemNexus`; branch `master`; starting HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; this microtask changed
  `build.gradle` to add the bounded `map-observer` focus and this task log.
  Remaining risk is task 4.4's full dedicated server/client, three-JVM E2E,
  Production Runtime and cross-module consumer validation; no commit, push or
  publication was performed. Next task: 4.4.

### 4.4 Full runtime and consumer validation

- Production map opening, payload, quote, commit, overview, detail sampling and
  visualization now use the current held MapId plus `NexusMapRecordSavedData`;
  old item UUID and creating-lodestone identity no longer authorize a map.
  Extended recorded coverage is used consistently by access, payload, quote
  and final commit checks.
- MapId derivation allocates collision-free IDs by checking the next candidate
  before atomically consuming it, rejects integer exhaustion, and preserves
  exact center, dimension, native data and logical scale. The transaction
  suite also isolates its global capacity fixture from parallel GameTests.
- Clean-world focused server evidence passed: map runtime 17/17, map crafting
  35/35 and map resolution 6/6. The final full dedicated-server suite passed
  all 286 required GameTests in 3.844 minutes.
- The complete development client GameTest run exited successfully under a
  virtual display after exercising every registered client entrypoint. Focused
  MapId network-resolution and held/item-frame overview runs also passed,
  including scale-7 detail delivery, extended pan, native overview palettes,
  Observer reconstruction, stale snapshot rejection, cache bounds and close
  cleanup. No screenshot baseline was changed.
- Production Runtime validation passed from the remapped 0.3.30 jar on
  Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.160.5+26.3 and Java 25.
  Its focused MapId network/extended-view test exited successfully after the
  asynchronous formal-jar wait budget was raised without weakening assertions.
- Dedicated three-JVM E2E passed with separate server, target and observer
  processes: access Observer flow, array enable/response/render, observer
  isolation, disable and disconnect cleanup all produced their required
  markers and screenshots.
- Final deterministic Gradle validation passed `test`, `assemble`,
  `compileGametestJava` and `validateAccessWidener`; JUnit was 352 tests with
  zero failures, errors or skips. Strict OpenSpec validation passed for this
  change and the pending cartography-cleansing change; `git diff --check`
  passed.
- Cross-module source review found no shared API or event contract change.
  TotemObserver accepts Nexus provider protocols 3–6 (Nexus remains protocol
  6); TotemRemnant remains reflection/service-decoupled from map internals;
  TotemDiscordBridge consumes only Core public audit events. Core 0.7.23 was
  used for every compile and runtime validation.
- Checkpoint: taskId `mapid-full-runtime-validation-4.4`; repository
  `TotemNexus`; branch `master`; starting/current HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; no commit, push or publication
  was performed. Generated stale GameTest/client worlds were moved to `/tmp`
  rather than deleted. Next task is the separate cartography water-bucket
  cleansing change.
