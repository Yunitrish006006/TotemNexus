## Why

Nexus map zoom currently scales one 128×128 vanilla map texture, so magnification enlarges existing pixels instead of recovering detail that existed before cartography expansion. Nexus maps also create vanilla map data with position tracking disabled, so the production Screen cannot rely on persisted vanilla player decorations to show the local player.

## What Changes

- Preserve the vanilla MapId/MapItemSavedData render path while recording server-proven SCALE ancestry for Nexus maps.
- Render the current coarse map as the viewport fallback and overlay compatible ancestor maps as progressively finer terrain when zoom makes that detail meaningful.
- Change map zoom semantics from linear 100%–400% texture magnification to power-of-two detail zoom bounded by the current map scale, with scale 0 as the finest available level.
- Add the owning local player's position as a transient Screen-only vanilla map decoration without enabling persisted map position tracking.
- Keep Observer framebuffer-free and prevent an Observer client from substituting its own local player position for the observed owner's position.
- Version any semantic Observer state whose zoom interpretation changes, and add unit/client/runtime coverage for lineage, zoom, player marker, and Observer behavior.

## Impact

- Affected specs: `lodestone-interface-activation`
- Affected code: `NexusMapBindingSavedData`, `NexusMapLifecycleAuthority`, `SpaceUnitMapPayload`/map payload construction and synchronization, `NexusSpaceUnitMapScreen`, `NexusObserverScreenProvider`, map visual/GameTests
- Persisted data: backward-compatible SavedData schema extension; legacy entries remain valid with no inferred ancestry
