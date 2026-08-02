## Why

Teleport-array materials currently only distinguish valid, worn and amethyst
catalyst blocks. Builders cannot make meaningful trade-offs: every structural
block has the same size contribution, and a block cannot improve one aspect of
a route while making another worse.

## What Changes

- Add a data-driven, signed multi-attribute profile for every valid teleport
  array material.
- Evaluate material totals for structural capacity, route stability, arrival
  accuracy, target lock, arrival safety, wear resistance, maintenance
  efficiency, interference resistance, food efficiency, phase speed, cooldown
  recovery, route load, local scan-expansion radius, dimensional affinity and
  cross-dimension catalyst units.
- Permit a single material to contribute several attributes at once, including
  negative contributions, with server-side clamps for safe outcomes.
- Replace the special-case amethyst counting path with the profile model while
  preserving the current four-catalyst-unit-per-shard default behaviour.
- Establish the first built-in balance table for brick, metal, mineral, raw
  material and ore families, including the complete copper oxidation and wax
  matrix.
- Show the final material breakdown and its effects in the Space Unit map and
  the in-game Nexus manual.

## Impact

- Affected specs: `teleport-array-materials` (new capability).
- Affected server systems: structure scan snapshots, teleport quote and wear
  calculation, data reloads, SavedData migration and network payloads.
- Affected client/documentation systems: Space Unit map metrics and the Nexus
  teleport manual.
- **BREAKING gameplay change:** material-array detection now begins with the
  3x3x3 cube immediately surrounding the lodestone. Blocks farther away only
  contribute when a previously scanned material explicitly expands detection
  from its own position toward them.
- **BREAKING for datapack authors:** material balance moves from implicit tags
  and hard-coded amethyst handling to an explicit profile resource. Existing
  worlds remain compatible through built-in compatibility profiles.
