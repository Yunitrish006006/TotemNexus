## Why

Totem world rules currently appear under unrelated vanilla categories, and some
rules expose raw translation keys. Administrators need one discoverable,
localized place to review and edit the rules contributed by the Totem modules
that are actually installed.

## What Changes

- Add one shared `totem:rules` vanilla game-rule category owned by TotemCore.
- Make every Remnant, Locksmith, and Nexus custom game rule use that shared
  category without changing its identifier, value, default, or owning module.
- Supply English and Traditional Chinese names and descriptions for every Totem
  game rule and localized labels for enum values.
- Reuse Minecraft 26.2's world-creation and in-world Game Rules screens, server
  synchronization, permission checks, and editing controls.

## Impact

- Affected specs: `unified-totem-gamerule-category`
- Affected code: TotemCore game-rule API and language resources; Remnant,
  Locksmith, and Nexus game-rule registration and language resources
- Compatibility: existing world data and `/gamerule` command identifiers remain
  unchanged; an absent optional module contributes no rules
