## Why

The ordinary bound Nexus compass currently opens a management-only screen and
receives only its source Space Unit, so it cannot select or start a teleport.
The existing Nexus-map path is intended to teleport, but its map-point
selection and full server-authoritative start flow do not have regression
coverage and are reported as non-functional.

## What Changes

- Let an ordinary bound compass receive eligible discovered destinations,
  select one from a list-only presentation, inspect the authoritative quote,
  and request teleport without exposing a map canvas.
- Keep map visualization exclusive to a valid Nexus filled map. Recovery
  compasses and books remain management-only and do not gain teleport target
  selection in this change.
- Preserve the existing Nexus-map interaction: clicking a Nexus marker at its
  map coordinate selects that Space Unit, and the selected target can start a
  teleport. The map presentation does not show a destination list; that list is
  exclusive to the ordinary-compass presentation.
- Add bounded integer zoom and drag-to-pan controls to the vanilla-map
  presentation while preserving marker-coordinate selection and keeping drag
  gestures from changing the selected destination.
- Keep previously issued Nexus maps usable when their item binding, MapId,
  server-owned map binding, and anchor identity are still valid. Arbitrary
  filled maps do not become Nexus interfaces.
- Separate interface teleport capability from map-visualization capability so
  server payload filtering and client controls do not use map presence as a
  proxy for permission to teleport.
- Retain exact-held-interface, proximity, access, quote, resource, route, and
  teleport-session validation on the server for both interfaces.
- Update the module-owned Observer snapshot/projection for the changed
  production screen, including monotonic protocol handling, remote cursor
  rendering, read-only input and packet suppression, and framebuffer-free
  reconstruction.
- Add unit, server GameTest, native-scale Client GameTest screenshot,
  three-JVM E2E, Production Runtime, localization, manual, and README coverage.

## Impact

- Affected specs: `lodestone-interface-activation`.
- Affected code: interface capability modeling, payload selection, map/compass
  screen layout and interaction, vanilla map-data synchronization if required,
  Observer provider state, networking regression coverage, localization, manual,
  and README.
- Security: destination visibility and teleport eligibility remain derived from
  server-owned records. A client-selected UUID, forged item binding, stale
  screen, wrong hand, changed MapId, or lost access cannot authorize travel.
- Compatibility: valid already-issued Nexus maps retain their MapId, coverage,
  marker selection, and teleport behavior. No arbitrary-map conversion or
  authority migration is introduced.
- Visuals: the compass uses a vanilla-style list and widgets with no custom or
  simulated map. The map variant continues through the vanilla map rendering
  path; Observer remains semantic and framebuffer-free.
