## Verification evidence

### TotemNexus feature branch

- Draft PR: `Yunitrish006006/TotemNexus#17` (`update-nexus-map-detail-zoom`).
- GitHub Actions Build run `#80` / `34560769785`: PASS after the implementation and README detail-zoom/player-marker documentation update.
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
- README current-behavior guidance now documents power-of-two historical-detail zoom, scale-0 detail bounds, transient local-player rendering, and Observer local-position suppression. Historical 0.3.13 release-note wording remains historical.

### TotemVanillaTweaks companion compatibility

- Draft PR: `Yunitrish006006/TotemVanillaTweaks#71` (`update-nexus-map-detail-zoom-protocol`).
- The companion protocol table retains released Nexus protocol 3 as the legacy fixture version while accepting protocols 3 and 4. Nexus capability negotiation succeeds when the observer advertises either supported provider version.
- Production Runtime run `#280` / `34561009123`: PASS after the E2E protocol parameterization changes.
  - module-owned Screen gate: PASS.
  - cross-module production sender Client GameTest: PASS.
  - cross-module screenshot verification/upload: PASS.
  - production-namespace Client GameTests: PASS.
- Earlier full Build run `#375` / `34517457165`: PASS with the released protocol-3 lockstep fixture, including server/client GameTests, cross-module production sender Client GameTest, dedicated-server two-client Observer E2E, production Client GameTests, and evidence uploads.

### Feature Nexus protocol-4 three-JVM lockstep

- Temporary release-gate workflow `Nexus Protocol 4 Feature E2E` run `#1` / `34561009134`: PASS.
- The workflow built real production JARs using TotemCore 0.7.19 and TotemNexus 0.3.20 from feature commit `2d7f52150d17e5c7c7ead6905fb3629f7548ba77`.
- It launched a dedicated server plus separate Target and Observer client JVMs with `totem.observer.e2e.nexus.protocol=4`.
- The protocol-4 path reached and verified Nexus compass, recovery compass, map, management, friends, registration, remote close, and the production map screenshot without failure markers.
- The workflow uploaded `nexus-protocol4-feature-e2e` evidence containing result markers and server/target/observer logs.
- The temporary pinned workflow was removed after the successful release-gate run so the companion branch does not retain a stale feature SHA.

## Release-gate status

All feature-specific release gates requested by this change have executable PASS evidence: strict OpenSpec validation, Nexus compile/JUnit, server and client GameTests, transient player-marker and Observer-suppression assertions, companion production-runtime compatibility, released protocol-3 three-JVM compatibility, and a dedicated feature Nexus protocol-4 three-JVM pairing. Temporary one-off workflows used only to obtain release evidence were removed after success.
