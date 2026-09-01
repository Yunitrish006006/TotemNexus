## Validation evidence

Executed locally on 2026-08-31 with Minecraft 26.2, Java 25, Fabric Loader
0.19.3 and Fabric API 0.154.2+26.2.

### TotemCore 0.7.13

- `../TotemCore/gradlew test jar --console=plain` — passed.
- JAR inspection found `TotemWorldOutlines`, `WorldOutlineStyle` and
  `WorldOutlineOcclusion` in `totem-core-0.7.13.jar`.
- Feature commit `d7aa9bbc441f064abaad377e2d47f9dbb7141af6` was pushed to
  `master`. GitHub Build run `33324609525` passed the build and Core-only
  dedicated-server startup check.
- Modrinth run `33324609536` uploaded and authenticated-API-verified version
  `0.7.13` as version ID `fesZ9uKk`; the repository publication marker records
  the same ID and SHA-512. Follow-up run `33325008061` reverified the artifact
  and successfully submitted the project for public review.

### TotemExcavation 0.1.9

- `../TotemCore/gradlew test assemble compileGametestJava --console=plain` —
  passed.
- `../TotemCore/gradlew runGameTest --console=plain` — all 27 required tests
  passed.
- `../TotemCore/gradlew test assemble compileGametestJava runClientGameTest
  --console=plain` — passed.
- Inspected `totem-excavation-selection-depth-tested.png`: the cyan outline is
  visible beside the opaque stone-brick wall and hidden behind the wall.
- JAR metadata reports version 0.1.9 and `totem-core >=0.7.13 <0.8.0`.
- Feature commit `8db965637eadf6ebd74bd627865aada5be5a8d6c` was pushed to
  `master`. GitHub Build run `33324787586` passed unit/build, all server
  GameTests and the newly required client GameTest.
- Modrinth run `33324787613` uploaded and authenticated-API-verified version
  `0.1.9` as version ID `airad8PP`; the repository publication marker records
  the same ID and SHA-512. Follow-up run `33325065559` reverified the artifact
  and successfully submitted the project for public review.

### TotemNexus 0.3.7

- `../TotemCore/gradlew test assemble compileGametestJava --console=plain` —
  passed.
- `../TotemCore/gradlew runGameTest --console=plain` — all 55 required tests
  passed.
- `../TotemCore/gradlew runClientGameTest --console=plain` — passed.
- Inspected `totem-nexus-array-through-wall.png`: cyan counted blocks, a gold
  expansion emitter and the purple lodestone remain visible behind the opaque
  wall.
- Inspected `totem-nexus-space-unit-material-diagnostics.png`: Repair, Show
  Array and Block Reference remain separately usable at the native 854x480
  fixture size. The same Client GameTest also asserts that Observer mode leaves
  the array control disabled.
- JAR inspection found the request/result payloads, server authority and client
  renderer; metadata reports version 0.3.7 and
  `totem-core >=0.7.13 <0.8.0`.
- `jq empty` passed for both locale files and production/GameTest Fabric
  metadata.
- OpenSpec CLI strict validation passed for
  `add-teleport-array-block-visualization`.
- Feature commit `009b3a3fc78572ced8de3e2916ab95aafad51175` was pushed to
  `master`. GitHub Build run `33325361637` passed unit/build, all 55 server
  GameTests and the client GameTests.
- Modrinth run `33325361690` uploaded and authenticated-API-verified version
  `0.3.7` as version ID `S5HsB3t9`, successfully submitted the project for
  public review and wrote the matching version ID and SHA-512 to the repository
  publication marker.

### Deliberately pending

- Task 1.3 remains open because there is no Core Client GameTest or dedicated
  three-JVM E2E/Production Runtime harness for this path, even though Core
  0.7.13 has been committed, built, uploaded and submitted for public review.
- No Nexus three-JVM E2E or Production Runtime harness exists for this path;
  task 5.5 remains open.
- Anonymous Modrinth lookups for the new Core, Excavation and Nexus version IDs
  still returned HTTP 404 immediately after their review-submission runs. The
  artifacts are uploaded, but public availability remains moderation-dependent
  and is not claimed by this evidence.

### TotemNexus 0.3.8 working-tree validation (persistent dual-mode extension)

Executed locally on 2026-09-01 with Minecraft 26.2, Java 25, Fabric Loader
0.19.3, Fabric API 0.154.2+26.2 and the unmodified local TotemCore 0.7.14 JAR.
This section records implementation evidence only; no commit, push or release
is claimed.

- `../TotemCore/gradlew test compileJava compileGametestJava --console=plain`
  with the isolated Gradle home and explicit Core JAR override passed.
- A clean `../TotemCore/gradlew runGameTest --console=plain` run passed all 70
  required server GameTests. This includes strict initial held-interface
  authorization, same-source refresh after switching to building materials,
  changed place/break snapshots, rejection/invalidation and lifecycle cleanup.
- `xvfb-run -a ../TotemCore/gradlew runClientGameTest --console=plain` passed.
  An earlier invocation without Xvfb stopped at GLFW initialization because
  the host had no `DISPLAY`; it did not enter any client test.
- JAR inspection found the request, snapshot and status payload classes, the
  exact material profile resource, structure tag and both locale resources;
  metadata remains Minecraft 26.2, Nexus 0.3.8 and Core `>=0.7.13 <0.8.0`.
- `jq empty` passed for the changed profile, tag and both locale resources;
  the English and Traditional Chinese key sets are identical.
- Strict OpenSpec validation passed for both
  `add-material-attribute-teleport-arrays` and
  `add-teleport-array-block-visualization`; `git diff --check` also passed.
- Inspected `totem-nexus-array-and-build-sites-live.png`: cyan counted blocks,
  gold expansion emitters and the purple lodestone remain visible through the
  opaque wall, while the separate green build-site outline is visible and uses
  the depth-tested path.
- Inspected the separate 854x480 world renders
  `totem-nexus-complex-counted-array.png` and
  `totem-nexus-complex-build-sites.png`. Both payloads come from the production
  server material scan rather than a hand-authored client list. The counted-only
  render covers tuff, obsidian, crying obsidian and gold branches across turns
  and stepped vertical levels, with exactly 12 profile-backed expansion
  emitters (four left, four right and four vertical) in gold and the lodestone
  origin in purple across an opaque partial wall; every counted outline remains
  `THROUGH_WALLS`. The build-site-only render contains the production scan's
  exact reached replaceable set in green and `DEPTH_TESTED`: exposed sites
  remain readable while equivalent sites behind the wall do not bleed through
  it. The fixture asserts exact emitter offsets and profiles, exact payload/scan
  equality, opposite-mode suppression and the 1,330-entry payload bound.
- Inspected `totem-nexus-space-unit-material-overlays-narrow.png`: the material
  title truncates before Repair and the three controls remain separated from
  the Block Reference slot. Inspected
  `totem-nexus-space-unit-material-observer-overlays-disabled.png`: both overlay
  controls are present but disabled in Observer mode.
- Inspected English and Traditional Chinese manual spreads 15-16: tuff,
  obsidian and crying obsidian are visible, with the live-catalog guidance and
  no clipping at the native fixture size.
- Unit and GameTest coverage proves exact tuff/obsidian/crying-obsidian profile
  values and tag membership, mixed/homogeneous bounds, deterministic buildable
  sets, place/break/expander transitions, 20-tick cadence, unchanged snapshot
  suppression, status invalidation, dual-mode rendering and Observer controls.
- The dedicated Nexus three-JVM E2E / Production Runtime harness still does
  not exist for this path, so task 5.5 deliberately remains open.

### 2026-09-02 outer-boundary working-tree validation

Executed locally with Minecraft 26.2, Java 25, Fabric Loader 0.19.3 and Fabric
API 0.154.2+26.2. This section covers the unreleased working-tree change that
replaces dense per-block boxes with exact merged exterior outlines. The local
artifacts still declare Core 0.7.15 and Nexus 0.3.11 only for validation; the
new Core API has not been assigned a publishable patch version.

- TotemCore `test jar` passed all 49 unit tests, including 12 new
  `VoxelUnionOutlineTest` cases for adjacent cuboid collapse, coplanar seam
  removal, concave and disconnected geometry, sealed-cavity suppression,
  maximal line merging, deterministic immutable results, coordinate limits,
  bounded-envelope rejection and style/occlusion submission. A final Core
  `build` also passed.
- Core JAR inspection found `VoxelUnionOutline`, its immutable `Segment` value
  and the updated `TotemWorldOutlines` submission helper in the local
  `totem-core-0.7.15.jar`. This is working-tree evidence, not a claim that the
  already published 0.7.15 artifact contains the new API.
- TotemNexus compile, unit and final `build` checks passed against that explicit
  local Core JAR. Both the standalone server GameTest run and the final build's
  clean server run passed all 80 required GameTests.
- Two complete `xvfb-run -a ../TotemCore/gradlew
  -PtotemCoreJar=/home/thomas/workspace/TotemCore/build/libs/totem-core-0.7.15.jar
  runClientGameTest --console=plain` runs exited 0. The first completed in 4m20s
  and the final artifact-producing run completed in 3m20s.
- `totem-nexus-complex-counted-array.png` was manually inspected: the irregular
  counted array and lodestone use only merged cyan `THROUGH_WALLS` exterior
  segments, with shared/per-block grid lines and the old gold/purple boxes
  absent. Final SHA-256:
  `88a9b48adadeb209ed31edb565469261b5f2838d57b9e6cb7b6bcd816cb53f90`.
- `totem-nexus-complex-build-sites.png` was manually inspected: reached
  replaceable positions use an independent green `DEPTH_TESTED` exterior,
  retain true concave/disconnected geometry and are occluded by the fixture
  wall. Final SHA-256:
  `f9fd93210a84e9c7dc20949d395dd6b0b8e140490683bc949a1cf65967aeb3ee`.
- English and Traditional Chinese locale JSON parsed successfully and retained
  identical key sets. Strict OpenSpec validation for
  `add-teleport-array-block-visualization` and `git diff --check` passed.
- No commit, push, release or version bump was performed. Publishing requires
  assigning and releasing a new TotemCore patch first, then raising Nexus's
  minimum Core dependency and releasing a new Nexus patch.

### 2026-09-02 release-candidate validation

The same implementation was prepared as TotemCore 0.7.16 and TotemNexus
0.3.12. No commit, push, workflow dispatch, Modrinth upload or successful-
publication marker was created during this preparation.

- `TotemCore ./gradlew clean build --no-daemon --console=plain` passed all 49
  unit tests on Java 25. A subsequent `runServer` loaded `totem-core 0.7.16`,
  logged `TotemCore API 1.1`, reached `Done`, accepted `stop` and exited 0.
- `totem-core-0.7.16.jar` declares Minecraft `~26.2`, contains
  `VoxelUnionOutline`, its `Segment` value and the plan submission helper, and
  has SHA-512
  `ed0fb614bde130d1ead3d86d217753f0be8088d8a6e679ba50b517bc74a9596534cb7b309996937866bcbfcd9fb96004fb1d308f035c69479396e8cd425ba1d5`.
- Nexus `clean test assemble compileGametestJava` passed against the explicit
  local `totem-core-0.7.16.jar`; `runGameTest` then passed all 80 required
  Server GameTests and exited 0 after its normal save/shutdown tail.
- `xvfb-run -a ... runClientGameTest` passed and exited 0 with Core 0.7.16 and
  Nexus 0.3.12 loaded. The two outer-boundary screenshots were regenerated and
  manually inspected; unrelated generated screenshots were discarded.
- `totem-nexus-complex-counted-array.png` shows the irregular cyan
  `THROUGH_WALLS` union without per-block grid seams; SHA-256
  `817a56c5b2a7ea8ce135608afd1152b4ed710948882efa55c31b932130d2d14f`.
- `totem-nexus-complex-build-sites.png` shows the independent green
  `DEPTH_TESTED` exterior and wall occlusion; SHA-256
  `09efef9109c389d6e618019dd9c93966735d6fd18016c58731897c95c5a8e562`.
- `totem-nexus-0.3.12.jar` declares Core `>=0.7.16 <0.8.0`, contains the
  cached outline-plan client implementation, and has SHA-512
  `e89700134cd9b370ca0567d628a2aac1539dd3cdaea308501219d8764e7d1df09077ce61a2a21cd33b6c31fd8dbcc69dae433daace5fb82a086760c2916ce6a8`.
- Both locale files and Fabric metadata parsed, English and Traditional Chinese
  key sets matched, strict OpenSpec validation passed, and `git diff --check`
  passed in both repositories.
- TotemCore 0.7.16 was subsequently published and verified by GitHub Actions
  run `33546528384` as Modrinth version `Z7Z0yoq5`; marker commit
  `b0b57bc98a98140a1c12a660a33952ea61167278` records the matching SHA-512.
  Nexus build and publish workflows now assert Core 0.7.16, its new dependency
  range and that exact verified Core release-marker commit.
