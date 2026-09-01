## ADDED Requirements

### Requirement: Data-driven multi-attribute material profiles

The system SHALL load a server-authoritative material profile for every valid
teleport-array material. A profile SHALL support signed values for structure
capacity, stability, arrival accuracy, target lock, arrival safety, wear
resistance, maintenance efficiency, interference resistance, food efficiency,
phase speed, cooldown recovery, route load capacity, local scan-expansion
radius,
dimensional affinity and cross-dimension catalyst units. A single material SHALL be allowed to set
multiple values at once, including positive and negative values.

#### Scenario: One material has several positive effects

- **WHEN** a valid material profile supplies arrival accuracy, phase speed and
  cross-dimension catalyst units
- **THEN** the evaluated array receives every declared effect from each occurrence of
  that material
- **AND THEN** neither effect suppresses the material's structural capacity or
  any other declared profile value

#### Scenario: A material carries a negative trade-off

- **WHEN** a valid material profile supplies negative arrival accuracy or
  negative cross-dimension catalyst units
- **THEN** the server applies the corresponding accuracy penalty or additional
  shard cost through the same aggregation path as positive values
- **AND THEN** final values remain within their configured safe bounds

### Requirement: Profile composition is deterministic and state-aware

The system SHALL compile an immutable material profile from exactly one base
profile, its declared state modifiers and at most one exact-block datapack
overlay. A same-priority base-profile conflict SHALL fail the resource reload
without replacing the last valid registry. Copper SHALL apply modifiers in the
fixed order shape, oxidation, then wax, and each modifier SHALL contribute at
most once.

#### Scenario: Conflicting profile bases are reloaded

- **WHEN** two material resources select the same block as an equal-priority
  base profile
- **THEN** the server rejects that reload with the conflicting resource IDs
- **AND THEN** the previously valid compiled profile registry remains active

#### Scenario: Copper layers compose once in a fixed order

- **WHEN** a waxed oxidized copper bulb is evaluated
- **THEN** the server starts with the copper-bulb shape values, applies the
  oxidized penalties, then applies the wax protection values
- **AND THEN** no layer is applied twice through an overlapping tag or overlay

### Requirement: Attribute conversions have fixed server-side bounds

The server SHALL convert signed profile totals using the documented capacity,
local scan-expansion, stability, interference, drift, damage, wear, food,
phase, load, cooldown, maintenance, affinity and catalyst formulas. It SHALL
keep the initial default bounds: 0–2 local scan-expansion radius per material,
0–100 stability/interference, 1–96 drift blocks, 0–60 arrival-damage percent,
0–100 wear percent, 50–200 food-percent multiplier, 40–300 preparation ticks,
1–8 endpoint load slots, 20–600 recovery ticks and a one-shard minimum for
paid cross-dimension routes.

#### Scenario: Extreme positive and negative totals are bounded

- **WHEN** a valid array's aggregated profiles exceed a final formula's upper
  or lower range
- **THEN** the server clamps the final quoted value to that formula's documented
  bound
- **AND THEN** it does not overflow, create a negative cost or preparation
  time, eliminate paid cross-dimension shard cost, or send an invalid packet

#### Scenario: Food efficiency changes a non-zero route cost

- **WHEN** a route has a non-zero base food cost and positive or negative food
  efficiency totals
- **THEN** the server applies the documented 50–200 percent multiplier and
  rounds up before resource deduction
- **AND THEN** the final food cost is at least one point

### Requirement: Bounded deterministic material evaluation

The system SHALL begin material evaluation from the centre-excluded 3×3×3 cube
around the lodestone, using only already-loaded block state and an
order-independent graph evaluation. Each scanned material with positive
effective local scan-expansion radius SHALL expose positions around its own
block position. Newly reached materials SHALL expose positions from their own
positions in the same way. The scanner SHALL never reach a position more than
Chebyshev distance five from the lodestone (1,330 candidate positions), force
chunks to load or unscan a position already reached in the current evaluation.

#### Scenario: Materials change effective structure size

- **WHEN** an array contains materials whose combined signed structure-capacity
  values differ from its raw block count
- **THEN** tier and array-capacity evaluation use the combined effective
  capacity
- **AND THEN** the effective capacity cannot be less than zero

#### Scenario: A material expands from its own position

- **WHEN** a scanned initial-position material has positive local
  scan-expansion radius
- **THEN** the server scans newly exposed positions around that material before
  finalising capacity, attributes, symmetry, wear and catalyst totals
- **AND THEN** an equally distant position in another direction remains
  unscanned unless its own expansion path exists

#### Scenario: Expansion follows a placed material chain

- **WHEN** a newly exposed material also has positive local scan-expansion
  radius
- **THEN** the server expands around that newly exposed material
- **AND THEN** the visited positions form the transitive placement-driven
  expansion path rather than a globally unlocked shell

#### Scenario: A distant material lacks a connected expansion path

- **WHEN** a valid material is outside the initial seed
- **AND WHEN** no chain of scanned positive-expansion materials reaches its
  position
- **THEN** it does not contribute any material value to that lodestone's array

#### Scenario: A negative state modifier cancels a local expander

- **WHEN** a material's composed local scan-expansion radius is zero or less
  after a negative state modifier
- **THEN** that material exposes no additional positions
- **AND THEN** already visited positions remain in the result and the graph
  evaluation stays deterministic

#### Scenario: An array touches an unloaded chunk boundary

- **WHEN** the initial seed or a locally expanded position would include an
  unloaded chunk
- **THEN** the evaluator only uses safely available block state and does not
  load that chunk to obtain material values

### Requirement: Initial brick, metal and mineral material catalogue

The system SHALL include the initial brick, metal, mineral, raw-material and
ore block profiles documented in the change design. It SHALL make the
documented metal, mineral, raw-material and ore families valid teleport-array
materials without invalidating any existing valid block. Every copper shape,
oxidation state and waxed state SHALL resolve to exactly one shape profile plus
the documented state adjustment. Vanilla tuff, obsidian and crying obsidian
SHALL also be valid exact-block materials with the final signed values and
distinct families documented in the change design.

#### Scenario: A new mineral storage block is used in an array

- **WHEN** a player builds an array with a documented metal or mineral storage
  block such as iron, diamond, redstone or amethyst
- **THEN** the block is accepted as a valid structural material
- **AND THEN** all of its documented positive and negative profile values are
  included in the server-calculated totals

#### Scenario: A weathered waxed copper grate is evaluated

- **WHEN** an array contains a waxed weathered copper grate
- **THEN** the server applies the copper-grate shape profile, the weathered
  state penalties and the waxed-state bonuses exactly once each
- **AND THEN** no copper state is counted as a second independent block profile

#### Scenario: An ore is used as temporary structure

- **WHEN** a player includes a documented ore or ancient debris in an array
- **THEN** it is a valid material with its documented capacity and thematic
  effects
- **AND THEN** its interference and maintenance penalties remain visible in
  the material breakdown

#### Scenario: Tuff provides a cheap Overworld baseline

- **WHEN** an array contains vanilla tuff
- **THEN** each tuff contributes capacity, easy maintenance and Overworld
  affinity with the documented stability penalty
- **AND THEN** it does not gain scan expansion, precision, safety or route-load
  benefits merely because it is inexpensive

#### Scenario: Obsidian variants retain different identities

- **WHEN** an array contains obsidian and crying obsidian
- **THEN** obsidian supplies the documented stability, durability and
  interference resistance together with slow phasing and difficult maintenance
- **AND THEN** crying obsidian instead supplies the documented targeting and
  Nether affinity together with interference, safety and phase-speed penalties

### Requirement: Built-in profiles respect block identity and state

The built-in material catalogue SHALL derive each non-zero profile value from
the block's vanilla form, physical state or dimensional origin. A cracked
variant SHALL not improve over its intact counterpart in stability, arrival
safety, wear resistance or maintenance efficiency. Copper oxidation SHALL be
monotonically no better across those same degradation-sensitive attributes,
and wax SHALL only improve the documented protection and maintenance effects
for the same copper shape and oxidation state.

#### Scenario: A cracked construction block is compared with its intact form

- **WHEN** the server evaluates a cracked brick profile and its intact
  counterpart
- **THEN** the cracked profile has no higher stability, arrival safety, wear
  resistance or maintenance efficiency than the intact profile
- **AND THEN** its retained capacity is explained as physical structure, not a
  hidden quality bonus

#### Scenario: Waxed weathered copper keeps weathering penalties

- **WHEN** the server evaluates a waxed weathered copper form
- **THEN** it retains every weathered stability, accuracy, wear-resistance and
  maintenance penalty relative to the unweathered shape
- **AND THEN** its wax adjustment only adds the documented protective values

#### Scenario: A signal mineral receives a trade-off

- **WHEN** a crystal or signal block gains accuracy, target lock, catalyst,
  phase-speed, recovery or load value
- **THEN** the built-in profile documents the matching vanilla rationale
- **AND THEN** signal-power gains include the documented interference or
  arrival-safety trade-off where required by the material-identity rules

### Requirement: Signed material totals affect teleport outcomes safely

The server SHALL apply material totals after geometric structure evaluation to
route stability, landing accuracy, target lock, arrival safety, structure wear,
interference, food cost, preparation time and cross-dimension shard cost. The
server SHALL clamp final stability, drift, damage probability, wear probability,
interference, resource cost and preparation time to valid gameplay limits. A
paid cross-dimension teleport SHALL still require at least one amethyst shard.

#### Scenario: Accuracy changes landing drift

- **WHEN** two arrays have equal geometry but different aggregated arrival
  accuracy values
- **THEN** the array with the higher final accuracy receives no greater maximum
  horizontal drift than the other array

#### Scenario: Negative catalyst units increase a cross-dimension cost

- **WHEN** the combined endpoint catalyst-unit total is negative enough to
  cross a configured shard-unit boundary
- **THEN** the final cross-dimension shard cost increases by the corresponding
  number of shards
- **AND THEN** positive catalyst units can reduce, but cannot eliminate, a paid
  shard cost

### Requirement: Dimensional affinity and route lifecycle are material-driven

The system SHALL use signed dimensional-affinity values only for matching
cross-dimension endpoints: source materials match the target dimension and
target materials match the source dimension. Matching affinity SHALL adjust
cross-dimension stability, drift and arrival safety without bypassing the
cross-dimension tier requirement. The system SHALL also derive route-load
capacity, cooldown recovery and maintenance work from material totals using
server-authoritative reservation and repair state.

#### Scenario: Nether-tuned material targets the Nether

- **WHEN** a source array has positive `minecraft:the_nether` affinity and its
  target is in the Nether
- **THEN** the server applies that affinity to the cross-dimension route's
  stability, drift and safety calculation
- **AND THEN** it does not apply the same affinity to a route targeting a
  different dimension

#### Scenario: Route load is exhausted

- **WHEN** active or queued teleport sessions have reserved every
  server-calculated route-load slot for an anchor
- **THEN** a new teleport using that anchor is rejected without consuming
  resources
- **AND THEN** load becomes available only through the server-calculated
  cooldown recovery or a completed session release

#### Scenario: Maintenance material restores a worn array

- **WHEN** an authorized player performs the supported maintenance action on a
  worn anchor with valid repair material
- **THEN** the server uses the array's maintenance-efficiency total to
  calculate restored work and any required material cost
- **AND THEN** maintenance efficiency does not retroactively alter an already
  rolled wear chance

### Requirement: Compatibility profiles and snapshot migration

The system SHALL ship the documented initial balance profiles for brick, metal,
mineral, raw-material and ore families, plus compatibility profiles for other
existing valid blocks, worn variants and four-amethyst-catalyst behavior until
a datapack overrides a profile. Existing saved structure snapshots SHALL decode
safely and be recalculated before a new authoritative quote relies on material
totals.

#### Scenario: Existing array loads after upgrade

- **WHEN** a world contains a structure snapshot written before material
  profiles existed
- **THEN** Nexus loads the snapshot without data loss or client-provided values
- **AND THEN** its next registration, validation or calibration recalculates
  the material totals using compatibility profiles

### Requirement: Material effects are inspectable

The Space Unit map SHALL receive a server-calculated material breakdown that
identifies every final profile effect, including capacity, stability,
accuracy/drift, target lock, safety, wear, maintenance, interference, food,
phase speed, cooldown/load, dimensional affinity and cross-dimension catalyst
effects. The in-game Nexus manual SHALL explain that material profiles can
provide several signed effects and SHALL direct players to the live breakdown
for datapack-specific values.

#### Scenario: Player inspects a mixed-material destination

- **WHEN** a player opens the Space Unit map for a destination built from
  several profiled materials
- **THEN** the map displays the server-calculated totals and contributing
  material effects
- **AND THEN** the displayed cross-dimension cost information matches the
  authoritative quote used for teleportation
