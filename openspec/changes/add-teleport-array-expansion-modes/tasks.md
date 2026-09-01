## 1. World rule and authority

- [x] 1.1 Register the namespaced enum gamerule with stable `local` and
  `centered` command values and a `local` default.
- [x] 1.2 Add English and Traditional Chinese title, description and value
  labels.
- [x] 1.3 Refresh loaded active lodestone snapshots on rule changes without
  loading absent chunks, and persist a compatible mode code in material totals.

## 2. Traversal algorithms

- [x] 2.1 Preserve the current placement-driven local traversal.
- [x] 2.2 Add the lodestone-centred, max-based fixed-point traversal with a
  radius-one seed and hard radius-five cap.
- [x] 2.3 Share aggregation, loaded-only visited sets, structural/emitter sets
  and buildable-site classification between both modes.

## 3. Coverage and documentation

- [x] 3.1 Add JUnit compatibility and bilingual resource coverage for the
  default, stable command values, mode codes and language surface.
- [x] 3.2 Add synchronous try/finally GameTests for local/centered geometry,
  centred fixed points, same-layer max semantics, cap five and unloaded chunks.
- [x] 3.3 GameTest mode-aware snapshots, worn targets and exact visualization
  relative/buildable sets from the production scan.
- [x] 3.4 Document the exact gamerule command, default and algorithms in README
  and the datapack material-profile guide.

## 4. Verification

- [x] 4.1 Run Java 25 / TotemCore 0.7.14 isolated `test compileGametestJava`.
- [x] 4.2 Run a clean complete Fabric Server GameTest suite.
- [x] 4.3 Run `build`, all three strict OpenSpec validations, JSON/lang parity
  and `git diff --check`.
- [x] 4.4 Confirm no client Screen, Observer provider, payload format or PNG
  baseline was changed for this expansion-mode capability.
