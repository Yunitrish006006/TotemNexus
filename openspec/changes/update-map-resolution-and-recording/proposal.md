## Why

0.3.27 displays newly recorded scale-zero pages at intermediate zoom through
texture minification. This does not meet the user's requirement that each zoom
use its own resolution. Recording also revisits a player-relative sample square
instead of completing world-anchored work, leaving gaps as the player moves.

## What Changes

- Select real map detail at `baseScale - log2(zoom)`, bounded at scale zero.
  A scale-four map uses 16, 8, 4, 2 and 1 blocks per terrain pixel at
  100%, 200%, 400%, 800% and 1600% respectively.
- Build intermediate, map-owned resolution levels from recorded terrain with
  explicit known coverage. Do not substitute a minified scale-zero texture.
- Replace the moving-relative scan with bounded, world-anchored missing-first
  work, prioritizing the player's traversed corridor and retaining unfinished
  eligible work while its chunks remain loaded.
- Make detail delivery converge under repeated requests without starving dirty
  pages; retain explicit CPU, queue, storage, texture and network bounds.
- Preserve coarse fallback, map transfer/copy/lock semantics, cursor anchoring,
  button priority and read-only module-owned Observer rendering.

## Impact

- Affected capability: `lodestone-interface-activation`.
- Implementation owner: TotemNexus sampling, detail storage, networking,
  production map composition and tests. No teleport/permission rule changes.
- Optional consumer: TotemObserver's reflected terrain relay. Its signature and
  protocol must be stabilized before implementation; changing that repository
  requires expanded write scope, not an incidental edit.
- The approved design supersedes the
  intermediate-zoom minification acceptance scenario added for 0.3.27 in
  `fix-map-recording-and-unloaded-teleport`.
- Approved by the user on 2026-10-05, including direct publication after verification.
  Candidate version: 0.3.28. Implementation and release evidence are tracked in
  `tasks.md` and `.github/staging/release-validation-0.3.28.json`; source edits or
  passing local tests alone are not a publication claim.
