## ADDED Requirements
### Requirement: Previously joined access targets
Nexus SHALL allow authorized managers to select previously joined online or offline players using server-resolved UUIDs, with bounded searchable pages. It MUST reject unknown identities and preserve live management authorization.

#### Scenario: Offline target
- **WHEN** an owner selects a player who joined before but is offline
- **THEN** administrator or allowed access can be granted and revoked by UUID

#### Scenario: Unauthorized manager
- **WHEN** an unauthorized actor queries or updates another lodestone
- **THEN** neither the directory nor permissions are disclosed or changed

### Requirement: Access dialog Observer ownership
Nexus SHALL reconstruct its access dialog through the same production Screen using bounded versioned semantic data, monotonic sequences and read-only input. Search input MUST be redacted.

#### Scenario: Observed selection
- **WHEN** a new authorized page or selected player is shown
- **THEN** Observer reflects it without accepting mutation packets or exposing search text
