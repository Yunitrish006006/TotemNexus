## Verification evidence

### TotemNexus feature branch

- Draft PR: `Yunitrish006006/TotemNexus#17` (`update-nexus-map-detail-zoom`).
- GitHub Actions Build run `#77` / `34518056409`: PASS on the latest OpenSpec-aligned branch head at the time of verification.
  - Java 25 / TotemCore 0.7.19 lockstep build: PASS.
  - Nexus compile + JUnit: PASS.
  - Server GameTests: PASS.
  - Client GameTests: PASS.
- `NexusMapBindingAndPayloadPolicyTest` covers backward-compatible empty legacy lineage, SCALE parent append, LOCK lineage copy, fabricated/self/duplicate ancestry rejection, and invalid scale jumps.
- `NexusMapDetailPayloadTest` covers bounded MapId-only detail payload round-trip and malformed identity rejection.
- `NexusSpaceUnitMapVisualGameTest` installs exact-centered scale 2/1/0 vanilla map data, verifies 1x -> 2x -> 4x detail zoom, refuses zoom beyond proven scale-0 detail, and verifies pan/marker-selection alignment.
- `NexusMapPlayerMarkerClientGameTest` directly waits for the owning production Screen to render the transient local PLAYER decoration, then reconstructs the same cached map in Observer mode and asserts that the observer client's own player marker is not rendered.
- `NexusObserverProviderClientGameTest` requires Nexus Observer protocol 4, accepts a semantic 2x detail zoom, rejects non-power-of-two `map_zoom=3` without partial mutation, and retains read-only/no-packet behavior.

### TotemVanillaTweaks companion compatibility

- Draft PR: `Yunitrish006006/TotemVanillaTweaks#71` (`update-nexus-map-detail-zoom-protocol`).
- Production Runtime run `#277` / `34517457061`: PASS.
  - module-owned Screen gate: PASS.
  - cross-module production sender Client GameTest: PASS.
  - cross-module screenshot verification/upload: PASS.
  - production-namespace Client GameTests: PASS.
- Build run `#375` / `34517457165`: PASS.
  - compile/JUnit and protocol test gates: PASS.
  - server/client GameTests: PASS.
  - cross-module production sender Client GameTest: PASS.
  - dedicated-server two-client Observer E2E: PASS.
  - production Client GameTests: PASS.
  - Observer and screenshot evidence upload: PASS.
- The companion transport now negotiates Nexus capability when the observer advertises protocol 3 or 4. Its pinned integration fixture intentionally remains on released Nexus protocol 3, so these runs prove backward compatibility rather than a protocol-4 feature-JAR three-JVM pairing.

## Remaining release gates

- `openspec validate update-nexus-map-detail-zoom --strict` has not been executed because the OpenSpec CLI is not installed in the available local runtime and the repository workflow does not currently provide that command. The proposal/design/spec/task structure has been kept in the repository-required format, but strict CLI validation must not be claimed until run in an environment containing OpenSpec.
- A dedicated three-JVM Observer E2E using the **feature Nexus protocol-4 JAR** together with the companion VanillaTweaks branch has not yet been executed. Nexus protocol-4 provider semantics are covered by Nexus Client GameTests and VanillaTweaks protocol-3 backward compatibility is covered by its full three-JVM E2E, but the feature-JAR cross-repository lockstep pairing remains a separate release gate.
- The current README quick-start text still describes the historical 100%-400% linear map zoom and should be updated before release to describe power-of-two historical-detail zoom. Historical release-note text for old versions should remain historical.
