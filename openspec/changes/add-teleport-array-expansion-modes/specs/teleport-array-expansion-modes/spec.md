## ADDED Requirements

### Requirement: World-selectable teleport-array expansion mode

The server SHALL register the enum gamerule
`deadrecall:teleport_array_expansion_mode` with the command values `local` and
`centered`. Its default MUST be `local`. The title, description and both value
labels MUST be present in English and Traditional Chinese.

#### Scenario: An upgraded or new world uses the compatibility default

- **WHEN** no administrator has changed the expansion gamerule
- **THEN** the authoritative value is `local`
- **AND THEN** existing placement-driven arrays retain their current geometry

#### Scenario: An administrator selects centered expansion

- **WHEN** an authorized administrator runs
  `/gamerule deadrecall:teleport_array_expansion_mode centered`
- **THEN** subsequent authoritative array evaluations use centered mode

### Requirement: Local placement-driven expansion

In `local` mode, the scanner SHALL begin with the 26 non-origin positions at
Chebyshev distance one from the lodestone. Every reached structural material
with a positive effective local expansion radius SHALL expose the cube around
its own position, and newly reached emitters SHALL continue that irregular
graph until no new eligible position remains.

#### Scenario: A one-sided emitter remains one-sided

- **WHEN** a positive emitter is placed on one side of the lodestone and no
  local expansion path reaches the opposite outer position
- **THEN** local mode reaches positions around the emitter
- **AND THEN** it does not unlock the complete opposite shell

#### Scenario: A local emitter chain grows to a fixed point

- **WHEN** each newly reached local cube contains another positive emitter
- **THEN** the scanner follows that placed chain until no emitter reveals a new
  eligible position

### Requirement: Lodestone-centered fixed-point expansion

In `centered` mode, the scanner SHALL begin with a lodestone-centred radius of
one. For every reached structural emitter at Chebyshev distance `d` with
positive effective local radius `r`, it SHALL update the reached radius to
`max(currentRadius, min(5, d + r))`, scan the complete centre-excluded cube of
that radius, and repeat until the radius no longer increases.

#### Scenario: A one-sided emitter unlocks a symmetric cube

- **WHEN** a radius-one emitter is reached one block to one side of the
  lodestone
- **THEN** centered mode evaluates the complete lodestone-centred radius-two
  cube, subject to loaded-chunk availability
- **AND THEN** equally distant positions on the opposite side are eligible

#### Scenario: Chained centered emitters reach a later fixed point

- **WHEN** an emitter in the newly unlocked centred cube increases `d + r`
- **THEN** the complete centred cube grows to that maximum
- **AND THEN** evaluation repeats until no reached emitter increases it

#### Scenario: Emitters in one layer do not stack

- **WHEN** multiple reached emitters produce candidate radii in the same
  evaluation layer
- **THEN** the scanner takes the greatest candidate radius
- **AND THEN** it does not sum their expansion values

### Requirement: Bounded loaded-only evaluation

Both modes MUST exclude the lodestone origin, MUST reject positions beyond
Chebyshev distance five, and MUST NOT load a chunk merely to inspect a block.
Only positions confirmed loaded SHALL appear in the authoritative visited,
structural or buildable sets.

#### Scenario: Centered radius reaches the hard cap

- **WHEN** reached emitters would produce `d + r` greater than five
- **THEN** the authoritative radius remains five
- **AND THEN** no position beyond that bound is visited or counted

#### Scenario: A centered cube crosses an unloaded chunk boundary

- **WHEN** a candidate position lies in an unloaded neighboring chunk
- **THEN** the position is omitted from the scan result
- **AND THEN** the chunk remains unloaded

### Requirement: One mode-consistent production scan

The server MUST derive structure snapshots, teleport material calculations,
wear and repair target selection, counted-array visualization and build-site
visualization from the same selected production scan. A gamerule change SHALL rescan loaded
active lodestones without force-loading unloaded anchors. A stale mode-specific
snapshot MUST NOT be used for a live quote while its anchor is unloaded. A
legacy snapshot with no explicit mode marker MUST be considered stale rather
than inferred as `local` from a missing integer's zero fallback.

#### Scenario: A loaded world switches expansion mode

- **WHEN** the gamerule changes while an active lodestone anchor is loaded
- **THEN** its persisted structure snapshot is refreshed with the new mode
- **AND THEN** wear/repair targets use the same reached structural set

#### Scenario: Visualization refresh follows a gamerule change

- **WHEN** an enabled array or build-site overlay sends its next accepted
  refresh after the gamerule changes
- **THEN** its relative blocks exactly match the structural, emitter and
  buildable sets from the newly selected production scan
- **AND THEN** no Observer protocol or framebuffer path is introduced

#### Scenario: A legacy snapshot has no mode marker

- **WHEN** a persisted material snapshot predates the expansion-mode total
- **THEN** the server treats it as mode-stale even though its missing integer
  accessor returns zero
- **AND THEN** it rescans only after the lodestone anchor is loaded
