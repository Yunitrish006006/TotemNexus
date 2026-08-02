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

The scan begins at the 26 positions in the 3×3×3 cube around the lodestone.
Only a scanned block's positive local expansion value exposes more positions;
the absolute Chebyshev distance limit is five and unloaded chunks are never
forced to load.
