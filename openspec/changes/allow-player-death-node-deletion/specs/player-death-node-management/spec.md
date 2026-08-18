## ADDED Requirements

### Requirement: Players can inspect only their own Death Nodes

The system SHALL allow an in-game player to open the Death Node management
screen without administrator permission. For a non-administrator, the server
MUST restrict every query result to records whose persisted owner UUID equals
the requesting player's UUID, regardless of client-supplied owner filters,
page requests or later action payloads. Administrator queries SHALL retain the
existing cross-player management behavior.

#### Scenario: Player opens their Death Node list

- **WHEN** a non-administrator opens `/deadrecall deathnodes`
- **THEN** the server returns only Death Nodes owned by that player
- **AND** the client presents the owner-only management view

#### Scenario: Player forges an owner filter

- **WHEN** a non-administrator submits another player name or UUID as the owner filter
- **THEN** the server ignores that value and still queries only the requester's persisted owner UUID

#### Scenario: Administrator opens the management list

- **WHEN** a command administrator opens the same command
- **THEN** the existing owner filtering, diagnostics, safe teleport and batch controls remain available

### Requirement: Owners can permanently delete one Death Node

The system SHALL allow a non-administrator to permanently remove one Death Node
they own after consuming a server-issued, single-use confirmation bound to the
player, node and owner-delete action. The confirmation MUST expire after 30
seconds. A successful deletion MUST remove the Space Unit record and all
discovery and favorite references, MUST NOT delete or move the associated death
backpack, and MAY remove an active Death Node without a separate disable step.

#### Scenario: Owner confirms deletion

- **WHEN** the owner requests deletion and confirms it with the matching unexpired token
- **THEN** the server removes that Death Node and all discovery/favorite references
- **AND** later recovery of its death backpack remains an idempotent success

#### Scenario: Owner deletes an active point

- **WHEN** the owner confirms deletion of their active Death Node
- **THEN** the teleport point is permanently removed without requiring a separate disable action
- **AND** the associated backpack remains in the world unchanged

#### Scenario: Confirmation is absent or stale

- **WHEN** the owner submits deletion without the matching live confirmation token
- **THEN** the server rejects the deletion and leaves all records unchanged

### Requirement: Player management cannot invoke administrator authority

The system MUST independently check the current record owner and caller role on
every management action. Non-administrators MUST NOT use the management protocol
to inspect or mutate another owner's node, perform safe teleport, submit batch
actions, access administrator diagnostics, or use administrator purge actions.

#### Scenario: Player forges a foreign node UUID

- **WHEN** a non-administrator submits an owner-delete request for another player's node UUID
- **THEN** the server rejects the request without revealing or mutating that node

#### Scenario: Player forges an administrator action

- **WHEN** a non-administrator submits teleport, disable, administrator purge or batch action IDs
- **THEN** the server rejects the action and leaves Death Node state unchanged

#### Scenario: Remnant contributes to the shared command tree

- **WHEN** TotemNexus and TotemRemnant are installed together
- **THEN** the Death Node child commands remain available to players
- **AND** Remnant's container administration child remains restricted to command administrators

