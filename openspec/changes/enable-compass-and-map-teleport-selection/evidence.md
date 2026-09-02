## Implementation evidence

- `TeleportInterfaceType` separates destination selection from map rendering:
  ordinary compasses are list-only selectors, valid Nexus maps are map-only
  selectors, and recovery compasses/books remain management-only.
- Server projection gives an ordinary bound compass a bounded set of active,
  authorized discovered destinations. Nexus maps retain map-coverage filtering
  and receive Minecraft's normal map-data update packet when opened.
- `NexusSpaceUnitMapScreen` keeps marker click selection while adding bounded
  integer zoom (100%-400%), mouse-drag pan, `+`/`-` zoom, and Shift+arrow pan.
  A drag beyond the click threshold does not change the selected destination.
- The module-owned Observer provider uses the production screen and protocol 3
  semantic snapshots. It transfers only the selected unit plus bounded zoom and
  pan state; map pixels remain framebuffer-free and are never serialized.

## Automated evidence

- TotemNexus focused Java 25 compile/unit suite — **PASS**:
  `test compileJava compileClientJava compileGametestJava`.
- TotemNexus full Java 25 build — **PASS**; all **83/83** required Server
  GameTests passed, including compass teleport, existing Nexus-map teleport,
  changed-interface cancellation, and forged management-interface rejection.
- TotemNexus native Client GameTest suite — **PASS** (`BUILD SUCCESSFUL in
  3m 52s`). Coverage exercises compass list selection, map marker selection,
  200% integer zoom, drag pan without accidental selection, keyboard access,
  and Observer snapshot reconstruction.
- TotemVanillaTweaks unit/E2E/integration compilation — **PASS**. Observer gate
  parity passed with **26** Client GameTests and **23** production bridges.
- Dedicated Nexus three-JVM Observer E2E — **PASS** for compass, map,
  management, friends, and registration variants; the map snapshot carried
  zoom/pan semantics without pixels.
- Cross-module integration Client GameTest — **PASS**, including the Nexus map
  200% zoom and pan update. Production Runtime validation — **PASS** with all
  **26** gates and **31** rendered screenshots.
- Strict OpenSpec validation — **PASS** for
  `enable-compass-and-map-teleport-selection`.

## Reviewed visual evidence

- `test-artifacts/screenshots/compass-and-map-teleport-selection/compass-teleport-list-en_us.png`
- `test-artifacts/screenshots/compass-and-map-teleport-selection/map-coordinate-teleport-en_us.png`
- `test-artifacts/screenshots/compass-and-map-teleport-selection/compass-teleport-list-zh_tw.png`
- `test-artifacts/screenshots/compass-and-map-teleport-selection/map-coordinate-teleport-zh_tw.png`

The four screenshots were reviewed at original resolution. The compass has a
scrollable list and no map; the Nexus map has vanilla map pixels, coordinate
markers, 200% zoom and no destination list. English and Traditional Chinese
controls remain readable at the native test resolution.
