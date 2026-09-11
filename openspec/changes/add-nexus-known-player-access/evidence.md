# Shared Core player directory and Nexus access selection

## Ownership and behavior

- Core 0.7.20 owns `TotemPlayerDirectoryApi`, login identity updates, world persistence (`totem:known_players`), existing player-file import, exact UUID/unambiguous name lookup, substring search and pagination. Names are last seen; UUID remains the permission identity. No network profile lookup occurs.
- Nexus 0.3.21 consumes the public Core API. Its own endpoint retains held-book/context, discovery, range, loaded-lodestone and owner/administrator authority checks. Unknown identities fail closed; owner entries are excluded. Queries coalesce per player and run at most every five ticks; each response contains at most six entries.
- The native `NexusAccessScreen` supports offline role grants/revocations and UUID tooltips. Its `nexus_access@1` provider reconstructs that production screen in read-only mode, redacts search input and rejects mismatched/stale semantic updates. Existing Nexus protocols are unchanged by this access feature.
- Observer relay tests explicitly cover missing `nexus_access` administrator and allowed providers, displaying metadata only.

## Validation

- Core `test build`: 56 unit tests passed, including persistence save/reload, rename/ambiguity and player-file import cases; see `/tmp/core-player-directory-validation.log`.
- Nexus `test build compileGametestJava prepareAccessE2e runGameTest`: 95 unit tests and all 107 server GameTests passed. Server assertions exercise offline UUID grants, case-insensitive name revocation, copied-book denial, missing-book denial and bounded pages. Repeated test worlds use unique fixture names to avoid previous saved test identities.
- Nexus production JAR `runAccessProductionClientGameTest`: PASS (`/tmp/nexus-access-production.log`). Provider registration, initial/later state, exact family/variant/protocol, monotonic snapshots/cursors, mutation/packet suppression and close lifecycle were tested on the client thread. Screenshots: `run/screenshots/0010_nexus-observer-access-administrator.png`, `0011_nexus-observer-access-allowed.png`.
- Dedicated server plus two real clients: PASS (`/tmp/nexus-access-e2e.log`). Verified offline permission grant, live Core directory, Observer initial/update/close/reopen, private search redaction, denied observer mutations and creative-mode restoration after Stop. Screenshots in `build/access-e2e/results/`.
- Observer production JAR without Nexus `runAccessAbsentClientGameTest`: PASS (`/tmp/observer-access-absent.log`), including both new role variants. No substitute screen or framebuffer relay is introduced.
- Final `test build accessE2eClasses`: PASS (`/tmp/nexus-access-final-build.log`), including a second successful 107-test server run against the final Core JAR.
- Native en_us and zh_tw screenshots were visually inspected at their rendered size: text and controls fit, online/offline status is localized, private search text appears only on the target, and the remote cursor is visible. No sprite/icon textures changed; the strict 16×16 asset gate is not applicable.
- TotemWorkspace impact/test_plan completed for Core, Nexus and Observer. Every other active Core consumer's current dependency range accepts 0.7.20; all 54 pre-existing public API class files are byte-for-byte identical between the 0.7.19 and 0.7.20 JARs. Existing payloads remain unchanged. Runtime cross-module validation covers Core/Nexus/Observer; unrelated consumer builds were not claimed.
- OpenSpec strict validation and `git diff --check`: PASS.
- Independent read-only review by `observer_publisher_review`: PASS after query-cost, UUID disambiguation and E2E process cleanup fixes. Final review found no remaining correctness issues.

## Local release boundary

Changes remain uncommitted and unpublished. Nexus CI/release version checks require Core 0.7.20. Set repository variable `NEXUS_CORE_REF` to the reviewed, committed Core 0.7.20 SHA before running clean CI or publishing; the prior pinned fallback intentionally fails the version gate. No remote variable was changed. The repository's OpenSpec project instructions require explicit user approval for commits/publication/deployment.
