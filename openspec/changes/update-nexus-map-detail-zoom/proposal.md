## Why

Nexus map zoom currently scales one 128×128 vanilla map texture, so magnification enlarges existing pixels instead of recovering detail that existed before cartography expansion. Nexus maps also create vanilla map data with position tracking disabled, so the production Screen cannot rely on persisted vanilla player decorations to show the local player.

## What Changes

- Preserve the vanilla MapId/MapItemSavedData render path while recording server-proven SCALE ancestry for Nexus maps.
- Render the current coarse map as the viewport fallback and overlay compatible ancestor maps as progressively finer terrain when zoom makes that detail meaningful.
- Change map zoom semantics from linear 100%–400% texture magnification to power-of-two detail zoom bounded by the current map scale, with scale 0 as the finest available level.
- Add the owning local player's position as a transient Screen-only vanilla map decoration without enabling persisted map position tracking.
- Keep Observer framebuffer-free. Protocol 5 may relay only the observed target's bounded map-local vanilla decoration state (off-map flag, signed map-local X/Y bytes, and 0–15 rotation); it never relays raw player world coordinates and never substitutes the observer client's own local player.
- Version semantic Observer state whose zoom/terrain interpretation changes, and add unit/client/runtime coverage for lineage, actual finer-layer submission, target player marker rendering, and no-local-fallback behavior.

## Impact

- Affected specs: `lodestone-interface-activation`
- Affected code: `NexusMapBindingSavedData`, `NexusMapLifecycleAuthority` derivation behavior, dedicated Nexus map-detail networking/client state, `NexusSpaceUnitMapScreen` rendering hooks, `NexusObserverScreenProvider`, map visual/GameTests
- Companion compatibility: the extracted TotemObserver runtime negotiates the exact Nexus provider identity advertised by both clients. The current detail/terrain/target-marker contract is protocol 5; mismatched older protocols are rejected rather than converted or interpreted as protocol 5.
- Persisted data: backward-compatible SavedData schema extension; legacy entries remain valid with no inferred ancestry. The observed-target player marker remains transient and is not written to `MapItemSavedData`, item components, or Nexus SavedData.
