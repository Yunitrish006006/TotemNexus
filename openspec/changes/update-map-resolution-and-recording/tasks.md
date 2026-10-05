## 1. Proposal and contracts

- [x] 1.1 Inspect live resolution, sampling, storage and delivery paths.
- [x] 1.2 Record per-zoom semantics, measured-versus-inferred evidence and acceptance criteria.
- [x] 1.3 Obtain proposal approval before production implementation (user: finish and directly publish the new Modrinth version).
- [x] 1.4 Stabilize storage/cache bounds, migration and desired-scale/Observer contracts; preserve the existing Observer signature without sibling edits (see implementation contract in design.md).

## 2. Implementation after approval

- [x] 2.1 Add resolution selection and deterministic palette-aware reduction with known-coverage tests.
- [x] 2.2 Implement bounded derived levels, migration/rebuild and lock/copy isolation.
- [x] 2.3 Implement world-anchored missing-first recording and fair bounded catch-up.
- [x] 2.4 Implement convergent bounded page delivery and selected-resolution production rendering.
- [x] 2.5 Replace the superseded intermediate-zoom minification acceptance scenario.

## 3. Verification and release

- [x] 3.1 Run impact/test plan and review affected consumers after source edits.
- [x] 3.2 Run Java 25 wrapper build, unit/server GameTests and actual moving-path budget/coverage comparisons (113 unit tests, 120 server GameTests; production-worker comparison plus full scheduler solo/shared/offhand route integration with real deadlines and per-scale completion, not an FPS benchmark).
- [x] 3.3 Run native-scale owner/Observer client screenshots, production runtime and dedicated three-JVM E2E (20 combinations per client runner; final isolated E2E passed without assertion changes).
- [x] 3.4 Obtain independent implementation/evidence review without loosening assertions (read-only reviewer verified the full scheduler gap closure and final server build).
- [ ] 3.5 Prepare new version and release evidence, record final JAR hashes; verify exact-source CI.
- [ ] 3.6 Publish only with applicable authorization, then read back Modrinth metadata and artifact hash.
