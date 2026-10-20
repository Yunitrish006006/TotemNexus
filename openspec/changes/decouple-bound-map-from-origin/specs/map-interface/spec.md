## ADDED Requirements

### Requirement: Registered map independence
A successfully bound filled map SHALL remain a usable portable interface when its original lodestone is disabled or destroyed, provided the held map matches the server binding ledger.

#### Scenario: Origin destroyed before reopening
- **WHEN** a registered map is loaded after its origin lodestone is destroyed
- **THEN** the player can reopen the map and teleport to an authorized, active, drawn destination without rebinding

#### Scenario: Origin destroyed during teleport
- **WHEN** the origin is destroyed during a valid portable-map teleport
- **THEN** destruction alone does not cancel the teleport

#### Scenario: Destination or interface is invalid
- **WHEN** the destination is destroyed, unauthorized, outside drawn coverage, or the held map fails server-ledger identity validation
- **THEN** the corresponding action is rejected
