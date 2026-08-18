# Project Context

## Purpose

Totem Nexus owns server-authoritative Space Units: player-registered lodestone
anchors, their discovery and access rules, teleport quotes, safe landing, and
the Space Unit map. It is released as an independent Fabric module.

## Tech Stack

- Java 25, Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.154.2 and
  Fabric Loom 1.17.12.
- TotemCore 0.5.0 is the only required Totem dependency.
- Persisted and resource identifiers retain the `deadrecall` namespace for
  compatibility with existing worlds and clients.

## Project Conventions

### Code Style

- Server-side Space Unit authority lives under `dev.totem.nexus.space`; client
  rendering and screens stay under `src/client`.
- Treat all client input as untrusted. The server calculates structure state,
  resource cost, route safety and teleport outcomes.
- Put player-visible text in both `en_us` and `zh_tw` resources.

### Architecture Patterns

- `NexusSpaceUnitSavedData` is the persisted source of truth for lodestones,
  discovery and their structure snapshots.
- Teleport calculations must remain deterministic, bounded and independent of
  client-provided numerical values.
- Data-driven rules may extend vanilla materials, but must not force-load
  chunks or make a missing data resource break an upgraded world.

### Testing Strategy

- Use JUnit for codecs, aggregation, rounding and quote boundaries.
- Use Fabric GameTests for data reloads, real lodestone scans, cross-dimension
  cost, migration and dedicated-server behavior.
- Run `../TotemCore/gradlew --no-daemon build --console=plain` with Java 25.

### Git Workflow

- Keep OpenSpec changes active until their implementation and release checks
  are complete.
- Do not commit, publish or deploy without explicit user approval.

## Domain Context

The current lodestone structure scanner treats every permitted structure block
as one equal slot in a fixed 5x3x5 scan. Completeness, mirror symmetry and
worn blocks produce the current stability value. Amethyst blocks are a special
catalyst: every four blocks across lodestone endpoints reduces a
cross-dimension amethyst-shard cost by one, with a one-shard minimum.

## Important Constraints

- Existing `deadrecall` SavedData and packet identifiers must remain readable.
- Teleport arrays must have bounded scans and must never load chunks merely to
  evaluate a material.
- No material combination may make a paid cross-dimension teleport free or
  create a negative cost, negative preparation time, or invalid landing range.

## External Dependencies

- Required: `totem-core =0.5.0`.
- Fabric's resource reload and tag systems provide the data-driven material
  configuration surface.
