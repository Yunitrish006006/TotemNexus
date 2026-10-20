## Why

The user wants Nexus-bound maps to keep expanding beyond vanilla's scale-four
limit while retaining the original binding center. The current recipe,
persistence, coverage checks, recording, and detail presentation all assume
vanilla scales; removing only the recipe limit would produce incorrect maps.

## What Changes

- Continue the existing paper-based expansion workflow beyond vanilla scale 4.
  Each successful expansion doubles width and height around the exact original
  center. Expansion stops only once the map encompasses Minecraft's supported
  coordinate domain; it cannot expose terrain outside the game world.
- Persist a server-owned effective extent independently of vanilla map scale.
  Preserve existing map identities, centers, exploration, clone/lock semantics,
  and independent use after the original lodestone is destroyed.
- Adapt terrain recording, detailed zoom, marker placement, selection, and
  teleport coverage to the effective extent, with bounded work and packets.
- Preserve the current requirement that expansion itself needs the original
  loaded lodestone. Removing that requirement is a separate behavior change.
- Retain actual explored coverage as authorization proof; a coarse overview
  pixel must not by itself authorize an unrecorded destination.
- Make the map a strict upgrade of the compass: a currently compass-authorized
  destination remains selectable even outside the map's extent or painted
  terrain. Use in-map markers, clickable edge indicators and map panning;
  the user explicitly rejects a destination list. No separate compass is needed.
- Combine current-holder compass discovery with legitimate map coverage, while
  retaining destination permissions, current validity, cost and safe landing.
  Unknown terrain stays blank; this does not grant discovery of other nodes.

## Impact

Implementation owner: TotemNexus. Audit the owning Observer provider and the
TotemObserver consumer if semantic metadata changes. No shared Core API change
is planned. Vanilla unbound maps retain vanilla behavior.

The user approved this revised proposal for implementation on 2026-10-17.
Implementation starts with authoritative extent and backward-compatible ledger
persistence; the separate independent-bound-map runtime regressions remain open.
No merge, push, version change, or release is included.
