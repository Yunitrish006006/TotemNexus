## Context

Nexus currently scans a fixed, bounded volume around a lodestone. A permitted
block contributes one structural slot; cracked blocks and oxidized copper add
wear; amethyst is counted separately for cross-dimension shard discounts.
This cannot express a block such as "large but inaccurate", "precise but
fragile", or "expensive to build but reduces inter-dimensional fuel".

## Goals / Non-Goals

### Goals

- Let each material provide any combination of positive and negative values.
- Keep all final teleport outcomes deterministic, bounded and server-owned.
- Make material balancing data-driven and explainable to players.
- Preserve existing arrays and the current amethyst default until a datapack
  intentionally changes their profile.

### Non-Goals

- Do not make material values client-authoritative.
- Do not enlarge scanning without a hard, server-configured maximum or force
  chunks to load.
- Do not change interface-item bonuses (book, map or recovery compass) in this
  proposal.

## Decisions

### Material profile resource

The server will load an effective profile for every valid block state from
datapack resources. Every omitted scalar value is zero; map values are empty.
The profile schema uses these signed values:

| Attribute | Meaning | Aggregation and safety rule |
| --- | --- | --- |
| `structure_capacity` | Effective structure size supplied by a block. | Sum across the bounded array; final capacity is never below zero. It replaces raw equal block count for tier and capacity checks. |
| `scan_expansion_radius` | Local detection radius emitted by a scanned material. | A positive effective value expands around that block's own position, allowing chained placement-driven growth. It is clamped to 0–2 per block; negative state modifiers can cancel a base expansion but cannot unscan positions already reached. |
| `stability` | Material adjustment to geometric route stability. | Added after completeness and symmetry are evaluated; final stability is clamped to the valid route range. |
| `arrival_accuracy` | Adjustment to landing precision. | Added to the stability-derived baseline; a higher value reduces horizontal drift and a lower value increases it within a configured safe maximum. |
| `target_lock` | Extra precision for long-distance and cross-dimension target acquisition. | Added only to the distance and cross-dimension portion of drift; it cannot make a landing more accurate than the configured minimum drift. |
| `arrival_safety` | Material adjustment to the arrival-damage chance. | Positive values reduce damage chance and negative values increase it; final probability is clamped to 0–100%. |
| `wear_resistance` | Adjustment to the chance of structure wear. | Positive values reduce wear chance and negative values increase it; the final probability is clamped to 0–100%. |
| `maintenance_efficiency` | Repair work restored per valid maintenance material. | Does not change wear chance. It changes the server-calculated repair work needed to restore a worn array, with a non-zero configured minimum. |
| `interference_resistance` | Resistance to material-mixing and environmental interference. | Added against computed interference; negative values increase interference. Final interference is clamped to the valid route range. |
| `food_efficiency` | Adjustment to food-equivalent teleport cost. | Applied after distance and target-type cost; final cost is clamped to the configured non-negative cost range. |
| `phase_speed` | Adjustment to preparation duration. | Positive values shorten preparation and negative values lengthen it; final time is clamped to the configured tick range. |
| `cooldown_recovery` | Rate at which an array recovers reserved route load after a session. | Positive values recover faster and negative values slower; final recovery time remains within configured bounds. |
| `route_load_capacity` | Number of simultaneous or queued teleport loads an anchor can reserve. | Added to the configured baseline; final capacity is at least one and no route may reserve more than its server-calculated capacity. |
| `dimension_affinity` | Per-destination-dimension signed affinity map. | For a cross-dimension route, source affinity matching the target dimension and target affinity matching the source dimension adjust cross-dimension stability, drift and safety. |
| `cross_dimension_catalyst_units` | Quarter-shard units applied only to lodestone-to-lodestone cross-dimension routes. | Totals are divided toward zero by the configured units-per-shard default (4). Positive totals reduce shard cost; negative totals add shard cost. A paid route still costs at least one shard. |

One block can set every attribute in the table. Negative values are first-class
values, not a separate "bad material" flag. The resource schema must reject
malformed values outside `-8..8` per scalar or per affinity entry; totals are
bounded again before they enter quote calculations.

### Profile resolution and data layout

Resources live under `data/deadrecall/teleport_array_material_profiles/` and
compile into an immutable server registry on data reload. A resource contains a
schema version, a material family, a selector, `valid_structure_material`, the
attribute object and the dimension-affinity map. A minimal profile is:

```json
{
  "schema_version": 1,
  "id": "deadrecall:iron_block",
  "family": "iron",
  "selector": { "blocks": ["minecraft:iron_block"] },
  "valid_structure_material": true,
  "attributes": {
    "structure_capacity": 2,
    "scan_expansion_radius": 1,
    "route_load_capacity": 2,
    "phase_speed": -1
  },
  "dimension_affinity": {}
}
```

Resolution is deterministic and cacheable by `BlockState`:

1. Select exactly one base profile: exact block identifier wins over family or
   tag fallback. A same-priority tie is a reload error, not a silently summed
   profile.
2. Apply only the base profile's declared state modifiers in fixed order.
   For copper this is **shape → oxidation → wax**. Each layer is applied once.
3. Apply an optional exact-block datapack overlay. It can replace the base
   profile or add one named overlay, but cannot add a second validity source.
4. Clamp the compiled profile to schema bounds, cache it, and expose the
   compiled profile revision to every later scan and quote.

This controlled composition lets a waxed weathered copper grate receive its
three intended layers without the accidental double counting that arbitrary
overlapping tags would cause. A custom material can use only a base profile;
state layering is optional rather than required.

### Units, defaults and final clamps

Attribute values are integer points. The built-in conversion rules below make
the balance table reviewable and are server-configurable only as one coherent
balance set, never from client input.

| Output | Default calculation and bound |
| --- | --- |
| Effective capacity | `max(0, sum(structure_capacity))`; Tier 1 at 8 and Tier 2 at 24 capacity. |
| Scan envelope | Start at the centre-excluded 3×3×3 seed. For every scanned block, enqueue the cube around that block with `radius = clamp(0, 2, scan_expansion_radius)`. Continue until no new position is reached; only positions within Chebyshev distance 5 of the lodestone are eligible. |
| Stability | `clamp(0, 100, geometric_stability + stability_total - interference_total + matching_affinity)`. |
| Interference | `clamp(0, 100, 2 × (distinct material families - 1) + environmental_penalty - interference_resistance_total)`. |
| Drift / accuracy | Start with the existing stability-derived drift band; subtract `arrival_accuracy_total`, and subtract `target_lock_total` only for distance and cross-dimension drift; clamp to 1–96 blocks. |
| Arrival damage | `clamp(0, 60, base_damage - arrival_safety_total - matching_affinity)`. |
| Wear chance | `clamp(0, 100, base_wear - wear_resistance_total)`. |
| Food cost | `ceil(base_food × clamp(50, 200, 100 - food_efficiency_total) / 100)`; a non-zero base cost remains at least one point. |
| Preparation | `clamp(40, 300, base_prepare_ticks - phase_speed_total)`. |
| Route load | Each endpoint has `clamp(1, 8, 1 + route_load_capacity_total)` slots; a route uses the smaller endpoint value. |
| Load recovery | `clamp(20, 600, base_recovery_ticks × 100 / clamp(25, 200, 100 + cooldown_recovery_total))`. |
| Maintenance | `ceil(base_repair_cost × 100 / clamp(25, 200, 100 + maintenance_efficiency_total))`; a repair action costs at least one valid repair item. |
| Catalyst shards | `trunc_toward_zero(source_units + target_units) / 4` changes the shard quote; positive units reduce and negative units increase it. A paid route costs at least one shard. |

`matching_affinity` is the sum of the source profile's affinity for the target
dimension and the target profile's affinity for the source dimension. It is
zero for same-dimension routes. `environmental_penalty` is initially zero and
is reserved for later, server-owned dimension or weather rules; this feature
must not invent client-side environmental input.

### Bounded material-driven scan expansion

`structure_capacity` is effective assembly size, while
`scan_expansion_radius` explicitly controls physical scan expansion. The
scanner always starts with the centre-excluded 3×3×3 cube centred on the
lodestone—one block in every direction, leaving 26 seed positions—and reads
only already-loaded chunks.

Expansion is local and placement-driven, not a global shell unlock. Each
scanned block with positive effective `scan_expansion_radius` exposes the cube
around **that block** at its local radius. Every newly reached position is
scanned; if it contains another positive expansion material, it exposes its own
surrounding cube. This produces a directional expansion graph that follows the
builder's placement path. For example, an extender east of the lodestone can
reveal farther east blocks without automatically revealing equally distant
western blocks.

The visited-position set is the order-independent transitive closure of that
graph. It has two fixed safety limits: no position farther than Chebyshev
distance 5 from the lodestone is eligible (at most 1,330 positions excluding the
lodestone), and one material's effective local radius is at most 2. A negative
value in a composed profile can reduce an extender to zero reach, such as an
oxidized copper-bulb modifier cancelling its base reach; it cannot retract a
position already visited. No profile may bypass the global distance cap, and a
future higher cap needs a separate approved performance and migration change.

### Structure scan and quote pipeline

Every registration, calibration, validation interval and profile-reload retry
uses the same server pipeline:

1. Read only loaded positions in the initial 3×3×3, centre-excluded envelope
   around the lodestone.
2. Resolve and cache the material profile for each candidate `BlockState`. A
   positive local expansion profile enqueues positions around that block; repeat
   until the bounded visited-position graph reaches a fixed point.
3. Collect raw structural positions, exact mirrored-block symmetry, material
   family counts, worn state, maximum reached distance and signed attribute
   totals in one deterministic scan result.
4. Compute geometric completeness from effective capacity and symmetry, then
   compute interference and final stability using the table above.
5. Persist a `MaterialStructureSnapshot` with raw counts, final totals,
   family breakdown, profile revision and evaluated stability. Do not persist a
   client quote.
6. When a player requests a teleport, recalculate endpoint-sensitive values
   (distance, affinity, food, shards, drift, damage, load availability) from
   the current snapshots before a quote is shown or resources are consumed.
7. Just before departure, revalidate the profile revision, endpoint positions,
   selected interface, player resources and route reservations. A changed
   result cancels safely before deduction.

### Built-in brick-family balance

The following values are the initial built-in balance for the brick-like
materials already accepted by the array tag. Values are attribute units, not
literal percentages; conversion, rounding and final limits remain
server-configured. `affinity[overworld]` and `affinity[nether]` refer to
`minecraft:overworld` and `minecraft:the_nether`. Every omitted value,
including `cross_dimension_catalyst_units`, is zero.

| Material | Initial non-zero profile values | Intended trade-off |
| --- | --- | --- |
| Stone bricks | `capacity +1`, `stability +1`, `maintenance +1`, `affinity[overworld] +1` | Dependable baseline. |
| Mossy stone bricks | `capacity +1`, `interference +2`, `maintenance +1`, `affinity[overworld] +1`, `accuracy -1` | More tolerant of interference, less precise. |
| Chiseled stone bricks | `capacity +1`, `accuracy +2`, `target_lock +2`, `arrival_safety +1`, `route_load -1` | Precise single-route focus at the cost of traffic capacity. |
| Cracked stone bricks | `capacity +1`, `stability -2`, `wear_resistance -3`, `arrival_safety -1`, `maintenance -2` | Still structural, but visibly worn and risky. |
| Deepslate bricks | `capacity +2`, `stability +2`, `wear_resistance +2`, `arrival_safety +1`, `phase_speed -1`, `affinity[overworld] +2` | Large, durable array material that charges slowly. |
| Deepslate tiles | `capacity +2`, `stability +1`, `accuracy +1`, `target_lock +1`, `arrival_safety +1`, `phase_speed -1`, `affinity[overworld] +2` | Durable with better targeting, still heavy to phase. |
| Polished deepslate | `capacity +2`, `stability +2`, `food_efficiency +1`, `maintenance +2`, `phase_speed -2`, `affinity[overworld] +2` | Efficient and maintainable, but slow to prepare. |
| Cracked deepslate bricks / tiles | `capacity +1`, `stability -2`, `wear_resistance -3`, `arrival_safety -1`, `maintenance -2`, `affinity[overworld] +1` | Degraded deep structure with a retained physical footprint. |
| Chiseled deepslate | `capacity +2`, `accuracy +2`, `target_lock +2`, `arrival_safety +1`, `phase_speed -1`, `affinity[overworld] +2` | Premium accurate deep-array component. |
| Nether bricks | `capacity +1`, `phase_speed +2`, `cooldown_recovery +1`, `food_efficiency -1`, `interference_resistance -1`, `affinity[nether] +2` | Fast Nether-tuned material that is hungry and noisy. |
| Red nether bricks | `capacity +1`, `phase_speed +1`, `accuracy +1`, `arrival_safety +1`, `food_efficiency -1`, `wear_resistance -1`, `affinity[nether] +2` | More controlled Nether phase at a durability and food cost. |
| Cracked nether bricks | `capacity +1`, `stability -2`, `wear_resistance -3`, `arrival_safety -1`, `maintenance -2`, `affinity[nether] +1` | Damaged Nether material; usable only as a deliberate penalty. |
| Chiseled nether bricks | `capacity +1`, `phase_speed +1`, `accuracy +2`, `target_lock +2`, `food_efficiency -1`, `affinity[nether] +3` | Fast, locked Nether targeting with higher food demand. |
| Polished blackstone | `capacity +2`, `stability +1`, `interference_resistance +2`, `maintenance +2`, `arrival_safety +1`, `phase_speed -1`, `affinity[nether] +1` | Safe and low-maintenance anti-interference shell. |
| Polished blackstone bricks | `capacity +2`, `stability +2`, `wear_resistance +1`, `arrival_safety +2`, `route_load +1`, `phase_speed -1`, `affinity[nether] +1` | Durable, safe high-throughput material that charges slowly. |
| Cracked polished blackstone bricks | `capacity +1`, `stability -2`, `wear_resistance -3`, `arrival_safety -1`, `maintenance -2`, `interference_resistance -1`, `affinity[nether] +1` | Worn blackstone with compounding risk. |
| Chiseled polished blackstone | `capacity +2`, `accuracy +1`, `target_lock +3`, `interference_resistance +1`, `cooldown_recovery +1`, `phase_speed -1`, `affinity[nether] +2` | Strong target lock and recovery, not fast to initiate. |

### Built-in metal and mineral balance

Metal, mineral, raw-material and ore blocks become valid array materials in
this change. Their profiles below are additive in exactly the same way as the
brick profiles. This is an eligibility expansion: an existing array remains
valid, while these new blocks become available once the profile resource is
installed.

#### Metals and raw metal blocks

| Material | Initial non-zero profile values | Intended trade-off |
| --- | --- | --- |
| Iron block | `capacity +2`, `scan_expansion_radius +1`, `stability +1`, `wear_resistance +1`, `arrival_safety +1`, `route_load +2`, `maintenance +1`, `phase_speed -1` | Dependable industrial high-throughput metal that expands from each placed block. |
| Gold block | `capacity +1`, `food_efficiency +2`, `phase_speed +2`, `cooldown_recovery +2`, `wear_resistance -1`, `arrival_safety -1`, `affinity[nether] +1` | Fast and efficient, but soft and less safe. |
| Netherite block | `capacity +3`, `scan_expansion_radius +2`, `stability +3`, `wear_resistance +3`, `arrival_safety +3`, `route_load +2`, `interference_resistance +1`, `phase_speed -2`, `maintenance -1`, `affinity[nether] +2` | Best all-round protection and capacity, able to extend two local blocks but heavy to initialise and repair. |
| Raw iron block | `capacity +1`, `stability -1`, `wear_resistance -1`, `interference_resistance -1`, `maintenance -1` | Cheap unfinished iron with a clear stability cost. |
| Raw gold block | `capacity +1`, `phase_speed +1`, `food_efficiency +1`, `stability -1`, `wear_resistance -2`, `arrival_safety -1`, `interference_resistance -1`, `affinity[nether] +1` | Fast unfinished gold, especially fragile. |
| Raw copper block | `capacity +1`, `phase_speed +1`, `cooldown_recovery +1`, `interference_resistance -2`, `wear_resistance -1` | Unrefined conductive material with noisy routing. |

#### Copper matrix

Each copper variant uses one shape profile, then receives its oxidation and
wax adjustment. This defines every copper block, cut copper, chiseled copper,
copper grate and copper bulb variant without duplicating forty rows.

| Copper shape | Base non-zero profile values | Intended trade-off |
| --- | --- | --- |
| Copper block | `capacity +1`, `accuracy +1`, `phase_speed +1`, `cooldown_recovery +1`, `route_load +1`, `maintenance +1` | Balanced conductive baseline. |
| Cut copper | `capacity +1`, `accuracy +2`, `target_lock +1`, `phase_speed +1` | Precision-oriented conductive plate. |
| Chiseled copper | `capacity +1`, `accuracy +1`, `target_lock +3`, `arrival_safety +1`, `phase_speed +1` | Strong lock geometry with modest capacity. |
| Copper grate | `capacity +1`, `phase_speed +2`, `cooldown_recovery +1`, `interference_resistance -2`, `arrival_safety -1` | Fast vented phase path that admits interference. |
| Copper bulb | `capacity +1`, `scan_expansion_radius +1`, `phase_speed +1`, `cooldown_recovery +2`, `route_load +2`, `stability -1`, `wear_resistance -1` | High throughput power node that can extend scans from its own position but is less stable. |

| State adjustment | Additional profile values |
| --- | --- |
| Exposed | `scan_expansion_radius -1`, `stability -1`, `accuracy -1`, `wear_resistance -1`, `maintenance -1` |
| Weathered | `scan_expansion_radius -2`, `stability -2`, `accuracy -2`, `wear_resistance -2`, `maintenance -2` |
| Oxidized | `scan_expansion_radius -3`, `stability -3`, `accuracy -3`, `wear_resistance -3`, `maintenance -3` |
| Any waxed state | `wear_resistance +1`, `maintenance +1` |

#### Minerals, crystals and storage blocks

| Material | Initial non-zero profile values | Intended trade-off |
| --- | --- | --- |
| Amethyst block | `capacity +1`, `scan_expansion_radius +1`, `accuracy +2`, `target_lock +1`, `arrival_safety +1`, `cross_dimension_catalyst_units +1`, `phase_speed -1` | The cross-dimension catalyst and a precise, slow crystal that expands around each placed block. |
| Quartz block | `capacity +1`, `accuracy +1`, `phase_speed +1`, `food_efficiency +1`, `interference_resistance -1`, `affinity[nether] +1` | Efficient, quick Nether crystal that dislikes interference. |
| Diamond block | `capacity +2`, `scan_expansion_radius +2`, `stability +2`, `accuracy +2`, `arrival_safety +2`, `wear_resistance +2`, `maintenance +1`, `route_load +1` | Premium durable precision material that efficiently extends two local blocks. |
| Emerald block | `capacity +1`, `food_efficiency +2`, `cooldown_recovery +1`, `maintenance +2`, `stability +1`, `affinity[overworld] +1` | Economical, maintainable Overworld logistics material. |
| Lapis block | `capacity +1`, `accuracy +2`, `target_lock +2`, `phase_speed +1`, `interference_resistance -1`, `arrival_safety -1`, `affinity[overworld] +1` | Quick divination and lock material with safety risk. |
| Redstone block | `capacity +1`, `scan_expansion_radius +1`, `phase_speed +3`, `cooldown_recovery +2`, `route_load +1`, `interference_resistance -3`, `arrival_safety -1`, `wear_resistance -1` | Excellent pulse throughput and local scan expansion, but highly noisy. |
| Coal block | `capacity +1`, `phase_speed +1`, `stability -1`, `interference_resistance -1`, `arrival_safety -1`, `maintenance -1` | Fuel-like speed at a reliability cost. |

#### Ore and debris blocks

Ore blocks are intentionally valid but poor permanent structure material. They
provide a cheap, thematic temporary build path while their exposed veins cause
interference and maintenance pressure.

| Material family | Blocks | Initial non-zero profile values |
| --- | --- | --- |
| Coal ore | Coal ore and deepslate coal ore | `capacity +1`, `scan_expansion_radius -1`, `phase_speed +1`, `stability -1`, `interference_resistance -2`, `arrival_safety -1`, `maintenance -1`; deepslate adds `capacity +1`, `phase_speed -1`, `affinity[overworld] +1`. |
| Base-metal ores | Iron, copper and gold ore plus deepslate variants | `capacity +1`, `scan_expansion_radius -1`, `stability -1`, `wear_resistance -1`, `interference_resistance -2`, `arrival_safety -1`, `maintenance -1`; gold additionally has `phase_speed +1`, `affinity[nether] +1`; deepslate adds `capacity +1`, `phase_speed -1`, `affinity[overworld] +1`. |
| Precision-mineral ores | Redstone, lapis, diamond and emerald ore plus deepslate variants | `capacity +1`, `scan_expansion_radius -1`, `accuracy +1`, `target_lock +1`, `stability -1`, `wear_resistance -1`, `interference_resistance -2`, `maintenance -1`; redstone additionally has `phase_speed +1`, `cooldown_recovery +1`, `interference_resistance -1`; diamond and emerald additionally have `arrival_safety +1`; deepslate adds `capacity +1`, `phase_speed -1`, `affinity[overworld] +1`. |
| Nether ores | Nether gold ore and nether quartz ore | `capacity +1`, `scan_expansion_radius -1`, `phase_speed +1`, `stability -1`, `wear_resistance -1`, `interference_resistance -2`, `arrival_safety -1`, `maintenance -1`, `affinity[nether] +2`; quartz additionally has `accuracy +1`. |
| Ancient debris | Ancient debris | `capacity +2`, `stability +1`, `wear_resistance +2`, `arrival_safety +2`, `interference_resistance +1`, `phase_speed -2`, `maintenance -1`, `affinity[nether] +2`. |

### Material-identity balance rules

The table is not a collection of arbitrary numerical bonuses. Every built-in
profile must explain its values using the block's vanilla form, state and
origin. Reviewers must reject a profile whose non-zero values have no such
explanation.

| Vanilla property | Required profile interpretation |
| --- | --- |
| Dense or refined construction (deepslate, iron, diamond, netherite) | More capacity, stability, safety, wear resistance or load; any phase-speed penalty represents mass rather than a hidden nerf. |
| Cracked block | Must not exceed its intact counterpart in stability, safety, wear resistance or maintenance efficiency. It remains usable only because its capacity represents physical footprint. |
| Copper oxidation | Each oxidation level must be monotonically no better than the previous level in stability, accuracy, wear resistance and maintenance efficiency. |
| Waxed copper | Compares only with the same shape and oxidation level; it may improve wear resistance and maintenance, but must not erase the oxidation penalties or add unrelated free capacity. |
| Open or powered-looking form (grate or bulb) | May improve phase speed, recovery or load, but must pay with interference, safety, stability or wear. |
| Crystal or signal material (amethyst, quartz, lapis, redstone) | Precision, locking, catalysts or pulse speed are appropriate; signal power must carry an interference or safety trade-off. |
| Precious refined material (diamond, emerald, netherite) | Must provide a durable or logistical advantage that justifies scarcity; it cannot make every cost, risk and preparation value optimal simultaneously. |
| Raw material, ore or debris | May be a valid temporary structure but must retain a maintenance, interference, stability or safety drawback versus its refined storage form. |
| Nether-origin material | May carry Nether affinity and phase behavior; it must not gain an unexplained End or Overworld affinity. |

Dimension affinity is therefore a thematic property, not a generic bonus: stone
and deepslate tune toward the Overworld, Nether-origin blocks tune toward the
Nether, and a later End-material pass must be the only source of End affinity.

### Compatibility and non-brick defaults

Built-in compatibility profiles preserve existing worlds while brick materials
receive the intentional first balance table above:

- Valid unprofiled materials receive their current one-slot equivalent until
  their own balance pass is approved. The new metal, mineral, raw-material and
  ore families use the documented profiles above.
- Existing cracked and oxidized non-brick materials retain their wear penalty.
- Amethyst supplies one catalyst unit, so four combined endpoint units reduce
  one cross-dimension shard exactly as today.
- A missing optional profile uses a neutral profile only when the block is
  already permitted by the existing structure tag; non-structural blocks do
  not become valid merely by lacking data.

### Snapshot and reload model

Structure snapshots persist the evaluated material totals and profile revision
needed for a teleport quote. Legacy snapshots decode with neutral defaults and
must be recalculated on the next validation, calibration or registration.
Reloading profiles invalidates affected live snapshots; the server recalculates
them before issuing the next quote rather than trusting client state.

### Route load, cooldown and maintenance interactions

Route load is a short-lived server reservation, not an item or a client-side
queue. Starting a teleport reserves one slot on both source and target as one
atomic operation. A rejected request reserves nothing. Cancelling before
departure releases the slot immediately; completing a teleport releases it
through the endpoint's calculated cooldown-recovery timer. Reservations are
intentionally ephemeral and are cleared on server restart rather than being
saved as potentially stale player sessions.

Maintenance is an explicit owner or administrator action in the Space Unit map
while the player is within the existing 8-block management radius. The player
selects a worn structural position from a server-provided list and supplies a
valid material from that position's family. The server calculates the required
item count from `maintenance_efficiency`, consumes only that count, replaces
the worn state with the supplied valid state, rescans the array and records an
audit entry. It never manufactures blocks, repairs unloaded positions or
changes a prior random wear roll.

### Player explanation

The Space Unit map must show final values and a server-provided material
breakdown: capacity, stability, accuracy/drift, target lock, safety, wear,
maintenance, interference, food, phase speed, cooldown/load, dimensional
affinity and cross-dimension catalyst adjustment. The manual must explain that
materials may carry multiple signed attributes and direct players to the map
for live values. It must not claim a static material table is exhaustive when
datapacks can alter profiles.

The map adds a Material tab to each lodestone detail view:

- a compact route summary shows capacity/tier, stability, drift, arrival risk,
  food, shard cost, preparation time, load slots and recovery;
- expandable rows group contributions by material family and show positive
  values in green, negatives in red and neutral values in gray;
- a cross-dimension route has a separate affinity row for both endpoints and a
  catalyst calculation (`source + target → shard change`);
- owners and administrators see worn-position maintenance actions and the
  server-calculated item cost; other players only see read-only diagnostics.

The manual gains an attribute glossary and a worked example using stone/deep
slate, copper, amethyst and a negative ore contribution. It tells players that
the Material tab, not the static manual, is authoritative for datapack values.

## Risks / Trade-offs

- Signed combinations are harder to reason about → expose totals and each
  contributing material in the map, and test clamp boundaries.
- Resource reload can change an active route → the server re-quotes before
  resource consumption and cancels an obsolete quote safely.
- Larger logical capacity can tempt wider scans → keep capacity independent
  from the hard scan bound and test that evaluation never force-loads chunks.
- Load slots and repair requests add mutable server state → reserve atomically,
  release deterministically and include cancellation/restart GameTests.
- The first balance table is broad → ship it as data, not code, so a balance
  adjustment requires a datapack/resource change and focused regression tests.
- A profile can drift away from a block's identity during balance changes →
  require the material-identity rules and state-comparison tests below before
  accepting a built-in profile change.

## Migration Plan

1. Ship built-in profiles and tags for the initial material catalogue; retain
   every legacy valid material as a neutral fallback unless its documented
   balance replaces it.
2. Decode older structure snapshots with neutral absent material fields and no
   reservations.
3. Recalculate a legacy snapshot at its next normal validation, calibration or
   first material-detail open; the recalculation begins at the new 3×3×3 seed
   and follows only material-derived local expansion paths. Do not change it
   solely on world load.
4. Version the Space Unit map payload and only send material detail fields to
   a matching Nexus client. A mismatched client must be rejected by the normal
   mod-version handshake rather than receive a partially decoded payload.
5. Retain legacy tags during the first release so existing datapacks and worlds
   remain readable; log diagnostics for blocks that are tagged but use the
   neutral fallback.

## Open Questions

- Future material families such as End stone, purpur, prismarine and modded
  blocks remain a separate balance review.

## Approved tuff and obsidian extension

The following three exact vanilla blocks extend the built-in catalogue. They
remain separate families so mixing and datapack diagnostics preserve their
physical identity. Omitted attributes are zero.

| Material | Family | Final non-zero values | Rationale and trade-off |
| --- | --- | --- | --- |
| `minecraft:tuff` | `tuff` | `structure_capacity +1`, `stability -1`, `maintenance_efficiency +1`, `affinity[minecraft:overworld] +1` | Cheap Overworld structure that is easy to service, but less stable than worked stone brick and provides no expansion, precision, safety or traffic bonus. |
| `minecraft:obsidian` | `obsidian` | `structure_capacity +2`, `stability +3`, `arrival_safety +1`, `wear_resistance +3`, `interference_resistance +3`, `maintenance_efficiency -3`, `phase_speed -3` | Very stable, durable and interference-resistant volcanic glass; its hardness makes phasing slow and repairs expensive. It provides no scan expansion, precision, target lock, load or catalyst benefit. |
| `minecraft:crying_obsidian` | `crying_obsidian` | `structure_capacity +2`, `stability -1`, `arrival_accuracy +3`, `target_lock +3`, `arrival_safety -2`, `interference_resistance -2`, `phase_speed -1`, `affinity[minecraft:the_nether] +3` | Strong Nether resonance and destination positioning, paid for by interference, unsafe arrival behavior and slower phasing. It provides no scan expansion or catalyst discount. |

These values deliberately avoid a universally best material. Tuff is the
low-cost baseline; obsidian is defensive but slow and maintenance-heavy;
crying obsidian specializes in target acquisition while weakening safety and
interference resistance. All values continue through the existing aggregate
clamps and server-owned quote formulas.
