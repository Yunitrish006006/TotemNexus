## ADDED Requirements

### Requirement: Installed Totem modules compose one canonical manual

The system SHALL compose every successfully registered installed Totem module
section into one deterministic canonical vanilla written book. The book SHALL
contain a generated cover and contents followed by sections ordered by declared
order and identifier. Section visibility SHALL NOT depend on feature-to-feature
dependencies or player discovery state.

#### Scenario: Nexus and Remnant are installed together

- **WHEN** Nexus and Remnant register their manual sections and a player obtains
  a Totem manual
- **THEN** the one generated book contains both the Nexus and Remnant sections
  in deterministic order
- **AND** no separate Nexus or Remnant manual is produced

#### Scenario: One feature module is absent

- **WHEN** only one feature module registers a section
- **THEN** the manual contains that installed section
- **AND** it does not contain placeholder pages for the absent module

### Requirement: Feature modules register bounded localized sections

TotemCore SHALL expose an additive v1 API for immutable section definitions
containing a unique identifier, order, localized title and localized pages.
Localized pages SHALL support nested translatable components so referenced
Minecraft and Totem item names follow the active client language and resource
pack instead of being stored as resolved or hard-coded text.
The registry SHALL reject duplicate identifiers and the assembler SHALL reject
content exceeding the vanilla written-book page limit rather than silently
dropping pages.

#### Scenario: Two modules choose the same order

- **WHEN** two unique sections register with the same declared order
- **THEN** the registry orders them deterministically by section identifier

#### Scenario: A duplicate section identifier is registered

- **WHEN** a second provider registers an existing section identifier
- **THEN** registration fails with an explicit duplicate diagnostic

#### Scenario: Player changes client language

- **WHEN** a manual page references an item through a nested translatable
  component and the player changes the active client language
- **THEN** the item name is rendered using that language's current item
  translation
- **AND** the manual does not require a separately hard-coded item name

### Requirement: Canonical manuals are marked and refreshable

The canonical manual SHALL remain a `minecraft:written_book` and SHALL carry a
versioned custom-data marker, the assembled section identifiers and a
deterministic content revision. A marked manual carried by a player SHALL be
rebuilt after login when its revision no longer matches registered content.
The refresh SHALL preserve unrelated safe item components.

#### Scenario: A new Totem module is installed

- **WHEN** a player logs in carrying a marked manual whose revision predates a
  newly registered section
- **THEN** the marked manual is rebuilt to include the new installed section
- **AND** no additional manual item is created

#### Scenario: An ordinary written book has a similar name

- **WHEN** a player logs in carrying an unmarked written book
- **THEN** the automatic refresh does not modify that book

### Requirement: Manual acquisition reuses or consolidates generated manuals

A feature manual source SHALL convert exactly one plain book when the player
does not carry a recognized Totem manual. When a recognized manual is held, the
source SHALL refresh that manual instead of producing another. When both hands
hold recognized generated Totem manuals during an explicit source interaction,
the system SHALL retain and rebuild the active-hand manual and consume the
secondary generated manual.

#### Scenario: Plain book records a new manual

- **WHEN** a player without a recognized manual uses one plain book on a manual
  source
- **THEN** exactly one plain book becomes a canonical Totem manual

#### Scenario: Other hand already carries the manual

- **WHEN** a player uses a plain book on a manual source while the other hand
  carries a recognized Totem manual
- **THEN** the recognized manual is refreshed
- **AND** the plain book is not consumed
- **AND** no second manual is created

#### Scenario: Both hands carry generated manuals

- **WHEN** both hands carry recognized generated Totem manuals and the player
  explicitly uses a manual source
- **THEN** the active-hand book becomes the one canonical refreshed manual
- **AND** the other-hand generated manual is consumed

### Requirement: Existing Nexus generated manuals migrate safely

The Nexus manual source SHALL recognize the exact legacy generated Nexus
manual signature and migrate it in place during an explicit manual
interaction. Recognition SHALL require the expected item, generated title,
author, translatable custom name and page count; title-only matching is
forbidden.

#### Scenario: Player updates a legacy Nexus manual

- **WHEN** a player uses an exact generated Nexus manual on a lodestone
- **THEN** it becomes a marked canonical Totem manual
- **AND** the registered Nexus and other installed sections are present

#### Scenario: Player-authored book reuses the Nexus title

- **WHEN** a player-authored written book has the Nexus title but does not match
  every generated invariant
- **THEN** the lodestone does not migrate or consume it as a legacy manual

### Requirement: Nexus and Remnant expose compatible manual sources

Nexus SHALL keep the plain-book-on-lodestone source. Remnant SHALL expose a
plain-book or recognized-manual interaction on a smithing table. Both sources
SHALL delegate to the shared manual helper and SHALL pass through unrelated
items and interactions.

#### Scenario: Remnant is installed without Nexus

- **WHEN** a player uses a plain book on a smithing table in standalone Remnant
- **THEN** the player obtains a canonical manual containing Remnant sections

#### Scenario: An ordinary item is used on a smithing table

- **WHEN** a player uses an item that is neither a plain book nor a recognized
  Totem manual on a smithing table
- **THEN** Remnant does not consume the item or override the normal interaction

### Requirement: Obtaining the canonical manual awards knowledge progression

The system SHALL award the shared `Knowledge Is Power` advancement when a
player obtains a canonical Totem manual. A player who already carries a marked
canonical manual when joining SHALL also receive the advancement. An ordinary
book or unmarked written book SHALL NOT satisfy this progression.

#### Scenario: Player records the Totem manual

- **WHEN** a supported manual source creates or refreshes a canonical manual
- **THEN** the player receives the `Knowledge Is Power` advancement

#### Scenario: Returning player already owns the manual

- **WHEN** a player joins while carrying a marked canonical manual
- **THEN** the player receives the advancement if it was not previously earned

#### Scenario: Player obtains an unrelated written book

- **WHEN** a player carries a written book without the canonical marker
- **THEN** the advancement is not awarded

### Requirement: Canonical manuals use a shared two-page presentation

TotemCore SHALL render canonical Totem manuals as a centered two-page spread
and SHALL advance or rewind navigation by two pages. This presentation SHALL be
available whenever Core and any manual-contributing feature module are
installed, without requiring TotemRemnant. Ordinary written books SHALL retain
the vanilla presentation. Feature modules MAY register client-only page
overlays without taking ownership of the shared book layout.

#### Scenario: Nexus is installed without Remnant

- **WHEN** a player opens a canonical manual containing the Nexus section with
  Core and Nexus installed but Remnant absent
- **THEN** the manual renders as a two-page spread
- **AND** navigation advances by two pages

#### Scenario: An ordinary written book is opened

- **WHEN** the opened written book does not contain the canonical Totem manual
  cover marker
- **THEN** the normal vanilla single-page presentation is used

#### Scenario: Remnant contributes recipe graphics

- **WHEN** Remnant is installed and a canonical Remnant recipe page is shown
- **THEN** Remnant's optional overlay draws the synchronized recipe graphics
- **AND** Core remains the sole owner of the two-page background, text and
  navigation behavior

### Requirement: Nexus explains teleport arrays with in-book diagrams

Nexus SHALL register client-only overlays for each of its twelve body pages.
The overlays SHALL use vanilla item imagery, compact flow lines and localized
labels to explain registration, teleport initiation, structure scanning,
material attributes, copper and catalyst behavior, and maintenance. Diagram
items SHALL expose their normal localized item tooltips when hovered. The
server remains authoritative for configurable material values.

#### Scenario: Player reads the Nexus chapter

- **WHEN** a canonical manual displays a Nexus body page
- **THEN** the matching diagram renders inside that page of the shared spread
- **AND** its labels follow the selected client language

#### Scenario: Player hovers a diagram ingredient

- **WHEN** the pointer is over an item rendered in a Nexus diagram
- **THEN** the normal localized item tooltip is shown

#### Scenario: Datapacks rebalance Nexus materials

- **WHEN** configured server material values differ from the illustrative
  diagram
- **THEN** the manual directs the player to the live Materials view as the
  authoritative source
