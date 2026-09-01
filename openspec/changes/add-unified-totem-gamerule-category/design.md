## Context

Minecraft 26.2 already provides native world-creation and in-world Game Rules
screens. Registered rules are grouped by their `GameRuleCategory`; the in-world
screen receives current values from the server and sends edits through vanilla
packets. TotemCore is required by every split Totem module, while individual
game rules remain owned by Remnant, Locksmith, or Nexus.

## Goals / Non-Goals

- Goals: one native Totem category, loaded-module-only discovery, bilingual
  labels and descriptions, and stable persisted rule identifiers.
- Non-Goals: a custom settings screen, copied rule state, module discovery by
  hard dependency, or changes to rule behavior/defaults.

## Decisions

### TotemCore owns one category instance

TotemCore registers `GameRuleCategory` ID `totem:rules` and exposes the instance
through its versioned API. Each owning module passes that instance to its own
`GameRuleBuilder`. This preserves optional dependency boundaries: only a loaded
module registers its rules, and no module needs to inspect another module.

### Use the vanilla management path

The implementation relies on Minecraft's `WorldCreationGameRulesScreen` and
`InWorldGameRulesScreen` instead of adding a Totem Screen or Menu. Boolean and
enum controls therefore keep the vanilla interaction, narration, focus,
permission, synchronization, and validation paths. Because no Screen is added
or modified, no new Observer family or semantic snapshot is required.

### Preserve registered rule IDs

Only each rule's category reference changes. The existing `totem:*` and
`deadrecall:*` rule identifiers remain stable so saved worlds, commands, data
packs, and server automation continue to resolve the same values.

## Risks / Trade-offs

- Older TotemCore artifacts do not expose the shared category API. Release
  coordination must publish Core before module builds adopt it and update their
  minimum Core dependency in a later versioning change.
- The category appears only when TotemCore client language resources are
  installed, which is already guaranteed by the minimum TotemCore dependency
  gate.

## Migration Plan

No world-data migration is needed. On the first launch with the coordinated
module versions, Minecraft presents the same registered rule IDs and stored
values under the new Totem category.
