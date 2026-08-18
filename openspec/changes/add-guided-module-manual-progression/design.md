## Context

TotemCore already assembles marked vanilla written books from independently
registered sections. Nexus, Remnant, and Alchemy register chapters, but their
sources currently generate a combined all-installed manual. Module root
advancements use `minecraft:tick`, so they complete before the player performs
any module action.

## Goals / Non-Goals

- Goals:
  - Provide one vanilla written-book onboarding guide exactly once per player.
  - Establish the visible sequence `craft source -> obtain guide -> module tree`.
  - Keep guide acquisition server-authoritative, localized, bounded, and safe
    for stacked books and full inventories.
  - Let every gameplay module remain independently installable with Core.
- Non-Goals:
  - A custom guide screen, new guide item, or baked-text texture.
  - An in-game advancement branch for Discord Bridge.
  - Removing or rewriting arbitrary player-authored books.

## Decisions

### Core-owned basic guide

Core registers one `totem:getting_started` section and activates its login
lifecycle. On join, Core checks the manually awarded `deadrecall:root`
criterion. If incomplete, it inserts a one-section canonical manual, drops it
beside the player only when inventory insertion fails, and then awards the
criterion. This advancement-backed marker is saved per player and avoids a
world-wide or per-tick inventory scan.

### One generated guide per module

Core adds a section-scoped acquisition operation. A source interaction accepts
a plain `minecraft:book` or any marked Totem guide. A plain book is consumed
only when a new module guide must be created; a generated guide acts as a
non-consumed reference. An existing carried guide for the same section is
refreshed instead of duplicated. Creation uses a section subset, so undiscovered
installed chapters are not exposed.

Legacy all-section manuals remain valid and refreshable. They are not treated
as proof that every new module-specific advancement was completed.

### Source mapping

The first crafted/source blocks are:

| Module | Crafted and right-clicked block |
| --- | --- |
| Remnant | Smithing Table |
| Alchemy | Brewing Stand |
| Automata | Copper Chest |
| Enchanting | Enchanting Table |
| Nexus | Lodestone |
| Vanilla Tweaks | Lectern |
| Excavation | Crafting Table |
| Villagers | Composter |

Discord Bridge has no gameplay source or advancement branch.

### Advancement topology

`deadrecall:root` is impossible-triggered and awarded only with the basic
guide. Each `*_root` uses `minecraft:recipe_crafted` for its source block and
instructs the player to record the block. Each `*_manual` uses an impossible
criterion awarded by the source interaction. Existing feature achievements are
reparented to `*_manual`; newly covered modules receive an equivalent branch.

### Vanilla visual language

All guides remain written books using the existing Core two-page renderer,
native font, vanilla item diagrams, translated components, integer coordinates,
and native advancement toasts. No new raster asset is required.

## Risks / Trade-offs

- A player can craft a generic source such as a crafting table very early.
  - Mitigation: the guide interaction remains the explicit second discovery
    step, and the advancement description names the target module.
- Old combined manuals reveal chapters without new guide advancements.
  - Mitigation: preserve player-owned generated content for compatibility while
    requiring explicit source interactions for advancement progression.
- A full inventory could hide the first guide.
  - Mitigation: use bounded inventory insertion followed by a server-side drop.

## Migration Plan

1. Publish the additive Core lifecycle and section-scoped acquisition API.
2. Convert existing Nexus, Remnant, and Alchemy sources.
3. Register the five missing module chapters and sources.
4. Replace tick roots, add guide nodes, and reparent descendants.
5. update exact Core pins, standalone metadata, and the DeadRecall lockstep
   bundle only after combined validation passes.

Rollback can leave generated written books in inventories; they remain ordinary
readable written books when the newer lifecycle is absent.

## Open Questions

- None. The user approved the source mapping and ordered implementation on
  2026-08-16.
