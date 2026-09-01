## Implementation evidence

- `NexusTeleportArrayExpansionRules` owns the registered enum gamerule,
  bilingual description keys, stable default/mode codes and loaded-anchor
  refresh callback.
- `TeleportArrayMaterialScan` dispatches once per scan, retaining the existing
  local queue traversal and adding the centred max-based fixed-point traversal.
  Both paths share one loaded-only accumulator and one result type.
- `SpaceStructureSnapshot` material totals record the mode code and distinguish
  an explicit local zero from an absent legacy key. Quote material lookup
  detects a missing/mismatched mode, rescans only a loaded anchor and refuses
  to use a stale unloaded snapshot.
- Existing visualization authority, wear, repair and snapshot code continue to
  call `TeleportArrayMaterialScan.scan`; no parallel geometry implementation
  exists.

## Automated evidence

- Java 25, TotemCore 0.7.14, isolated Gradle cache:
  `test compileGametestJava` — **PASS**.
- Clean Fabric Server GameTest suite:
  `build/run/gameTest/logs/latest.log` — **78/78 required tests passed**.
- JUnit locks `local`/`centered`, default `local`, stable mode codes `0`/`1`,
  explicit-versus-missing snapshot markers, absence from client payload record
  surfaces, and bilingual gamerule language-key parity.
- Server GameTests cover local versus centered geometry, complete symmetric
  cubes, fixed-point chaining, non-stacking emitters, radius-five cap,
  loaded-only boundaries, mode-refreshed snapshots/worn targets and exact
  visualization transformation from production scan sets.

- Java 25 / TotemCore 0.7.14 release preparation:
  `test compileGametestJava build` — **PASS**; the build-owned clean Server
  GameTest invocation also passed all **78/78** required tests.
- Strict OpenSpec validation — **PASS** for
  `add-material-attribute-teleport-arrays`,
  `add-teleport-array-block-visualization` and
  `add-teleport-array-expansion-modes`.
- English/Traditional Chinese exact key parity and JSON parsing — **PASS**;
  `git diff --check` — **PASS**.
- Release artifact `build/libs/totem-nexus-0.3.9.jar` embeds module version
  `0.3.9`, requires `totem-core >=0.7.14 <0.8.0`, and contains the registered
  gamerule classes plus both language and material-profile resources. SHA-512:
  `f55dfb2e9a798e929980406a2adfdae70f4a72df2e90f90eb801714553c7ee4ecf6d762a07664099998e6f8d5ad808c98613d81224dffc3618045d414fa99b9d`.
