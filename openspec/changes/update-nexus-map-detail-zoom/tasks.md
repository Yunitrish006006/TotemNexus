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

- [x] 4.1 Version the Nexus Observer provider and validate bounded zoom/terrain semantics; current map/detail behavior uses exact protocol 5.
- [x] 4.2 Keep map pixels and raw player world coordinates out of Observer semantic snapshots; allow only a bounded map-local target decoration (off-map flag, signed X/Y bytes, rotation 0–15).
- [x] 4.3 Render the relayed observed-target decoration in Observer mode without ever falling back to the observer client's own local player.

## 5. Verification

- [x] 5.1 Add unit/GameTest coverage for legacy lineage decoding, SCALE ancestry, LOCK ancestry, and invalid lineage rejection.
- [x] 5.2 Add native-scale Client GameTest coverage for restored-detail zoom, pan/marker selection alignment, owning-local marker, Observer target-marker rendering, and no observer-local fallback.
- [x] 5.3 Add Observer-specific regression coverage proving semantic 2x/4x zoom submits real compatible finer MapIds rather than only scaling the coarse map.
- [x] 5.4 Run strict OpenSpec validation plus the relevant Nexus build/GameTests, production map runtime validation, and pinned TotemObserver three-JVM/runtime integration; record exact evidence without masking regressions.
