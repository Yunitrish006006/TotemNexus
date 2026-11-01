## Context

A filled map is an item that references world-owned `MapItemSavedData` through
its MapId. Copies can share that identity, and Nexus may also have binding and
detail records keyed by it. Clearing the shared SavedData in place would alter
every copy and could invalidate server-owned lineage. The requested outcome is
instead the vanilla empty-map item that can enter the existing activation flow.

The cartography table has native preview and result-taking behavior but no
data-pack recipe for a water-bucket cleansing operation. Its preview is not an
authorization boundary: the server must revalidate the live slots when the
result is actually taken.

## Goals / Non-Goals

- Goals:
  - Recycle one genuine filled-map item into one genuine empty map.
  - Consume one water bucket and conserve its empty-bucket remainder.
  - Commit only from a current, server-authoritative cartography result take.
  - Preserve all unrelated copies and persisted records for the old MapId.
- Non-Goals:
  - Importing, migrating, or interpreting old terrain/detail formats.
  - Retaining the old MapId, pixels, center, scale, lock state, decorations, or
    Nexus identity on the empty result.
  - Deleting orphaned map data or binding records as part of crafting.
  - Adding a custom Screen, packet, or Observer framebuffer representation.

## Decisions

### Return a new empty-map item instead of clearing shared SavedData

The result is one `minecraft:map` with no MapId or Nexus-specific components.
The transaction consumes only the supplied filled-map stack. It does not edit
or delete the referenced SavedData, Nexus detail pages, lineage, or binding
ledger because another physical copy may still reference them.

This intentionally replaces the pending direction that would activate an
existing filled map while preserving its data. Players cleanse first and then
use the established empty-map activation flow when they want a fresh Nexus map.

### Integrate with the native cartography menu transaction

The server offers a cleansing preview only when the map slot contains exactly
one filled map and the additional slot contains a water bucket. Result pickup
revalidates those live slots and the expected empty-map result before changing
inventory. Preview recomputation never mutates map storage or consumes items.

The commit consumes one filled map and one water bucket, produces one empty
map, and places one empty bucket using deterministic remainder handling. The
implementation must cover pickup, quick-move, hotbar swap and drop behavior;
if the selected path cannot conserve all outputs, it refuses before mutation.
Creative mode does not bypass conservation or duplicate either result.

### Reuse native UI and semantic observation

No Nexus-owned visual replacement is introduced. The native cartography Screen
continues to render its slots, and Observer remains framebuffer-free through
the existing vanilla semantic adapter. Compatibility tests must prove that the
new item combination does not create an interactive authority path for a
viewer and that absent/incompatible providers retain their normal fallback.

## Risks / Trade-offs

- A stale preview could consume a replacement input. Mitigation: compare and
  validate the live map, water bucket and result at take time and fail closed.
- A full inventory could lose or duplicate the empty bucket during quick-move.
  Mitigation: preflight all destinations and test full-inventory paths before
  committing the transaction.
- In-place SavedData clearing could corrupt other map copies. Mitigation: never
  mutate or delete old MapId-owned data during cleansing.
- Native menu mixins may accidentally affect paper, glass-pane or map-copy
  operations. Mitigation: keep the water-bucket branch exact and rerun native
  cartography regression coverage.

## Migration Plan

1. Remove or disable the pending filled-map activation compatibility path while
   retaining ordinary empty-map activation.
2. Add the exact water-bucket preview and take-time transaction.
3. Validate server authority, inventory conservation, save/reload isolation,
   native cartography regressions, Observer compatibility, three-JVM E2E and
   production runtime behavior before considering the change complete.

Rollback removes the cleansing branch and leaves old map data untouched; no
persisted migration is required.

## Open Questions

- None. The empty result deliberately has no MapId, and cleansing deliberately
  leaves old persisted records available to any remaining copies.

