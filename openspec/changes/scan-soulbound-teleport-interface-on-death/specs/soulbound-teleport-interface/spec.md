## MODIFIED Requirements

### Requirement: One valid teleport interface survives death automatically

When Nexus and Remnant are installed, the system SHALL retain at most one valid
teleport-interface item across a player's death without requiring that item to
have completed a previous teleport. Valid candidates SHALL be a compass,
recovery compass, plain book, or filled map carrying a map ID. Selection SHALL
use main hand, offhand, remaining hotbar, then main inventory order. Items with
the configured vanishing behavior SHALL remain excluded.

#### Scenario: Player dies before first teleport

- **WHEN** a player has never completed a Nexus teleport and dies carrying a
  valid interface item
- **THEN** Remnant stages and restores one valid interface item

#### Scenario: Several interfaces are present

- **WHEN** a player dies with valid interfaces in both hands, the hotbar, and
  main inventory
- **THEN** only the first non-vanishing candidate in the configured order is
  retained

#### Scenario: Filled map lacks a map ID

- **WHEN** a filled-map stack has no server-valid map ID
- **THEN** it is not eligible for retention

#### Scenario: No valid interface is present

- **WHEN** the bounded player inventory scan finds no eligible item
- **THEN** no teleport-interface item is staged

### Requirement: Retention remains server-authoritative and durable

Remnant SHALL remain the sole owner of extraction, persisted staging, and
exactly-once restoration. Nexus SHALL expose eligibility through the optional
Core policy without requiring Remnant to import Nexus implementation classes.
The scan SHALL run only in the existing death path and SHALL NOT inspect nested
containers, offline players, or unloaded chunks.

#### Scenario: Player reconnects before restoration completes

- **WHEN** an eligible item was staged and the player reconnects or respawns
- **THEN** Remnant restores it exactly once from existing SavedData

#### Scenario: Nexus is absent

- **WHEN** no feature module registers a death-retained item policy
- **THEN** Remnant performs no teleport-interface retention
- **AND** its ordinary death-backpack flow remains unchanged

### Requirement: Legacy successful-teleport tags remain compatible

The system SHALL preserve legacy owner/token metadata for backward
compatibility. Nexus MAY continue writing and reading it, but death eligibility
SHALL NOT depend on that metadata and the
migration SHALL NOT strip it from existing items.

#### Scenario: Legacy tagged interface is carried

- **WHEN** a player carries an interface tagged by an older successful teleport
- **THEN** it is evaluated by the same current resolver and priority rules
- **AND** its unrelated legacy custom data is preserved
