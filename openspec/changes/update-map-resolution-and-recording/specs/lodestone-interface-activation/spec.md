## ADDED Requirements

### Requirement: Resolution-matched map zoom

Nexus SHALL choose recorded terrain resolution according to the current map
scale and power-of-two zoom, independently of GUI scale. For a scale-four base,
100%, 200%, 400%, 800% and 1600% SHALL use terrain scales four, three, two, one
and zero respectively. Finer-than-selected pages MUST NOT replace a matching
resolution through texture minification. Missing matching data SHALL retain
coarser recorded fallback without inventing terrain or proof of exploration.

#### Scenario: Newly explored expanded region at every zoom

- **WHEN** a scale-four map records an expanded region without historical ancestors
- **THEN** each zoom renders its corresponding recorded or derived resolution
- **AND** nonuniform terrain is reduced deterministically at intermediate levels,
  rather than displaying the same finest texture at all zooms
- **AND** GUI scaling does not change the selected world resolution

#### Scenario: Partial coverage and legacy records

- **WHEN** source texels needed for an intermediate pixel are not all known
- **THEN** that derived pixel stays unknown and the coarse recorded fallback remains
- **AND** loading old records never invents historical ancestry or missing terrain

#### Scenario: Owner and Observer view the same resolution

- **WHEN** compatible authorized owner and Observer clients view the same map and zoom
- **THEN** both use the owning module's production resolution selection and renderer
- **AND** markers, hit tests and cursor-anchored zoom remain world-aligned
- **AND** Observer remains read-only and framebuffer-free

### Requirement: Bounded movement-aware recording

Nexus SHALL maintain bounded world-anchored progress for legitimately explored
map terrain, prioritize missing cells near the traversed route, and fairly
complete admitted work while it remains authorized and loaded. Player movement
MUST NOT silently relocate unfinished samples. Recording SHALL NOT force-load
chunks, sample from a client viewport alone, or fabricate unavailable terrain.

#### Scenario: Player walks past unfinished terrain

- **WHEN** a player moves onward while admitted eligible terrain remains loaded
- **THEN** the scheduler retains that work's world coordinates and progress
- **AND** fills route-adjacent gaps before refreshing already complete terrain
- **AND** a deterministic moving-player test verifies coverage and bounded work
- **AND** bounded aligned-footprint work completes intermediate-resolution pixels
  where all required source cells are individually eligible, with per-zoom
  completion coverage measured rather than only the number of fine samples

#### Scenario: Contention and unavailable terrain

- **WHEN** multiple players hold maps or pending terrain unloads
- **THEN** per-tick work, queue memory and derived processing remain bounded
- **AND** eligible older work receives a fair share without chunk tickets
- **AND** unavailable work remains unknown and can resume on an eligible later visit
- **AND** locked maps never acquire mutable exploration updates

### Requirement: Convergent detail delivery

Repeated requests SHALL NOT indefinitely postpone eligible unsent page revisions.
Nexus SHALL retain bounded authorized progress, prefer the requested resolution,
and maintain packet/cache limits. Any changed request or Observer contract MUST
be explicitly compatible or versioned and validated before use.

#### Scenario: Repeated request during dirty-page streaming

- **WHEN** the same viewport is requested repeatedly while early pages change
- **THEN** other admitted visible pages still receive bounded fair delivery
- **AND** unchanged base terrain is not repeatedly transmitted without need
- **AND** tests distinguish recording delay from delivery and texture-update delay

#### Scenario: Authority is revoked while delivery is pending

- **WHEN** the map is no longer held or the Observer session loses authorization
- **THEN** pending delivery stops before sending further unauthorized terrain
- **AND** disconnect and close paths release owned transient state
