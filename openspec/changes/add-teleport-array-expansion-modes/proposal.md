## Why

Teleport-array builders need a world-level choice between the existing organic,
placement-driven shape and a predictable symmetric range centred on the
lodestone. A single authoritative switch avoids datapack forks and keeps every
server calculation on the same geometry.

## What Changes

- Add the enum gamerule
  `deadrecall:teleport_array_expansion_mode` with `local` and `centered` command
  values; default to `local` for compatibility.
- Preserve the current local expansion graph unchanged.
- Add a bounded lodestone-centred fixed-point expansion algorithm that grows
  complete cubes and takes the maximum emitter reach rather than stacking
  emitters in the same layer.
- Make structure snapshots, teleport calculations, wear/repair targeting and
  array/build-site visualization consume the selected production scan.
- Refresh loaded active lodestone snapshots when the gamerule changes while
  retaining the loaded-only, no-force-load boundary.
- Document the command and both exact algorithms in English and Traditional
  Chinese player/admin surfaces.

## Impact

- Affected specs: `teleport-array-expansion-modes` (new capability)
- Affected server code: gamerule bootstrap, teleport-array traversal, snapshot
  freshness and loaded lodestone refresh
- Affected tests: JUnit compatibility/resources and Fabric Server GameTests
- Protocol/UI impact: no packet, Observer semantic snapshot, Screen or client
  rendering change
