## ADDED Requirements

### Requirement: Each player receives one basic guide

TotemCore SHALL give a player one localized canonical basic guide when that
player first joins a world with Core installed. The grant SHALL be persisted per
player, SHALL NOT repeat on ordinary reconnects, and SHALL drop the guide beside
the player only if bounded inventory insertion fails.

#### Scenario: New player joins

- **WHEN** a player joins without completed basic-guide grant progress
- **THEN** Core gives that player one basic guide
- **AND** awards the shared root only after the guide is delivered

#### Scenario: Player reconnects

- **WHEN** a player whose basic-guide grant is complete reconnects
- **THEN** Core does not create another basic guide

### Requirement: Basic guide explains module discovery

The basic guide SHALL use the canonical vanilla written-book presentation and
SHALL explain that players craft a module source block and use a basic guide,
another generated Totem guide, or a plain book on that block to record the
module guide. English and Traditional Chinese SHALL be translatable text rather
than texture-baked text.

#### Scenario: Player reads the first guide

- **WHEN** the player opens the delivered basic guide
- **THEN** it explains the source-block and recording sequence
- **AND** uses native book navigation, font, tooltip, and narration behavior

### Requirement: Each gameplay module has a source-block milestone

The system SHALL expose a recipe-crafted source-block advancement for each of
Remnant, Alchemy, Automata, Enchanting, Nexus, Vanilla Tweaks, Excavation, and
Villagers. Each advancement SHALL use its configured
source block. No module root SHALL complete from `minecraft:tick`. Discord
Bridge SHALL NOT add a gameplay source milestone.

#### Scenario: Module is merely installed

- **WHEN** a player joins with a gameplay module installed but has not crafted
  that module's source block
- **THEN** its module root remains incomplete

#### Scenario: Player crafts a source block

- **WHEN** the player crafts the configured source block
- **THEN** the corresponding module root completes
- **AND** its description directs the player to record a guide at that block

### Requirement: Source interaction creates only the target module guide

A configured source block SHALL accept a plain book or a marked Totem guide and
SHALL create or refresh a canonical guide containing only the target module
section. A marked guide used as a reference SHALL not be consumed. A plain book
SHALL consume exactly one item only when creating the target guide. Unrelated
items and blocks SHALL pass through unchanged.

#### Scenario: Basic guide records a module guide

- **WHEN** a player uses the basic guide on a configured module source
- **THEN** the player keeps the basic guide
- **AND** receives one target module guide
- **AND** no undiscovered installed module section is added to that guide

#### Scenario: Plain book records a module guide

- **WHEN** a player uses a stack of plain books on a configured source and does
  not already carry the target module guide
- **THEN** exactly one plain book is consumed
- **AND** one target module guide is inserted or safely dropped

#### Scenario: Target guide already exists

- **WHEN** the player records the same source while carrying its generated guide
- **THEN** that guide is refreshed
- **AND** no duplicate guide or plain-book consumption occurs

### Requirement: Guide acquisition gates each module branch

Each gameplay module SHALL award a module-specific impossible-triggered guide
advancement only after its server-authoritative guide acquisition succeeds. All
other module feature advancements SHALL descend from that guide advancement.

#### Scenario: Source block is crafted but not recorded

- **WHEN** the module root is complete but the player has not obtained the
  module guide
- **THEN** the guide advancement and subsequent module branch remain incomplete

#### Scenario: Module guide is obtained

- **WHEN** the server creates or refreshes the target module guide for a player
- **THEN** it awards that module's guide advancement
- **AND** exposes the remaining module advancement branch

### Requirement: Legacy generated manuals remain safe

The migration SHALL preserve marked legacy all-section manuals and SHALL never
rewrite an unmarked player-authored written book. Legacy guide possession SHALL
not automatically award every module-specific guide advancement.

#### Scenario: Existing player carries a combined manual

- **WHEN** an existing player joins carrying a marked combined Totem manual
- **THEN** the manual remains readable and refreshable
- **AND** module-guide advancements are not bulk-awarded

#### Scenario: Existing player carries an ordinary written book

- **WHEN** an unmarked player-authored book is present during login or source
  interaction
- **THEN** Core does not rewrite it as a Totem guide
