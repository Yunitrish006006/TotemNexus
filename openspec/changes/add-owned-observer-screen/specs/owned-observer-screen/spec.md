## ADDED Requirements

### Requirement: Nexus owns its Observer rendering
Nexus SHALL reconstruct map, friends, registration and death-node administration observation with the corresponding production Nexus Screen implementation.

#### Scenario: Compatible variant
- **WHEN** a compatible Nexus semantic snapshot is relayed
- **THEN** the owning Nexus provider creates the matching production Screen in Observer mode

### Requirement: Observer authority is read-only
Nexus Observer screens MUST NOT grant teleport, friendship, registration, deletion or administration authority to the viewer.

#### Scenario: Viewer activates a control
- **WHEN** the viewer clicks or types in an observed Nexus screen
- **THEN** no target mutation request is sent and only server-authorised relay data remains visible
