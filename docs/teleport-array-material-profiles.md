# Teleport-array material profiles

Server datapacks may define signed material values below
`data/<namespace>/teleport_array_material_profiles/*.json`. Every file uses
`"schema_version": 1` and contains a `profiles` array. Reloading is atomic:
if any file is invalid, the previous compiled registry remains active and the
server log identifies the resource and invalid field.

```json
{
  "schema_version": 1,
  "profiles": [{
    "id": "example:reinforced_iron",
    "family": "iron",
    "priority": 0,
    "selector": { "blocks": ["minecraft:iron_block"] },
    "valid_structure_material": true,
    "attributes": {
      "structure_capacity": 2,
      "scan_expansion_radius": 1,
      "route_load_capacity": 2,
      "phase_speed": -1
    },
    "dimension_affinity": { "minecraft:overworld": 1 }
  }]
}
```

All attribute and affinity values must be integers from `-8` through `8`.
Omitted values are zero. Valid attribute names are
`structure_capacity`, `scan_expansion_radius`, `stability`,
`arrival_accuracy`, `target_lock`, `arrival_safety`, `wear_resistance`,
`maintenance_efficiency`, `interference_resistance`, `food_efficiency`,
`phase_speed`, `cooldown_recovery`, `route_load_capacity`, and
`cross_dimension_catalyst_units`. Affinity map keys are dimension identifiers,
for example `minecraft:the_nether`.

## Selectors and precedence

`selector.blocks` is a list of exact block identifiers. `selector.block_tags`
is an optional list of block-tag identifiers. A profile needs at least one of
them. Resolution is deterministic:

1. An exact `blocks` match beats any matching `block_tags` match.
2. Within the same selector class, the higher `priority` wins (default `0`).
3. A same-priority tie is a reload error; it is never resolved by resource
   load order.

The selected base profile then receives built-in copper state adjustments in
the fixed shape → oxidation → wax order. Custom non-copper blocks do not need
state modifiers.

An optional exact-block overlay may add attributes after those adjustments:

```json
{
  "id": "example:iron_overlay",
  "family": "iron",
  "overlay": true,
  "priority": 1,
  "selector": { "blocks": ["minecraft:iron_block"] },
  "attributes": { "arrival_safety": 1 }
}
```

Overlays cannot declare `valid_structure_material`, cannot use block tags, and
only one winning overlay is allowed per block. Set `"replace_base": true` to
replace the selected profile's attributes and family while preserving whether
the base was a valid structure material.

## Operational notes

Profiles are compiled and cached by `BlockState`; a successful reload clears
that cache and advances the profile revision. Existing lodestone snapshots are
marked stale by their revision and are re-scanned only when their chunk is
loaded. Until then, their old material bonus is not used for a quote. Existing
legacy tag materials without a profile remain structurally valid through the
neutral compatibility profile and produce one server warning per block type.

The server exposes two expansion algorithms through one world rule:

```text
/gamerule deadrecall:teleport_array_expansion_mode local
/gamerule deadrecall:teleport_array_expansion_mode centered
```

The default is `local`. Both modes begin at the 26 positions in the
centre-excluded 3×3×3 cube around the lodestone and use an absolute Chebyshev
distance cap of five.

- `local` preserves the placement-driven graph. Each scanned structural block
  with positive effective `scan_expansion_radius` exposes a cube around its own
  position. Newly reached emitters may continue that irregular path.
- `centered` keeps one lodestone-centred radius `R`, initially 1. For every
  reached structural emitter at Chebyshev distance `d` with effective local
  radius `r > 0`, it applies `R = max(R, min(5, d + r))`. Whenever `R` grows,
  the scanner evaluates the complete centre-excluded cube of radius `R` and
  repeats until a fixed point. Emitters in the same reached layer do not add
  their radii together.

Both modes read loaded positions only and never force-load a chunk. Changing
the rule immediately rescans loaded active lodestones. Unloaded lodestones are
left unloaded and their stale mode-specific snapshot is not used for a quote;
it is refreshed after the anchor chunk is loaded and legitimately accessed.
Structure snapshots, wear and repair targeting, teleport calculations, and
array/build-site visualization all consume this same production scan.

## Built-in tuff and obsidian profiles

These exact vanilla blocks are included in the built-in catalogue. Omitted
values are zero and remain subject to the normal aggregate clamps.

| Block | Family | Non-zero profile values |
| --- | --- | --- |
| `minecraft:tuff` | `tuff` | capacity `+1`, stability `-1`, maintenance `+1`, Overworld affinity `+1` |
| `minecraft:obsidian` | `obsidian` | capacity `+2`, stability `+3`, safety `+1`, wear `+3`, interference resistance `+3`, maintenance `-3`, phase speed `-3` |
| `minecraft:crying_obsidian` | `crying_obsidian` | capacity `+2`, stability `-1`, accuracy `+3`, target lock `+3`, safety `-2`, interference resistance `-2`, phase speed `-1`, Nether affinity `+3` |

None of these three blocks expands the scan. Tuff is the inexpensive
Overworld baseline, obsidian is defensive but slow and difficult to repair,
and crying obsidian specializes in Nether target acquisition with explicit
safety and interference penalties.
