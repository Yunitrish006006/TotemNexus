## ADDED Requirements

### Requirement: Lodestone map creation is exact-center convenience

Using an empty map through the authorized lodestone creation flow SHALL allocate
a native scale-0 filled map whose center is the exact lodestone X/Z and SHALL
publish the same MapId-owned baseline Nexus record used by ordinary filled
maps. The item and record MUST NOT require a creating Space Unit identity for
later opening, locking, scaling or detail derivation.

#### Scenario: Lodestone creates an exact-centered map

- **WHEN** an authorized player completes map creation at a lodestone whose X/Z
  is not on the vanilla map-center grid
- **THEN** the resulting native map center equals that exact X/Z
- **AND** its baseline Nexus record is keyed by the new MapId without an origin
  Space Unit field

#### Scenario: Creating lodestone is later unavailable

- **GIVEN** an exact-centered map created at a lodestone
- **WHEN** that lodestone is unloaded, disabled or destroyed
- **THEN** the map can still open, lock, scale and derive detail
- **AND** none of those operations loads or validates the former origin

#### Scenario: Ordinary and exact-centered maps share behavior

- **WHEN** one ordinary grid-centered map and one lodestone exact-centered map
  have compatible MapId-owned Nexus records
- **THEN** both use the same interface, node filtering, teleport authority,
  scale, lock and detail lifecycle
- **AND** their persisted centers are the only intentional behavioral difference

