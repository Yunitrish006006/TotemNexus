## 0. Approval and balance lock

- [x] 0.1 Approve the fifteen-attribute model, the default conversions and
  final clamps in `design.md`.
- [x] 0.2 Approve the brick, metal, mineral, raw-material and ore catalogue
  values, including the copper shape/oxidation/wax matrix.
- [x] 0.3 Confirm that detection starts at the centre-excluded 3×3×3 cube and
  follows each scanned block's local `scan_expansion_radius` path, with hard
  Chebyshev distance 5 from the lodestone; defer End, prismarine and modded
  material balance to separate changes.

## 1. Profile resources and reload authority

- [x] 1.1 Define immutable Java records/codecs for profile attributes,
  affinity maps, selectors, base profiles, state modifiers and compiled
  profiles; reject scalar and affinity values outside `-8..8`.
- [x] 1.2 Add resource loading under
  `data/deadrecall/teleport_array_material_profiles/`, including schema version
  diagnostics, atomic reload and a monotonically changing profile revision.
- [x] 1.3 Implement deterministic selection: one base profile, declared shape
  modifier, oxidation modifier, wax modifier, then one optional exact-block
  datapack overlay; fail a reload on a same-priority base tie.
- [x] 1.4 Cache compiled profiles by `BlockState`; ensure custom non-copper
  profiles do not require state modifiers.
- [x] 1.5 Add built-in profiles and valid-block tag entries for every approved
  brick, metal, mineral, raw-material, ore and ancient-debris block.
- [x] 1.6 Generate or declare the full copper matrix and verify every shape,
  oxidation state and waxed state resolves once.
- [x] 1.7 Keep neutral compatibility profiles for pre-existing valid blocks
  outside the approved catalogue and log a clear fallback diagnostic.
- [x] 1.8 Add exact built-in profiles and structure-tag entries for tuff,
  obsidian and crying obsidian using the approved material-identity values.

## 2. Bounded array evaluation and persistence

- [x] 2.1 Refactor the lodestone scanner into a local-expansion, loaded-chunk-
  only graph walk: begin at the 3×3×3 centre-excluded seed, then expand from
  each scanned block by its effective `scan_expansion_radius` up to Chebyshev
  distance 5 from the lodestone.
- [x] 2.2 Ensure the visited set is an order-independent fixed point; negative
  state modifiers can cancel an individual expander but cannot unscan an
  already evaluated position or create an oscillating result.
- [x] 2.3 Replace raw equal-block tier input with effective structure capacity;
  retain exact mirrored-block symmetry and bounded volume semantics.
- [x] 2.4 Implement geometric stability, family-based interference,
  interference resistance and final stability using the approved formula.
- [x] 2.5 Add a versioned `MaterialStructureSnapshot` to SavedData containing
  raw counts, maximum reached distance, local expansion-path diagnostics,
  capacity, all totals, family breakdown, profile revision and final evaluated
  values.
- [x] 2.6 Decode legacy snapshots safely with neutral material fields and mark
  them stale for lazy server-side recalculation.
- [x] 2.7 Invalidate affected live snapshots on a successful profile reload;
  never trust an old snapshot for a new quote.

## 3. Teleport quote and execution authority

- [x] 3.1 Apply capacity/tier, stability/interference and accuracy/target lock
  to the authoritative route and drift quote with the approved final clamps.
- [x] 3.2 Apply arrival safety, wear resistance, food efficiency and phase
  speed to the server quote before the client receives it.
- [x] 3.3 Calculate matching dimensional affinity from both endpoints only for
  cross-dimension routes; retain existing cross-dimension tier requirements.
- [x] 3.4 Replace the amethyst-only branch with signed catalyst units and
  truncation-toward-zero shard rounding; preserve the one-shard paid minimum.
- [x] 3.5 Revalidate profile revision, live endpoints, permissions, interface,
  resources and all material-derived values immediately before deduction and
  departure.
- [x] 3.6 Update damage and structure-degradation selection so the final wear
  quote, not client data, controls degradation.

## 4. Route load, cooldown and maintenance

- [x] 4.1 Add an ephemeral server route-reservation store keyed by Space Unit;
  atomically reserve both endpoints before accepting a teleport session.
- [x] 4.2 Derive endpoint capacity from `route_load_capacity`, use the smaller
  endpoint as the route bottleneck and reject exhausted routes before costs.
- [x] 4.3 Release cancelled reservations immediately; schedule successful
  reservations through `cooldown_recovery`; clear all reservations safely on
  server stop or restart.
- [x] 4.4 Add an owner/administrator maintenance request with 8-block range,
  selected worn position, server-side family validation and audit output.
- [x] 4.5 Quote and consume the minimum valid number of supplied repair blocks
  from `maintenance_efficiency`, replace only the selected worn state and
  rescan after success.

## 5. Player interface and documentation

- [x] 5.1 Version the Space Unit map payload with a server-calculated Material
  tab; validate all new fields before sending them to the client.
- [x] 5.2 Add compact route summary rows for capacity/tier, stability,
  interference, drift/lock, safety, food, shards, phase time, load and
  recovery.
- [x] 5.3 Add expandable material-family rows with contribution counts,
  signed colour treatment, affinity endpoint rows and catalyst arithmetic.
- [x] 5.4 Add owner/administrator maintenance controls; keep diagnostics
  read-only for every other player and validate every action server-side.
- [x] 5.5 Update the Nexus manual in `en_us` and `zh_tw` with the attribute
  glossary, material-identity explanation and live-inspection workflow.

## 6. Compatibility and datapack operations

- [x] 6.1 Preserve existing `deadrecall` SavedData keys, resource IDs and
  legacy structure tags during the migration release.
- [x] 6.2 Document profile schema, selector precedence, overlay limits,
  affinity identifiers, signed-value bounds and reload failure behavior for
  datapack authors.
- [x] 6.3 Ensure a malformed profile reload retains the last valid compiled
  registry and reports the exact resource and field without corrupting live
  snapshots.
- [x] 6.4 Verify old worlds, existing neutral arrays and current amethyst
  arrays remain readable before recalculation.

## 7. Verification

- [x] 7.0 Verify the tuff/obsidian/crying-obsidian profiles, tag membership,
  data reload and representative mixed/homogeneous array bounds.

- [x] 7.1 Unit-test codecs, schema bounds, selector precedence, tie rejection,
  copper layer order, compiled-profile caching and neutral fallback.
- [x] 7.2 Unit-test every approved formula: capacity, stability/interference,
  accuracy/lock drift, food, phase, safety, wear, affinity, catalyst rounding,
  load, recovery and maintenance clamps.
- [x] 7.3 Unit-test material-identity invariants: cracked versus intact,
  oxidation monotonicity, wax limits, refined versus ore, and dimension-origin
  affinity.
- [x] 7.4 GameTest the 26-position initial scan, directional expansion from a
  single placed extender, chained extenders, no expansion in an unconnected
  direction, negative-state cancellation, hard distance cap, all initial
  material families, mixed positive/negative arrays, profile reload re-quote,
  tag compatibility, stale snapshot refresh and no forced chunk load.
- [x] 7.5 GameTest concurrent reservation races, cancellation, cooldown,
  restart clearing, maintenance authorization/range/cost and physical block
  replacement.
- [x] 7.6 Client-test Material tab rendering, signed colours, narrow layouts,
  tooltip localization and manual text in `en_us` and `zh_tw`.

## 8. Release validation

- [x] 8.1 Run `../TotemCore/gradlew --no-daemon test --console=plain` with
  Java 25.
- [x] 8.2 Run the dedicated-server Fabric GameTests and capture the required
  result, handling any runner shutdown issue separately from test outcomes.
- [x] 8.3 Run the full Nexus build, inspect the final JAR resources and update
  the RESULT artifact only after all approved checks pass.
