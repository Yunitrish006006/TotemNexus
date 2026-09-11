## Verification evidence

### TotemNexus feature branch

- PR: `Yunitrish006006/TotemNexus#17` (`update-nexus-map-detail-zoom`).
- Final pre-evidence GitHub Actions Build run `#83` / `34561625162`: PASS.
  - Java 25 / TotemCore 0.7.19 lockstep build: PASS.
  - Nexus compile + JUnit: PASS.
  - Server GameTests: PASS.
  - Client GameTests: PASS.
- OpenSpec Strict run `#1` / `34560769846`: PASS.
  - Installed the official `@fission-ai/openspec` CLI on Node 22 in CI.
  - `openspec validate update-nexus-map-detail-zoom --strict --no-interactive`: PASS.
- `NexusMapBindingAndPayloadPolicyTest` covers backward-compatible empty legacy lineage, SCALE parent append, LOCK lineage copy, fabricated/self/duplicate ancestry rejection, and invalid scale jumps.
- `NexusMapDetailPayloadTest` covers bounded MapId-only detail payload round-trip and malformed identity rejection.
- `NexusSpaceUnitMapVisualGameTest` installs exact-centered scale 2/1/0 vanilla map data, verifies 1x -> 2x -> 4x detail zoom, refuses zoom beyond proven scale-0 detail, and verifies pan/marker-selection alignment.
- `NexusMapPlayerMarkerClientGameTest` directly waits for the owning production Screen to render the transient local PLAYER decoration, then reconstructs the same cached map in Observer mode and asserts that the observer client's own player marker is not rendered.
- `NexusObserverProviderClientGameTest` requires Nexus Observer protocol 4, accepts a semantic 2x detail zoom, rejects non-power-of-two `map_zoom=3` without partial mutation, and retains read-only/no-packet behavior.
- README current-behavior guidance documents power-of-two historical-detail zoom, scale-0 detail bounds, transient local-player rendering, and Observer local-position suppression. Historical 0.3.13 release-note wording remains historical.

### Observer ownership migration

- During validation, Observer View was extracted from TotemVanillaTweaks into the dedicated `Yunitrish006006/TotemObserver` module. TotemVanillaTweaks PR #70 removed the embedded Observer runtime after the extracted module had its own validation coverage.
- The earlier TotemVanillaTweaks companion PR #71 therefore became obsolete and was closed without merge. Merging it after extraction would have reintroduced removed Observer runtime/protocol code.
- Historical pre-extraction evidence remains valid as regression context:
  - TotemVanillaTweaks Build `#378` / `34561009110`: PASS.
  - TotemVanillaTweaks Production Runtime `#280` / `34561009123`: PASS.
  - Legacy-runtime feature Nexus protocol-4 three-JVM run `34561009134`: PASS.
- No TotemVanillaTweaks production change is required for Nexus protocol 4 after extraction.

### Post-extraction TotemObserver protocol-4 release gate

- Temporary verification PR: `Yunitrish006006/TotemObserver#6`, closed without merge after evidence collection.
- `Verify Nexus Protocol 4` run `#1` / `34563494573`: PASS against the actual extracted Observer owner.
  - TotemCore 0.7.19 production JAR: PASS.
  - TotemNexus 0.3.20 feature production JAR from commit `b5d4d02b2368081f7f22b839bfbbc475b6b38cc3`: PASS.
  - Module-owned Observer Screen boundary gate: PASS.
  - Dedicated server + separate Target client + Observer client: PASS.
  - Nexus protocol 4 semantic flow through extracted TotemObserver: PASS.
  - E2E evidence upload: PASS.
- TotemObserver baseline Build run `34563494510`: PASS.
- TotemObserver baseline three-JVM E2E run `34563494581`: PASS, proving the temporary protocol-4 verification did not replace the existing released-lockstep regression path.
- The active TotemObserver relay is module-agnostic: it relays an owner snapshot only when Target and Observer advertise the same `family + protocol` provider identity. The Nexus-owned provider advertises `nexus + protocol 4`, so no Nexus-specific TotemObserver production table change is required.
- The temporary TotemObserver verification branch was reset to `main` after the successful run, so no stale feature SHA or one-off workflow remains in the Observer repository.

## Release-gate status

All feature-specific release gates requested by this change have executable PASS evidence: strict OpenSpec validation, Nexus compile/JUnit, server and client GameTests, restored-detail zoom and pan/selection behavior, transient player-marker and Observer-suppression assertions, legacy Observer compatibility evidence, and a dedicated **post-extraction TotemObserver** feature Nexus protocol-4 three-JVM pairing. Temporary one-off workflows used only to obtain release evidence were removed or reset after success.
