## Why

Nexus currently creates a standalone seven-page written manual, while Remnant
needs its own in-game tutorial and future Totem modules will have the same
need. Separate generated books would fill player inventories, duplicate common
instructions and drift independently as the installed Totem module set
changes.

## What Changes

- Add a shared TotemCore API for feature modules to register ordered manual
  sections made from localized page components.
- Compose registered sections into one marked vanilla written book named
  `Totem 手冊` / `Totem Manual`; TotemCore will own the canonical client-side
  two-page presentation while continuing to register no item, block or feature
  SavedData.
- Make manual content reflect every installed module that registered a
  section, with a generated cover and table of contents.
- Replace Nexus manual creation with the shared assembler while preserving the
  plain-book-on-lodestone acquisition flow and migrating existing generated
  Nexus manuals.
- Add Remnant backpack, dyeing, dropped-item protection, death-backpack and
  portable-container-safety sections, obtainable by recording a plain book at
  a smithing table.
- Refresh marked manuals after login when the registered section revision has
  changed, and explicitly consolidate recognized manuals held in both hands
  without scanning containers, offline players or unloaded chunks.
- Publish the additive Core API as TotemCore 0.5.0 and coordinate exact Core
  dependency pins so the Totem standalone set remains co-installable.

## Impact

- Affected specs: `unified-totem-manual` (new capability).
- Affected Core API: new `dev.totem.core.api.v1.manual` contracts and assembler.
- Affected gameplay modules: TotemNexus manual acquisition and TotemRemnant
  tutorial acquisition/content.
- Affected compatibility: existing Nexus manuals remain recognizable and are
  upgraded in place during an explicit manual interaction.
- Affected release coordination: TotemCore receives an additive minor version;
  exact Core pins in the active Totem module set and DeadRecall lockstep bundle
  must move together before release.
- No new external dependency, custom item ID, packet, screen, world SavedData
  or forced chunk loading is introduced.
- TotemRemnant's recipe overlays remain optional client extensions, while the
  base two-page layout works with Core and any single feature module.
