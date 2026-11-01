## 1. Retire the superseded compatibility direction

- [x] 1.1 Remove the pending filled-map activation acceptance path and its
  compatibility-specific tests/documentation without changing empty-map
  activation.
- [x] 1.2 Add a regression proving a filled map still cannot directly activate
  or bind a Nexus.

## 2. Add server-authoritative cartography cleansing

- [x] 2.1 Add an exact native cartography preview for one filled map plus one
  water bucket, producing one component-clean empty map without mutating inputs.
- [x] 2.2 Add take-time revalidation and an atomic transaction that consumes
  the filled map and water bucket only after all output destinations pass
  preflight.
- [x] 2.3 Return exactly one empty bucket and preserve exactly one empty-map
  result across pickup, quick-move, swap and drop paths, including full
  inventories and stale previews.
- [x] 2.4 Prove cleansing never changes or deletes the old MapId SavedData,
  Nexus detail/lineage data, or binding ledger used by another copy.

## 3. Validate UI, authority and compatibility

- [x] 3.1 Add focused unit and dedicated-server GameTests for eligibility,
  result identity, fail-closed paths, inventory conservation and save/reload.
- [x] 3.2 Run native cartography regressions for paper scaling, glass-pane
  locking and map copying.
- [x] 3.3 Validate the native cartography Screen at native scale, its existing
  semantic Observer adapter, input/packet suppression, privacy, close cleanup,
  dedicated three-JVM E2E and Production Runtime behavior.
- [x] 3.4 Run the Java 25 Gradle build and required cross-module consumer-impact
  review; record exact evidence without committing, pushing or publishing.

## Evidence checkpoints

### 1.1 Superseded filled-map activation removal

- The obsolete `enable-existing-map-activation` OpenSpec change was removed so
  it can no longer advertise filled-map lodestone activation as pending work.
- Production registration resolution continues to accept `minecraft:map` and
  explicitly excludes `minecraft:filled_map`; ordinary MapId-owned filled maps
  remain usable only through the direct map-interface path.
- Existing empty-map exact-center creation coverage remains in place. The
  explicit filled-map rejection regression is task 1.2 and was not claimed by
  this checkpoint.
- Focused Java 25 JUnit validation:
  `NexusInterfaceBindingTest` — 4 tests, 0 failures/errors/skips; it proves the
  registration resolver still accepts an empty map and excludes filled maps.
- `openspec validate add-cartography-map-cleansing --strict`, `openspec list`
  and `git diff --check` passed; the superseded change no longer appears in the
  OpenSpec change list.
- Checkpoint: taskId `cartography-cleansing-1.1`; repository `TotemNexus`;
  branch `master`; starting HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; changed workspace inputs are the
  removal of `openspec/changes/enable-existing-map-activation/**` and this task
  log. Remaining risk: the dedicated server rejection behavior still requires
  the focused task-1.2 regression. Next task is 1.2 only.

### 1.2 Filled-map activation rejection regression

- The dedicated `NexusMapActivationRemovalGameTest` covers both a normal
  filled map and a three-item stack carrying legacy Nexus identity metadata.
- Every case requires registration resolution to reject the input, lodestone
  interaction to pass through without creating a Space Unit, the complete
  input stack/components to remain unchanged, and the original MapId SavedData
  object and sentinel pixel to remain untouched.
- Focused Java 25 dedicated-server GameTest with
  `nexusTestFocus=map-activation-removal` reported all 3 required tests passed.
  After the server stopped, the Gradle wrapper did not return despite no live
  Java or Gradle process and was interrupted; the GameTest result itself was
  already complete and successful.
- Checkpoint: taskId `cartography-cleansing-1.2`; repository `TotemNexus`;
  branch `master`; starting HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; validated files
  `NexusMapActivationRemovalGameTest.java`, `TeleportInterfaceItemResolver.java`
  and the GameTest focus/manifest. Remaining risk: cartography cleansing has
  not yet been implemented. Next task is 2.1 only.

### 2.1 Exact non-mutating cartography preview

- The native `CartographyTableMenu` now offers one newly constructed
  `minecraft:map` only for one MapId-bearing filled map plus one water bucket.
  Stacked inputs, missing MapId, empty maps and empty buckets produce no result.
- The result does not copy the old MapId, Nexus identity, custom name or any
  other source component. Preview computation does not consume either input or
  read, rewrite or delete the old MapId SavedData.
- Focused Java 25 JUnit passed 5/5 tests after updating the pre-existing test
  fixture for Minecraft 26.3 data-component initialization. Gametest sources
  compiled successfully.
- Focused dedicated-server GameTest reported all 2 required tests passed and
  verified the real menu result, unchanged input slots and unchanged SavedData
  object/sentinel pixel. As in task 1.2, the server stopped but the Gradle
  wrapper did not return despite no live Java/Gradle process and was interrupted.
- Checkpoint: taskId `cartography-cleansing-2.1`; repository `TotemNexus`;
  branch `master`; starting HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; changed files
  `NexusCartographyTableMenuMixin.java`, `NexusMapRecipeInputs.java`,
  `NexusMapRecipeInputsTest.java`, `NexusMapCleansingPreviewGameTest.java`,
  GameTest manifest/focus and this task log. Remaining risk: result pickup does
  not yet have the cleansing-specific server transaction. Next task is 2.2 only.

### 2.2 Server-authoritative cleansing commit

- Every result-slot take now revalidates the live MapId-bearing filled map,
  single water bucket and component-clean empty-map result before native menu
  handling can consume an input. Water-bucket combinations with a forged or
  stale result fail closed.
- The transaction snapshots the menu, carried stack and player inventory. A
  successful native take must consume both exact inputs; partial consumption is
  rolled back. Nested native `safeTake` calls reuse the outer transaction so
  there is one commit boundary.
- The single water bucket's own additional-input slot is the preflighted empty
  bucket destination. Only after native result delivery and complete input
  consumption does the transaction place one empty bucket there.
- Focused Java 25 JUnit passed 5/5 tests and Gametest compilation passed.
  Focused dedicated-server GameTest reported all 4 required tests passed,
  including direct pickup and a forged stale preview. The recurring post-stop
  Gradle wrapper hang again had no live Java/Gradle process and was interrupted
  after the completed GameTest result.
- Checkpoint: taskId `cartography-cleansing-2.2`; repository `TotemNexus`;
  branch `master`; starting HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; changed files
  `NexusMapCraftingTransaction.java`, `NexusMapRecipeInputs.java`, focused unit
  and GameTest coverage, and this task log. Remaining risk: quick-move, swap,
  drop, full-inventory and creative-mode conservation require task 2.3. Next
  task is 2.3 only.

### 2.3 Click-path and inventory conservation

- Successful pickup, quick-move, empty-hotbar swap and drop paths now conserve
  exactly one component-clean empty map and one empty bucket. The bucket remains
  in the cartography additional-input slot, whose availability was guaranteed
  by the accepted single-water-bucket input.
- A full-inventory quick-move leaves both inputs and the preview unchanged.
  Stale results still fail before consumption. Creative clone attempts are
  rolled back when the inputs remain intact but the cursor or inventory changes,
  preventing free result duplication.
- Focused Java 25 main and Gametest compilation passed. The first focused run
  exposed test-fixture assumptions for hotbar/drop behavior; after clearing and
  positioning the mock player like the established native-menu tests, the
  focused dedicated-server suite reported all 9 required tests passed.
- The recurring post-stop Gradle wrapper hang had no live Java/Gradle process
  and was interrupted only after the complete successful GameTest report.
- Checkpoint: taskId `cartography-cleansing-2.3`; repository `TotemNexus`;
  branch `master`; starting HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; changed files
  `NexusMapCraftingTransaction.java`, `NexusMapCleansingPreviewGameTest.java`
  and this task log. Remaining risk: persisted SavedData/detail/lineage/binding
  isolation still requires task 2.4. Next task is 2.4 only.

### 2.4 Shared MapId persistence isolation

- A focused dedicated-server GameTest creates two physical filled-map stacks
  sharing one MapId, cleanses only the first, and verifies the second still
  carries and resolves the same MapId.
- The test seeds and compares the exact vanilla `MapItemSavedData` object and
  sentinel pixel, a Nexus MapId record with non-empty lineage, a detail page
  with sentinel color/revision, and a legacy binding-ledger entry. All remain
  present and byte/field-equivalent after the other copy becomes an empty map.
- Java 25 main and GameTest compilation passed. The final focused dedicated
  server run reported all 10 required cleansing tests passed. The recurring
  post-stop Gradle wrapper hang again had no live Java/Gradle process and was
  interrupted only after the complete successful GameTest report.
- Checkpoint: taskId `cartography-cleansing-2.4`; repository `TotemNexus`;
  branch `master`; starting HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; changed files
  `NexusMapCleansingPreviewGameTest.java` and this task log. Remaining risk:
  save/reload and the consolidated eligibility/fail-closed matrix remain for
  task 3.1. Next task is 3.1 only.

### 3.1 Consolidated authority and persistence validation

- The focused unit matrix verifies exact eligibility, a genuinely clean empty
  map result, rejected stacked/missing-MapId/wrong-bucket inputs and forged
  result components. Java 25 JUnit reported 5 tests with no failures, errors or
  skips.
- Dedicated-server coverage now additionally proves the cleansed map is
  accepted by the normal empty-map registration resolver, replacing the live
  water bucket fails closed before consumption, and vanilla map data plus
  Nexus record/detail/binding SavedData survive codec save/reload unchanged.
- Java 25 main and GameTest incremental compilation passed. The first GameTest
  run exposed shared-world MapId ledger coupling in the reload fixture; the
  persistence fixture was isolated to standalone SavedData instances, after
  which all 12 required focused GameTests passed.
- The recurring post-stop Gradle wrapper hang had no live Java/Gradle process
  and was interrupted only after the complete successful GameTest report.
- Checkpoint: taskId `cartography-cleansing-3.1`; repository `TotemNexus`;
  branch `master`; starting HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; changed files
  `NexusMapCleansingPreviewGameTest.java` and this task log. Remaining risk:
  native paper/glass-pane/map-copy cartography behavior has not yet been
  regression-tested alongside cleansing. Next task is 3.2 only.

### 3.2 Native cartography recipe regressions

- Added an isolated dedicated-server focus covering the three non-cleansing
  native cartography operations: paper scaling, glass-pane locking and empty-map
  copying. Each test exercises the real preview and result-slot take path.
- Scaling and locking still consume exactly one filled map plus their native
  ingredient, create the expected processed filled map, and leave the source
  MapId SavedData unchanged. Copying still consumes one empty map and returns
  exactly two filled maps sharing the original MapId without post-processing.
- The first run exposed test assumptions rather than product regressions: native
  scaling may re-grid its center in 26.3, and shared GameTest worlds can retain
  orphan Nexus records for otherwise free MapIds. The fixture now asserts the
  actual native contract and selects a MapId absent from both map and Nexus
  record storage.
- Java 25 compilation passed and the final focused dedicated-server run reported
  all 4 required GameTests passed. The recurring post-stop Gradle wrapper hang
  had no live Java/Gradle process and was interrupted only after the complete
  successful report.
- Checkpoint: taskId `cartography-cleansing-3.2`; repository `TotemNexus`;
  branch `master`; starting HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; changed files
  `NexusNativeCartographyRegressionGameTest.java`, GameTest manifest/focus and
  this task log. Remaining risk: native-scale Screen, Observer adapter,
  suppression/privacy/cleanup, three-JVM E2E and Production Runtime validation
  remain for task 3.3. Next task is 3.3 only.

### 3.3 Native Screen and Observer runtime validation

- Java 25 Observer Client GameTests completed successfully under Xvfb with the
  Vulkan llvmpipe fallback. The native cartography reconstruction asserted the
  `scale` operation, map/material/result presence and all 39 semantic slots,
  saved an inspected 854x480 native-scale screenshot, relayed the close state
  and waited until the Screen was null.
- Fresh focused Observer unit validation passed with no failures, errors or
  skips: `ObserverReadOnlyPacketFirewallTest` 1/1,
  `ObserverNativePayloadsTest` 5/5 and `ObserverOwnedScreenProtocolTest` 5/5.
  These cover mutation-packet suppression plus the bounded, framebuffer-free
  semantic payload and owned-Screen protocol contracts.
- A focused dedicated-server + Target JVM + Observer JVM run used the existing
  production Cartography E2E bridge and this Nexus candidate JAR. It produced
  real state-sent, Observer render-ok, 854x480 screenshot-saved and Target
  close-sent evidence. The independent Client GameTest above supplies the
  deterministic remote-close cleanup assertion; the focused harness could not
  emit its final Observer-close marker because the generic connection driver
  immediately replaced the closed cartography Screen with its base container.
- The unmodified all-family three-JVM suite cannot reach cartography in this
  workspace: Automata 0.1.28 snapshots are rejected by its provider with
  `Trailing Automata Observer bytes`; after bypassing that unrelated stage, the
  older Nexus-map E2E waits for its terrain/ancestor condition before the
  cartography stage. These are pre-cartography consumer-harness compatibility
  risks, not cartography relay failures, and remain explicitly unresolved.
- `runProductionClientGameTest` completed in the official namespace with exit
  code 0 and `BUILD SUCCESSFUL` under Java 25/Xvfb/Vulkan llvmpipe. Missing
  audio and unauthenticated Realms access were non-test runtime warnings.
- Checkpoint: taskId `cartography-cleansing-3.3`; repository `TotemNexus`;
  branch `master`; starting HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; changed file for this microtask is
  this task log only. Remaining risk: the full unmodified three-JVM suite has
  the two pre-cartography compatibility blockers above. Next task is 3.4 only;
  no commit, push or publication was performed.

### 3.4 Final build and consumer-impact review

- The first unqualified Java 25 build exposed a stale local-development default:
  `build.gradle` selected Core 0.7.22 while the module metadata requires
  `>=0.7.23 <0.8.0`. The default now selects the already required Core 0.7.23;
  no published dependency range or shared API contract changed.
- With Core 0.7.23, the dedicated-server suite reported all 300 required
  GameTests passed in 12.24 minutes. Minecraft then repeated the known
  post-stop hang; after the complete report was captured, the exact Nexus
  GameTest/Gradle JVMs were interrupted without touching the pre-existing
  Observer server.
- The remaining Java 25 build completed with `BUILD SUCCESSFUL`: 353 JUnit tests,
  zero failures and zero ignored tests, plus `check`, binary JAR and sources JAR.
  A final unqualified `build -x runGameTest` also passed, proving the corrected
  default Core path works without an override.
- Cross-module search found no Core, Observer, Remnant or Discord Bridge source
  references to `NexusMapRecipeInputs`, `NexusMapCraftingTransaction` or the
  cartography mixin. The change remains Nexus-internal; Observer continues to
  use its vanilla cartography semantic adapter, while Remnant death-node and
  Core/Discord audit contracts are unchanged.
- Checkpoint: taskId `cartography-cleansing-3.4`; repository `TotemNexus`;
  branch `master`; starting HEAD
  `e09a24faffdb884f63648382d27fafd2dfeccc94`; files changed by this microtask
  are `build.gradle` and this task log. Focused and final validation are complete.
  Remaining external risk is limited to the two pre-cartography all-family E2E
  compatibility blockers recorded in task 3.3. There is no next task in this
  change; no commit, push or publication was performed.
