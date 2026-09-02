## 1. Capability and server projection

- [x] 1.1 Add an explicit destination-teleport capability independent of map visualization.
- [x] 1.2 Project bounded authorized discovered destinations to an ordinary compass while retaining Nexus-map coverage filtering.
- [x] 1.3 Add unit coverage for all interface capability combinations, payload privacy, bounds, and valid previously issued Nexus maps.

## 2. Production screen and map behavior

- [x] 2.1 Add the compass list-only target-selection layout with no map render path.
- [x] 2.2 Enable compass list click, scrolling, filters, keyboard/focus/narration, quote footer, and teleport-button state.
- [x] 2.3 Remove the destination list from the Nexus-map presentation and preserve coordinate-marker selection, non-list keyboard accessibility, bounded integer zoom, drag/keyboard pan, quote updates, and teleport-button state.
- [x] 2.4 Ensure a held valid Nexus map receives normal vanilla map-data synchronization when the screen opens, without adding map pixels to Observer snapshots.
- [x] 2.5 Update English and Traditional Chinese localization, manual content, and README behavior tables.

## 3. Observer ownership

- [x] 3.1 Extend/version the bounded semantic snapshot as needed for the changed production Screen and its map/compass variant state.
- [x] 3.2 Render remote cursor/carried-stack state through the production Screen and retain exact monotonic family/variant/protocol checks.
- [x] 3.3 Prove Observer input, scrolling, widgets, lifecycle requests, and packets are suppressed except stop-observing, with client-thread capture/create/apply coverage.

## 4. Runtime verification

- [x] 4.1 Add server GameTests proving a valid compass teleport and a valid existing Nexus-map teleport start and complete, while stale/wrong interfaces are rejected.
- [x] 4.2 Add native-scale Client GameTests and reviewed screenshots for compass list-only selection and Nexus-map coordinate selection, including meaningful English/Traditional Chinese fit.
- [x] 4.3 Run focused unit tests and the full Java 25 build.
- [x] 4.4 Run the dedicated three-JVM Observer E2E for compass and Nexus-map variants, paired with server GameTests for teleport completion and changed-item cancellation.
- [x] 4.5 Run Production Runtime validation and record commands, artifacts, and screenshot evidence.
