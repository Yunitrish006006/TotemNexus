## 1. Persisted map lineage

- [x] 1.1 Extend `NexusMapBindingSavedData` with backward-compatible bounded ancestor MapIds and validation helpers.
- [x] 1.2 Preserve/append lineage correctly for SCALE versus LOCK without inferring legacy ancestry.

## 2. Payload and synchronization

- [x] 2.1 Carry bounded validated ancestor MapIds in a dedicated Nexus map-detail payload without transporting pixels.
- [x] 2.2 On an owning Screen detail request, validate the exact held Nexus map, send vanilla map update packets for the current map and accepted ancestors, then return only their MapIds in the detail payload.

## 3. Client viewport and overlays

- [x] 3.1 Replace linear 1x-4x texture zoom semantics with bounded power-of-two detail zoom.
- [x] 3.2 Render current-map terrain as fallback plus compatible cached ancestor terrain layers with one world-coordinate viewport transform.
- [x] 3.3 Render Nexus markers/labels exactly once after terrain composition and keep click/pan transforms aligned.
- [x] 3.4 Add a transient owning-local-player map marker while suppressing observer-local position substitution.

## 4. Observer compatibility

- [x] 4.1 Increment the Nexus Observer protocol and validate the new bounded zoom semantics.
- [x] 4.2 Keep map pixels and player position out of Observer snapshots.

## 5. Verification

- [x] 5.1 Add unit/GameTest coverage for legacy lineage decoding, SCALE ancestry, LOCK ancestry, and invalid lineage rejection.
- [x] 5.2 Add native-scale Client GameTest coverage for restored-detail zoom, pan/marker selection alignment, local-player marker, and Observer suppression.
- [ ] 5.3 Run strict OpenSpec validation plus the relevant build, GameTests, three-JVM Observer E2E, and Production Runtime validation; record evidence without masking regressions.
