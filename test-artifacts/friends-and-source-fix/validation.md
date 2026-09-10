# Nexus friends and teleport source fix — 2026-09-10

Local patched artifact: `/home/thomas/workspace/artifacts/nexus-friends-source-fix-20260910/totem-nexus-0.3.19.jar`.
SHA-256: `78bea32108fd02af395aa8daa98ffe6472c849840f5c650efe585f966e81587a`.
Version remains 0.3.19; this is a local patch, not a published release.

## Behavior

- Compass use on a lodestone uses the clicked lodestone as teleport source. Air use uses the player. Recovery compass and map routing receive regression coverage.
- Online mutual friends return as teleport targets. Server validation checks relationship, online/living state and map coverage; client target entries use coarse positions. Revocation invalidates stale requests.
- Nearby selected lodestones can provide array/build-site previews from a player-source interface without replacing the teleport source. Map-authorized previews retain their map authority while the player builds and revalidate painted coverage.
- The screen identifies player/lodestone source. Observer snapshots preserve the material-page state through optional bounded protocol-3 metadata and reject malformed metadata before mutating a projection. Observer controls remain read-only.

## Validation

| Check | Result | Evidence |
| --- | --- | --- |
| Server GameTests | All 106 required tests passed | `final-build.log`, lines 7180–7181 |
| Unit tests | 88 passed; zero failures/errors/skips | `unit-results.json`, `resource-build.log` |
| Build excluding already-completed server tests | Passed | `resource-build.log` |
| Full development client GameTests | Passed | `client.log` |
| Packaged production client GameTests | Passed, using actual Nexus JAR | `production.log`, `production.init.gradle` |
| Dedicated server + target + Observer clients | Passed; 210 result artifacts; no failure markers; both completion markers and server cleanup marker present | `e2e-results/`, `e2e-*.log` |
| Whitespace validation | `git diff --check` passed | Final local command |
| OpenSpec | Strict validation passed for `improve-death-recovery-and-portable-teleport` | Development validation |
| Intelligence impact/test plan | Refreshed | `impact-final.json`, `test-plan-final.json` |
| Independent review | No outstanding findings after corrections | `/root/nexus_review` |

Server GameTests passed before the enclosing build hit the outdated expected locale-key count (429 versus the new 430); the assertion was corrected and the subsequent unit/build run passed. Earlier client test-fixture allocation and navigation issues were corrected before the successful full rerun. Independent review prompted retained-map preview revalidation and parsing Observer metadata before mutation; both corrections have regression coverage.

Screenshots in `screenshots/` cover friend selection and enabled nearby preview controls in English and Traditional Chinese at GUI scales 2 and 3, plus disabled controls in the Observer material projection. Representative screenshots were visually inspected. Existing material-page clipping at scale 3 remains outside this behavior fix; no claim of a complete UI layout redesign is made.

The packaged artifact checksum was compared with the JAR used by runtime tests after completion and matched. No release, deployment, commit, or push was performed.

## Authorized 0.3.20 release follow-up

The user subsequently requested a version update and upload. The 0.3.20 build and 88 unit tests passed. Comparing every archive entry against the tested local patch found only `fabric.mod.json` changed, and its sole semantic change is version 0.3.19 → 0.3.20. The release hash and comparison evidence are in `.github/staging/release-validation-0.3.20.json`. A further independent review found no release blockers. Publication uses the existing workflow, which requires a successful Build for the exact pushed commit and the recorded SHA-512 before upload.
