# Nexus compatibility surface

This is the extraction gate for the first TotemNexus artifact.  The module
continues to use the `deadrecall` namespace while the compatibility bundle is
supported.  Changing an entry below requires a world migration and a protocol
compatibility review.

## SavedData keys

- `deadrecall:space_units`
- `deadrecall:space_discovery`
- `deadrecall:space_friends`
- `deadrecall:distributed_spawns`

All preserve their current `data_version` field and codecs.

The copied subset is guarded by `NexusCompatibilitySurfaceTest`; extending the
copy requires extending that test before activation in a bundle release.

All payload IDs listed below now have a Nexus codec and compatibility assertion;
their receivers remain inactive until the corresponding authority/UI cutover.

## Payload IDs

- `deadrecall:request_space_unit_map`
- `deadrecall:request_space_unit_friends`
- `deadrecall:remove_space_unit_friend`
- `deadrecall:start_space_unit_teleport`
- `deadrecall:toggle_space_unit_favorite`
- `deadrecall:calibrate_space_unit`
- `deadrecall:update_space_unit_visibility`
- `deadrecall:rename_space_unit`
- `deadrecall:update_space_unit_access`
- `deadrecall:confirm_space_unit_registration`
- `deadrecall:space_unit_map`
- `deadrecall:space_unit_friends`
- `deadrecall:space_unit_registration_preview`
- `deadrecall:request_death_node_admin`
- `deadrecall:manage_death_node_admin`
- `deadrecall:death_node_admin`

## Extraction rules

The first standalone copy is additive. It registers the optional Core
death-backpack lifecycle adapter and must not require TotemRemnant. It may only
replace the bundle implementation after standalone, legacy-world, restart,
multi-player, dimension and Dedicated Server validation passes.
