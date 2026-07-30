# TotemNexus

Optional Space Unit, teleport, friend, death-node and distributed-spawn module
for Totem. It depends on TotemCore only and can subscribe to Remnant's optional
death-backpack lifecycle contract when both modules are installed.

`0.2.0` is the current candidate built against TotemCore `0.2.0`. It adds the
optional persisted reverse death-backpack binding, duplicate-binding
diagnostics and external-authority authorization coverage. The immutable
`0.1.4` graph remains the rollback baseline until a new graph is committed and
verified.

## Migration baseline

The repository now builds as a standalone Fabric 26.2 module with TotemCore as
its only Totem dependency. Its entrypoints deliberately register no Space Unit
gameplay yet: the compatibility bundle remains the sole owner of the preserved
`deadrecall:*` SavedData keys and payload IDs until the complete persistence,
server receiver, client UI and GameTest unit moves together.

## Standalone verification baseline

On 2026-07-23, a clean dedicated server containing only Fabric API, TotemCore
and TotemNexus reached `Done`. The log recorded both Totem initializers and no
DeadRecall or Remnant JAR was present. This proves dependency-safe standalone
loading only; teleport, legacy-world, multi-player, dimension and restart
qualification remain required before cutover.

See [AUTHORITY_MIGRATION.md](AUTHORITY_MIGRATION.md) for the required server
authority migration and validation gates.

## Current standalone artifact verification

The current `0.1.0-SNAPSHOT` artifact (SHA-512
`038660c572d9945dfcf69acc02236f3c49a8c9077d8415d3d20ac51e31e3f6d8077546aaac1f780bf1e182bfd6af53893c9043ca53626faa59e60a353867c4f4`)
was rebuilt and installed with only Fabric API and TotemCore. On the official
Fabric dedicated server with Fabric Loader `0.19.3`, Minecraft `26.2` and Java
25, both Totem initializers ran, the server reached `Done (0.722s)`, and all
three dimensions saved. DeadRecall and Remnant were absent.
