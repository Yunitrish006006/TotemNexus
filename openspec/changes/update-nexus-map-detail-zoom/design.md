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
  - Preserve bounded pan, marker hit testing, server-authoritative teleport, and framebuffer-free Observer behavior.
- Non-Goals:
  - Scan chunks or construct a second world-map database.
  - Synthesize terrain detail that was never recorded by a vanilla map.
  - Infer ancestry for legacy maps from matching anchor/center alone.
  - Persist player decorations or transmit observed-player position through Observer solely for this feature.

## Decisions

### Record SCALE lineage in the server-owned binding registry

`NexusMapBindingSavedData.Entry` gains a bounded list of ancestor MapIds ordered from finest/oldest to immediate parent. On SCALE, `derive` validates the source binding and result center/dimension, then copies the source ancestry and appends the source MapId. On LOCK, lineage is copied without appending a same-scale parent. Legacy entries decode with an empty lineage and remain valid.

Every resolved ancestor remains untrusted until its own binding and `MapItemSavedData` are validated against the same unit, anchor, center, dimension, and a strictly finer vanilla scale. Missing or invalid ancestors are ignored rather than force-loaded or reconstructed.

### Synchronize only proven ancestor vanilla maps

When opening a valid filled-map interface, the server continues to send the current map's vanilla update packet and additionally sends update packets for validated lineage MapIds that exist in server map storage. Pixel data remains in vanilla map packets and never enters `SpaceUnitMapPayload` or Observer snapshots. The Nexus payload carries only bounded ancestor MapIds needed to identify locally cached layers.

### Use world-coordinate composition instead of scaling one texture

The Screen owns one viewport transform expressed relative to the current map's world center. The current map always renders first as the coarse fallback. For each cached finer ancestor, the renderer places that 128×128 vanilla map over the exact world extent represented by the ancestor and clips it to the viewport. Because Nexus SCALE preserves the exact center, these layers are concentric and require no resampling metadata beyond vanilla map scale.

Zoom is expressed as a power-of-two factor. At factor `2^n`, the viewport can reveal up to `n` finer scale levels, bounded by the current map scale and available ancestry. Fine layers remain crisp because each vanilla map pixel is rendered at an integer screen scale relative to the selected detail level.

### Separate terrain composition from Nexus/player overlays

Multi-layer terrain rendering must not duplicate decorations. Terrain layers are rendered from transient copies with decorations removed. Nexus destination markers, selected-target state, labels, and the local-player marker are rendered once using the current viewport world transform.

The local player marker is added only on the owning production Screen, only when the client player is in the map dimension, and only when the player lies within the current map's bounded coverage. It uses the vanilla player decoration visual and current yaw but is never written back to cached/persisted `MapItemSavedData`.

### Observer keeps semantic viewport state but no owner position

The map Observer variant continues to reconstruct the production Screen and receives only semantic selection/viewport state plus the existing payload. Since zoom semantics change, the provider protocol is incremented. Observer read-only rendering must not create a player marker from the observer client's own `Minecraft.player`; owner-player position is omitted unless a later approved protocol explicitly adds a privacy-reviewed semantic field.

## Risks / Trade-offs

- Extra vanilla map update packets on Screen open increase one-time bandwidth by at most the bounded lineage depth (vanilla scales 0–4). Mitigation: maximum four ancestors and only validated existing maps are sent.
- Legacy expanded maps have no recoverable lineage proof. Mitigation: preserve their current behavior; the next valid SCALE begins a provable chain from that source forward.
- Rendering several MapRenderStates can duplicate or obscure decorations. Mitigation: terrain copies contain colors only; overlays render exactly once after all terrain layers.
- A missing client ancestor cache can temporarily reduce restored detail. Mitigation: coarse current map is always complete fallback and normal vanilla packets are sent before the Nexus Screen payload.

## Migration Plan

1. Decode existing binding entries with empty ancestry.
2. Start recording ancestry on new SCALE/LOCK derivations without rewriting old worlds.
3. Keep current MapId validation unchanged; invalid ancestry is ignored independently.
4. If the feature must be rolled back, older code can ignore the new optional SavedData field while current MapIds and item bindings remain valid.
