# Close backpack recovery and portable teleport validation

Implementation: Core 0.7.19, Nexus 0.3.19, Remnant 0.2.24. Minecraft 26.2, Java 25, Fabric Loader 0.19.3 and Fabric API 0.154.2+26.2. The user subsequently authorized publication and remote synchronization. All three versions were uploaded and verified; Modrinth project review remains pending. See `.github/staging/release-publication-0.3.19.json`.

## Behavior and coverage

- Exact bound Remnant item entity updates the death node location. Wrong owner/entity reports are rejected. Teleport preparation rechecks movement and actual live entity availability; the original search deadline is retained across movement. Close landing prefers 2–4 blocks, permits at most 6 horizontal / 3 vertical blocks and a bounded walk of at most 10 steps. Sealed-wall and no-nearby-ground fixtures reject inaccessible destinations without charging.
- `totem-nexus:phasing` is one Nexus-owned effect, icon and translation. It implements Weakness I, full native night vision, invisibility and Resistance I. Server tests exercise actual damage, resistance bypass and stronger independent effects; client tests check synchronization and removal without changing independent potion effects.
- Successful own-death teleport through any supported selecting interface grants indefinite searching protection. The exact backpack's successful inventory pickup begins the one-shot 60-tick suffix. The real Remnant integration fixture uses production spawn-before-bind/tag order, actual `ItemEntity.playerTouch`, and separate non-retried assertions at ticks 59 and 60. Full survival inventory and foreign pickup do not recover the node. Destruction handles an emptied item stack; movement/unloading does not falsely count as destruction.
- Player location is the portable source. Compass, recovery compass and map work beyond the former eight-block gate. Recognized permitted array structure gives a stability bonus; empty space inside the scanner's bounding envelope does not. Field/low stability alone cannot block a valid teleport. Destination permission, held identity, painted map coverage, resources, management range and reservation checks remain server authoritative.
- Existing Nexus production Screens and protocol 3 owner providers reconstruct player-source payloads. No relay renderer, framebuffer path or protocol replacement was added.

## Executed results

| Validation | Result | Evidence |
| --- | --- | --- |
| Core build and unit tests | 53 passed | `core-build.log`, `unit-results.json` |
| Remnant build, unit tests and standalone server GameTests | 2 unit + 59 required GameTests passed | `remnant-build.log` |
| Nexus unit/build phase and server GameTests with actual Remnant 0.2.24 JAR | 88 unit + 101 required GameTests passed | `nexus-mixed-run.log` through the completed server/build phase |
| Full standalone Nexus client suite | Passed all eight entrypoints | `nexus-client-standalone.log`; current screenshots in `screenshots/` |
| Module-present VanillaTweaks Observer integration | Passed; 10 screenshots | `observer-integration.log` |
| Dedicated server + Target + Observer JVMs | Passed; 210 result files including screenshots, no failure files | `observer-e2e.log`, `observer-e2e-results/`, `e2e-server.log`, `e2e-target.log`, `e2e-observer.log` |
| Built distribution JAR, official namespace Phasing and Nexus provider tests | Passed both entrypoints; 14 screenshots | `production-runtime.log`, `production-screenshots/` |
| OpenSpec strict validation and whitespace checks | Passed | `improve-death-recovery-and-portable-teleport`; `git diff --check` in all three modules |
| Native effect art | 16×16 RGBA strict validator passed; four native client captures reviewed | `screenshots/0000_nexus-phasing-en_us-scale-2.png` through `0003_nexus-phasing-zh_tw-scale-3.png` |

The initial combined server/client run completed its 101 server tests but its later legacy `TotemAdvancementsVisualGameTest` looked for `totem:main` when Remnant was loaded. Current Remnant owns `totem-remnant:main`. This unrelated mixed visual fixture is not counted as a pass; the entire intended standalone Nexus client suite was then run separately and passed. The targeted cross-module server and Observer integration coverage uses the actual new Remnant JAR.

The first production-runtime launch found the existing Nexus development task had normalized the shared local Fabric API cache to `named`. The retry explicitly loads unmodified official Fabric API JARs from the isolated, already verified Observer cache. The production mod JARs were not changed to accommodate this fixture issue. The saved production init script records the exact dependency paths.

## Impact and independent review

`impact.json` and `test-plan.json` preserve the actual intelligence results. Core's only gameplay-contract change is a default no-op `DeathBackpackNodeLifecycle.moved` callback. Old implementations remain valid on new Core; new callers require Core 0.7.19, enforced by Nexus and Remnant metadata. Review inspected all ten Core consumers: only Nexus/Remnant use this lifecycle API. DiscordBridge subscribes to the unchanged recovery event; other APIs and Observer contracts are unchanged. Runtime integration additionally loads Automata, Villagers, Locksmith and VanillaTweaks against the new Core.

Independent review was performed by `/root/release_review` (Euler). Reported issues were fixed before the successful final server run: Remnant binding-key recognition, post-spawn tracking, held bound-unit versus player-source validation, injected quote store consistency, movement deadline, pickup test retry masking and exact 59/60-tick expiry. The reviewer confirmed no remaining code blockers; final runtime evidence is recorded above.

## Artifacts and reproduction

Installable JARs and SHA-256 manifest are in `/home/thomas/workspace/artifacts/nexus-close-recovery-20260910`. Upgrade Core, Nexus and Remnant together. Sources, tests, approved OpenSpec and artifacts are left locally reviewable.

Saved `remnant-integration.init.gradle`, `production.init.gradle`, `production-fabric.mod.json`, `observer-integration.sh` and `observer-e2e.sh` record this machine's exact harness inputs. Their temporary absolute paths must be adjusted if replayed on another machine. Unit and server tests live in the owning repositories. Full standalone client command: `JAVA_HOME=/home/thomas/.local/temurin-25 PATH=/home/thomas/.local/temurin-25/bin:$PATH xvfb-run -a ../TotemCore/gradlew runClientGameTest -PtotemCoreJar=/home/thomas/workspace/TotemCore/build/libs/totem-core-0.7.19.jar` from TotemNexus.
