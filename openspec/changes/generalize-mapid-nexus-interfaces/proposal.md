## Why

Nexus currently treats a filled map as a portable interface only when the item
carries a Nexus-specific Space Unit claim and its MapId matches a server ledger
entry tied to the creating lodestone. That makes the lodestone more than the
intended convenience for choosing an exact map center, and prevents otherwise
valid vanilla filled maps from opening the same server-authoritative interface.

Native map data already owns MapId, center, dimension, pixels, lock state and
scale 0–4. Nexus still needs durable data for logical scales above 4, detailed
terrain, coverage and derivation history, but those properties belong to the
MapId rather than to an origin lodestone or an item-carried Space Unit UUID.

## What Changes

- **BREAKING**: stop requiring Nexus identity metadata on a filled-map item and
  stop treating a creating Space Unit UUID or original lodestone as map identity.
- Allow every server-resolvable filled map to open the Nexus map interface. On
  first use, atomically create a server-owned Nexus record keyed by its MapId
  without replacing, recentering or rewriting its native map data.
- Persist logical scale beyond vanilla scale 4, detailed terrain/coverage,
  derivation lineage and integrity expectations in the MapId-owned Nexus record.
- Preserve the exact native center through every Nexus derivation. Ordinary
  maps retain their vanilla grid-aligned center; lodestone creation remains a
  convenience that creates scale-0 map data centered on the exact lodestone X/Z.
- Remove original-lodestone presence/loading from open, scale, lock and
  derivation eligibility. The player's current position remains the teleport
  source, while the map's dimension/center/extent only define its viewport and
  painted authorization area.
- Build visible nodes at open/refresh time from current server Space Unit,
  discovery, friendship, visibility, status and painted-coverage data for the
  current holder. A map never stores or transfers a node list.
- Supersede the pending `enable-existing-map-activation` design: a filled map
  does not need to be activated at or bound to a lodestone before use.

This change does not implement filled-map cleansing; that remains in the
separate `add-cartography-map-cleansing` proposal.

## Approval

The user approved starting this MapId-owned interface scope after confirming
that Nexus data remains necessary for extended scale and detail, while the
lodestone should only provide exact centering. This authorizes implementation,
not commit, push or publication.

## Impact

- Affected specs: `map-interface`, `lodestone-interface-activation`
- Affected code: map item resolution/open authority, binding/record SavedData,
  exact-center creation, scale/lock derivation, detail/coverage ownership,
  cartography transaction guards, lifecycle tests and documentation
- Persistence: replace origin-bound map identity with a MapId-owned record;
  pre-change Nexus logical scale/detail compatibility is intentionally not
  promised, while valid native MapItemSavedData remains usable as a fresh base
- Observer/UI: existing module-owned Nexus Screen/provider remains responsible
  for semantic, read-only, framebuffer-free reconstruction
- Consumers: Core friendship, Remnant death nodes, Discord audit events and
  Observer transport contracts do not change

