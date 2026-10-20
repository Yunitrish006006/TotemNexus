# Implementation evidence

## 2026-10-17: extent and ledger foundation

Implemented NexusMapExtent with exact fixed centers, wide logical bounds,
supported-domain clipping and expansion termination at full-world containment.
Ledger format 3 persists the effective scale separately from the native scale;
old format 1/2 records retain their actual native scale when the field is absent.
Explicit extent must match its native backing scale. Derivation validates one
expansion step or a new lock before changing lineage or inserting a result.
The physical lifecycle now shares the ledger's backing-data validation.

Existing recipe gates remain in place. This step does not yet expose extended
maps through a player recipe or change the client/provider wire formats.

Validation uses Java 25.0.3, Gradle 9.6.1 and Core 0.7.23, with the private build
directory configured by `/tmp/nexus-independent-map-build.gradle`:

- Before the production change, the ledger suite ran 8 tests with 3 expected
  new failures: absent authoritative scale, mismatched native backing data,
  and ignored malformed scale. Log: `/tmp/nexus-extent-red.log`.
- After the change, all 124 JUnit tests passed with zero errors or skips,
  including 12 ledger/payload tests and 4 extent tests. Coverage includes
  actual disk save/reload and non-destructive canonical migration.
- `test assemble compileGametestJava` completed successfully, exit 0.
  Log: `/tmp/nexus-extent-fixed.log`.
- `git diff --check` and strict OpenSpec validation passed.
- Independent review accepted this bounded foundation step after checking
  bounds, legacy behavior, native backing identity and atomic derivation.

Consumer impact review: Core interfaces, death-node integration, audit events,
network payloads and Observer snapshots are unchanged. The new Entry component
retains existing five/six-argument source constructors. UI, provider and runtime
validation remains required when the later feature stages change those paths.

## Remaining obligations

The preceding independent-map runtime is not validated: the last focused run
completed 36 tests with four failures (binding fixtures, recording route budget,
and teleport completion). Its log is
`/tmp/nexus-independent-map-fixture-fixed.log`; it is not successful evidence.

The extended-map recipe, bounded sparse recording/storage, native presentations,
client/Observer changes, compass discovery alternative and map-only edge
indicators are still pending, along with their server/client/E2E/runtime tests.
The overall feature is incomplete. No merge or release has been performed.

## 2026-10-17: native recipe preflight

Implemented NexusMapRecipeInputs and NexusMapCraftingGuard. The server now
checks only actual crafting/cartography result slots, the full native paper or
glass recipe, current source/result components and MapId/UUID, result count,
and current lifecycle eligibility. Each native quick-move call is rechecked;
full inventories return without allocating map records. Ordinary inventory
slots and native cloning remain outside the result-specific restrictions.
Unregistered Nexus identity claims cannot fall through to vanilla processing,
and impossible legacy lineage is rejected before result-taking.

Validation:

- 129 JUnit tests passed with zero failures/errors/skips in
  `/tmp/nexus-map-crafting-fixed.log`. Its server portion failed and it did not
  reach `assemble`; that combined run is not successful build/runtime evidence.
- A separate `assemble` completed normally, exit 0, `BUILD SUCCESSFUL` in
  `/tmp/nexus-map-crafting-assemble.log`, reusing the unchanged compiled outputs.
- The current-source isolated run completed all 18 required server GameTests
  (17 new transaction tests plus the runner's existing smoke test), exit 0,
  `BUILD SUCCESSFUL`. Log: `/tmp/nexus-map-crafting-final.log`.
- Native coverage includes valid pickup, three successive crafts in one
  quick-move, scale and lock in cartography, empty/occupied hotbar behavior,
  dropping, creative cloning, source/preview forgery, stale ingredients,
  inventory no-ops without orphan MapIds, direct quick-move, and collect-all
  exclusion of result slots.
- Independent review found no preflight blocker and accepted server-native
  coverage for this backend-only change. Screen rendering, menu structure,
  network snapshots and provider formats did not change.

The baseline reproduced ordinary inventory deletion. Other early failures
were contaminated by a local test-directory configuration error: changing
Gradle's output directory did not change the game runner's working directory.
Its reused world had map IDs already present in the binding ledger. The local
init script now explicitly sets and cleans the isolated runner directory.
CI's normal build-directory configuration was not changed. Earlier failed runs
are retained as failed evidence; the preceding independent-map suite still
requires a fresh-world rerun before being accepted.

Two initial isolated test failures were corrected against native behavior:
occupied hotbar slots cannot swap into crafting result slots, and setting a
cartography result triggers immediate preview recomputation. The final tests
cover the native no-op and mutate a preview in place to exercise a genuine
stale result.

This completes preflight only. Full postprocess commit failure rollback and
player-accessible expansion beyond scale four remain pending with the storage
and presentation stages. The overall transaction feature is not complete.

## 2026-10-17: fresh-world full server validation (not yet accepted)

The full server suite in an explicitly cleaned private game directory completed
144 required tests: 142 passed and two failed. Gradle exited normally with code
1. Log: `/tmp/nexus-map-full-clean.log`. The failures are
`previouslyIssuedValidNexusMapStartsAndCompletesServerAuthoritativeTeleport`
and `destroyingOriginalLodestoneDuringMapTeleportDoesNotCancelIt`; neither is
accepted as successful evidence. Binding creation now succeeds in the fresh
world, distinguishing these failures from the earlier reused-world collision.

Inspection found the teleport helper's destination array extended beyond the
default 8-cube template. Seven map teleport cases now use an explicit
24 x 12 x 16 all-air template containing both arrays and the map anchor.
A GameTest-only message probe records at most eight Nexus translation keys
for each of at most 64 explicitly watched players. It captures no message
arguments and does not alter packets or messages. Success assertions remain
unchanged. Independent review found no blocker in this diagnostic-only delta;
runtime verification is pending in `/tmp/nexus-map-fixture-diagnostic.log`.
This fixture defect alone is not proof of the two failures' cause.

The focused runtime subsequently reported all 36 required tests passed,
including both previously failing map positives (22:15:23). The fixture loaded
successfully. Normal process exit is still pending: a thread dump shows the
server waiting in `ChunkMap.saveAllChunks` on a CompletableFuture during
shutdown (`/tmp/nexus-map-fixture-shutdown-threads.txt`). This is not yet a
successful Gradle result. The full 144-test suite has not been rerun after the
fixture correction. The existing test process is retained for continuation.

## 2026-10-17: independent-map verification closeout

The focused run exited normally with code 0, `BUILD SUCCESSFUL in 8m 36s`.
All 36 required map-related server tests passed. Its shutdown completed after
chunk saving; the prior waiting state was not treated as a successful exit.

A subsequent clean-world full run on unchanged production/test source passed
all 144 required server GameTests and exited normally with code 0 and
`BUILD SUCCESSFUL`. Log: `/tmp/nexus-map-full-fixture-fixed.log`. This includes
both map positives that failed with the out-of-bounds default test template,
the independent-map rejection cases and the original compass/recovery paths.
Only test geometry and bounded diagnostics changed to resolve those failures;
production success conditions and teleport assertions were not relaxed.

The independent reviewer rechecked the three production diffs and regressions:
only server-ledger-validated filled maps bypass original-lodestone liveness.
Held-item identity, actual destination permissions/status/coverage/cost checks,
compass liveness and live-origin expansion gates remain enforced. No blocker
was found. Prior impact/test-plan analysis and unchanged 129-unit-test,
18-native-preflight and assemble evidence remain scoped as documented above.
No Screen, wire format, Observer provider or shared API changed in this step.

This completes the bounded map-independence runtime step. It does not complete
extended recipes, postprocess rollback, sparse terrain, native/client/Observer
presentation or list-free compass-upgrade navigation. Nothing was merged,
pushed or published.

## 2026-10-17: atomic native SCALE/LOCK result-taking

Native cartography removes both inputs before invoking map postprocessing.
A controlled Nexus lifecycle refusal now aborts only the current native take
and restores its backing container cells, carried stack, crafting remove count
and crafted statistic. Server-only transaction scopes wrap native doClick,
direct/per-iteration quickMoveStack, and each safeTake in batch dropping.
Nested refusal returns the native empty/no-move result, retaining previous
successful crafts and already dropped items. Restoration deduplicates backing
container/index pairs and restores the result preview last before full resync.
Scope cleanup runs in finally. Unrelated callback exceptions still propagate;
this is not a promise to roll back arbitrary exceptions or external side effects.

Lifecycle derivation is prepared and validated without writes before allocating
the next MapId. Occupied IDs and exhausted allocator headroom are refused before
allocation. There is no expected DENIED branch after allocation/ledger commit.
Native quick-move also postprocesses a bookkeeping copy; the current iteration
reuses its already committed MapId for that copy, preventing duplicate bindings
and allocations. This reuse is restricted to exact level/source MapId/UUID/
processing in the active scope and never carries into a subsequent iteration.

Validation uses the unchanged private clean game directory and Core 0.7.23:

- Initial native run: 26 required tests, one failure in second quick-move refusal.
  It exposed native bookkeeping double-postprocessing. The failed run exited 1
  and remains failed evidence: `/tmp/nexus-map-atomic-runtime.log`.
- Final current-source `test runGameTest assemble` exited normally with code 0,
  `BUILD SUCCESSFUL`: `/tmp/nexus-map-atomic-fixed.log`.
- All 129 JUnit tests passed, zero failures/errors/skips.
- All 27 required native server tests passed, including late actual origin
  removal after cartography consumption; controlled SCALE/LOCK failures;
  pickup/swap/drop/clone/shift-click paths; direct quick-move; second-iteration
  quick-move and batch-drop failures; exact binding increments per craft;
  occupied/exhausted allocator; exception propagation and scope cleanup.
- Current source hashes matched the checkpoint throughout the final run.
- Impact/test_plan refreshed for the final changed files. Consumers' APIs,
  Screens, rendering, provider formats and networking did not change.
- Independent review caught the batch-drop transaction-boundary defect before
  acceptance; per-safeTake rollback and its second-drop regression resolved it.
  Final independent review accepted this bounded server transaction step.
  Existing server-native menu coverage is appropriate; no new GUI/provider/
  shader suite is implied by this backend-only delta.

Player-accessible expansion beyond scale four remains gated. Sparse terrain,
precise extended coverage, native/client/Observer presentations and list-free
compass-upgrade navigation remain pending. No merge, push or release occurred.

## 2026-10-17: standalone sparse disk page foundation

Added NexusSparseMapStore without changing existing SavedData, samplers,
teleport authorization, recipes, networking or Screens. It stores immutable
page values with 16 KB colors and a separate 2 KB exact coverage mask in a
fixed-length, versioned, key/revision/checksum-validated file. The storage owner
namespace is independent of item MapId; the future snapshot/index must manage
frozen sharing and COW explicitly. A color never grants known coverage here.

All IO runs on one worker with bounded pending operations and LRU pages (each
configurable up to 256). Deterministic flat filenames provide direct disk
lookup with no global resident terrain index. Persistent file metadata is
counted on startup/retry; damaged entries still count, and metadata errors
never guess free quota. Existing readable pages survive quota/initialization
failure. Writes use one bounded staging file, forced data and atomic rename;
post-commit durability uncertainty is explicit. Single canonical JVM ownership
and an OS lock prevent concurrent quota writers. Nonblocking close drains
accepted futures and releases ownership before signaling completion.

Validation:

- Initial focused compile failed because a test revision referenced refreshQuota
  before that source revision compiled. It is failed evidence only:
  `/tmp/nexus-sparse-foundation.log`. The subsequent focused run passed but
  predates final review corrections and is not final-source evidence.
- Final fixed-source `test assemble` exited 0, BUILD SUCCESSFUL in 1m 55s:
  `/tmp/nexus-sparse-foundation-final.log`.
- All 141 JUnit tests passed, zero failures/errors/skips, including 12 filesystem
  tests covering save/reopen, eviction/reload, independent coverage and array
  immutability, quota/recovery, corrupt and wrong-key/version/revision files,
  stale writes, retained old file/cache after failed writes, bounded queue,
  accepted-future shutdown/submit races, and exclusion of a second writer.
- Independent review found two P2 issues: failed writes could leave per-key
  empty directories, and release failure could skip channel close. Flat paths
  plus a 30-distinct-key failure regression fix the first; independent resource
  close attempts fix the second. Final review accepted the standalone scope.
- Final source hashes match the checkpoint. Impact and test_plan were refreshed;
  no runtime consumer or client/provider/shared API changed. This Java-only
  primitive is exercised by real filesystem tests; server integration, migration
  and cross-platform runtime validation are still pending.

This is storage foundation only. It does not migrate old exploration, implement
persistent lineage/COW indexes, change live map recording/coverage, or enable
extended recipes/UI. Existing active paths remain on their old storage. No
merge, push or release occurred.

## 2026-10-17: immutable sparse snapshot/index foundation

Added NexusImmutableMapIndex and immutable content-addressed blob batches to
NexusSparseMapStore. It does not change active game callers. The fixed-depth
quadtree validates actual world tiles, typed/versioned branch depth and terminal
tile identity. It distinguishes empty children from missing referenced data.
Immutable roots share old blobs; an update rewrites at most 19 branches and one
page, without walking history or copying terrain. Same-leaf stale revisions or
lost coverage are rejected before writing.

The existing store worker/lock/quota now also owns .nmb records. Blob reads
validate bounded length and full SHA-256 content. Complete-batch preflight
verifies existing disk content and deduplicates new-record quota before writes.
Partial IO commits remain counted and never yield a new root; identical retries
recover through existing records. Separate blob and namespace-page LRUs are each
bounded by the configured cache limit, and batches are at most 20 records/64 KB.
Index-level logical operations are bounded and drained before worker shutdown;
cancelling the public response cannot leak the logical operation permit.

Validation:

- Current-source `test assemble` exited normally with code 0, BUILD SUCCESSFUL
  in 1m 47s: `/tmp/nexus-immutable-index.log`.
- All 156 JUnit tests passed, zero failures/errors/skips. This includes 11 new
  index tests and 16 store tests (four new immutable-blob regressions).
- Runtime filesystem cases cover 1,000 forks without new files; eight retained
  historical roots with exactly 20 new records/update; branch/world-edge
  isolation; reopen from saved root values; capacity refusal before any batch
  writes; full-quota deduplication; deterministic partial-commit uncertainty
  followed by retry without double-counting; same-revision/lost-coverage
  refusals; missing/corrupt/wrong-depth/type/tile references; exact coverage;
  whole multi-stage close draining; cancelled response cleanup; defensive
  copies and corrupt cached-blob disk revalidation before deduplication.
- All four source hashes match the run-start checkpoint. Impact/test_plan
  refreshed. Existing production samplers, native recipe gates, SavedData,
  network/provider formats and Screens are unchanged.
- Independent review accepted the bounded immutable index/blob foundation
  after reviewing the current implementation and successful test/build result.

This step does not implement the native MapId/root registry, CAS publication,
orphan GC, legacy migration, actual recording/teleport integration, cross-platform
runtime, or player-accessible extended recipes. Roots are storage handles,
never an authorization substitute. No merge, push or release occurred.


## 2026-10-18: standalone native MapId/root registry and CAS

Added NexusMapRootRegistry and worker-owned flat .nmr registry IO to the sparse
store. Full-entry CAS publication prevents stale writers from replacing newer
roots. Versioned operation receipts and recomputed results support exact retries
without rollback. SCALE/LOCK storage forks atomically check source and target,
share immutable roots, and retain their frozen snapshot across source updates.
Rows validate native ID, unit identity, dimension, fixed center, extent, lock,
revision, bounded encoding and checksum. Metadata shares persisted quota with
page/blob records; corrupt rows are preserved and missing expected rows fail.

Current-source `test assemble` exited normally with code 0, BUILD SUCCESSFUL
in 2m 31s: `/tmp/nexus-root-registry.log`. All 168 JUnit tests passed with zero
failures/errors/skips, including 12 registry tests. The four implementation/test
hashes match `/tmp/nexus-root-registry-checkpoint.json`. Impact/test_plan refreshed;
no Screen, provider, network contract or production recording caller changed.

Filesystem tests exercise deterministic competing publications, counted losing
blocks, failure after immutable writes but before row replacement, post-rename
uncertainty and exact retry/reopen, frozen fork retry, malformed and missing rows,
identity refusal, shared quota, revision overflow, close draining and cancelled
responses. Required independent review accepted the frozen implementation after
receiving successful test/build evidence; no remaining blocker in this scope.

This is storage foundation only. Native-ledger authorization, authoritative
birth/migration markers, old exploration import, orphan GC and live integration
remain pending. Preserve current/previous/in-flight roots before reclamation.
No merge, push or release occurred.


## 2026-10-18: native-ledger PREPARED import intent

Ledger v4 adds a versioned optional import-intent list. A package-private
server-thread handoff fixes a ticket UUID, exact source binding, resolved
extent and native lock state. Repeated matching preparation returns the same
ticket; conflicting claims, extent or lock refuse without reset. Current old
recording and terrain storage remain active for every map, including newly
bound maps. No missing intent or registry row is treated as an empty-map proof.

The codec rejects duplicate bindings/intents, orphan/conflicting source proofs,
malformed intent fields, unsupported intent versions and future ledger versions.
Canonical legacy namespace copy retains intents; source compatibility constructors
and existing native binding/derivation behavior remain. There are no production
callers, import-completion states, activation or legacy-retirement APIs in this
step. An intent is identity metadata, not a frozen terrain snapshot or complete
terrain import receipt.

Current-source test/assemble exited normally code 0, BUILD SUCCESSFUL in 2m 32s:
`/tmp/nexus-import-intent.log`. All 177 JUnit tests passed with zero failures,
errors or skips. The nine new intent tests include old ledger versions, exact
retry/serialization, forged claims, changed source extent/lock, independent
native SCALE/LOCK handoffs, malformed/duplicate/orphan data, future ledger
version refusal and actual disk save/reopen through canonical legacy copy.
Both current source hashes match `/tmp/nexus-import-intent-checkpoint.json`.
The initial compile-only run failed generic inference; explicit codec builder
types fixed it before the successful final run. It is not passing evidence.

Impact/test_plan completed. Shared Core APIs, relay/provider contracts, Screens,
network formats and player-visible behavior are unchanged. Required independent
review accepted the current implementation and successful final validation;
no remaining blocker within PREPARED-only persistence scope.

Strict codec rejection alone does not establish fail-closed SavedData loading.
Canonical failure versus absence, legacy fallback, complete terrain import
manifest/receipt, activation and old-data retirement still need implementation
and validation. Retain legacy terrain and current/previous/in-flight/import roots.
No merge, push or release occurred.


## 2026-10-18: strict native binding-file loading

Added NexusMapBindingStorage and a binding-codec-only WrapMethod around native
SavedDataStorage.readSavedData. Both canonical and legacy binding names use
strict cache-miss reads; unrelated SavedData and native cache hits keep their
original behavior. NOFOLLOW metadata permits null only for initial confirmed
absence; symlinks/nonregular files, metadata errors and read errors refuse.
Native readTagFromDisk retains Minecraft version fixing and registry ops. Only
complete codec results are accepted, never resultOrPartial. Failures escape
outside the native catch before caching absence, so corrupt canonical data
cannot fallback to old legacy data, and corrupt legacy data cannot create an
empty canonical replacement. No failed read writes or repairs a file.

The first runtime run `/tmp/nexus-strict-loader.log` passed 177 JUnit tests but
failed two native corruption tests because Minecraft throws unchecked
ReportedNbtException for malformed NBT, which escaped without the uniform
refusal wrapper. This still refused loading, but did not meet the failure
contract. That process completed normally with exit 1; it is failed evidence.
The read-phase catch now normalizes IOException and RuntimeException while
leaving Error propagation intact.

The corrected current-source `test assemble runGameTest` run completed normally
with exit 0 and BUILD SUCCESSFUL in 5m 17s:
`/tmp/nexus-strict-loader-fixed.log`. All 177 JUnit tests passed with zero failures,
errors or skips. All nine required runtime tests passed: eight module-owned
storage cases and the Fabric smoke test. These exercise corrupt canonical and
legacy refusal, repeated get/computeIfAbsent, no cached absence, explicit repair,
normal cached reads after file removal, future and partial decode refusal,
actual canonical migration/reopen retaining the import ticket, directory and
dangling symlink refusal, a metadata/read disappearance race and unchanged
unrelated native SavedData policy. The runner finished saving chunks and shut
down normally; passing test lines alone were not treated as normal completion.

All six source/config hashes match `/tmp/nexus-strict-loader-checkpoint.json`.
Impact/test_plan completed and corrected source impact refreshed. Scoped tracked
whitespace checks are clean. Toolchain: Minecraft 26.3, Java 25, Core 0.7.23.
No shared Core API, Screen, provider, relay, network or terrain recording change
was made. Independent implementation review found no remaining code blocker;
final acceptance is recorded in the checkpoint after reviewing successful normal
runtime completion.

This does not distinguish entire ledger-file deletion before restart from first
migration; that still needs a separate durable witness/recovery contract. Terrain
import manifests/receipts, sparse activation and legacy retirement remain pending.
No merge, push or release occurred.


## 2026-10-18: canonical-file deletion witness foundation

Added NexusMapBindingWitness and the production strict-read deletion gate. An
explicitly established canonical sibling witness has fixed eight-byte magic and
version. Cold-read validation reads at most nine bytes and rejects partial,
corrupt, future-version or nonregular markers. Canonical absence with a witness
refuses before either valid legacy fallback or empty creation. Without a witness,
old-world bootstrap/migration remains compatible; native cache hits add no IO.

A pure metadata capture supplies the expected ledger fingerprint, including
import tickets. Binding/intent serialization is sorted by MapId so insertion
order does not change this fingerprint. Worker-only establishment strict-decodes
and verifies the frozen saved ledger, forces canonical plus namespace/data
directories, writes the marker using CREATE_NEW, forces marker/directories and
rechecks the same expected snapshot before acknowledgment. Valid existing markers
are confirmed on retry, never truncated. Post-create failures are uncertain
commits; partial/corrupt witnesses are retained and never silently repaired.

Current-source `test assemble runGameTest` completed normally exit 0 with
BUILD SUCCESSFUL in 4m 37s: `/tmp/nexus-binding-witness.log`. All 185 JUnit tests
passed, zero failures/errors/skips, including eight new witness tests. All 11
required native tests passed (ten Nexus storage cases plus Fabric smoke), and
the runner completed chunk-save/shutdown normally. The two new native cases
exercise fresh-storage restart after canonical deletion despite valid legacy,
no replacement writes, preserved marker/legacy bytes, successful witness/ticket
reload and refusal of a partial marker beside an intact canonical ledger.

Unit filesystem regressions cover semantic snapshot order, import-ticket
inclusion, immutable exact retry, missing/different snapshot refusal, missing
canonical refusal, partial/future marker preservation, directories/dangling
symlinks, post-create uncertainty and retry, and snapshot changes before ack.
All five current source hashes match `/tmp/nexus-binding-witness-checkpoint.json`.
Impact/test_plan completed. Independent final review accepted this bounded
foundation after successful normal runtime completion, with no remaining blocker.
No shared API, Screen, provider, relay, network or terrain recording changed.

There is deliberately no production establishment caller yet. The future single
world migration owner must freeze binding mutations, join prior native saves,
retain that freeze throughout the worker barrier and use a durably created world
root. Synchronizing explicit witness writers does not lock native async saves.
The witness is not a backup, authorization or complete terrain import receipt.
Pre-witness saves and deletion of both ledger and witness remain outside this
guard. Owner integration, terrain import/completion and sparse activation are
still pending. No merge, push or release occurred.


## 2026-10-18: explicit native metadata migration owner

Added a lazy, single-storage NexusMapMigrationOwner. Explicit preparation freezes
binding mutations before native save scheduling, drains previously queued native
saves asynchronously, then establishes the witness on a bounded worker. The
worker forces the world directory before the existing canonical/directory barrier.
A held Lease keeps metadata frozen; callers must check its validity before any
future migration work. No automatic preparation or terrain activation is wired.

Native snapshot epochs are transient and removed before disk serialization.
Retired or sealed snapshots cannot overwrite the witnessed canonical ledger,
including after Lease release. Canonical replacement, stale prepared commits and
map allocation during the freeze refuse. Cancellation, synchronous/future save
failure, wrong-thread close and storage shutdown have explicit cleanup paths;
normal preparation does not join IO on the server thread. Native shutdown alone
may wait for the drained worker, without a server callback dependency.

Exact frozen-source `test assemble runGameTest` completed normally exit 0 with
BUILD SUCCESSFUL in 3m 57s: `/tmp/nexus-migration-owner-verified.log`. All 187
JUnit tests passed, zero failures/errors/skips; unchanged unit inputs reused their
successful Gradle result. All 47 required native tests passed: ten storage,
nine migration owner, 27 crafting transaction and one Fabric smoke. The runner
saved chunks and shut down normally. All nine source/config hashes match
`/tmp/nexus-migration-owner-checkpoint.json`.

Coverage includes pending native-save ordering, an older queued native snapshot,
held/retired snapshot exclusion, cancellation and retry, save failure and retry,
storage-close drain, Lease cleanup and recipe/material conservation without map
ID allocation while frozen. The unrelated corrupt-NBT error in the log is the
intentional unrelated-storage fallback regression. The first run failed because
nested GameTest tick registration mutated the native task map; fixtures now use
one initial callback and their own bounded watch list. Intermediate passing runs
are retained as discovery evidence, not final-source acceptance.

Impact/test_plan and independent final review completed without a remaining
blocker. Acceptance covers metadata ownership only. No shared API, production
Screen, Observer provider, relay or network contract changed. Terrain recording
freeze/snapshot manifests, full legacy import/receipts and the activation
controller remain pending, followed by the extended presentation and navigation
work. No merge, push or release occurred.


## 2026-10-18: incremental in-memory legacy detail snapshot

Added an explicit package-private snapshot cursor to NexusMapDetailSavedData.
Authoritative initialize/derive/record operations refuse mutation while held;
existing reads and rebuildable LOD work remain available. Each owner-thread step
copies at most eight 16 KiB pages or bounded map indexes. The completed immutable
snapshot retains page IDs, shared references, historical pages, empty indexes
and orphan pages. Pixel copies are exposed as independent read-only buffers.
No partial result is publishable; malformed references, cancellation and explicit
close release the exact freeze without changing terrain or the legacy codec.
Old closed cursors cannot release a later capture.

The seven new focused JUnit tests passed in 2m 37s, exit 0:
`/tmp/nexus-terrain-snapshot-unit.log`. Final exact six-source
`test assemble runGameTest` passed normally exit 0 in 9m 12s:
`/tmp/nexus-terrain-snapshot-verified.log`. All 194 JUnit tests passed, zero
failures/errors/skips, and all 48 required native tests passed. The new native
regression captures with step(1) across ticks, checks no map ID consumption or
legacy codec changes during freeze, and verifies real post-release COW leaves
both the derived map and completed snapshot unchanged. Existing storage, owner
and crafting transaction regressions also passed.

Shutdown took longer than prior runs. A read-only thread diagnostic at
`/tmp/nexus-terrain-shutdown-threads.txt` showed native ChunkMap.saveAllChunks
waiting for RegionFile IO in pwrite, not the migration lock. The same process
subsequently saved all dimensions and shut down normally. No services or tests
were restarted to bypass this wait. The initial sandbox launch could not lock
the existing Gradle cache; the authorized rerun used the unchanged toolchain.
All six frozen source/config SHA-256 values match
`/tmp/nexus-terrain-snapshot-checkpoint.json`. Impact/test_plan completed and
independent final review accepted this bounded implementation without blockers.
Changes are internal to Nexus; shared Core API, death-node lifecycle, EventBus,
production UI, Observer providers and network contracts remain unchanged.

This is incremental CPU work, not a low-total-memory or complete migration
solution: the maximum 8192 pages require up to another 128 MiB of pixel memory
plus metadata. More than 8192 map indexes explicitly refuse admission. No
production caller is wired. Strict legacy terrain loading, metadata Lease
integration, native base-map capture, durable manifests, full import receipts
and activation remain pending. No merge, push or release occurred.


## 2026-10-18: strict legacy terrain loads and gameplay fault containment

The production native cache-miss wrapper now handles legacy detail CODEC reads.
Only initial metadata absence permits bootstrap. Nonregular files, dangling
symlinks, malformed NBT, future versions, partial decoding and read races refuse
without caching absence or writing replacement data. Native data fixing and
registry contexts remain unchanged. The detail constructor rejects duplicate
page/map IDs, missing/repeated page refs and ambiguous fine tiles within a map.
Valid old format (including absent optional version), shared/history/orphan pages
and empty indexes retain their data and existing codec version.

Independent review identified a P2 blocker: strict refusal from the unconditional
END_SERVER_TICK terrain load could stop the whole server. The implementation now
uses a dedicated ReadFailure and per-storage sticky gameplay availability state.
Only this failure type is contained and logged once. Sampling, resolution builds
and detail delivery stop; no substitute SavedData or repeated tick-time IO is
created. Repair requires reopening the world for gameplay recovery; direct
strict reads remain retryable. Creation/SCALE/LOCK preflight checks availability
and freeze state before materials/MapId/binding commit, and postprocessing reuses
the preflight terrain instance. Unrelated exceptions still propagate.

Final exact twelve-source `test assemble runGameTest` passed normally exit 0:
`/tmp/nexus-strict-terrain-contained.log`, BUILD SUCCESSFUL in 4m 27s. All 202
JUnit tests passed, zero failures/errors/skips. All 56 required native tests
passed: seven terrain storage, ten binding storage, nine migration owner, one
incremental snapshot, 28 crafting and one Fabric smoke. All dimensions saved and
the same server process shut down normally. All twelve source/config hashes,
Core JAR identity and temporary init script are recorded in
`/tmp/nexus-strict-terrain-checkpoint.json`.

The native cases exercise repeated corrupt get/computeIfAbsent, byte preservation,
external repair visibility, cache-hit behavior, old-format native save/reload and
snapshot, future/partial/duplicate/dangling data, nonregular paths and read races.
Gameplay cases verify one read/error state across 40 repeated calls, no empty
replacement, sticky refusal through repair until reopen, recovery on reopen and
propagation of unrelated exceptions. A real crafting-menu regression checks
preserved recipe/carried stack and zero MapId allocation on unavailable terrain.
The intended gameplay failure emits exactly one terrain-disabled error log.

Validation history matters: `/tmp/nexus-strict-terrain-verified.log` failed before
native cases because it read an old ambiguous detail file from the repository's
build/run/gameTest world. Inspection of Loom 1.17.12 AbstractRunTask.exec showed
it overrides JavaExec.workingDir from its internal RunConfiguration directory.
The old init script's printed /tmp working directory was therefore not proof of
actual isolation; prior passed case evidence remains valid, but its assumed
fresh-world status is withdrawn. The new init configures
loom.runs.gameTest.runDirectory, and --info confirms actual Java process cwd
`/tmp/nexus-strict-terrain-run` after deleteGameTestRunDir. An intermediate init
logger error was corrected before launch. The next isolated run passed 53 native
cases in 2m 53s before containment edits, so it is intermediate evidence only.
No corrupt source files were deleted or repaired to obtain success.

Impact/test_plan completed for the final source delta. Independent final review
accepted strict loading and P2 containment without remaining blockers. Shared
Core APIs, death-node/EventBus contracts, production Screens, semantic Observer
providers and packet formats are unchanged. Complete source-presence proof,
native base-map capture, metadata-owned full manifests, import receipts and
activation remain pending. No merge, push, version change or release occurred.


## 2026-10-18: metadata-owned single-map T0 source capture

A held migration Lease now admits one child capture for an existing PREPARED
ticket. Cache-only access reads the native map from the server-global storage
and detail from the owner's canonical storage; it never calls get/computeIfAbsent
or performs native disk reads on capture. Missing caches/tickets/bindings,
mismatched geometry/scale/lock and quarantined detail refuse before freezing.
The native 16 KiB pixel copy and detail freeze are consecutive on the server
thread, establishing a consistent T0 view. Detail copying remains bounded to
1..8 units per step through the existing immutable snapshot cursor.

Every step/result verifies the parent Lease, cached native/detail identities and
terrain health. Replacement/invalidation releases the child and refuses further
publication. Partial captures cannot return results. Child cancellation leaves
metadata held; parent Lease close/storage shutdown reclaims the child before
releasing metadata. Wrong-thread calls cannot advance, publish or release it,
and an old closed child cannot release a newer capture. Snapshot pixels remain
immutable while native terrain continues changing after T0.

Final exact seven-source `test assemble runGameTest` completed normally exit 0:
`/tmp/nexus-source-capture-final.log`, BUILD SUCCESSFUL in 3m 26s. All 205 JUnit
tests passed, zero failures/errors/skips, and all 68 required native tests passed.
The twelve new native source-capture cases cover cross-tick T0 isolation,
cancel/retry, parent/storage cleanup, missing ticket/binding/native/detail cache,
mismatch, quarantine, cached source replacement, wrong-thread refusal and parent
invalidation by a rejected native write. Three new unit cases check immutable
native bytes and ticket/base identity, geometry, scale, lock/dimension consistency
and missing components. All dimensions saved and the same server shut down
normally. Actual Java cwd was the isolated /tmp/nexus-strict-terrain-run world.

The initial run `/tmp/nexus-source-capture-verified.log` failed at
compileGametestJava: two Runnable callbacks could not propagate checked cleanup
exceptions. The fixture callback now uses its existing checked Action interface.
This was a test-only correction; the successful unit inputs remained unchanged.
No production source changed after its initial freeze. All seven final source
hashes, Core dependency and init-script hashes are retained in
`/tmp/nexus-source-capture-checkpoint.json`. Final impact/test_plan and independent
review completed without a remaining blocker. The internal cache accessor changes
no shared Core API, production Screen, Observer provider, packet layout, relay,
death-node or EventBus contract.

Acceptance covers one map's in-memory T0 terrain source only. The full detail
copy can still require another 128 MiB plus metadata. A controller must strictly
stage/verify cold native sources; capture does not turn absent data into an empty
source. It is not all-world source-presence proof, native disk provenance, a
marker snapshot, durable import receipt or live activation authority. Future
cutover must freeze or reconcile native pixels recorded after T0, establish full
source inventory/manifests and verify import receipts before enabling sparse
storage. No production flow automatically starts capture. No merge, push,
version change or release occurred.


## 2026-10-18: Lease-owned read-only disk source observation

A completed T0 capture can now request strict native-map and legacy-detail disk
verification on the existing bounded owner worker. Source paths and DataFixer
descriptors come from the owning native storage. No disk decode or file hashing
runs on the server thread. Readiness carries no public evidence; the checked
server-thread accessor revalidates the parent Lease, source cache identities and
terrain health before exposing the private observation.

Native files are limited to 4 MiB raw and 8 MiB NBT heap; detail files and NBT
heap are each limited to 256 MiB. Missing/non-regular sources, malformed or future
versions, invalid codecs, native pixel length/scale errors, gzip truncation/footer
errors and quota violations refuse without source creation, repair or rewriting.
Canonical semantic fingerprints cover T0 base pixels and all detail pages,
including historical/orphan pages and empty map indexes.

Independent review identified a P2 A-to-B-to-A race in separate raw-hash and
decode reads. Before validation, hashing was bound to the exact bounded raw
stream used for decoding, drained to complete EOF and compared with a later
whole-file fingerprint. A controlled regression now rejects swapped decoded
bytes even when the path is restored after decoding. The resulting observation
is not a durable source receipt or proof that files can never change later.

Cancelling readiness or closing child/parent invalidates publication immediately.
A new session cannot start until the prior worker has actually drained. Storage
shutdown waits for that drain without requiring a server-thread callback. Six
new native cases cover background-only execution, missing files, cancellation
and retry admission, shutdown drain, post-read cache replacement and later
native terrain refusing a T0 match.

Final exact six-source `test assemble runGameTest` completed normally with exit 0:
`/tmp/nexus-disk-source-final.log`, BUILD SUCCESSFUL in 3m 22s. All 216 JUnit tests
passed with zero failures/errors/skips; all 74 required native tests passed. The
11 new disk-source unit cases also passed separately in 1m 57s in
`/tmp/nexus-disk-source-unit.log`. All dimensions saved and the same server shut
down normally. The actual Java process cwd was `/tmp/nexus-strict-terrain-run`.
All six final source hashes, Core 0.7.23 JAR and isolated-run init-script hashes
matched the frozen checkpoint `/tmp/nexus-disk-source-checkpoint.json`.

Impact and test_plan completed. This delta changes no shared Core API, production
Screen, Observer provider, packet layout, relay, death-node or EventBus contract.
Acceptance remains limited to a read-only source observation under a live Lease.
Full source inventory, fsync-backed immutable manifests, import receipts and
cutover reconciliation/activation remain pending. No production flow automatically
starts capture. No merge, push, version change or release occurred.

Independent final review accepted exact-stream hash binding, T0 comparison and
cancel/drain lifecycle management with no remaining blocker, after the complete
frozen-source validation above. Review explicitly excludes durable receipts,
full source inventory, import, activation and release.


## 2026-10-18: standalone durable source-observation manifests

A new module-private, worker-only helper persists immutable per-ticket source
observations. The fixed 252-byte format binds ticket, MapId, canonical ledger
fingerprint, complete intent fingerprint and all four raw/semantic source hashes.
The canonical intent digest includes the unit, dimension, anchor XYZ, original
center, effective/resolved scales, lock state and ordered ancestry. A versioned
header, exact length, SHA-256 checksum and filename ticket are strictly checked
on cold reads; symlinks, non-regular files, corrupt/partial/future records refuse.

CREATE_NEW never overwrites a prior record. Complete publication forces the file
and containing directory and rereads the exact expected record. Existing exact
retries repeat the durability barrier without changing bytes. Conflicts refuse
before writes. Cancellation/failure after creation reports an uncertain commit:
a complete matching record can be retried, while partial files remain untouched
and fail closed. The owner must supply an already durably created dedicated
directory and serialize external writers. In-process helper publishers serialize
through the helper monitor. No filesystem-wide transaction is claimed.

The focused 12 new tests passed normally, exit 0, BUILD SUCCESSFUL in 2m 25s:
`/tmp/nexus-manifest-unit.log`. Final exact two-source `test assemble
compileGametestJava` passed normally, exit 0, BUILD SUCCESSFUL in 2m 1s:
`/tmp/nexus-manifest-final.log`. All 228 JUnit tests passed with zero failures,
errors or skips. Tests exercise full field binding, conflicting retries, damaged
and oversized data, valid-checksum unsupported/identity errors, filesystem kinds,
missing-directory refusal, injected durability boundaries, uncertain cancellation,
post-force replacement/deletion, and independent tickets. Final source, Core JAR
and init-script hashes matched `/tmp/nexus-source-manifest-checkpoint.json`.
OpenSpec strict validation and git diff --check passed. The first sandboxed
Gradle attempt could not open its existing cache lock; the authorized rerun used
the same offline dependencies without changing permissions or source inputs.

Impact/test_plan completed. This standalone helper has no production caller and
changes no shared API, packet, Screen/provider, relay, death-node or EventBus
contract. Existing native GameTest sources compile successfully; the preceding
74-case runtime evidence remains scoped to its preceding exact sources, and is
not new runtime coverage for the manifest. No client/UI/runtime behavior changed.
The manifest durably saves an observation, not original source bytes. Full source
inventory, Lease-owner integration, immutable source staging, import receipts,
cutover reconciliation and activation remain pending. No merge, push, version
change or release occurred.

Independent final review accepted the standalone manifest with no blocker after
checking immutable publication, strict checksum/identity reads, exact retry and
uncertain-commit handling. Acceptance retains the caller-owned durable-directory
and external-writer exclusion requirements and excludes owner integration, full
inventory, backups, import receipts and activation.


## 2026-10-18: metadata-Lease-owned source manifest publication

SourceCapture now explicitly publishes its checked disk observation through the
existing owner worker. The immutable expected manifest binds the held canonical
ledger fingerprint and complete import intent. The owner creates the canonical
namespace's nexus_map_sources directory without following/replacing conflicts
and forces the parent before invoking the existing file/directory force barrier.
No directory, file decode/write or force operation runs on the server thread.

Publication follows verification on the single worker. The latest pending IO
completion covers earlier queued work and blocks new owner sessions/captures
until actual drain. Duplicate requests reuse one Void readiness response; private
manifest evidence is returned only by a checked server-thread getter that
revalidates Lease, source cache identities and terrain health. Child/parent close
cancels readiness and invalidates access; failed retrieval closes the child while
retaining parent ownership. Storage close drains publication without a server
callback. Existing partial/conflicting records are preserved and refused.

The final pre-validation test-only hook adjustment moves waiting gates outside
the standalone publisher's global monitor, so another fixture's cleanup cannot
wait behind a gate that requires the server to tick. Cancellation/Lease checks
surround that worker hook and precede directory IO. Production hooks are no-ops.
No source edits occurred after the final frozen-source run began.

Final exact two-source `test assemble runGameTest -PnexusTestFocus=map-storage`
completed normally with exit 0: `/tmp/nexus-owner-manifest-final.log`, BUILD
SUCCESSFUL in 4m 37s. All 228 JUnit tests passed with zero failures/errors/skips;
all 81 required native tests passed. Seven new native cases cover worker-only
publication and checked cold-read evidence, cancel/drain admission, shutdown
drain, partial-file preservation, invalid directory refusal, cache replacement
and uncertain publication failure. The existing wrong-thread case now checks
persistManifest and manifest access too. Actual Java cwd was the isolated
`/tmp/nexus-strict-terrain-run`; all dimensions saved and the same server shut
down normally. Both changed-source hashes, unchanged standalone manifest and
verifier dependency hashes, Core JAR and init-script hashes matched
`/tmp/nexus-owner-manifest-checkpoint.json`. OpenSpec strict and diff checks passed.

Impact/test_plan completed. No shared Core API, production Screen, Observer
provider, packet/relay layout, death-node or EventBus contract changed. This
remains an explicit foundation API; no production flow automatically starts it.
Acceptance is limited to ownership/lifecycle integration of observation records.
It does not make source bytes immutable, establish a persistent known-manifest
deletion guard, prove full source inventory, import terrain, create completion
receipts or activate sparse storage. Those source-staging/inventory/cutover
obligations remain pending. No merge, push, version change or release occurred.

Independent final review accepted bounded owner manifest integration without a
blocker after frozen-source validation and normal shutdown. Acceptance covers
publication, checked retrieval, cancellation and shutdown drain, and excludes
full inventory, source staging/backups, import receipts, activation and release.


## 2026-10-18: bounded held-source requirements inventory

A completed SourceCapture now exposes one incremental inventory cursor over the
exact metadata freeze and its already immutable full legacy detail snapshot.
Every bound map is retained with its complete binding and optional PREPARED
intent. Missing tickets remain explicit unprepared rows. The deduplicated native
source requirements include all bindings, ordered ancestry and all detail-map
indexes, including empty/unbound indexes. The entire existing detail snapshot,
including shared/historical/orphan pages, is retained without another pixel copy.

Each step visits 1..8 bindings (each at most 20 ancestors) or detail indexes.
Preflight bounds bindings/detail indexes to 8192; native source IDs are limited
to 65536. Duplicate/conflicting tickets, bad counts, invalid IDs and quota
failures refuse partial publication and close the child while retaining parent
ownership. Completed sorted collections transfer to unmodifiable wrappers without
a final full collection copy. No source load, filesystem operation, ticket
creation or metadata mutation occurs. Each step/result checks original child,
Lease, cache identity/health and exact freeze token; old cursors cannot resume
under a later freeze. Returned immutable data remains data, not live authority.

Final exact five-source `test assemble runGameTest -PnexusTestFocus=map-storage`
completed normally exit 0: `/tmp/nexus-inventory-final.log`, BUILD SUCCESSFUL in
4m 40s. All 239 JUnit tests passed with zero failures/errors/skips; all 86 required
native tests passed. Eleven new unit tests cover complete source requirements,
unprepared/empty/orphan retention, immutable output, per-step budgeting, empty
inventory, count/ticket/identity failures, quotas and owner/freeze invalidation.
Five new native cases cover all bindings including an uncached unprepared map,
parent/storage closure, cached native replacement and wrong-thread traversal;
the existing wrong-thread case also checks inventory creation. All dimensions
saved and the same server shut down normally. Actual Java cwd was the isolated
`/tmp/nexus-strict-terrain-run`. All five source hashes, Core JAR and init-script
hashes matched `/tmp/nexus-inventory-checkpoint.json`. No source edits followed
run start. OpenSpec strict and git diff --check passed.

Impact/test_plan completed. No shared Core API, production Screen, Observer
provider, packet/relay layout, death-node or EventBus contract changed. The
inventory is complete within held ledger/detail source requirements; it does
not scan unrelated native map files or prove required files physically exist.
Durable physical-source staging, complete inventory persistence, prepared tickets
for unprepared maps, import receipts and cutover reconciliation/activation remain
pending. No production flow automatically starts inventory. No merge, push,
version change or release occurred.

Independent final review accepted the bounded requirements inventory without a
blocker after frozen-source validation. Acceptance covers completeness within
the held requirements, incremental bounds, immutability and lifecycle checks;
it excludes physical file presence, full native-file inventory, staging, import
and activation.


## 2026-10-18: standalone immutable raw-source staging

A new worker-only helper streams native/detail raw files into kind-qualified
SHA-256 blobs with a constant 64 KiB buffer. Both the full opened-stream hash
and original size must match before publication; source growth is bounded while
reading. Per-file caps are 4 MiB native and 256 MiB detail. Existing blobs are
strictly reverified and forced without opening the original source, permitting
exact retries after original deletion. Corrupt blobs never fall back to or
rewrite original source bytes. The helper does not decode NBT or prove semantic
equality; strict staged-source verification remains a future caller obligation.

Publication uses an owned CREATE_NEW pending file, file force, CREATE_NEW hard
link to the final name, owned pending unlink, directory force and complete final
hash/size readback. Unsupported hard links fail closed; no replacing rename is
used. Failure/cancellation before publication removes only the owned pending
file when possible; potential publication reports UncertainCommit and retains
the final name for exact retry. No final blob is repaired or deleted. Caller
must own an already durable dedicated directory and exclude external writers.

Persistent file-count/byte quotas scan existing final and abandoned pending
names; unexpected/non-regular entries refuse. Independent review identified P2:
one-name reservation could leave two counted hard links after a crash. The
final correction reserves two names and twice the source size, with streaming
limited to half remaining byte capacity. The added same-inode restart fixture
counts both names, preserves orphan files, rejects insufficient cold-call quota
and permits exact existing retry without the original. New creation requires
two free name slots; completed success leaves one.

The preliminary pre-quota-correction run passed 250 tests and build in 2m 19s,
exit 0, retained as `/tmp/nexus-staging-pre-quota.log`; it is not final acceptance.
Final exact two-source `test assemble compileGametestJava` passed normally
exit 0: `/tmp/nexus-staging-final.log`, BUILD SUCCESSFUL in 2m 15s. All 251 JUnit
tests passed with zero failures/errors/skips, including twelve new staging
cases for exact byte retention/retry, changed source streams, persistent quota,
corruption refusal, durability boundaries, cancellation, conflicting hard link,
filesystem kinds, size limits and same-inode crash recovery. Assemble passed;
existing native test sources remained successfully compiled/up-to-date. Final
source, Core JAR and init-script hashes matched `/tmp/nexus-staging-checkpoint.json`.
No source edits followed final run start. OpenSpec strict and diff checks passed.

Impact/test_plan completed. No shared Core API, production Screen/provider,
packet/relay, death-node or EventBus contract changed. The previous 86-case
native runtime evidence remains scoped to preceding exact sources; no new
runtime integration is claimed for this standalone helper. Owner/inventory
integration, strict semantic staged-source checks, durable complete physical
inventory, known-blob deletion protection, import receipts and activation remain
pending. Abandoned pending files remain quota charges until future bounded
reclamation. No production flow calls staging. No merge, push, version change
or release occurred.

Independent final review accepted P2-corrected standalone raw staging with no
remaining blocker after frozen-source validation. Acceptance covers quota,
no-overwrite publication, exact retry and failure handling, and excludes owner/
inventory integration, semantic decode, full source verification, import and
activation. No new native runtime pass is asserted.


## 2026-10-18: Lease-owned staged source pair and semantic verification

A SourceCapture with a checked persisted observation manifest can now explicitly
request native/detail staging. Immutable descriptors and expected raw hashes are
captured on the server thread. The existing owner worker creates and durably
parents the canonical namespace's nexus_map_source_blobs directory, stages each
exact raw source, and then strictly decodes BOTH saved blobs using their original
DataFixers/registry context and immutable T0 snapshot. The complete raw/semantic
observation must equal the held observation before pair evidence is stored.
World limits are 65536 counted names and 1 GiB including conservative pending/
publication reservations; per-file limits remain 4/256 MiB. No blocking source
IO, copying, hashing, decoding or force runs on the server thread.

Readiness is Void and duplicate requests reuse it. The private immutable pair
result is accessible only through the server-thread checked getter. Closed or
invalid parent/child, replaced caches or quarantined terrain refuse access.
Child/parent close cancels readiness and releases freezes; later sessions wait
for actual pending worker drain. Storage shutdown drains without requiring a
server callback. A partial or uncertain failure never exposes a complete pair.
Published blobs remain available when the other source fails; rollback cannot
delete potentially shared immutable bytes. Checked failure retrieval closes only
the child while retaining parent ownership. Test gates remain outside the global
staging publication monitor.

Final exact two-source `test assemble runGameTest -PnexusTestFocus=map-storage`
completed normally exit 0: `/tmp/nexus-staged-owner-final.log`, BUILD SUCCESSFUL
in 5m 13s. All 251 JUnit tests passed with zero failures/errors/skips; all 93
required native tests passed. Seven new native cases cover worker-only paired
staging/strict decode and saved-source survival after original deletion, cancel
admission/drain, shutdown, changed originals, missing-detail partial failure,
cache replacement and uncertain publication. Wrong-thread coverage now includes
staging request and evidence retrieval. The same server saved its world and
shut down normally. Actual Java cwd was `/tmp/nexus-strict-terrain-run`. Both
changed source hashes, staging/verifier/manifest dependency hashes, Core JAR and
init-script hashes matched `/tmp/nexus-staged-owner-checkpoint.json`. No source
edits followed run start. OpenSpec strict and diff checks passed.

Impact/test_plan completed. No shared Core API, production Screen/provider,
packet/relay, death-node or EventBus contract changed. Independent final review
accepted bounded SourceCapture pair staging, semantic revalidation and lifecycle
integration with no blocker after frozen-source validation. Acceptance covers
one captured native map plus the full frozen detail store; it does not stage
all remaining ancestor/unbound native IDs or produce a complete physical
inventory/receipt. Cold recovery, full-inventory publication, source-update
freeze/reconciliation, import and activation remain pending. Previously returned
pair data does not authorize skipping physical revalidation at later import.
There is no automatic production entry point. No merge, push, version change
or release occurred.


## 2026-10-18: strict cold native source observation

The verifier now observes one uncached native map without requiring a ticket,
metadata cache or detail file. It returns an immutable 16 KiB Base copy plus
exact opened-stream raw and canonical terrain hashes. Strict native decode is
shared with the existing pair verifier; mandatory pixel length, explicit scale,
full codec result, original DataFixer/version and 4 MiB raw/8 MiB NBT limits
remain intact. Cold observation also refuses centers outside the supported
world domain. Final path hashing must match the exact bytes actually decoded,
so changed/deleted input and the controlled swap/restore race refuse. Nothing
is loaded into native storage, created, repaired or written.

Final exact two-source `test assemble runGameTest -PnexusTestFocus=map-storage`
completed normally exit 0: `/tmp/nexus-cold-native-final.log`, BUILD SUCCESSFUL
in 4m 4s. All 259 JUnit tests passed with zero failures/errors/skips; all 93
required native tests passed. Eight new unit cases cover independent cold
observation/immutable pixels, compressed/plain inputs and caller-supplied ID,
missing/corrupt/future/non-regular files, pixels/scale/codec/finite centers, quota
and gzip footer, post-decode mutation/deletion, exact-stream swap/restore refusal
and cancellation. Existing native tests were rerun because native decode was
shared/extracted, preserving pair staging/runtime behavior. The same server
saved its world and shut down normally; actual Java cwd was the isolated
`/tmp/nexus-strict-terrain-run`. Both changed-source hashes, Core JAR and init
hashes matched `/tmp/nexus-cold-native-checkpoint.json`. No source edits followed
run start. OpenSpec strict and diff checks passed.

Impact/test_plan completed. No shared Core API, production Screen/provider,
packet/relay, death-node or EventBus contract changed. Native raw bytes do not
embed their MapId; the future controller must provide the trusted canonical
inventory descriptor. The observation does not prove binding/ancestry, a common
T0 across multiple maps, durable staging, complete physical inventory, import or
activation. All-inventory caller integration and relationship/source-update
reconciliation remain pending. No automatic production caller, merge, push,
version change or release was added.

Independent final review accepted bounded cold native observation and shared
decoder changes without a blocker after frozen-source validation. Acceptance
retains trusted caller MapId requirements and excludes relationship/common-T0
proof, fsync, staging, complete inventory, import and activation.


## 2026-10-18: standalone complete native-inventory batch staging

A new worker-only helper saves the native portion of an immutable requirements
inventory. Preflight requires exact descriptor ID coverage, bounded count,
non-null values and unique normalized source paths, then rechecks the selected
sorted copy. Each file is strictly cold-observed, validated against any binding/
prepared scale and lock, durably staged by exact raw hash under existing quota,
and strictly observed again from the saved bytes. Raw and semantic hashes must
match. Unprepared bindings and unbound/empty detail indexes are not omitted.

Completed results retain only bounded metadata (ID, center/dimension, native
scale/lock, blob descriptor and semantic fingerprint), the held metadata hash
and explicit unprepared set. Dimension strings are limited to 256 characters.
No pixel arrays accumulate in the result. Ordered ancestry checks center,
dimension and expected native scale; bound ancestors additionally require the
same unit/anchor and exact resolved logical scale. A scale-four clamp cannot
hide conflicting logical levels. The completed sorted map transfers ownership
to an immutable wrapper without a final full copy.

Missing/extra descriptors, corrupt/changed originals, saved-file corruption,
binding/ancestry conflicts, quota failure and cancellation expose no partial
batch. Published blobs remain in place for retry/recovery; original files are
never repaired. Exact calls can reuse existing valid saved blobs, and saved
bytes remain verifiable after originals are deleted. A new batch still observes
original files first; recovery directly from a persisted complete batch receipt
is not implemented by this helper.

Final exact two-source `test assemble compileGametestJava` passed normally exit
0: `/tmp/nexus-native-batch-final.log`, BUILD SUCCESSFUL in 2m 13s. All 269 JUnit
tests passed with zero failures/errors/skips, including ten new batch cases
covering complete/unprepared/orphan retention, immutable metadata, exact input
coverage, missing/corrupt sources, binding and lineage geometry, clamped logical
scale/unit conflicts, changed observed bytes, saved corruption, cancellation,
empty inventory and quota refusal. Assemble and native test source compilation
passed. Source, verifier/staging/inventory dependency, Core JAR and init-script
hashes matched `/tmp/nexus-native-batch-checkpoint.json`. No source edits followed
run start. OpenSpec strict and git diff --check passed.

Impact/test_plan completed. No shared Core API, production Screen/provider,
packet/relay, death-node or EventBus contract changed. Prior 93-case native
runtime evidence remains scoped to the preceding exact sources; no new runtime
claim is made for this standalone helper. It does not stage the separate detail
source, establish a common T0, retain metadata ownership, publish a durable full
batch receipt or complete cold recovery. Canonical descriptors, frozen metadata,
already durable directory and external writer exclusion remain caller duties.
Owner/detail integration, complete inventory receipt, recording reconciliation,
import and activation remain pending. No automatic production caller, merge,
push, version change or release occurred.

Independent final review accepted standalone native batch staging without a
blocker after frozen-source validation. Acceptance excludes owner/detail
integration, common T0, durable batch receipts, cold recovery, import and
activation. No additional native runtime validation is asserted.


## 2026-10-18: held native-inventory batch joined to saved detail proof

SourceCapture now prepares the complete native descriptor set in 1..8 path-only
server-thread visits per call, after checked pair staging and completed inventory.
A private TreeMap transfers to the worker without a server-thread full copy.
The captured primary uses its immutable saved blob; remaining IDs use canonical
server-global native paths without get/computeIfAbsent/cache loads. Incomplete
preparation refuses staging, and construction failure releases the child.

The existing owner worker stages every required native source through the batch
helper, requires the held metadata hash and exact primary blob/semantic identity,
then strictly revalidates the saved primary/detail pair against the original
immutable snapshot and observation. Only a fully successful join publishes
private immutable StagedInventory data. Void readiness and duplicate memoization
prevent a future from carrying unchecked authority. Checked retrieval revalidates
parent/child/cache/health; failures close only the child. Pending IO ordering,
cancellation and storage shutdown drain retain ownership guarantees without
requiring a queued server callback. No rollback deletes published shared blobs.

Final exact two-source `test assemble runGameTest -PnexusTestFocus=map-storage`
completed normally exit 0: `/tmp/nexus-batch-owner-final.log`, BUILD SUCCESSFUL
in 3m 46s. All 269 JUnit tests passed with zero failures/errors/skips; all 99
required native tests passed. Six new native cases cover incremental preparation,
an uncached/unprepared extra map and saved-primary survival after original
deletion, worker-only staging, cancel/drain admission, shutdown, missing extra
sources, saved-detail corruption after native staging and primary cache
replacement. Wrong-thread coverage includes preparation, batch request and
result access. All dimensions saved and the same server shut down normally.
Actual Java cwd was `/tmp/nexus-strict-terrain-run`. Both changed-source hashes,
native-batch/verifier/staging dependencies, Core JAR and init-script hashes
matched `/tmp/nexus-batch-owner-checkpoint.json`. No source edits followed run
start. OpenSpec strict and diff checks passed.

Impact/test_plan completed. No shared Core API, production Screen/provider,
packet/relay, death-node or EventBus contract changed. Independent final review
accepted bounded held-native inventory/detail-proof integration without a
blocker after frozen-source validation. This is not a durable batch receipt or
cold recovery controller. Other native maps have separate disk observations,
not a shared T0 with primary/detail; global native-save drain and recording
freeze/reconciliation remain required before cutover. Unprepared rows remain
explicit and cannot activate without validated intents. No automatic production
entry point, import, live activation, merge, push, version change or release
was added.


## 2026-10-18: standalone durable saved-source inventory receipt

A new immutable receipt records the held primary ticket/manifest, ledger and
intent fingerprints, raw/semantic primary/detail hashes, detail blob length,
complete native metadata/blob rows and explicit unprepared IDs. Construction
checks primary/detail identity, finite native geometry/scales/dimensions, native
blob kinds and unprepared subsets. Capture from checked staging also verifies
held ledger and primary blob identity. Collections are defensively sorted/copied
on the worker; no pixel arrays or complete encoded payload buffers are retained.

The versioned streaming codec enforces 24 MiB file size, 65536 native rows and
8192 unprepared IDs. Strict cold reads check filename UUID, native/unprepared
ordering, booleans, dimensions, counts, all structural fields, body SHA-256,
exact trailer/EOF and observed length. CREATE_NEW publication never overwrites
or repairs a prior record. File and directory are forced, followed by strict
complete readback. Exact retries reverify/force without rewriting. Failures or
cancellation after possible creation are uncertain commits; partial/corrupt
records remain preserved and refused. This is not a known-record deletion guard.

The initial run failed compileTestJava because the test helper publish(Receipt)
shadowed statically imported overloads. Test-only calls were explicitly qualified
before the final re-freeze; production source and test assertions were unchanged.
The failed exit-1 log remains `/tmp/nexus-inventory-receipt-test-compile.log`; it
is not success evidence. Final exact two-source `test assemble compileGametestJava`
passed normally exit 0: `/tmp/nexus-inventory-receipt-final.log`, BUILD SUCCESSFUL
in 2m. All 281 JUnit tests passed with zero failures/errors/skips, including twelve
new receipt cases for canonical roundtrip, immutability, conflicting retry,
structural linkage, valid-checksum illegal payloads, truncation/oversize/trailing
data, injected durability boundaries, cancellation and filesystem conflicts.
Assemble and native source compilation passed. Both final source hashes, Core
JAR and init-script hashes matched `/tmp/nexus-inventory-receipt-checkpoint.json`.
No source edits followed final run start. OpenSpec strict and diff checks passed.

Impact/test_plan completed. No shared Core API, production Screen/provider,
packet/relay, death-node or EventBus contract changed. Independent final review
accepted bounded receipt codec/publication without a blocker after final
validation. Prior 99-case native runtime evidence remains scoped to preceding
exact sources; no runtime claim is made for this standalone record.

Receipt cold reads validate metadata, not referenced blob bytes, completeness
against a newly held ledger/detail inventory, or a common T0. Owner publication,
aggregate directory quota, deletion protection, strict physical-source recovery,
recording reconciliation, import and activation remain pending. Caller owns an
already durable dedicated directory and external writer exclusion. No automatic
production entry point, merge, push, version change or release occurred.


### Lease-owned complete saved-source receipt publication (2026-10-18)

SourceCapture now joins checked batch evidence to owner-worker-only receipt
publication. Every saved native blob is rehashed, strictly decoded and compared
with recorded raw/terrain hashes and geometry. The complete saved detail and
primary pair are revalidated against the held snapshot. A dedicated owner receipt
directory has 64-file/256-MiB aggregate limits and reserves 24 MiB for a new record;
partial records remain quota charges. Publication does not repair or delete them.

Nine new native regressions cover worker-only IO/readiness memoization/strict cold
receipt readback, cancellation and pending IO exclusion, storage close without a
server callback, missing saved native, corrupt saved detail, count quota, byte
reservation quota, cache replacement after publication, and uncertain publication
without held evidence. Existing wrong-thread coverage now includes publication
and its checked getter. Independent review found a missing test helper closing
brace before validation; it was fixed and the final source review accepted with
no remaining blocker. No production source change followed the final run start.

Frozen-source validation `/tmp/nexus-receipt-owner-final.log` exited 0 after
4m 9s: 281 JUnit tests passed with zero failures/errors/skips; 108 required native
GameTests passed (nine added). Test/assemble and native compilation passed. The
actual Java game working directory was `/tmp/nexus-strict-terrain-run`; normal
world save and shutdown completed. Source/dependency/Core JAR/init-script hashes
matched `/tmp/nexus-receipt-owner-checkpoint.json`. OpenSpec strict and diff checks
passed. Impact/test_plan completed; impacted consumers have no shared Core API,
Screen/provider, packet/relay, death-node or EventBus delta in this microtask.

This proves held saved-source publication under external writer exclusion, not
cold recovery, required-set reconciliation against newly held metadata, common
terrain T0, import or activation. No automatic gameplay caller, deletion guard,
reclamation, merge, push, version change or release was added. Next bounded task
is strict physical-source cold recovery from the durable inventory receipt.


### Strict standalone cold inventory verification (2026-10-18)

NexusMapInventoryRecovery strictly reopens a required saved-inventory receipt,
compares its ledger/native-ID-set/unprepared/primary-ticket identity to a freshly
held immutable inventory, and reuses staging binding/ordered-ancestor checks.
Every native source requires exact physical hash/length, strict decode, terrain
fingerprint and recorded geometry. The saved detail requires exact physical bytes
and full strict semantic agreement with held detail; the primary pair must
reproduce the complete manifest, including intent fingerprint. A private verified
result is returned only after all checks. Missing receipts/sources, corrupt data,
conflicts or cancellation fail without writes or original-source fallback.

Twelve new JUnit tests cover recovery after all originals are deleted; missing or
corrupt receipts/native/detail files; missing/extra inventory IDs and unprepared
state mismatch; ledger/ticket/intent identity; forged geometry/hash/length;
binding/ancestry mismatch; unreferenced detail-page divergence; hash-consistent
malformed native and detail; and cancellation without partial result. The shared
staging validator retains pre-stage checks and passes existing native coverage.

Frozen-source log `/tmp/nexus-inventory-recovery-final.log` exited 0 with BUILD
SUCCESSFUL in 3m 59s. All 293 JUnit tests passed with zero failures/errors/skips,
and all 108 required native GameTests passed. Test/assemble/native compilation
succeeded. The actual Java game working directory was the isolated
`/tmp/nexus-strict-terrain-run`; world save and shutdown completed normally.
All source/dependency/Core-JAR/init-script hashes matched
`/tmp/nexus-inventory-recovery-checkpoint.json`. Impact/test_plan completed; no
shared Core API, UI/Observer provider, packet/relay, death-node or EventBus surface
changed. OpenSpec strict and diff checks passed. Independent review acceptance
is recorded in the checkpoint after validation.

The result is point verification under freshly held immutable metadata/detail and
external writer exclusion. It is not a common terrain T0, restart controller,
terrain import or activation. Recording changes since backup still need explicit
reconciliation. Lease-owned cold recovery lifecycle/cancel/shutdown integration,
known-record deletion protection and reclamation remain pending. No automatic
gameplay entry point, version change, merge, push or publication occurred.


### Lease-owned explicit cold inventory recovery (2026-10-18)

SourceCapture now schedules strict cold inventory verification only after its
held detail and requirements inventory complete. A fresh idle capture is required:
observation/publication and recovery are exclusive branches. The worker captures
no server callbacks, returns Void readiness and retains private verified evidence;
server-thread retrieval rechecks Lease, child, cache identities and terrain health.
Cancellation/close/shutdown retain actual IO drain ownership; a new owner session
cannot overlap a cancelled gated recovery. Failure retrieval releases only the
child and never repairs or recreates a required saved receipt/source.

Six added native regressions cover worker-only recovery and absent-original-file
success with branch exclusivity, cancel/drain exclusion, callback-free storage
shutdown, missing receipt refusal, corrupt saved-detail refusal and completed
recovery cache replacement. Existing wrong-thread tests include both recovery
APIs. The success fixture releases the original publication capture and acquires
a fresh capture after actual drain, re-enumerating held detail and requirements;
this is not a process restart or automatic startup test.

`/tmp/nexus-recovery-owner-final.log` exited 0 with BUILD SUCCESSFUL in 3m 49s.
All 293 JUnit tests passed with zero failures/errors/skips, and all 114 required
native GameTests passed. Assemble and native compilation succeeded. Actual Java
game cwd was `/tmp/nexus-strict-terrain-run`; normal world save/shutdown completed.
All source/dependency/Core-JAR/init-script hashes match
`/tmp/nexus-recovery-owner-checkpoint.json`. No source edits followed run start.
OpenSpec strict and diff checks passed. Impact/test_plan and impacted-consumer
surface review found no changed Core API, production Screen/Observer provider,
packet/relay, death-node or EventBus contract. Final independent acceptance is
recorded in the checkpoint separately from test results.

Recovery remains an explicit API under an existing cache-only source capture,
held immutable requirements and external writer exclusion. Automatic startup,
uncached source bootstrap, common terrain T0/recording reconciliation, legacy
terrain import, activation and known-record deletion/reclamation are unimplemented.
The next migration obligation is recording freeze/snapshot reconciliation before
import. No merge, push, version change or release occurred.


### Selected-native recording hold and full-detail snapshot (2026-10-18)

Lease.captureFrozenSource now acquires an exclusive native map-object recording
token and full-detail hold consecutively on the server thread before the base
copy. The registered production mixin suppresses setColor/updateColor only while
that object is held. Ordinary captureSource retains native recording behavior.
The frozenSnapshot getter requires completed detail capture and the optional
native hold; every checked capture operation verifies the token and full selected
pixel equality against the immutable base. Direct array disagreement invalidates
the child and releases its holds. Existing Lease/cache/terrain-health checks apply.

Eight new native regressions cover blocked set/update and detail recording across
incremental steps with resumed recording after close; normal-mode compatibility;
direct pixel tampering; parent close; storage shutdown; detail-acquisition failure
rollback; token ownership/idempotence and wrong-thread refusal; and cache
replacement releasing the old object. Existing wrong-thread coverage includes
captureFrozenSource/frozenSnapshot. javap of configured Minecraft26.3 verified
both native method signatures before adding injections.

Frozen-source `/tmp/nexus-terrain-freeze-final.log` exited 0 with BUILD SUCCESSFUL
in 3m 47s. All 293 JUnit tests passed with zero failures/errors/skips; all 122
required native GameTests passed (eight added). Assemble/native compilation and
actual dedicated-server mixin loading passed. Actual Java game cwd was isolated
`/tmp/nexus-strict-terrain-run`; normal world save/shutdown completed. All five
changed source/configuration hashes, dependencies, Core JAR and build init matched
`/tmp/nexus-terrain-freeze-checkpoint.json`. No source edits followed run start.
OpenSpec strict and diff checks passed. Impact/test_plan and consumer surface
review found no changed Core API, production Screen/Observer provider, packet,
relay, death-node or EventBus contract. Final independent acceptance is recorded
in the checkpoint separately from actual test results.

This hold covers selected native terrain and the complete detail snapshot; other
native inventory sources remain unfrozen. Public colors-array writes remain an
external-writer exclusion obligation: checked access detects disagreement, not
transient ABA history. Full native-inventory recording ownership and saved-source
reconciliation still gate common T0/import/activation. No automatic gameplay
caller, startup recovery, merge, push, version change or release was added.


### Production server expansion pipeline; framework integration deferred (2026-10-18)

The user explicitly prioritized necessary playable behavior over additional
migration/recovery infrastructure. Production expansion now uses persisted logical
extent and the existing strict atomic detail SavedData without requiring a new
store migration. Both actual workbench and cartography previews/takes support
scale four to five and beyond, retain the exact original center, and stop only
when the supported coordinate domain is covered. Native terrain remains at its
true scale-four coordinates; higher logical extent does not stretch vanilla
pixels or create false painted coverage. Historical native bases including scale
four and existing fine pages survive derivation with copy-on-write ownership.

Loaded-only route sampling now uses the validated logical extent for extended
maps, retaining existing deadline/read/work limits and ordinary-map admission.
Fine exploration may continue outside the native footprint within logical/world
bounds. The per-map allowance shares the existing8192-page world quota; quota
exhaustion preserves old exploration and refuses derivation before consumption.
A pure conservative capacity preflight reserves ancestry initialization and the
new historical snapshot. This is bounded exploration storage, not unlimited disk
capacity. Capacity feedback localization remains pending for feature completion.

Six new native regressions cover actual cartography4-to7 plus native pixels/fine
terrain/codec reload/source preservation; actual workbench preview and take with
no forged success preview; extended lock and COW isolation; repeated expansion
to supported-world coverage plus origin-removal refusal; loaded route sampling
outside vanilla footprint and logical bounds; and exactly-last-history-page
success followed by full-capacity refusal with real stale-preview QUICK_MOVE,
unchanged inputs/cursor/inventory/MapId/ledger. No test claims a full process
restart: persistence checks roundtrip the production codecs, while existing
storage/native lifecycle regressions provide their own scoped coverage.

Initial frozen run `/tmp/nexus-production-expansion-final.log` exited0 after
3m55s with128native tests passed, but review identified that the quota stale
preview needed an explicit nonempty/SCALE precondition. Adding that precondition
correctly failed `/tmp/nexus-production-expansion-verified.log` (exit1): Slot.set
recalculated and cleared the test fixture. It was not counted as success.
The fixture now injects only backing ResultContainer.setItem, asserts nonempty
SCALE output, then uses actual menu QUICK_MOVE. All conservation assertions remain.
Production sources were unchanged between these runs.

Final frozen-source `/tmp/nexus-production-expansion-accepted.log` exited0 with
BUILD SUCCESSFUL in3m37s: all163requirednative tests passed (128storage/crafting
plus35existing recording/resolution/interface lifecycle). Existing293JUnit tests
have zero failures/errors/skips; final Gradle test was UP-TO-DATE and reuses the
same production/dependency inputs from the successful initial run. Assemble and
native compilation passed. Actual Java game cwd was the isolated
`/tmp/nexus-strict-terrain-run`, followed by normal world save/shutdown. All
source/dependency/Core-JAR/init-script hashes match
`/tmp/nexus-production-expansion-checkpoint.json`. Final independent review
accepted with no blocker. OpenSpec strict/diff checks passed; final impact/test_plan
and consumer-surface checks found no changed shared Core API, production Screen,
Observer provider/protocol, relay, death-node or EventBus contract.

This is server pipeline completion only. Logical-extent rendering in native/Nexus
presentations and the owning Observer path, precise extended coverage and
current-holder compass-equivalent off-map authorization, capacity feedback, and
required client/E2E/runtime validation remain. No further experimental migration
integration is a prerequisite for this existing-format production path. No merge,
push, version change or release occurred.
