## 0. Approval

- [x] 0.1 Review and approve the final proposal before implementation begins.

## 1. Binding and server authority

- [x] 1.1 Replace ordinary-compass capability gates with explicit resolver
  capabilities for registration, durable binding, management/friends, and map
  visualization.
- [x] 1.2 Introduce one shared binding codec that stores only Space Unit UUID and
  version, while preserving legacy ordinary-compass keys.
- [x] 1.3 Bind ordinary compass, recovery compass, and plain book without
  changing native components; split exactly one component-preserving book or
  compass copy from non-creative stacks.
- [x] 1.4 Generalize friendship/member and lodestone-management actions to every
  correctly held, bound interface while retaining server owner/admin/allowed
  UUID authorization.
- [x] 1.5 Generalize attack-block discovery to supported interfaces and retain
  spectator/range/permission/loaded/lodestone checks.

## 2. Registration and item identity

- [x] 2.1 Preserve normal plain-book manual acquisition and route only crouch +
  right-click to Nexus registration/opening.
- [x] 2.2 Bind pending registration to exact initiating hand, input type, and map
  identity and re-resolve it server-side for repeat-use and GUI confirmation.
- [x] 2.3 Reject expired/replayed/swapped/missing/malformed identities without
  searching another hand or trusting client fields.
- [x] 2.4 Preserve structure, range, permission, loaded-state, lodestone, and
  no-force-load validation on every path.

## 3. Nexus map lifecycle and presentation

- [x] 3.1 Accept an empty map only on the authorized lodestone registration/bind
  path, allocate new vanilla map data centered on the lodestone, and atomically
  return a bound filled Nexus map with a new MapId.
- [x] 3.2 Reject arbitrary/existing filled maps as Nexus registration or map-view
  interfaces, while preserving already valid Nexus maps and their MapId.
- [x] 3.3 Keep map expansion centered on the source lodestone and refuse new
  expansion while the anchor is missing or unloaded without force-loading.
- [x] 3.4 Restrict map visualization to a valid bound Nexus map; provide non-map
  management presentation for compass, recovery compass, and book.
- [x] 3.5 Render vanilla map pixels and transient named Nexus markers only for
  authorized active Space Units in the map dimension and coverage; omit
  out-of-bounds markers/edge arrows and never persist viewer-specific markers
  into shared map SavedData.

## 4. Player guidance

- [x] 4.1 Update `README.md` with the four binding/management interfaces, UUID
  trust model, book gesture, and Nexus map lifecycle/visibility rules.
- [x] 4.2 Update Nexus manual content for binding, management, map creation,
  lodestone-centered expansion, missing-anchor behavior, and named markers.
- [x] 4.3 Update matching `en_us` and `zh_tw` messages, including localized
  unnamed-Nexus fallback and precise invalid-map/binding diagnostics.

## 5. Automated verification

- [x] 5.1 Add unit tests for resolver capabilities, shared binding round trips,
  no player UUID in items, pending identity matching, and map bounds filtering.
- [x] 5.2 Add GameTests for binding/management/friends across ordinary compass,
  recovery compass, book, and Nexus map; reject unauthorized players, copied or
  forged bindings, and stale/replayed identities.
- [x] 5.3 Add GameTests for native component retention, book stack splitting and
  gesture priority, plus arbitrary filled-map rejection.
- [x] 5.4 Add GameTests proving empty-map conversion creates a new map centered
  on the lodestone and preserves MapId/binding thereafter.
- [x] 5.5 Add GameTests for map-only visualization, dimension/bounds/permission
  marker filtering and names, no edge arrows/shared-data leakage, and refusal to
  expand while the anchor is absent.
- [x] 5.6 Re-run existing teleport, friendship, management, manual,
  registration-preview, Observer, and screenshot coverage.

## 6. Release gates

- [x] 6.1 Run `openspec validate expand-lodestone-interface-activation --strict`.
- [x] 6.2 Run `../TotemCore/gradlew --no-daemon test assemble
  compileGametestJava --console=plain` with the pinned compatible TotemCore JAR.
- [x] 6.3 Run feasible Fabric server GameTests and the existing Client GameTest
  suite; inspect registration/Observer screenshots without hiding regressions.
- [x] 6.4 Confirm dedicated-server startup, compatibility checks, and production
  runtime smoke coverage, recording any unavailable gate and residual risk.

## 7. Validation evidence

- On 2026-09-01, the local Java 25 build passed unit, assemble, and compile
  gates with TotemCore 0.7.14; all 64 required Fabric server GameTests passed.
- The full TotemNexus Client GameTest suite passed, and the manual, Nexus map,
  registration, and Observer screenshots were inspected without regressions.
- TotemVanillaTweaks' local Nexus protocol-v3 integration Client GameTest passed.
  Its three-JVM loopback produced 196 evidence files and 28 PNGs while completing
  Nexus map, friends, registration, and close transitions.
- Production Runtime passed 26 Client GameTests and produced 31 PNGs. A dedicated
  Minecraft 26.2 server loaded TotemCore 0.7.14 and this change's pre-bump Nexus
  artifact, reached `Done`, and stopped cleanly; no release gate was unavailable.
