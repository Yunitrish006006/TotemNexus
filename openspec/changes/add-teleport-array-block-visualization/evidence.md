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

### Deliberately pending

- Task 1.3 remains open because there is no Core Client GameTest or dedicated
  three-JVM E2E/Production Runtime harness for this path, even though Core
  0.7.13 has been committed, built, uploaded and submitted for public review.
- No Nexus three-JVM E2E or Production Runtime harness exists for this path;
  task 5.5 remains open.
- Anonymous Modrinth lookups for the new Core and Excavation version IDs still
  returned HTTP 404 immediately after their review-submission runs. The
  artifacts are uploaded, but public availability remains moderation-dependent
  and is not claimed by this evidence.
- Nexus commit, CI and publication results are intentionally not claimed in
  this pre-push evidence; they must be verified independently from the GitHub
  Actions run and repository publication marker after this change reaches
  `master`.
