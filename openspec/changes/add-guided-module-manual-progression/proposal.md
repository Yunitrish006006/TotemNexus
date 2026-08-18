## Why

The current canonical Totem manual exposes every installed chapter immediately,
while feature advancement roots complete on the first tick. Players therefore
receive neither a guided first step nor a meaningful discovery sequence.

## What Changes

- Give each player one Core-owned basic manual on first world entry.
- Make every gameplay module start with crafting a recognizable source block,
  then require using a basic manual, another generated guide, or a plain book on
  that block to obtain the module guide.
- Award a module-specific guide advancement and place the remaining module
  advancement branch behind it.
- Replace tick-triggered module roots with real recipe-crafted criteria.
- Add localized guide chapters and source interactions for Automata,
  Enchanting, Excavation, Vanilla Tweaks, and Villagers while adapting the
  existing Remnant, Alchemy, and Nexus chapters.
- Keep Discord Bridge outside the gameplay advancement tree.

## Impact

- Affected specs: `guided-module-manual-progression` (new capability) and the
  active `unified-totem-manual` change.
- Affected code: TotemCore manual lifecycle/assembler/player helper; all eight
  gameplay module initializers, language resources, and advancement data.
- Compatibility: legacy canonical manuals stay readable; new players receive
  only the basic chapter until they discover module guides.
- Release coordination: the additive Core API and all exact Core pins must be
  published in one lockstep standalone and DeadRecall bundle update.
