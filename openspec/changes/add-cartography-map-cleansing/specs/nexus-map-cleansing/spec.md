## ADDED Requirements

### Requirement: Filled maps can be cleansed into real empty maps

The server SHALL offer a cartography-table cleansing result when the map slot
contains one filled map and the additional slot contains one water bucket. The
result SHALL be one genuine empty `minecraft:map` with no MapId or Nexus item
identity. Cleansing SHALL recycle only the supplied item and MUST NOT mutate or
delete the old MapId's vanilla SavedData, Nexus detail or lineage data, or
binding-ledger entry.

#### Scenario: Player cleanses a filled map

- **WHEN** a player places one filled map and one water bucket into a
  cartography table and takes the current cleansing result
- **THEN** the player receives exactly one empty map with no MapId
- **AND** exactly one filled map and one water bucket are consumed
- **AND** exactly one empty bucket is returned

#### Scenario: Another copy still references the old map

- **GIVEN** another filled-map item references the same old MapId
- **WHEN** one copy is cleansed
- **THEN** the other copy retains its pixels, metadata and Nexus behavior
- **AND** the server does not delete or rewrite SavedData, detail data, lineage,
  or binding records for the old MapId

### Requirement: Cleansing commits only from current server-owned inputs

The cleansing preview SHALL NOT mutate storage or inventory. On every result
take, the server MUST revalidate the live map slot, live water-bucket slot and
expected empty-map result. It MUST refuse stale or invalid state before any
input is consumed. All accepted click paths SHALL conserve one empty-map result
and one empty-bucket remainder without duplication or loss.

#### Scenario: Preview becomes stale

- **WHEN** either input or the displayed result no longer matches the cleansing
  operation before result pickup
- **THEN** the server refuses the take
- **AND** neither input, map storage nor Nexus binding state changes

#### Scenario: Inventory cannot accept every output

- **WHEN** a pickup, quick-move, hotbar swap or drop path cannot conserve both
  the empty map and empty bucket
- **THEN** the server refuses before consuming either input
- **AND** no result or remainder is duplicated, deleted or orphaned

### Requirement: Filled maps do not directly enter empty-map Nexus activation

The existing Nexus activation flow SHALL continue to accept genuine empty maps
and SHALL NOT treat a filled map as an empty-map-compatible input. A player who
wants to reuse the material from a filled map MUST complete cleansing first.

#### Scenario: Player attempts direct filled-map activation

- **WHEN** a player supplies a filled map to the empty-map Nexus activation flow
- **THEN** activation is rejected without changing the map or binding state
- **AND** cleansing it into an empty map makes the resulting item eligible for
  the normal empty-map flow

