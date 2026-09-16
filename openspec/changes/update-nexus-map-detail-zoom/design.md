## Context

Nexus maps are real vanilla maps. The server owns a persisted MapId binding to a lodestone anchor, cartography SCALE creates a new MapId with the same exact center and dimension at the next vanilla map scale, and the client Screen renders cached `MapItemSavedData` through Minecraft's `MapRenderer`. The current UI zoom multiplies the same 128×128 render state by an integer scale, so it cannot restore information discarded when a map was expanded.

The existing binding registry proves anchor identity but does not retain derivation ancestry. Therefore legacy maps cannot safely infer that another same-anchor MapId is their historical parent. Nexus map data also uses `trackingPosition=false`; local-player presentation must remain transient rather than changing shared persisted map behavior.

## Goals / Non-Goals

- Goals:
  - Keep vanilla MapId, map pixels, update packets, and MapRenderer as the terrain source.
  - Preserve server-verifiable parentage when a Nexus map is scaled.
  - Reuse parent MapIds as historical higher-resolution LOD layers.
  - Keep the current map as a complete coarse fallback while finer ancestors only overlay their own smaller world extents.
  - Show the owning local player on the production map Screen without writing viewer-specific state into SavedData or item components.
  - Let an authorized Observer reconstruction render the observed target's transient player marker using only bounded map-local decoration state.
  - Preserve bounded pan, marker hit testing, server-authoritative teleport, and framebuffer-free Observer behavior.
- Non-Goals:
  - Scan chunks or construct a second world-map database.
  - Synthesize terrain detail that was never recorded by a vanilla map.
  - Infer ancestry for legacy maps from matching anchor/center alone.
  - Persist player decorations or transmit raw observed-player world coordinates, screenshots, framebuffers, or video through Observer.

## Decisions

### Record SCALE lineage in the server-owned binding registry

`NexusMapBindingSavedData.Entry` gains a bounded list of ancestor MapIds ordered from finest/oldest to immediate parent. On SCALE, `derive` validates the source binding and result center/dimension, then copies the source ancestry and appends the source MapId. On LOCK, lineage is copied without appending a same-scale parent. Legacy entries decode with an empty lineage and remain valid.

Every resolved ancestor remains untrusted until its own binding and `MapItemSavedData` are validated against the same unit, anchor, center, dimension, and a strictly finer vanilla scale. Missing or invalid ancestors are ignored rather than force-loaded or reconstructed.

### Synchronize only proven ancestor vanilla maps

The existing interface-open path continues to synchronize the current filled map through Mojang's normal map-data packet before opening the Screen. Once the owning production Screen exists, it requests historical detail for that exact MapId. The server re-resolves the player's actual held interface, validates the requested current binding, validates every recorded ancestor independently, sends vanilla map-data packets for the accepted maps, and then returns a bounded `NexusMapDetailPayload` containing only their MapIds.

Pixel data remains exclusively in vanilla map packets and never enters `SpaceUnitMapPayload`, `NexusMapDetailPayload`, or Observer snapshots. An Observer reconstruction does not issue the owner-only detail request. TotemObserver protocol-5 terrain relay reuses the same Nexus-authorized detail identities and vanilla map packets for the active observed target.

### Use world-coordinate composition instead of scaling one texture

The Screen owns one viewport transform expressed relative to the current map's world center. The current map always renders first as the coarse fallback. For each cached finer ancestor, the renderer places that 128×128 vanilla map over the exact world extent represented by the ancestor and clips it to the viewport. Because Nexus SCALE preserves the exact center, these layers are concentric and require no resampling metadata beyond vanilla map scale.

Zoom is expressed as a power-of-two factor. At factor `2^n`, the viewport can reveal up to `n` finer scale levels, bounded by the current map scale and available ancestry. Fine layers remain crisp because each vanilla map pixel is rendered at an integer screen scale relative to the selected detail level. Observer mode uses the same production Screen composition and the same client map cache, so 2×/4× semantic zoom must submit the corresponding real finer MapIds rather than merely enlarge the coarse render state.

### Separate terrain composition from Nexus/player overlays

Multi-layer terrain rendering must not duplicate decorations. Terrain layers are rendered with decorations removed from their submitted render states. Nexus destination markers, selected-target state, labels, and the player marker are rendered once using the current viewport world transform.

The local player marker is added only on the owning production Screen, only when the client player is in the map dimension, and only when the player lies within the current map's bounded coverage. It uses the vanilla player decoration visual and current yaw but is never written back to cached/persisted `MapItemSavedData`.

### Observer protocol 5 carries only bounded target decoration state

The map Observer variant reconstructs the same production Screen and receives semantic selection/viewport state plus the existing owner payload. Protocol 5 also permits one privacy-reviewed transient target-player decoration represented only as:

- an off-map boolean;
- signed map-local X and Y bytes in `[-128, 127]`;
- a vanilla rotation nibble in `[0, 15]`.

These values are derived on the observed target client from its current map projection. The snapshot never carries the target player's raw world X/Z coordinates for this marker. The marker fields are all-or-nothing and range-validated before mutating the Observer Screen. If the target marker is absent, Observer renders no player marker; it must never synthesize one from the observer client's own `Minecraft.player`.

Map pixels remain excluded from the semantic snapshot. Terrain/detail pixels continue to travel only through the separately authorized vanilla map-packet relay, while the semantic snapshot carries zoom/pan, bounded terrain geometry/revision metadata, selection state, and the optional map-local target decoration.

The extracted TotemObserver relay accepts a snapshot only when Target and Observer advertise the exact same `family + protocol` provider identity. There is no conversion between protocol 3/4/5 semantics. Current Nexus map terrain/detail and observed-target decoration behavior is protocol 5.

## Risks / Trade-offs

- Extra vanilla map update packets are requested only while an owning map Screen is open and are bounded by the vanilla lineage depth (scales 0–4). Mitigation: maximum four ancestors and only validated existing maps are sent.
- Legacy expanded maps have no recoverable lineage proof. Mitigation: preserve their current behavior; the next valid SCALE begins a provable chain from that source forward.
- Rendering several MapRenderStates can duplicate or obscure decorations. Mitigation: terrain render states contain no decorations; overlays render exactly once after all terrain layers.
- A missing client ancestor cache can temporarily reduce restored detail. Mitigation: the current map is always the complete coarse fallback; Observer renders only compatible detail that was actually relayed/cached.
- A player marker is viewer-sensitive state. Mitigation: protocol 5 transmits only bounded map-local decoration bytes, never raw player world coordinates, and missing/invalid marker metadata cannot fall back to observer-local position.

## Migration Plan

1. Decode existing binding entries with empty ancestry.
2. Start recording ancestry on new SCALE/LOCK derivations without rewriting old worlds.
3. Keep current MapId validation unchanged; invalid ancestry is ignored independently.
4. Use protocol 5 for the current extracted TotemObserver pairing: exact provider identity, authorized terrain relay, bounded map-local observed-target decoration, and no observer-local substitution.
5. Keep older provider protocols exact-match only; do not reinterpret their semantic state as protocol 5.
6. If the detail feature is rolled back, current MapIds and item bindings remain valid; the optional lineage field is not an authorization source.
