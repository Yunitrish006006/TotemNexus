# Recovery rescue and map routing validation

Base: TotemNexus `93bb7387e3ed1f77401bc1ebf35b3cbab6877a0f` (0.3.16, equal to fetched origin/master before edits). Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.154.2+26.2, Java 25, TotemCore 0.7.18.

## Implementation

- `TeleportInterfaceType`, quote policy and production Screen expose compass/recovery lists and default source-distance sorting. Recovery uses a dedicated `recovery_compass` Observer variant at protocol 3.
- `NexusInterfaceAccess`, production `NexusSpaceUnitAuthority`, map source/context and painted coverage policy re-resolve held identity, permissions and map pixels. Map favorites remain independent from discovery. Source changes never rewrite the map registry or vanilla MapId/data; expansion retains its original anchor.
- `NexusRecoveryGrace`, `RecoveryGraceState` and `NexusRecoveryGraceSavedData` implement server sessions, persistent consumed player/node pairs, exact-backpack suffix and cancellation. Two server mixins preserve vanilla invisibility effects and observe actual projectile launch (chunk loading an old arrow does not cancel).
- Safe landing, repeated authority validation, route reservation, wear and costs retain the production path. Added loaded-state guards prevent map maintenance from loading remote chunks.
- README, OpenSpec and native handbook English/Traditional Chinese/Spanish text describe the new rules. No item textures or component formats changed.

## Regression coverage

| Requirement | Executed coverage |
| --- | --- |
| Both compasses select and complete ordinary teleport | `NexusInterfaceLifecycleGameTest` real countdown/landing tests; native client list selection |
| Own death rescue only after successful safe teleport | `completedOwnDeathRescueGrantsGraceOnlyAfterSafeLanding` |
| Discovery, rebind and native custom component preservation | `compassFamilyRequiresDiscoveryAndCanRebindThroughRealBlockCallback` |
| Painted undiscovered map source/destination, no anchor/MapId mutation | `mapUsesPaintedUndiscoveredSourceWithoutChangingAnchorAndRejectsForgedInputs`; actual non-anchor map teleport completion |
| Painted remote marker does not force-load; expansion retains center | `mapPayloadMaintenanceNeverLoadsPaintedRemoteChunk` |
| Map favorite authorization without compass discovery | Production favorite success and forged out-of-coverage rejection in routing GameTest |
| Quarter deviation | `TeleportInterfaceQuotePolicyTest` and successful safe death landing |
| Recovery ordinary target / compass-map death ineligible | Real ordinary recovery teleport plus grace eligibility GameTest |
| Correct backpack 60-tick suffix, wrong backpack ignored, original effect retained | `exactBackpackRecoveryGetsThreeSecondsAndPreservesExistingInvisibility` using the production death lifecycle adapter |
| 1200-tick timeout and no refresh | `rescueTimeoutDoesNotRefreshAndEndsWithoutPotion` |
| Attack/PvP/projectile/new teleport/invalidation/logout | Fabric attack/damage/disconnect adapter invocations, actual Mojang projectile spawn, production startTeleport and SavedData invalidation |
| Persistent once-only grant and exact node/backpack matching | `RecoveryGraceStateTest` including SavedData codec roundtrip |
| Forged/stale authority input | Real held MapId/anchor validation, wiped coverage, private and outside nodes; existing changed-held-item countdown cancellation |
| Observer production rendering/read-only behavior | Nexus provider client tests and companion VanillaTweaks integration/E2E evidence |

## Results

- Unit tests: **87 passed**, zero failures/errors/skips; [suite counts](unit-results.json).
- Server GameTests: **91 required passed**; [full test/build log](unit-gametest-build.log).
- Nexus client GameTests: **all 7 entrypoints passed**, including recovery destination selection and owner-provider updates/input/cursor/close; [log](client-gametest.log).
- Dedicated production server: Minecraft 26.2 + Core 0.7.18 + Nexus 0.3.16, port 25597; startup reached `Done`, then explicit `stop`, all dimensions saved, Gradle exit 0; [log](dedicated-server-smoke.log).
- OpenSpec strict validation and `git diff --check`: passed.
- Companion VanillaTweaks: **87 unit tests, 17 required server GameTests and build passed** after the exact recovery variant allowlist fix. Its module-present production sender integration passed; all **26 development client and 26 production-runtime client entrypoints passed**, including absent-owner recovery metadata.
- Dedicated **three-JVM E2E passed all six variants**: compass → recovery_compass → map → management → friends → registration. The final run includes translated recovery UI, initial/update snapshots, selection, rendered cursor, input/packet suppression and close cleanup.
- Companion VanillaTweaks commit: `42db88f3061c869d714902b2322deb30d752eec5`; [full companion results and screenshots](../../../TotemVanillaTweaks/test-artifacts/nexus-recovery-validation.md). Relay JAR SHA-256: `81f049621f5cdab083828bf49f70e70ea0d7dc55a68cba10c1c578c94be5f505`.
- [Complete changed-file list for both repositories](changed-files.txt). Both commits are local; no release or push was performed.

Commands used from TotemNexus (Java home `/home/thomas/.local/temurin-25`):

```sh
../TotemCore/gradlew test runGameTest build --console=plain
xvfb-run -a ../TotemCore/gradlew runClientGameTest --console=plain
../TotemCore/gradlew -I /tmp/nexus-recovery-smoke.gradle runServer --console=plain
```

The smoke override selects an isolated test world and port 25597; it receives `stop` through standard input. Final `test build` passed again after correcting map-source error translations, including all 87 unit tests and 91 server GameTests. Final Nexus JAR SHA-256: `a293e7d3ed50803c5eda8f3a1c157fe67dfb6a53b8af95f557de83f08c5f2d8e`. The preceding client and dedicated smoke runs exercised the same production Java code before those three error-message translations were corrected; dedicated Observer E2E uses the final packaged Nexus JAR.

Native screenshots were visually inspected at 1280×720: destination list and selected teleport target are present, recovery has no canvas, and English/Traditional Chinese handbook text remains within the vanilla book pages.

- [Recovery list, English](../screenshots/recovery-rescue-and-map-routing/recovery-compass-en_us.png)
- [Recovery list, Traditional Chinese](../screenshots/recovery-rescue-and-map-routing/recovery-compass-zh_tw.png)
- [Owner Observer reconstruction](../screenshots/recovery-rescue-and-map-routing/observer-recovery-compass.png)
- [Rescue handbook, English](../screenshots/recovery-rescue-and-map-routing/recovery-handbook-en_us.png)
- [Rescue handbook, Traditional Chinese](../screenshots/recovery-rescue-and-map-routing/recovery-handbook-zh_tw.png)

## Review and scope

TotemWorkspace resolve/orchestrate/context selected guarded parallel work with primary-owned Nexus implementation, read-only contract discovery and independent review, and bounded VanillaTweaks relay integration work. Post-edit impact/test-plan required server authority, client runtime, owner-present/absent Observer checks, input suppression, privacy, close lifecycle and three-JVM E2E.

Independent review found and verified fixes for loaded-state maintenance, immediate foreign-recovery invalidation and map favorites. No remaining findings in the bounded production review. Core and relay production API signatures were unchanged. The dedicated end-to-end run exposed a missing server relay allowlist entry for recovery_compass; the companion VanillaTweaks change adds that exact variant while retaining rejection of unknown variants.

The live authority is `NexusSpaceUnitAuthority`. Dormant extracted `NexusTeleportCutover` classes have no production activation caller and retain older policies; this validation does not claim that inactive alternative path implements the new behavior.
