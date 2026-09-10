## 1. Persisted map lineage

- [ ] 1.1 Extend `NexusMapBindingSavedData` with backward-compatible bounded ancestor MapIds and validation helpers.
- [ ] 1.2 Preserve/append lineage correctly for SCALE versus LOCK without inferring legacy ancestry.

## 2. Payload and synchronization

- [ ] 2.1 Carry bounded validated ancestor MapIds in the filled-map payload without transporting pixels.
- [ ] 2.2 Send vanilla map update packets for the current map and each validated ancestor before the Screen payload.

## 3. Client viewport and overlays

- [ ] 3.1 Replace linear 1x-4x texture zoom semantics with bounded power-of-two detail zoom.
- [ ] 3.2 Render current-map terrain as fallback plus compatible cached ancestor terrain layers with one world-coordinate viewport transform.
- [ ] 3.3 Render Nexus markers/labels exactly once after terrain composition and keep click/pan transforms aligned.
- [ ] 3.4 Add a transient owning-local-player map marker while suppressing observer-local position substitution.

## 4. Observer compatibility

- [ ] 4.1 Increment the Nexus Observer protocol and validate the new bounded zoom semantics.
- [ ] 4.2 Keep map pixels and player position out of Observer snapshots.

## 5. Verification

- [ ] 5.1 Add unit/GameTest coverage for legacy lineage decoding, SCALE ancestry, LOCK ancestry, and invalid lineage rejection.
- [ ] 5.2 Add native-scale Client GameTest coverage for restored-detail zoom, pan/marker selection alignment, local-player marker, and Observer suppression.
- [ ] 5.3 Run strict OpenSpec validation plus the relevant build, GameTests, three-JVM Observer E2E, and Production Runtime validation; record evidence without masking regressions.
