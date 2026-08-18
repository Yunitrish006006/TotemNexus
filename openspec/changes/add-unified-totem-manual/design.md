## Context

`NexusTeleportManual` currently creates a `minecraft:written_book` whose seven
pages are translatable components. TotemCore is required by every feature
module but intentionally registers no gameplay objects. All active feature
modules currently require the exact Core 0.4.0 artifact.

The unified manual must work when Nexus or Remnant is installed alone, when
both are installed, and when more Totem modules later add sections. It must not
introduce direct feature-to-feature dependencies.

## Goals / Non-Goals

- Goals:
  - Keep one recognizable Totem manual instead of one book per module.
  - Let independently installed feature modules register localized sections.
  - Preserve Nexus's current lodestone discovery flow.
  - Give standalone Remnant a discoverable manual source.
  - Migrate the generated Nexus book without matching arbitrary player books.
  - Refresh existing canonical manuals when installed sections change.
- Non-Goals:
  - A custom guide screen or search UI in this change.
  - Unlock-gating installed module documentation.
  - Global inventory, container, offline-player or chunk migration.
  - Registering a custom manual item in TotemCore.

## Decisions

### Shared API without a Core gameplay entrypoint

TotemCore will add `dev.totem.core.api.v1.manual` with:

- an immutable section definition containing an identifier, deterministic
  order, localized title key and localized page keys;
- a duplicate-safe process-local registry;
- a stateless assembler and player inventory helper operating on vanilla
  `ItemStack` and `WrittenBookContent`;
- an idempotent lifecycle registration helper that feature modules explicitly
  activate after registering sections.

Core itself will still register no item, block, command or persisted feature
data. It will own a client-only Mixin for the canonical manual's two-page
presentation. The API is analogous to the existing legacy migration registry:
feature modules invoke it to implement their own acquisition flows.

### Core-owned two-page rendering with optional page overlays

TotemCore will identify only canonical manuals by their generated cover page,
then replace vanilla's single-page book presentation with a centered two-page
spread. Page navigation advances by two and ordinary written books remain
unchanged.

Core exposes a client-only page-overlay registry invoked after a manual page's
base text is rendered. TotemRemnant registers its recipe and smithing overlays
there instead of owning the book layout Mixin. This avoids Mixin ordering and
cancelation conflicts while keeping feature-specific rendering out of Core.

### Canonical manual representation

The manual remains `minecraft:written_book`. It uses:

- a translatable custom name `item.totem.manual`;
- author string `Totem`;
- a generated cover and contents page followed by ordered section dividers and
  pages;
- `DataComponents.CUSTOM_DATA` marker `totem_manual`, schema version, section
  identifiers and a deterministic content revision.

The registered installed section set is authoritative. Stored section IDs are
diagnostic/version metadata, not permission or discovery gates.

### Acquisition and consolidation

Nexus keeps plain-book-on-lodestone. Remnant adds plain-book-on-smithing-table.
Either source calls the same helper:

1. If the active hand contains a plain book and the other hand contains a
   recognized Totem manual, refresh the other-hand manual and do not consume
   the plain book.
2. If no recognized manual is held, convert exactly one active-hand plain book
   into the canonical manual, retaining current overflow-to-inventory/drop
   behavior for stacked books.
3. If both hands contain recognized generated Totem manuals, keep the active
   hand as primary, rebuild it with all installed sections and consume the
   secondary generated manual.
4. Do not consolidate arbitrary inventory copies automatically.

A marked canonical manual is safe to refresh on login. Unmarked legacy books
are migrated only during an explicit manual-source interaction.

### Legacy Nexus recognition

An unmarked legacy manual is recognized only when all generated invariants
match:

- item is `minecraft:written_book`;
- title is `Nexus Teleport Manual`;
- author is `Totem Nexus`;
- custom name uses the Nexus manual translation component;
- generated page count is seven.

This avoids converting an ordinary player book based on title alone.

### Localization and page limits

Each feature module owns the translations for its section title and pages.
Core owns only the canonical name, cover, contents and shared status messages.
The assembler sorts by `(order, section id)`, rejects duplicate IDs and enforces
the vanilla written-book page limit. Registration or assembly failure must be
explicit rather than silently dropping a feature section.

### Version coordination

The new API is additive, so Core moves from 0.4.0 to 0.5.0 under its documented
minor-version policy. Nexus and Remnant move to new patch releases and require
Core 0.5.0. Before releasing the combined set, every exact `totem-core` pin and
the DeadRecall lockstep manifest must be updated together; implementation may
not leave a published mixed 0.4.0/0.5.0 module set.

## Risks / Trade-offs

- Vanilla written books have limited navigation and page count.
  - Mitigation: deterministic compact sections, generated contents, bounded
    registration and an API reusable by a later custom screen.
- Core API additions require lockstep version coordination.
  - Mitigation: one additive minor bump and mechanical exact-pin verification
    across the active module set.
- A shared book Mixin can affect unrelated written books if identification is
  too broad.
  - Mitigation: activate only when page zero is the canonical Totem cover
    translation and retain vanilla behavior for every other book.
- Login refresh mutates a carried marked book.
  - Mitigation: only the explicit canonical marker is refreshed; legacy books
    require an explicit interaction.
- A smithing-table book interaction could conflict with another mod.
  - Mitigation: handle only a plain book or recognized Totem manual and return
    PASS for every other item.

## Migration Plan

1. Publish and verify Core 0.5.0 API.
2. Update Nexus to register its existing seven pages and use the shared grant
   helper.
3. Add Remnant sections and smithing-table acquisition.
4. Update exact Core pins and rebuild the active Totem module set.
5. Verify old generated Nexus manuals upgrade on interaction.
6. Update the DeadRecall lockstep bundle only after standalone artifacts pass.

Rollback retains the current Nexus manual implementation and omits Remnant
manual acquisition; canonical marked books remain ordinary readable written
books even without the shared helper.

## Open Questions

- None for the first vanilla-written-book implementation. A custom guide screen
  remains a separate future proposal.
