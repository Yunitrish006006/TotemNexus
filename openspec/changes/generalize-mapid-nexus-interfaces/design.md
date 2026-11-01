## Context

`MapItemSavedData` already persists a filled map's center, dimension, native
scale, pixels, decorations, tracking flags and lock state under a world-owned
MapId. Nexus map creation deliberately bypasses vanilla center snapping so a
lodestone-created scale-0 map can use the lodestone's exact X/Z. The native
center remains available after the lodestone disappears.

Nexus also supports logical scale beyond vanilla's maximum scale 4 and retains
fine terrain/detail lineage that native map storage cannot represent. The
current server ledger stores those concerns together with the creating Space
Unit UUID and anchor, while the item repeats a Nexus identity claim. Scaling
still checks origin-lodestone availability even though established portable
maps can already open and teleport independently of that origin.

## Goals / Non-Goals

- Goals:
  - make any server-resolvable filled map a first-class Nexus map interface;
  - preserve its exact existing native geometry and pixels on first use;
  - retain extended scale, detail, coverage and derivation safely by MapId;
  - keep all opening, registration, derivation and teleport decisions on the
    server and fail closed on missing or contradictory state;
  - make lodestone map creation only an exact-center convenience.
- Non-Goals:
  - storing node lists in map items or map records;
  - using the map center as the player's teleport source;
  - importing old Nexus logical scale/detail records into the new ownership
    model;
  - garbage-collecting native map data or Nexus records when one physical copy
    is consumed or lost;
  - implementing the water-bucket cleansing operation in this change.

## Decisions

### Native data owns native geometry; Nexus records own extensions

For scale 0–4, the live server `MapItemSavedData` is authoritative for MapId,
center, dimension, native scale, pixels, decorations, tracking flags and lock
state. Nexus persists a record keyed only by MapId containing the logical scale,
detail/coverage roots, parent/ancestor lineage, revision and enough expected
native geometry to reject contradictory or substituted backing data.

The record does not contain a creating Space Unit UUID or require an origin
lodestone. MapIds are world-owned and not reused during normal world lifetime;
all physical copies carrying the same MapId intentionally share the same native
and Nexus records.

Logical scale 0–4 must agree with native scale. Above 4, native storage remains
at scale 4 while the Nexus record is authoritative for the larger logical
extent. Every derived MapId retains the exact parent center and dimension.

### Bootstrap ordinary maps atomically on first Nexus use

When a held filled map has a MapId and resolvable native data but no Nexus
record, the server creates a baseline record from an immutable snapshot of that
data. The initial logical scale equals its native scale and lineage/detail roots
start empty. Bootstrap does not mutate the item or native map data.

Record publication and interface-session creation form a server-thread-owned,
fail-closed operation. Read-only validation happens first; if storage cannot
accept the record, no session opens and no partial record is advertised. A
second copy or concurrent retry observes the same compatible record rather than
creating another identity.

Item-carried legacy Nexus claims are neither authority nor a reason to reject a
valid native map. Unsupported pre-change extended scale/detail is not inferred
from native scale 4 and is not imported. Native center, dimension and pixels
remain usable as the new baseline.

### Treat lodestone creation as exact-center map creation

Using an empty map on an authorized lodestone still allocates a filled map whose
scale-0 native center is the lodestone's exact X/Z instead of vanilla's 128-block
grid center. It immediately publishes the same MapId-owned baseline record used
by ordinary maps, with no creating-unit field or item identity metadata.

Removing or unloading the lodestone cannot affect later open, lock, scale or
detail derivation. Expansion preserves the persisted center exactly and does
not load chunks to revalidate an obsolete origin.

### Recompute holder-visible nodes; keep teleport source independent

Opening or refreshing the interface queries current server Space Unit records
and filters them for the current player by active status, visibility/friendship,
discovery or painted coverage, map dimension and logical extent. No result list
is persisted in or copied with the map.

The map center and extent control presentation and map-based authorization only.
Teleport quotes and execution continue to use the player's current authoritative
position/dimension as the source and revalidate the held MapId and destination.

## Risks / Trade-offs

- A forged or substituted MapId could claim another map's extension record.
  Mitigation: resolve native data server-side and compare persisted geometry,
  lineage and revision; item metadata never supplies authority.
- Lazy bootstrap mutates SavedData during open. Mitigation: perform it on the
  server thread after complete preflight and publish once before the session.
- Removing origin gates could accidentally force-load the old anchor.
  Mitigation: remove the anchor from the record and test open/derivation with
  unloaded and destroyed former origins.
- Native scale 4 cannot reveal whether an unsupported old map once represented
  logical scale 5+. Mitigation: deliberately start unimported maps at their
  native scale and make no legacy-compatibility claim.
- Copies are not enumerable, so eager cleanup is unsafe. Mitigation: retain
  MapId-owned records; reclamation is a separate bounded design.

## Migration Plan

1. Add the MapId-owned record model and strict native snapshot/bootstrap tests
   without enabling production callers.
2. Switch ordinary-map resolution/opening to atomic lazy bootstrap.
3. Switch exact-center creation and scale/lock/detail derivation to the new
   record, removing item Space Unit identity and origin availability gates.
4. Remove the superseded filled-map-at-lodestone activation path and its legacy
   identity assumptions.
5. Validate save/reload, native cartography, permissions, Observer semantics,
   dedicated server, three-JVM E2E and Production Runtime before completion.

Rollback disables new bootstrap/open callers. New native maps remain valid
vanilla maps; the added Nexus records are inert without those callers.

## Open Questions

- None for this scope. Record reclamation and filled-map cleansing remain
  separate changes.

